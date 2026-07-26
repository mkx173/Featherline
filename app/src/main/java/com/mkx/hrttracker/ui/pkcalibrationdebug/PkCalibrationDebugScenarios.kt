package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveState
import com.mkx.hrttracker.data.repository.PkCalibrationLiveUnavailableReason
import com.mkx.hrttracker.model.pk.CanonicalDigest
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA
import com.mkx.hrttracker.model.pk.PK_CALIBRATION_SCOPE_DECISION_DIGEST_SCHEMA
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationDefaults
import com.mkx.hrttracker.model.pk.PkCalibrationEngineEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkCurvePoint
import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** Runtime gate checked by every debug source and command boundary. */
fun interface PkCalibrationDebugGate {
    fun isEnabled(): Boolean

    companion object {
        val Build = PkCalibrationDebugGate { BuildConfig.DEBUG }
    }
}

enum class PkCalibrationDebugPreset {
    POPULATION_ONLY,
    INJECTION_CALIBRATED,
    MIXED_INJECTION_ORAL,
    INJECTION_REVIEW_FIT,
    ORAL_OUT_OF_RANGE,
}

enum class PkCalibrationDebugFixtureDisposition {
    AUTO,
    ACCEPTED,
    EXCLUDED,
}

enum class PkCalibrationDebugSnapshotKind {
    LIVE_BOUND_EVALUATION,
    SYNTHETIC_ENGINE_EVALUATION,
    VALIDATED_CONTRACT_FIXTURE,
    VALIDATED_FAULT_FIXTURE,
}

enum class PkCalibrationDebugSourceUnavailableReason {
    LIVE_EVALUATION_UNAVAILABLE,
    LIVE_RENDER_DOMAIN_UNAVAILABLE,
    LIVE_RENDER_UNAVAILABLE,
    LIVE_RUNTIME_POLICY_UNAVAILABLE,
    LIVE_INPUT_GENERATION_UNSTABLE,
    LIVE_SOURCE_READ_FAILED,
    LIVE_SOURCE_PROVIDER_ASSAY_IDENTITY_UNAVAILABLE,
    LIVE_SOURCE_DATA_INVALID,
    SOURCE_EXCEPTION,
    SOURCE_CONTRACT_MISMATCH,
}

/**
 * Validated control state for the throwaway debug harness. It is not a model result: every
 * observable result/render object is still constructed by the production contract factories.
 */
