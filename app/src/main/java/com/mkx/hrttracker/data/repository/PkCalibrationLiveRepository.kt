package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationE2LabSource
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEngineEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationIdentityPolicy
import com.mkx.hrttracker.model.pk.PkCalibrationInputSnapshot
import com.mkx.hrttracker.model.pk.PkCalibrationMedicationEventSource
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCompound
import com.mkx.hrttracker.model.pk.PkRoute
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvent
import com.mkx.hrttracker.model.pk.isStableAsciiIdentity
import java.time.Clock
import java.time.Instant
import java.util.Collections
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

enum class PkCalibrationLiveUnavailableReason {
    INPUT_GENERATION_UNSTABLE,
    SOURCE_READ_FAILED,
    SOURCE_DATA_INVALID,
    RENDER_DOMAIN_UNAVAILABLE,
    RENDER_UNAVAILABLE,
}

/** Explicit identity/config inputs for the live evaluation; source records never fabricate these. */
class PkCalibrationRuntimePolicy private constructor(
    val identityPolicy: PkCalibrationIdentityPolicy,
    val config: PkCalibrationConfig,
    val forwardModelVersion: String,
    val calibrationModelVersion: String,
) {
    companion object {
        fun create(
            identityPolicy: PkCalibrationIdentityPolicy,
            config: PkCalibrationConfig,
            forwardModelVersion: String,
            calibrationModelVersion: String,
        ): PkCalibrationRuntimePolicy? {
            if (!forwardModelVersion.isStableAsciiIdentity() ||
                !calibrationModelVersion.isStableAsciiIdentity()
            ) {
                return null
            }
            return PkCalibrationRuntimePolicy(
                identityPolicy = identityPolicy,
                config = config,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
            )
        }

        const val FORWARD_MODEL_VERSION = "hrttracker:pk-forward/v1"
        const val CALIBRATION_MODEL_VERSION = "hrttracker:route-calibration/v10"

        /**
         * App policy. Constants (user decision, 2026-08-09), revisitable from
         * QA telemetry:
         * - R_LOG = 0.0225 is the §A3 anchor (~15% log-SD from published
         *   assay CV plus the collection-timing budget).
         * - D_min = 5 pg/mL of population drug-attributable E2 for a lab to
         *   count as informative.
         */
        val Default: PkCalibrationRuntimePolicy by lazy {
            requireNotNull(
                create(
                    identityPolicy = requireNotNull(
                        PkCalibrationIdentityPolicy.researchOrTest(
                            builtinE2AnalyteId = "hrttracker:analyte/e2/v1",
                            targetHormoneId = "hrttracker:hormone/estradiol/v1",
                            // Keys are the persisted unit snapshots; exactly the
                            // units the blood-test catalog allows for E2.
                            unitIdBySourceSnapshot = mapOf(
                                BloodUnitKey.PG_ML.storageValue to "hrttracker:unit/pg-ml/v1",
                                BloodUnitKey.PMOL_L.storageValue to "hrttracker:unit/pmol-l/v1",
                                BloodUnitKey.NG_DL.storageValue to "hrttracker:unit/ng-dl/v1",
                            ),
                            eventTypeIdByRoute = mapOf(
                                PkRoute.INJECTION to "hrttracker:event/injection-dose/v1",
                                PkRoute.PATCH_APPLY to "hrttracker:event/patch-apply/v1",
                                PkRoute.PATCH_REMOVE to "hrttracker:event/patch-remove/v1",
                                PkRoute.GEL to "hrttracker:event/gel-dose/v1",
                                PkRoute.ORAL to "hrttracker:event/oral-dose/v1",
                                PkRoute.SUBLINGUAL to "hrttracker:event/sublingual-dose/v1",
                            ),
                            routeIdByRoute = mapOf(
                                PkRoute.INJECTION to PkCalibrationRoute.INJECTION.stableId,
                                PkRoute.PATCH_APPLY to PkCalibrationRoute.PATCH.stableId,
                                PkRoute.PATCH_REMOVE to PkCalibrationRoute.PATCH.stableId,
                                PkRoute.GEL to PkCalibrationRoute.GEL.stableId,
                                PkRoute.ORAL to PkCalibrationRoute.ORAL.stableId,
                                PkRoute.SUBLINGUAL to PkCalibrationRoute.SUBLINGUAL.stableId,
                            ),
                            compoundIdByCompound = mapOf(
                                PkCompound.E2 to "hrttracker:compound/e2/v1",
                                PkCompound.EB to "hrttracker:compound/eb/v1",
                                PkCompound.EV to "hrttracker:compound/ev/v1",
                                PkCompound.EC to "hrttracker:compound/ec/v1",
                                PkCompound.EN to "hrttracker:compound/en/v1",
                                PkCompound.EU to "hrttracker:compound/eu/v1",
                            ),
                        )
                    ),
                    config = requireNotNull(
                        PkCalibrationConfig.create(drugMinInformativePgml = 5.0, rLog = 0.0225)
                    ),
                    forwardModelVersion = FORWARD_MODEL_VERSION,
                    calibrationModelVersion = CALIBRATION_MODEL_VERSION,
                )
            )
        }
    }
}

