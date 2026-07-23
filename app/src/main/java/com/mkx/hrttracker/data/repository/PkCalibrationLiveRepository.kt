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
import com.mkx.hrttracker.model.pk.PkCalibrationScopeDecision
import com.mkx.hrttracker.model.pk.PkCalibrationScopeDecisionProvider
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvent
import com.mkx.hrttracker.model.pk.isStableAsciiIdentity
import java.time.Instant
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

enum class PkCalibrationLiveUnavailableReason {
    RUNTIME_POLICY_UNAVAILABLE,
    INPUT_GENERATION_UNSTABLE,
    SOURCE_READ_FAILED,
    SOURCE_PROVIDER_ASSAY_IDENTITY_UNAVAILABLE,
    SOURCE_DATA_INVALID,
    RENDER_DOMAIN_UNAVAILABLE,
    RENDER_UNAVAILABLE,
}

data class PkCalibrationLabSourceIdentity(
    val providerId: String,
    val assayMethodId: String,
) {
    init {
        require(providerId.isStableAsciiIdentity())
        require(assayMethodId.isStableAsciiIdentity())
    }
}

sealed interface PkCalibrationRuntimePolicy {
    data class Unavailable(
        val reason: PkCalibrationLiveUnavailableReason =
            PkCalibrationLiveUnavailableReason.RUNTIME_POLICY_UNAVAILABLE,
    ) : PkCalibrationRuntimePolicy

    /** Explicit research-only inputs; source records never fabricate these identities. */
    class ResearchOrTest private constructor(
        val identityPolicy: PkCalibrationIdentityPolicy,
        val config: PkCalibrationConfig,
        val scopeDecision: PkCalibrationScopeDecision,
        val forwardModelVersion: String,
        val calibrationModelVersion: String,
        val labIdentityByResultId: Map<UUID, PkCalibrationLabSourceIdentity>,
    ) : PkCalibrationRuntimePolicy {
        companion object {
            fun create(
                identityPolicy: PkCalibrationIdentityPolicy,
                config: PkCalibrationConfig,
                scopeDecision: PkCalibrationScopeDecision,
                forwardModelVersion: String,
                calibrationModelVersion: String,
                labIdentityByResultId: Map<UUID, PkCalibrationLabSourceIdentity>,
            ): ResearchOrTest? {
                if (!config.personalizedOutputEnabled || !config.isResearchOrTest ||
                    !forwardModelVersion.isStableAsciiIdentity() ||
                    !calibrationModelVersion.isStableAsciiIdentity() ||
                    labIdentityByResultId.isEmpty()
                ) {
                    return null
                }
                return ResearchOrTest(
                    identityPolicy = identityPolicy,
                    config = config,
                    scopeDecision = scopeDecision,
                    forwardModelVersion = forwardModelVersion,
                    calibrationModelVersion = calibrationModelVersion,
                    labIdentityByResultId = Collections.unmodifiableMap(
                        LinkedHashMap(labIdentityByResultId)
                    ),
                )
            }
        }
    }
}

interface PkCalibrationRuntimePolicyProvider {
    val policy: StateFlow<PkCalibrationRuntimePolicy>
}

@Singleton
class ProductionUnavailablePkCalibrationRuntimePolicyProvider @Inject constructor() :
    PkCalibrationRuntimePolicyProvider {
    private val policyState = MutableStateFlow<PkCalibrationRuntimePolicy>(
        PkCalibrationRuntimePolicy.Unavailable()
    )
    override val policy: StateFlow<PkCalibrationRuntimePolicy> = policyState.asStateFlow()
}

/** Every value consumed by one evaluation, frozen before solver work begins. */
class PkCalibrationEvaluationContext private constructor(
    val inputGeneration: Long,
    val input: PkCalibrationInputSnapshot,
    val metadata: List<E2CalibrationMetadata>,
    val identityPolicy: PkCalibrationIdentityPolicy,
    val config: PkCalibrationConfig,
    val scopeDecisionProvider: PkCalibrationScopeDecisionProvider,
    internal val runtimePolicy: PkCalibrationRuntimePolicy.ResearchOrTest?,
) {
    companion object {
        internal fun create(
            inputGeneration: Long,
            input: PkCalibrationInputSnapshot,
            metadata: List<E2CalibrationMetadata>,
            policy: PkCalibrationRuntimePolicy.ResearchOrTest,
        ): PkCalibrationEvaluationContext {
            require(inputGeneration >= 0L)
            val frozenDecision = policy.scopeDecision
            return PkCalibrationEvaluationContext(
                inputGeneration = inputGeneration,
                input = input,
                metadata = Collections.unmodifiableList(ArrayList(metadata)),
                identityPolicy = policy.identityPolicy,
                config = policy.config,
                scopeDecisionProvider = FrozenScopeDecisionProvider(frozenDecision),
                runtimePolicy = policy,
            )
        }

        internal fun forTest(
            inputGeneration: Long,
            input: PkCalibrationInputSnapshot,
            metadata: List<E2CalibrationMetadata>,
            identityPolicy: PkCalibrationIdentityPolicy,
            config: PkCalibrationConfig,
            scopeDecisionProvider: PkCalibrationScopeDecisionProvider,
        ): PkCalibrationEvaluationContext {
            require(inputGeneration >= 0L)
            val frozenDecision = scopeDecisionProvider.currentDecision()
            return PkCalibrationEvaluationContext(
                inputGeneration = inputGeneration,
                input = input,
                metadata = Collections.unmodifiableList(ArrayList(metadata)),
                identityPolicy = identityPolicy,
                config = config,
                scopeDecisionProvider = FrozenScopeDecisionProvider(frozenDecision),
                runtimePolicy = null,
            )
        }
    }
}