@ConsistentCopyVisibility
data class PkCalibrationDebugScenario private constructor(
    val globalState: PkCalibrationGlobalState,
    val routeStateByRoute: Map<PkCalibrationRoute, PkRouteCalibrationDisplayState>,
    val routeRenderFallback: PkCalibrationRoute?,
    val bandUnavailable: Boolean,
    val centralUnavailable: Boolean,
    val outlierRoute: PkCalibrationRoute?,
    val nonPositiveInput: Boolean,
    val guardDroppedRoute: PkCalibrationRoute?,
    val displayCapBoundaryRoute: PkCalibrationRoute?,
    val fixtureDisposition: PkCalibrationDebugFixtureDisposition,
) {
    fun withGlobalState(value: PkCalibrationGlobalState): PkCalibrationDebugScenario =
        requireNotNull(create(
            globalState = value,
            routeStateByRoute = routeStateByRoute,
            routeRenderFallback = routeRenderFallback,
            bandUnavailable = bandUnavailable,
            centralUnavailable = centralUnavailable,
            outlierRoute = outlierRoute,
            nonPositiveInput = nonPositiveInput,
            guardDroppedRoute = guardDroppedRoute,
            displayCapBoundaryRoute = displayCapBoundaryRoute,
            fixtureDisposition = fixtureDisposition,
        ))

    fun withRouteState(
        route: PkCalibrationRoute,
        value: PkRouteCalibrationDisplayState,
    ): PkCalibrationDebugScenario = requireNotNull(create(
        globalState = PkCalibrationGlobalState.READY,
        routeStateByRoute = routeStateByRoute + (route to value),
        routeRenderFallback = routeRenderFallback,
        bandUnavailable = bandUnavailable,
        centralUnavailable = centralUnavailable,
        outlierRoute = outlierRoute.takeUnless { it == route },
        nonPositiveInput = false,
        guardDroppedRoute = guardDroppedRoute,
        displayCapBoundaryRoute = displayCapBoundaryRoute.takeUnless { it == route },
        fixtureDisposition = PkCalibrationDebugFixtureDisposition.AUTO,
    ))

    fun withRouteRenderFallback(route: PkCalibrationRoute?): PkCalibrationDebugScenario {
        val states = if (route != null && routeStateByRoute.getValue(route).isPopulationState()) {
            routeStateByRoute + (route to PkRouteCalibrationDisplayState.LAB_CALIBRATED)
        } else {
            routeStateByRoute
        }
        return requireNotNull(create(
            globalState = PkCalibrationGlobalState.READY,
            routeStateByRoute = states,
            routeRenderFallback = route,
            bandUnavailable = bandUnavailable,
            centralUnavailable = centralUnavailable,
            outlierRoute = outlierRoute.takeUnless { it == route },
            nonPositiveInput = false,
            guardDroppedRoute = guardDroppedRoute,
            displayCapBoundaryRoute = displayCapBoundaryRoute,
            fixtureDisposition = if (outlierRoute == route) {
                PkCalibrationDebugFixtureDisposition.AUTO
            } else {
                fixtureDisposition
            },
        ))
    }

    fun withBandUnavailable(value: Boolean): PkCalibrationDebugScenario {
        val states = if (value && routeStateByRoute.values.all { it.isPopulationState() }) {
            routeStateByRoute +
                    (PkCalibrationRoute.INJECTION to
                            PkRouteCalibrationDisplayState.LAB_CALIBRATED)
        } else {
            routeStateByRoute
        }
        return requireNotNull(create(
            globalState = PkCalibrationGlobalState.READY,
            routeStateByRoute = states,
            routeRenderFallback = routeRenderFallback,
            bandUnavailable = value,
            centralUnavailable = centralUnavailable,
            outlierRoute = outlierRoute,
            nonPositiveInput = false,
            guardDroppedRoute = guardDroppedRoute,
            displayCapBoundaryRoute = displayCapBoundaryRoute,
            fixtureDisposition = fixtureDisposition,
        ))
    }

    fun withCentralUnavailable(value: Boolean): PkCalibrationDebugScenario = requireNotNull(create(
        globalState = PkCalibrationGlobalState.READY,
        routeStateByRoute = routeStateByRoute,
        routeRenderFallback = routeRenderFallback,
        bandUnavailable = bandUnavailable,
        centralUnavailable = value,
        outlierRoute = outlierRoute,
        nonPositiveInput = false,
        guardDroppedRoute = guardDroppedRoute,
        displayCapBoundaryRoute = displayCapBoundaryRoute,
        fixtureDisposition = fixtureDisposition,
    ))

    fun withOutlierRoute(route: PkCalibrationRoute?): PkCalibrationDebugScenario =
        requireNotNull(create(
            globalState = PkCalibrationGlobalState.READY,
            routeStateByRoute = routeStateByRoute,
            routeRenderFallback = routeRenderFallback.takeUnless { it == route },
            bandUnavailable = bandUnavailable,
            centralUnavailable = centralUnavailable,
            outlierRoute = route,
            nonPositiveInput = false,
            guardDroppedRoute = guardDroppedRoute.takeUnless { it == route },
            displayCapBoundaryRoute = displayCapBoundaryRoute.takeUnless { it == route },
            fixtureDisposition = PkCalibrationDebugFixtureDisposition.AUTO,
        ))

    fun withNonPositiveInput(value: Boolean): PkCalibrationDebugScenario = requireNotNull(create(
        globalState = globalState,
        routeStateByRoute = routeStateByRoute,
        routeRenderFallback = routeRenderFallback,
        bandUnavailable = bandUnavailable,
        centralUnavailable = centralUnavailable,
        outlierRoute = outlierRoute,
        nonPositiveInput = value,
        guardDroppedRoute = guardDroppedRoute,
        displayCapBoundaryRoute = displayCapBoundaryRoute,
        fixtureDisposition = fixtureDisposition,
    ))

    fun withGuardDroppedRoute(route: PkCalibrationRoute?): PkCalibrationDebugScenario =
        requireNotNull(create(
            globalState = globalState,
            routeStateByRoute = routeStateByRoute,
            routeRenderFallback = routeRenderFallback,
            bandUnavailable = bandUnavailable,
            centralUnavailable = centralUnavailable,
            outlierRoute = outlierRoute.takeUnless { it == route },
            nonPositiveInput = nonPositiveInput,
            guardDroppedRoute = route,
            displayCapBoundaryRoute = displayCapBoundaryRoute,
            fixtureDisposition = if (outlierRoute == route) {
                PkCalibrationDebugFixtureDisposition.AUTO
            } else {
                fixtureDisposition
            },
        ))

    fun withDisplayCapBoundaryRoute(
        route: PkCalibrationRoute?,
    ): PkCalibrationDebugScenario {
        val states = if (route == null) {
            routeStateByRoute
        } else {
            routeStateByRoute + (route to PkRouteCalibrationDisplayState.LAB_CALIBRATED)
        }
        return requireNotNull(create(
            globalState = PkCalibrationGlobalState.READY,
            routeStateByRoute = states,
            routeRenderFallback = routeRenderFallback,
            bandUnavailable = bandUnavailable,
            centralUnavailable = centralUnavailable,
            outlierRoute = outlierRoute.takeUnless { it == route },
            nonPositiveInput = false,
            guardDroppedRoute = guardDroppedRoute,
            displayCapBoundaryRoute = route,
            fixtureDisposition = if (outlierRoute == route) {
                PkCalibrationDebugFixtureDisposition.AUTO
            } else {
                fixtureDisposition
            },
        ))
    }

    fun withFixtureDisposition(
        value: PkCalibrationDebugFixtureDisposition,
    ): PkCalibrationDebugScenario = requireNotNull(create(
        globalState = globalState,
        routeStateByRoute = routeStateByRoute,
        routeRenderFallback = routeRenderFallback,
        bandUnavailable = bandUnavailable,
        centralUnavailable = centralUnavailable,
        outlierRoute = outlierRoute,
        nonPositiveInput = nonPositiveInput,
        guardDroppedRoute = guardDroppedRoute,
        displayCapBoundaryRoute = displayCapBoundaryRoute,
        fixtureDisposition = value,
    ))

    companion object {
        fun create(
            globalState: PkCalibrationGlobalState = PkCalibrationGlobalState.READY,
            routeStateByRoute: Map<PkCalibrationRoute, PkRouteCalibrationDisplayState> =
                populationRouteStates(),
            routeRenderFallback: PkCalibrationRoute? = null,
            bandUnavailable: Boolean = false,
            centralUnavailable: Boolean = false,
            outlierRoute: PkCalibrationRoute? = null,
            nonPositiveInput: Boolean = false,
            guardDroppedRoute: PkCalibrationRoute? = null,
            displayCapBoundaryRoute: PkCalibrationRoute? = null,
            fixtureDisposition: PkCalibrationDebugFixtureDisposition =
                PkCalibrationDebugFixtureDisposition.AUTO,
        ): PkCalibrationDebugScenario? {
            if (routeStateByRoute.keys != PkCalibrationRoute.entries.toSet()) return null
            val canonicalStates = linkedMapOf<PkCalibrationRoute, PkRouteCalibrationDisplayState>()
            PkCalibrationRoute.entries.forEach { route ->
                canonicalStates[route] = routeStateByRoute.getValue(route)
            }
            if (outlierRoute == null && fixtureDisposition !=
                PkCalibrationDebugFixtureDisposition.AUTO
            ) {
                return null
            }
            if (outlierRoute != null && outlierRoute == guardDroppedRoute) return null
            if (outlierRoute != null && outlierRoute == displayCapBoundaryRoute) return null
            if (displayCapBoundaryRoute != null &&
                routeStateByRoute.getValue(displayCapBoundaryRoute).isPopulationState()
            ) {
                return null
            }
            return PkCalibrationDebugScenario(
                globalState = globalState,
                routeStateByRoute = Collections.unmodifiableMap(canonicalStates),
                routeRenderFallback = routeRenderFallback,
                bandUnavailable = bandUnavailable,
                centralUnavailable = centralUnavailable,
                outlierRoute = outlierRoute,
                nonPositiveInput = nonPositiveInput,
                guardDroppedRoute = guardDroppedRoute,
                displayCapBoundaryRoute = displayCapBoundaryRoute,
                fixtureDisposition = fixtureDisposition,
            )
        }

        fun preset(preset: PkCalibrationDebugPreset): PkCalibrationDebugScenario {
            val states = populationRouteStates().toMutableMap()
            var outlierRoute: PkCalibrationRoute? = null
            when (preset) {
                PkCalibrationDebugPreset.POPULATION_ONLY -> Unit
                PkCalibrationDebugPreset.INJECTION_CALIBRATED -> {
                    states[PkCalibrationRoute.INJECTION] =
                        PkRouteCalibrationDisplayState.LAB_CALIBRATED
                }
                PkCalibrationDebugPreset.MIXED_INJECTION_ORAL -> {
                    states[PkCalibrationRoute.INJECTION] =
                        PkRouteCalibrationDisplayState.LAB_CALIBRATED
                    states[PkCalibrationRoute.ORAL] =
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                }
                PkCalibrationDebugPreset.INJECTION_REVIEW_FIT -> {
                    states[PkCalibrationRoute.INJECTION] =
                        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE
                    outlierRoute = PkCalibrationRoute.INJECTION
                }
                PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE -> {
                    states[PkCalibrationRoute.ORAL] =
                        PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED
                }
            }
            return requireNotNull(create(
                routeStateByRoute = states,
                outlierRoute = outlierRoute,
            ))
        }

        private fun populationRouteStates(): Map<
            PkCalibrationRoute,
            PkRouteCalibrationDisplayState,
        > = PkCalibrationRoute.entries.associateWith {
            PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS
        }
    }
}