/** Every value consumed by one evaluation, frozen before solver work begins. */
class PkCalibrationEvaluationContext private constructor(
    val inputGeneration: Long,
    val input: PkCalibrationInputSnapshot,
    val metadata: List<E2CalibrationMetadata>,
    val identityPolicy: PkCalibrationIdentityPolicy,
    val config: PkCalibrationConfig,
) {
    companion object {
        internal fun create(
            inputGeneration: Long,
            input: PkCalibrationInputSnapshot,
            metadata: List<E2CalibrationMetadata>,
            identityPolicy: PkCalibrationIdentityPolicy,
            config: PkCalibrationConfig,
        ): PkCalibrationEvaluationContext {
            require(inputGeneration >= 0L)
            return PkCalibrationEvaluationContext(
                inputGeneration = inputGeneration,
                input = input,
                metadata = Collections.unmodifiableList(ArrayList(metadata)),
                identityPolicy = identityPolicy,
                config = config,
            )
        }
    }
}

sealed interface PkCalibrationLiveState {
    data object Loading : PkCalibrationLiveState

    data class Unavailable(val reason: PkCalibrationLiveUnavailableReason) :
        PkCalibrationLiveState

    data class Available(
        val inputGeneration: Long,
        val context: PkCalibrationEvaluationContext,
        val evaluation: PkCalibrationEngineEvaluation,
        val domain: PkChartDomain,
        val render: PkCalibrationRenderResult?,
    ) : PkCalibrationLiveState {
        init {
            require(inputGeneration == context.inputGeneration)
        }
    }
}

