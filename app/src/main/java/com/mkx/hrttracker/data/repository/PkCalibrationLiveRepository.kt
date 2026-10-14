package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

data class PkCalibrationLive(
    val input: PkCalibrationInput,
    val evaluation: PkCalibrationEvaluation,
    val domain: PkChartDomain,
    /** Null for a non-READY evaluation. */
    val render: PkCalibrationRenderResult?,
)

/**
 * One finished evaluation pass. [live] is null after a failed read or when the
 * inputs cannot form an evaluation. [build] is the snapshot build it rendered,
 * null for the page's own evaluation before the first build.
 */
class PkCalibrationLiveResult(
    val live: PkCalibrationLive?,
    val build: HomeCalibrationBuild? = null,
)

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationLiveRepository @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val userProfileRepository: UserProfileRepository,
    private val storageRepository: PkCalibrationStorageRepository,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val clock: Clock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @AppScope appScope: CoroutineScope,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val retryVersion = MutableStateFlow(0L)

    /**
     * Null until the first evaluation finishes. A re-evaluation keeps the
     * previous result until it completes, so the calibration section never
     * blinks out on a snapshot write.
     *
     * The solve is shared with the Home snapshot build: every build publishes
     * its calibration and this only renders it for the page's window. Before
     * the first build of the process (a usable snapshot skips the build) the
     * page solves once itself, so it never waits on a snapshot write.
     */
    val liveState: StateFlow<PkCalibrationLiveResult?> = combine(
        homeSnapshotRepository.calibrationBuilds,
        retryVersion,
    ) { build, _ -> build }
        .transformLatest { build ->
            try {
                emit(PkCalibrationLiveResult(if (build != null) render(build) else evaluate(), build))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                // Surfaced as the numeric-failure row with Try again; the log
                // is the only trace of why.
                diagnosticsLogger.warning(TAG, "pk_calibration_live_failed", exception)
                // With the build it was for: the failure row (and Try again)
                // must replace the held section, not stay behind it.
                emit(PkCalibrationLiveResult(null, build))
            }
        }
        .stateIn(
            scope = appScope,
            // Eager: evaluated at app start and kept warm across generations,
            // so the calibration page has its section on the first frame
            // instead of waiting for a fresh evaluation on every entry.
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /**
     * True while a dose, lab or other data change is being re-solved; the
     * calibration page holds its lab adjustment section until the rebuild lands.
     * The rebuild's counter drops when its write returns, before [liveState]
     * has rendered the build it published, so this stays true until the
     * rendered result is the newest build. Only a forced build counts, as on
     * Home: a launch, date-change or expiry build landing while the page is
     * open re-renders without the indicator. The build's forced flag is
     * observed, so a forced request joining after publication promotes it
     * here even if its brief counter transition is conflated away.
     */
    val updating: StateFlow<Boolean> = combine(
        homeSnapshotRepository.forcedRebuildInFlight,
        homeSnapshotRepository.calibrationBuilds.flatMapLatest { build ->
            build?.forcedChanges?.map { forced -> build.takeIf { forced } } ?: flowOf(null)
        },
        liveState,
    ) { forced, forcedBuild, live -> forced || (forcedBuild != null && live?.build !== forcedBuild) }
        .stateIn(scope = appScope, started = SharingStarted.Eagerly, initialValue = false)

    /** Re-solves: a forced snapshot rebuild publishes a new build to render. */
    fun retry() {
        retryVersion.value = retryVersion.value + 1L
        homeSnapshotRepository.refreshHomeSnapshotAsync(force = true)
    }

    private suspend fun render(build: HomeCalibrationBuild): PkCalibrationLive? {
        val evaluation = build.evaluation ?: return null
        val nowMillis = clock.millis()
        val domain = renderDomain(nowMillis) ?: return null
        return withContext(defaultDispatcher) {
            PkCalibrationLive(
                input = build.input,
                evaluation = evaluation,
                domain = domain,
                render = evaluation.renderFor(domain, statusRange = build.statusRange),
            )
        }
    }

    private suspend fun evaluate(): PkCalibrationLive? {
        val panels = bloodTestRepository.getPanels()
        val entries = medicationLogRepository.getEntries()
        val profile = userProfileRepository.getCurrentProfile()
        val metadata = storageRepository.getAllMetadata()
        val input = buildPkCalibrationInput(
            labs = panels.toPkCalibrationLabs(),
            entries = entries,
            weightKg = profile.weightKg,
            metadata = metadata,
            fallbackOriginEpochMillis = clock.millis(),
        )
        val nowMillis = clock.millis()
        val domain = renderDomain(nowMillis) ?: return null
        // The status describes Home's visible chart, as the build path does;
        // the domain only has to cover it.
        val statusRange = homeSnapshotRepository.visibleChartRange(
            now = LocalDateTime.ofInstant(clock.instant(), clock.zone),
            zoneId = clock.zone,
        )
        return withContext(defaultDispatcher) {
            val evaluation = PkCalibrationEngine.evaluate(input)
            PkCalibrationLive(
                input = input,
                evaluation = evaluation,
                domain = domain,
                render = evaluation.renderFor(domain, statusRange = statusRange),
            )
        }
    }

    // The render domain covers any option's visible window around the
    // current clock: the widest selectable past span (plus a day of
    // start-of-day flooring slack) through the widest future span. The
    // status range narrows it to the current option's window.
    private fun renderDomain(nowMillis: Long): PkChartDomain? {
        return PkChartDomain.create(
            rangeStartEpochMillis = nowMillis - RENDER_PAST_MILLIS,
            rangeEndEpochMillis = nowMillis + RENDER_FUTURE_MILLIS,
            samplingIntervalMillis = SIX_HOURS_MILLIS,
        )
    }

    private companion object {
        private const val TAG = "PkCalibrationLiveRepository"
        private const val SIX_HOURS_MILLIS = 6L * 60L * 60L * 1_000L
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        private val RENDER_PAST_MILLIS =
            (HomeE2ChartWindowOption.entries.maxOf { option -> option.pastDays } + 1L) *
                DAY_MILLIS
        private val RENDER_FUTURE_MILLIS =
            HomeE2ChartWindowOption.entries.maxOf { option -> option.futureDays } * DAY_MILLIS
    }
}