data class PkCalibrationDebugSnapshot(
    val result: PkCalibrationResult,
    val render: PkCalibrationRenderResult?,
    val kind: PkCalibrationDebugSnapshotKind,
    val scenario: PkCalibrationDebugScenario?,
    val reviewDispositionByResultId: Map<UUID, E2CalibrationDisposition> = emptyMap(),
)

sealed interface PkCalibrationDebugSourceResult {
    data object DebugDisabled : PkCalibrationDebugSourceResult
    data object Loading : PkCalibrationDebugSourceResult
    data class Unavailable(val reason: PkCalibrationDebugSourceUnavailableReason) :
        PkCalibrationDebugSourceResult
    data class Available(val snapshot: PkCalibrationDebugSnapshot) :
        PkCalibrationDebugSourceResult
}

interface PkCalibrationDebugLiveSnapshotProvider {
    suspend fun currentEvaluation(): PkCalibrationEngineEvaluation?
    suspend fun currentRenderDomain(): PkChartDomain?

    /** Stored review state needed to expose only currently applicable debug actions. */
    suspend fun currentReviewDispositions(): Map<UUID, E2CalibrationDisposition> = emptyMap()
}

interface ObservablePkCalibrationDebugLiveSnapshotProvider :
    PkCalibrationDebugLiveSnapshotProvider {
    val liveState: StateFlow<PkCalibrationLiveState>
    fun retry()
}