private class FrozenScopeDecisionProvider(
    private val decision: PkCalibrationScopeDecision?,
) : PkCalibrationScopeDecisionProvider {
    override fun currentDecision(): PkCalibrationScopeDecision? = decision
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
    private val runtimePolicyProvider: PkCalibrationRuntimePolicyProvider,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @AppScope appScope: CoroutineScope,
) : PkCalibrationCurrentEvaluationContextProvider {
    private val retryVersion = MutableStateFlow(0L)

    val liveState: StateFlow<PkCalibrationLiveState> = combine(
        storageRepository.observeHomeDataGeneration(),
        runtimePolicyProvider.policy,
        retryVersion,
    ) { _, policy, _ -> policy }
        .transformLatest { policy ->
            emit(PkCalibrationLiveState.Loading)
            if (policy is PkCalibrationRuntimePolicy.Unavailable) {
                emit(PkCalibrationLiveState.Unavailable(policy.reason))
                return@transformLatest
            }
            policy as PkCalibrationRuntimePolicy.ResearchOrTest
            try {
                when (val read = stableRead(policy)) {
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
            runtimePolicyProvider.policy.value === it.runtimePolicy &&
                    storageRepository.captureHomeDataGeneration() == available.inputGeneration
        }
    }

    private suspend fun stableRead(
        policy: PkCalibrationRuntimePolicy.ResearchOrTest,
    ): StableRead {
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
                scopeDecisionProvider = context.scopeDecisionProvider,
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
        policy: PkCalibrationRuntimePolicy.ResearchOrTest,
    ): StableRead {
        val e2Rows = panels.flatMap { panel ->
            panel.results.mapNotNull { result ->
                if ((result.analyte as? BloodTestResultAnalyte.Builtin)?.key ==
                    BloodAnalyteKey.E2
                ) panel to result else null
            }
        }
        val relevantEntries = entries.filter { it.category == MedicationCategory.ESTRADIOL }
        val origin = (e2Rows.map { it.first.collectedAt.toEpochMilli() } +
                relevantEntries.map { it.appliedAt.toEpochMilli() })
            .minOrNull()
            ?: return StableRead.Unavailable(PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID)

        val labs = ArrayList<PkCalibrationE2LabSource>(e2Rows.size)
        for ((panel, result) in e2Rows) {
            val identity = policy.labIdentityByResultId[result.uuid]
                ?: return StableRead.Unavailable(
                    PkCalibrationLiveUnavailableReason
                        .SOURCE_PROVIDER_ASSAY_IDENTITY_UNAVAILABLE
                )
            val unitId = policy.identityPolicy.unitIdBySourceSnapshot[result.unitSnapshot]
                ?: return StableRead.Unavailable(
                    PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
                )
            labs += PkCalibrationE2LabSource.create(
                panel = panel,
                result = result,
                analyteId = policy.identityPolicy.builtinE2AnalyteId,
                unitId = unitId,
                providerId = identity.providerId,
                assayMethodId = identity.assayMethodId,
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

        val input = PkCalibrationInputSnapshot.create(
            labs = labs,
            medicationEvents = medicationSources,
            forwardTimeOriginEpochMillis = origin,
            resolvedCurrentWeightKg = profile.weightKg,
            forwardModelVersion = policy.forwardModelVersion,
            calibrationModelVersion = policy.calibrationModelVersion,
        ) ?: return StableRead.Unavailable(
            PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID
        )
        val domainEnd = runCatching { Math.addExact(origin, THIRTY_DAYS_MILLIS) }
            .getOrNull()
            ?: return StableRead.Unavailable(
                PkCalibrationLiveUnavailableReason.RENDER_DOMAIN_UNAVAILABLE
            )
        val domain = PkChartDomain.create(
            rangeStartEpochMillis = origin,
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
                policy = policy,
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
        private const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        private const val DEBUG_CHART_GRID_VERSION = "hrttracker.calibration-debug-grid/v1"
    }
}
