package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkChartDomain
import java.time.Clock
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

enum class PkCalibrationLiveUnavailableReason {
    SOURCE_READ_FAILED,
    SOURCE_DATA_INVALID,
    RENDER_DOMAIN_UNAVAILABLE,
}

sealed interface PkCalibrationLiveState {
    data object Loading : PkCalibrationLiveState

    data class Unavailable(val reason: PkCalibrationLiveUnavailableReason) :
        PkCalibrationLiveState

    data class Available(
        val input: PkCalibrationInput,
        val evaluation: PkCalibrationEvaluation,
        val domain: PkChartDomain,
        /** Null for a non-READY evaluation. */
        val render: PkCalibrationRenderResult?,
    ) : PkCalibrationLiveState
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationLiveRepository @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val userProfileRepository: UserProfileRepository,
    private val storageRepository: PkCalibrationStorageRepository,
    private val clock: Clock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @AppScope appScope: CoroutineScope,
) {
    private val retryVersion = MutableStateFlow(0L)

    // Keyed on Home snapshot writes, not the durable generation:
    // runHomeDataMutation bumps the generation BEFORE the write commits, so a
    // generation-triggered read would see pre-write data and never re-run.
    // The snapshot is written after the mutation, so it is the post-write
    // signal; it is also rewritten on date change and projection expiry, so
    // the render domain below tracks the window Home draws.
    val liveState: StateFlow<PkCalibrationLiveState> = combine(
        storageRepository.observeHomeSnapshotWrites(),
        retryVersion,
    ) { _, _ -> }
        .transformLatest {
            emit(PkCalibrationLiveState.Loading)
            try {
                emit(evaluate())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emit(PkCalibrationLiveState.Unavailable(
                    PkCalibrationLiveUnavailableReason.SOURCE_READ_FAILED
                ))
            }
        }
        .stateIn(
            scope = appScope,
            // Eager: evaluated at app start and kept warm across generations,
            // so the calibration page has its section on the first frame
            // instead of waiting for a fresh evaluation on every entry.
            started = SharingStarted.Eagerly,
            initialValue = PkCalibrationLiveState.Loading,
        )

    fun retry() {
        retryVersion.value = retryVersion.value + 1L
    }

    private suspend fun evaluate(): PkCalibrationLiveState {
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
        ) ?: return PkCalibrationLiveState.Unavailable(
            PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
        )
        // The render domain tracks the chart's visible window around the
        // current clock: the widest selectable past span (plus a day of
        // start-of-day flooring slack) through the widest future span.
        val nowMillis = clock.millis()
        val domain = PkChartDomain.create(
            rangeStartEpochMillis = nowMillis - RENDER_PAST_MILLIS,
            rangeEndEpochMillis = nowMillis + RENDER_FUTURE_MILLIS,
            samplingIntervalMillis = SIX_HOURS_MILLIS,
        ) ?: return PkCalibrationLiveState.Unavailable(
            PkCalibrationLiveUnavailableReason.RENDER_DOMAIN_UNAVAILABLE
        )
        return withContext(defaultDispatcher) {
            val evaluation = PkCalibrationEngine.evaluate(input)
            PkCalibrationLiveState.Available(
                input = input,
                evaluation = evaluation,
                domain = domain,
                render = evaluation.renderFor(domain),
            )
        }
    }

    private companion object {
        private const val SIX_HOURS_MILLIS = 6L * 60L * 60L * 1_000L
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        private val RENDER_PAST_MILLIS =
            (HomeE2ChartWindowOption.entries.maxOf { option -> option.pastDays } + 1L) *
                DAY_MILLIS
        private val RENDER_FUTURE_MILLIS =
            HomeE2ChartWindowOption.entries.maxOf { option -> option.futureDays } * DAY_MILLIS
    }
}