@Singleton
class RepositoryPkCalibrationDebugLiveSnapshotProvider @Inject constructor(
    private val repository: PkCalibrationLiveRepository,
) : ObservablePkCalibrationDebugLiveSnapshotProvider {
    override val liveState: StateFlow<PkCalibrationLiveState> = repository.liveState

    override suspend fun currentEvaluation(): PkCalibrationEngineEvaluation? =
        (liveState.value as? PkCalibrationLiveState.Available)?.evaluation

    override suspend fun currentRenderDomain(): PkChartDomain? =
        (liveState.value as? PkCalibrationLiveState.Available)?.domain

    override suspend fun currentReviewDispositions(): Map<UUID, E2CalibrationDisposition> =
        (liveState.value as? PkCalibrationLiveState.Available)
            ?.context
            ?.metadata
            ?.associate { item -> item.resultId to item.disposition }
            .orEmpty()

    override fun retry() = repository.retry()
}

object UnavailablePkCalibrationDebugLiveSnapshotProvider :
    PkCalibrationDebugLiveSnapshotProvider {
    override suspend fun currentEvaluation(): PkCalibrationEngineEvaluation? = null
    override suspend fun currentRenderDomain(): PkChartDomain? = null
}

interface PkCalibrationDebugScenarioSource {
    suspend fun loadLive(): PkCalibrationDebugSourceResult
    fun observeLive(): Flow<PkCalibrationDebugSourceResult> = emptyFlow()
    fun retryLive() = Unit
    fun loadFixture(scenario: PkCalibrationDebugScenario): PkCalibrationDebugSourceResult
}