fun interface PkCalibrationCurrentEvaluationContextProvider {
    suspend fun currentEvaluationContext(): PkCalibrationEvaluationContext?
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationLiveRepository @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val userProfileRepository: UserProfileRepository,
    private val storageRepository: PkCalibrationStorageRepository,
    private val runtimePolicy: PkCalibrationRuntimePolicy,
    private val clock: Clock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @AppScope appScope: CoroutineScope,
) : PkCalibrationCurrentEvaluationContextProvider {
    private val retryVersion = MutableStateFlow(0L)

    val liveState: StateFlow<PkCalibrationLiveState> = combine(
        storageRepository.observeHomeDataGeneration(),
        retryVersion,
    ) { _, _ -> }
        .transformLatest {
            emit(PkCalibrationLiveState.Loading)
            try {
                when (val read = stableRead(runtimePolicy)) {
                    is StableRead.Unavailable -> emit(PkCalibrationLiveState.Unavailable(read.reason))
                    is StableRead.Ready -> emit(evaluate(read))
                }
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

    val result: StateFlow<PkCalibrationResult?> = liveState
        .map { state -> (state as? PkCalibrationLiveState.Available)?.evaluation?.result }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000L), null)

    val render: StateFlow<PkCalibrationRenderResult?> = liveState
        .map { state -> (state as? PkCalibrationLiveState.Available)?.render }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun retry() {
        retryVersion.value = retryVersion.value + 1L
    }

    override suspend fun currentEvaluationContext(): PkCalibrationEvaluationContext? {
        val available = liveState.value as? PkCalibrationLiveState.Available ?: return null
        return available.context.takeIf {
            storageRepository.captureHomeDataGeneration() == available.inputGeneration
        }
    }

    private suspend fun stableRead(policy: PkCalibrationRuntimePolicy): StableRead {
        repeat(MAX_STABLE_READ_ATTEMPTS) {
            val before = storageRepository.captureHomeDataGeneration()
            val panels = bloodTestRepository.getPanels()
            val entries = medicationLogRepository.getEntries()
            val profile = userProfileRepository.getCurrentProfile()
            val metadata = storageRepository.getAllMetadata()
            val after = storageRepository.captureHomeDataGeneration()
            if (before == after) {
                return adapt(before, panels, entries, profile, metadata, policy)
            }
        }
        return StableRead.Unavailable(
            PkCalibrationLiveUnavailableReason.INPUT_GENERATION_UNSTABLE
        )
    }

    private suspend fun evaluate(read: StableRead.Ready): PkCalibrationLiveState {
        return withContext(defaultDispatcher) {
            val context = read.context
            val evaluation = PkCalibrationEngine.evaluate(
                input = context.input,
                metadata = context.metadata,
                identityPolicy = context.identityPolicy,
                config = context.config,
            )
            val render = if (evaluation.isReady) {
                evaluation.renderFor(read.domain)
            } else {
                null
            }
            if (evaluation.isReady && render == null) {
                return@withContext PkCalibrationLiveState.Unavailable(
                    PkCalibrationLiveUnavailableReason.RENDER_UNAVAILABLE
                )
            }
            PkCalibrationLiveState.Available(
                inputGeneration = read.generation,
                context = context,
                evaluation = evaluation,
                domain = read.domain,
                render = render,
            )
        }
    }

    private fun adapt(
        generation: Long,
        panels: List<BloodTestPanel>,
        entries: List<MedicationLogEntry>,
        profile: UserProfile,
        metadata: List<E2CalibrationMetadata>,
        policy: PkCalibrationRuntimePolicy,
    ): StableRead {
        val e2Rows = panels.flatMap { panel ->
            panel.results.mapNotNull { result ->
                if ((result.analyte as? BloodTestResultAnalyte.Builtin)?.key ==
                    BloodAnalyteKey.E2
                ) panel to result else null
            }
        }
        val relevantEntries = entries.filter { it.category == MedicationCategory.ESTRADIOL }
        // A user with no logged doses and no labs still gets a live surface:
        // an empty history is anchored by any origin, so fall back to the
        // clock instead of failing closed. The evaluation then lands on
        // NO_USABLE_LABS and the calibration section renders its CTA.
        val origin = (e2Rows.map { it.first.collectedAt.toEpochMilli() } +
                relevantEntries.map { it.appliedAt.toEpochMilli() })
            .minOrNull()
            ?: clock.millis()

        val labs = ArrayList<PkCalibrationE2LabSource>(e2Rows.size)
        for ((panel, result) in e2Rows) {
            val unitId = policy.identityPolicy.unitIdBySourceSnapshot[result.unitSnapshot]
                ?: return StableRead.Unavailable(
                    PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
                )
            labs += PkCalibrationE2LabSource.create(
                panel = panel,
                result = result,
                analyteId = policy.identityPolicy.builtinE2AnalyteId,
                unitId = unitId,
            ) ?: return StableRead.Unavailable(
                PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
            )
        }

        val anchor = Instant.ofEpochMilli(origin)
        val medicationSources = ArrayList<PkCalibrationMedicationEventSource>(
            relevantEntries.size
        )
        for (entry in relevantEntries) {
            val event = entry.buildEstradiolPkDoseEvent(anchor)
                ?: return StableRead.Unavailable(
                    PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
                )
            medicationSources += PkCalibrationMedicationEventSource.create(
                event = event,
                epochMillis = entry.appliedAt.toEpochMilli(),
                eventTypeId = policy.identityPolicy.eventTypeIdByRoute.getValue(event.route),
                hormoneId = policy.identityPolicy.targetHormoneId,
                routeId = policy.identityPolicy.routeIdByRoute.getValue(event.route),
                compoundId = policy.identityPolicy.compoundIdByCompound.getValue(event.compound),
            ) ?: return StableRead.Unavailable(
                PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
            )
        }

        // Same resolution as the Home projection (PkMedicationSimulation):
        // an unset Current Weight falls back to the app-wide default instead
        // of failing the whole evaluation as SHARED_INPUT_INVALID.
        val resolvedWeightKg = profile.weightKg
            ?.takeIf { value -> value.isFinite() && value > 0.0 }
            ?: PkMedicationSimulation.DefaultBodyWeightKg
        val input = PkCalibrationInputSnapshot.create(
            labs = labs,
            medicationEvents = medicationSources,
            forwardTimeOriginEpochMillis = origin,
            resolvedCurrentWeightKg = resolvedWeightKg,
            forwardModelVersion = policy.forwardModelVersion,
            calibrationModelVersion = policy.calibrationModelVersion,
        ) ?: return StableRead.Unavailable(
            PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
        )
        // Phase-3 #9: the render domain tracks the chart's visible window
        // around the current clock — the widest selectable past span (plus a
        // day of start-of-day flooring slack) through the widest future span —
        // instead of anchoring at earliest-event + 30 days, which put every
        // knot outside the chart for any history older than a month.
        val nowMillis = clock.millis()
        val domainStart = runCatching { Math.subtractExact(nowMillis, RENDER_PAST_MILLIS) }
            .getOrNull()
            ?: return StableRead.Unavailable(
                PkCalibrationLiveUnavailableReason.RENDER_DOMAIN_UNAVAILABLE
            )
        val domainEnd = runCatching { Math.addExact(nowMillis, RENDER_FUTURE_MILLIS) }
            .getOrNull()
            ?: return StableRead.Unavailable(
                PkCalibrationLiveUnavailableReason.RENDER_DOMAIN_UNAVAILABLE
            )
        val domain = PkChartDomain.create(
            rangeStartEpochMillis = domainStart,
            rangeEndEpochMillis = domainEnd,
            samplingIntervalMillis = SIX_HOURS_MILLIS,
            chartGridVersion = DEBUG_CHART_GRID_VERSION,
        ) ?: return StableRead.Unavailable(
            PkCalibrationLiveUnavailableReason.RENDER_DOMAIN_UNAVAILABLE
        )
        return StableRead.Ready(
            generation = generation,
            context = PkCalibrationEvaluationContext.create(
                inputGeneration = generation,
                input = input,
                metadata = metadata,
                identityPolicy = policy.identityPolicy,
                config = policy.config,
            ),
            domain = domain,
        )
    }

    private sealed interface StableRead {
        data class Ready(
            val generation: Long,
            val context: PkCalibrationEvaluationContext,
            val domain: PkChartDomain,
        ) : StableRead

        data class Unavailable(val reason: PkCalibrationLiveUnavailableReason) : StableRead
    }

    private companion object {
        private const val MAX_STABLE_READ_ATTEMPTS = 3
        private const val SIX_HOURS_MILLIS = 6L * 60L * 60L * 1_000L
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        private val RENDER_PAST_MILLIS =
            (HomeE2ChartWindowOption.entries.maxOf { option -> option.pastDays } + 1L) *
                DAY_MILLIS
        private val RENDER_FUTURE_MILLIS =
            HomeE2ChartWindowOption.entries.maxOf { option -> option.futureDays } * DAY_MILLIS
        private const val DEBUG_CHART_GRID_VERSION = "hrttracker.calibration-debug-grid/v1"
    }
}
