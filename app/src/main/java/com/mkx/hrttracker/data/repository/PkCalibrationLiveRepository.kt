package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvent
import java.time.Clock
import java.time.Instant
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

    // Re-evaluates on every Home-data generation bump, so a write that lands
    // mid-read simply triggers the next evaluation.
    val liveState: StateFlow<PkCalibrationLiveState> = combine(
        storageRepository.observeHomeDataGeneration(),
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
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = PkCalibrationLiveState.Loading,
        )

    fun retry() {
        retryVersion.value = retryVersion.value + 1L
    }

    private suspend fun evaluate(): PkCalibrationLiveState {
        val panels = bloodTestRepository.getPanels()
        val entries = medicationLogRepository.getEntries()
            .filter { it.category == MedicationCategory.ESTRADIOL }
        val profile = userProfileRepository.getCurrentProfile()
        val metadata = storageRepository.getAllMetadata()

        val labs = panels.flatMap { panel ->
            panel.results
                .filter { (it.analyte as? BloodTestResultAnalyte.Builtin)?.key == BloodAnalyteKey.E2 }
                .map { result ->
                    PkCalibrationLab(
                        resultId = result.uuid,
                        collectedAtEpochMillis = panel.collectedAt.toEpochMilli(),
                        valuePgml = result.canonicalValue,
                    )
                }
        }
        // An empty history is anchored by any origin; fall back to the clock so
        // a user with no data still lands on NO_USABLE_LABS and its CTA.
        val origin = (labs.map { it.collectedAtEpochMillis } +
                entries.map { it.appliedAt.toEpochMilli() })
            .minOrNull() ?: clock.millis()
        val anchor = Instant.ofEpochMilli(origin)
        val doseEvents = entries.map { entry ->
            entry.buildEstradiolPkDoseEvent(anchor)
                ?: return PkCalibrationLiveState.Unavailable(
                    PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
                )
        }
        // Same resolution as the Home projection: an unset Current Weight
        // falls back to the app-wide default.
        val input = PkCalibrationInput(
            labs = labs,
            doseEvents = doseEvents,
            originEpochMillis = origin,
            weightKg = profile.weightKg
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: PkMedicationSimulation.DefaultBodyWeightKg,
            metadata = metadata,
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