class DefaultPkCalibrationDebugScenarioSource(
    private val liveSnapshotProvider: PkCalibrationDebugLiveSnapshotProvider =
        UnavailablePkCalibrationDebugLiveSnapshotProvider,
    private val debugGate: PkCalibrationDebugGate = PkCalibrationDebugGate.Build,
) : PkCalibrationDebugScenarioSource {
    override suspend fun loadLive(): PkCalibrationDebugSourceResult {
        if (!debugGate.isEnabled()) return PkCalibrationDebugSourceResult.DebugDisabled
        (liveSnapshotProvider as? ObservablePkCalibrationDebugLiveSnapshotProvider)?.let {
            return it.liveState.value.toDebugSourceResult()
        }
        val evaluation = liveSnapshotProvider.currentEvaluation()
            ?: return PkCalibrationDebugSourceResult.Unavailable(
                PkCalibrationDebugSourceUnavailableReason.LIVE_EVALUATION_UNAVAILABLE
            )
        val reviewDispositions = canonicalReviewDispositions(
            liveSnapshotProvider.currentReviewDispositions()
        )
        if (!evaluation.isReady) {
            return PkCalibrationDebugSourceResult.Available(
                PkCalibrationDebugSnapshot(
                    result = evaluation.result,
                    render = null,
                    kind = PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION,
                    scenario = null,
                    reviewDispositionByResultId = reviewDispositions,
                )
            )
        }
        val domain = liveSnapshotProvider.currentRenderDomain()
            ?: return PkCalibrationDebugSourceResult.Unavailable(
                PkCalibrationDebugSourceUnavailableReason.LIVE_RENDER_DOMAIN_UNAVAILABLE
            )
        val render = evaluation.renderFor(domain)
            ?: return PkCalibrationDebugSourceResult.Unavailable(
                PkCalibrationDebugSourceUnavailableReason.LIVE_RENDER_UNAVAILABLE
            )
        return PkCalibrationDebugSourceResult.Available(
            PkCalibrationDebugSnapshot(
                result = evaluation.result,
                render = render,
                kind = PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION,
                scenario = null,
                reviewDispositionByResultId = reviewDispositions,
            )
        )
    }

    override fun observeLive(): Flow<PkCalibrationDebugSourceResult> {
        if (!debugGate.isEnabled()) return emptyFlow()
        val observable = liveSnapshotProvider as?
            ObservablePkCalibrationDebugLiveSnapshotProvider ?: return emptyFlow()
        return observable.liveState.map(PkCalibrationLiveState::toDebugSourceResult)
    }

    override fun retryLive() {
        if (debugGate.isEnabled()) {
            (liveSnapshotProvider as? ObservablePkCalibrationDebugLiveSnapshotProvider)?.retry()
        }
    }

    override fun loadFixture(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSourceResult {
        if (!debugGate.isEnabled()) return PkCalibrationDebugSourceResult.DebugDisabled
        PkCalibrationDebugSyntheticScenarios.buildRenderFaultIfSupported(scenario)
            ?.let { result -> return result }
        PkCalibrationDebugSyntheticScenarios.buildIfSupported(scenario)?.let { result ->
            return result
        }
        return PkCalibrationDebugSourceResult.Available(
            PkCalibrationDebugFixtures.build(scenario)
        )
    }

    private fun canonicalReviewDispositions(
        source: Map<UUID, E2CalibrationDisposition>,
    ): Map<UUID, E2CalibrationDisposition> = source.entries
        .sortedBy { entry -> entry.key.toString() }
        .associateTo(linkedMapOf()) { entry -> entry.key to entry.value }
}

private fun PkCalibrationLiveState.toDebugSourceResult(): PkCalibrationDebugSourceResult {
    return when (this) {
        PkCalibrationLiveState.Loading -> PkCalibrationDebugSourceResult.Loading
        is PkCalibrationLiveState.Unavailable -> PkCalibrationDebugSourceResult.Unavailable(
            when (reason) {
                PkCalibrationLiveUnavailableReason.RUNTIME_POLICY_UNAVAILABLE ->
                    PkCalibrationDebugSourceUnavailableReason
                        .LIVE_RUNTIME_POLICY_UNAVAILABLE
                PkCalibrationLiveUnavailableReason.INPUT_GENERATION_UNSTABLE ->
                    PkCalibrationDebugSourceUnavailableReason
                        .LIVE_INPUT_GENERATION_UNSTABLE
                PkCalibrationLiveUnavailableReason.SOURCE_READ_FAILED ->
                    PkCalibrationDebugSourceUnavailableReason.LIVE_SOURCE_READ_FAILED
                PkCalibrationLiveUnavailableReason
                    .SOURCE_PROVIDER_ASSAY_IDENTITY_UNAVAILABLE ->
                    PkCalibrationDebugSourceUnavailableReason
                        .LIVE_SOURCE_PROVIDER_ASSAY_IDENTITY_UNAVAILABLE
                PkCalibrationLiveUnavailableReason.SOURCE_DATA_INVALID ->
                    PkCalibrationDebugSourceUnavailableReason.LIVE_SOURCE_DATA_INVALID
                PkCalibrationLiveUnavailableReason.RENDER_DOMAIN_UNAVAILABLE ->
                    PkCalibrationDebugSourceUnavailableReason
                        .LIVE_RENDER_DOMAIN_UNAVAILABLE
                PkCalibrationLiveUnavailableReason.RENDER_UNAVAILABLE ->
                    PkCalibrationDebugSourceUnavailableReason.LIVE_RENDER_UNAVAILABLE
            }
        )
        is PkCalibrationLiveState.Available -> {
            val reviewDispositions = context.metadata
                .sortedBy { item -> item.resultId.toString() }
                .associateTo(linkedMapOf()) { item -> item.resultId to item.disposition }
            PkCalibrationDebugSourceResult.Available(
                PkCalibrationDebugSnapshot(
                    result = evaluation.result,
                    render = render,
                    kind = PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION,
                    scenario = null,
                    reviewDispositionByResultId = reviewDispositions,
                )
            )
        }
    }
}

/**
 * Direct UI-contract fixtures for combinations that would require fragile synthetic lab
 * construction or deliberate numerical corruption. They remain valid production contract
 * objects and are explicitly classified as contract vs fault fixtures in every snapshot.
 */
internal object PkCalibrationDebugFixtures {
    private const val DebugForwardModelVersion = "debug:pk-forward/v1"
    private const val DebugCalibrationModelVersion = "debug:route-calibration/v9"
    private val ScopeDigest = requireNotNull(CanonicalDigest.create(
        PK_CALIBRATION_SCOPE_DECISION_DIGEST_SCHEMA,
        "SHA-256",
        "b".repeat(64),
    ))
    private val DomainDigest = requireNotNull(CanonicalDigest.create(
        PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA,
        "SHA-256",
        "d".repeat(64),
    ))

    fun build(scenario: PkCalibrationDebugScenario): PkCalibrationDebugSnapshot {
        val result = buildResult(scenario)
        val render = if (result.globalState == PkCalibrationGlobalState.READY) {
            buildRender(result, scenario)
        } else {
            null
        }
        val isFaultFixture = scenario.centralUnavailable || scenario.bandUnavailable ||
                scenario.routeRenderFallback != null ||
                result.globalState == PkCalibrationGlobalState.SHARED_NUMERIC_FAILURE ||
                result.routeResults.any { routeResult ->
                    routeResult.displayState ==
                            PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE
                }
        return PkCalibrationDebugSnapshot(
            result = result,
            render = render,
            kind = if (isFaultFixture) {
                PkCalibrationDebugSnapshotKind.VALIDATED_FAULT_FIXTURE
            } else {
                PkCalibrationDebugSnapshotKind.VALIDATED_CONTRACT_FIXTURE
            },
            scenario = scenario,
            reviewDispositionByResultId = scenario.outlierRoute?.let { route ->
                mapOf(
                    outlierId(route) to when (scenario.fixtureDisposition) {
                        PkCalibrationDebugFixtureDisposition.AUTO ->
                            E2CalibrationDisposition.AUTO
                        PkCalibrationDebugFixtureDisposition.ACCEPTED ->
                            E2CalibrationDisposition.ACCEPTED
                        PkCalibrationDebugFixtureDisposition.EXCLUDED ->
                            E2CalibrationDisposition.EXCLUDED
                    }
                )
            }.orEmpty(),
        )
    }

    fun buildRenderFault(
        result: PkCalibrationResult,
        scenario: PkCalibrationDebugScenario,
        reviewDispositionByResultId: Map<UUID, E2CalibrationDisposition>,
    ): PkCalibrationDebugSnapshot {
        require(
            scenario.centralUnavailable || scenario.bandUnavailable ||
                    scenario.routeRenderFallback != null
        )
        return PkCalibrationDebugSnapshot(
            result = result,
            render = if (result.globalState == PkCalibrationGlobalState.READY) {
                buildRender(result, scenario)
            } else {
                null
            },
            kind = PkCalibrationDebugSnapshotKind.VALIDATED_FAULT_FIXTURE,
            scenario = scenario,
            reviewDispositionByResultId = reviewDispositionByResultId,
        )
    }

    fun outlierId(route: PkCalibrationRoute): UUID = UUID(
        0x44454255474c4142L,
        when (route) {
            PkCalibrationRoute.INJECTION -> 1L
            PkCalibrationRoute.PATCH -> 2L
            PkCalibrationRoute.GEL -> 3L
            PkCalibrationRoute.ORAL -> 4L
            PkCalibrationRoute.SUBLINGUAL -> 5L
        },
    )

    private fun buildResult(scenario: PkCalibrationDebugScenario): PkCalibrationResult {
        val globalState = if (scenario.nonPositiveInput) {
            PkCalibrationGlobalState.SHARED_INPUT_INVALID
        } else {
            scenario.globalState
        }
        if (globalState != PkCalibrationGlobalState.READY) {
            val reason = when {
                scenario.nonPositiveInput -> PkCalibrationReason.INVALID_NONPOSITIVE_E2
                globalState == PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED ->
                    PkCalibrationReason.SCOPE_NOT_CONFIRMED
                globalState == PkCalibrationGlobalState.SHARED_INPUT_INVALID ->
                    PkCalibrationReason.SHARED_INPUT_INVALID
                else -> PkCalibrationReason.NUMERIC_FAILURE
            }
            return requireNotNull(PkCalibrationResult.create(
                globalState = globalState,
                globalReasons = setOf(reason),
                forwardModelVersion = DebugForwardModelVersion,
                calibrationModelVersion = DebugCalibrationModelVersion,
            ))
        }

        val routeResults = PkCalibrationRoute.entries.map { route ->
            val state = when {
                scenario.guardDroppedRoute == route ->
                    PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS
                scenario.outlierRoute != route -> scenario.routeStateByRoute.getValue(route)
                scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO ->
                    PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE
                scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.ACCEPTED ->
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                else -> PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS
            }
            routeResult(
                route = route,
                state = state,
                guardDropped = scenario.guardDroppedRoute == route,
                unreviewedOutlier = scenario.outlierRoute == route &&
                        scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO,
                atDisplayCapBoundary = scenario.displayCapBoundaryRoute == route,
            )
        }
        val promotedRoutes = routeResults
            .filterNot { result -> result.displayState.isPopulationState() }
            .map(PkRouteCalibrationResult::route)
        val displayBetas = linkedMapOf<PkCalibrationRoute, Double>()
        routeResults.forEach { routeResult ->
            if (routeResult.route in promotedRoutes && routeResult.displayBeta != 0.0) {
                displayBetas[routeResult.route] = routeResult.displayBeta
            }
        }
        return requireNotNull(PkCalibrationResult.create(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = routeResults,
            promotedRoutes = promotedRoutes,
            displayParams = requireNotNull(PkPersonalParams.create(displayBetas)),
            scopeDecisionDigest = ScopeDigest,
            forwardModelVersion = DebugForwardModelVersion,
            calibrationModelVersion = DebugCalibrationModelVersion,
        ))
    }

    private fun routeResult(
        route: PkCalibrationRoute,
        state: PkRouteCalibrationDisplayState,
        guardDropped: Boolean,
        unreviewedOutlier: Boolean,
        atDisplayCapBoundary: Boolean,
    ): PkRouteCalibrationResult {
        val promoted = !state.isPopulationState()
        val displayBeta = when {
            !promoted -> 0.0
            atDisplayCapBoundary -> ln(
                PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE
                    .getValue(route).minInclusive
            )
            else -> ln(1.25)
        }
        val dominantLabCount = when {
            guardDropped -> 0
            else -> when (state) {
                PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS -> 0
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS -> 1
                else -> 3
            }
        }
        val candidateCount = if (guardDropped) 1 else dominantLabCount
        val baseReasons = when (state) {
            PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS ->
                setOf(PkCalibrationReason.NO_DOMINANT_LABS)
            PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS ->
                setOf(PkCalibrationReason.INSUFFICIENT_DOMINANT_LABS)
            PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE -> if (unreviewedOutlier) {
                setOf(PkCalibrationReason.UNREVIEWED_OUTLIER)
            } else {
                setOf(PkCalibrationReason.RESIDUAL_FIT_POOR)
            }
            PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED ->
                setOf(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED)
            PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE ->
                setOf(PkCalibrationReason.NUMERIC_FAILURE)
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ->
                setOf(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE)
            PkRouteCalibrationDisplayState.LAB_CALIBRATED -> emptySet()
        }
        val reasons = if (atDisplayCapBoundary) {
            baseReasons + PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY
        } else {
            baseReasons
        }
        val fittedBeta = when (state) {
            PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED -> {
                val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE.getValue(route)
                ln(cap.maxInclusive * 1.1)
            }
            PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
            PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
            PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
            -> null
            else -> displayBeta
        }
        return requireNotNull(PkRouteCalibrationResult.create(
            route = route,
            fittedBeta = fittedBeta,
            displayBeta = displayBeta,
            betaPosteriorSd = if (fittedBeta != null) {
                if (state ==
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                ) {
                    0.3
                } else {
                    0.1
                }
            } else {
                null
            },
            betaUncertaintyReduction = fittedBeta?.let { 0.5 },
            laplaceVarianceBeta = fittedBeta?.let {
                if (state ==
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                ) {
                    0.09
                } else {
                    0.01
                }
            },
            displayState = state,
            reasons = reasons,
            dominantCandidateLabCount = candidateCount,
            dominantLabCount = dominantLabCount,
            drugSignalLogRange = when (state) {
                PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                -> 1.0
                else -> null
            },
            robustRmseLog = fittedBeta?.let {
                if (state ==
                    PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE &&
                    !unreviewedOutlier
                ) {
                    0.4
                } else {
                    0.1
                }
            },
            minStudentTWeight = fittedBeta?.let { if (unreviewedOutlier) 0.1 else 0.8 },
            atDisplayCapBoundary = atDisplayCapBoundary,
            unreviewedOutlierLabIds = if (unreviewedOutlier) {
                setOf(outlierId(route))
            } else {
                emptySet()
            },
        ))
    }

    private fun buildRender(
        result: PkCalibrationResult,
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationRenderResult {
        if (scenario.centralUnavailable) {
            return requireNotNull(PkCalibrationRenderResult.create(
                domainDigest = DomainDigest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            ))
        }

        val routeRenderFallbacks = result.promotedRoutes
            .filter { route -> route == scenario.routeRenderFallback }
        val effectiveRoutes = result.promotedRoutes.filterNot(routeRenderFallbacks::contains)
        val effectiveBetas = linkedMapOf<PkCalibrationRoute, Double>()
        effectiveRoutes.forEach { route ->
            result.displayParams.routeLogScale[route]?.let { beta ->
                effectiveBetas[route] = beta
            }
        }
        val effectiveParams = requireNotNull(PkPersonalParams.create(effectiveBetas))
        val centralCurve = listOf(
            requireNotNull(PkCurvePoint.create(1_700_000_000_000L, 90.0)),
            requireNotNull(PkCurvePoint.create(1_700_003_600_000L, 120.0)),
            requireNotNull(PkCurvePoint.create(1_700_007_200_000L, 105.0)),
        )
        if (effectiveRoutes.isEmpty()) {
            return requireNotNull(PkCalibrationRenderResult.create(
                domainDigest = DomainDigest,
                renderState = PkCalibrationRenderState.POPULATION,
                routeRenderFallbacks = routeRenderFallbacks,
                centralCurve = centralCurve,
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            ))
        }
        val bandState = if (scenario.bandUnavailable) {
            PkCalibrationBandState.NUMERIC_UNAVAILABLE
        } else {
            PkCalibrationBandState.READY
        }
        return requireNotNull(PkCalibrationRenderResult.create(
            domainDigest = DomainDigest,
            renderState = PkCalibrationRenderState.PERSONALIZED,
            effectivePromotedRoutes = effectiveRoutes,
            effectiveDisplayParams = effectiveParams,
            routeRenderFallbacks = routeRenderFallbacks,
            centralCurve = centralCurve,
            bandState = bandState,
            bandReasons = if (scenario.bandUnavailable) {
                setOf(PkCalibrationReason.BAND_NUMERIC_FAILURE)
            } else {
                emptySet()
            },
            bandKnots = if (scenario.bandUnavailable) emptyList() else centralCurve.map { point ->
                requireNotNull(PkPredictiveBandKnot.create(
                    epochMillis = point.epochMillis,
                    p025Pgml = point.concentrationPgMl * 0.7,
                    p158655254Pgml = point.concentrationPgMl * 0.85,
                    p50Pgml = point.concentrationPgMl,
                    p841344746Pgml = point.concentrationPgMl * 1.15,
                    p975Pgml = point.concentrationPgMl * 1.3,
                ))
            },
        ))
    }
}

internal fun PkRouteCalibrationDisplayState.isPopulationState(): Boolean {
    return when (this) {
        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
        PkRouteCalibrationDisplayState.LAB_CALIBRATED,
        -> false
        else -> true
    }
}
