package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.CanonicalDigest
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationPromotedCovariance
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCurvePoint
import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import java.util.Collections
import java.util.UUID
import kotlin.math.ln

enum class PkCalibrationDebugPreset {
    POPULATION_ONLY,
    INJECTION_CALIBRATED,
    MIXED_INJECTION_ORAL,
    INJECTION_REVIEW_FIT,
    ORAL_OUT_OF_RANGE,
}

enum class PkCalibrationDebugFixtureDisposition {
    AUTO,
    EXCLUDED,
}

enum class PkCalibrationDebugSnapshotKind {
    SYNTHETIC_ENGINE_EVALUATION,
    VALIDATED_CONTRACT_FIXTURE,
    VALIDATED_FAULT_FIXTURE,
}

enum class PkCalibrationDebugSourceUnavailableReason {
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
            fixtureDisposition = fixtureDisposition,
        ))

    /** Null when the combination is unrepresentable by the contract. */
    fun withRouteState(
        route: PkCalibrationRoute,
        value: PkRouteCalibrationDisplayState,
    ): PkCalibrationDebugScenario? = create(
        globalState = PkCalibrationGlobalState.READY,
        routeStateByRoute = routeStateByRoute + (route to value),
        routeRenderFallback = routeRenderFallback,
        bandUnavailable = bandUnavailable,
        centralUnavailable = centralUnavailable,
        outlierRoute = outlierRoute.takeUnless { it == route },
        nonPositiveInput = false,
        fixtureDisposition = PkCalibrationDebugFixtureDisposition.AUTO,
    )

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
            fixtureDisposition = PkCalibrationDebugFixtureDisposition.AUTO,
        ))

    fun withNonPositiveInput(value: Boolean): PkCalibrationDebugScenario = requireNotNull(create(
        globalState = PkCalibrationGlobalState.READY,
        routeStateByRoute = routeStateByRoute,
        routeRenderFallback = routeRenderFallback,
        bandUnavailable = bandUnavailable,
        centralUnavailable = centralUnavailable,
        outlierRoute = outlierRoute,
        nonPositiveInput = value,
        fixtureDisposition = fixtureDisposition,
    ))

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
            return PkCalibrationDebugScenario(
                globalState = globalState,
                routeStateByRoute = Collections.unmodifiableMap(canonicalStates),
                routeRenderFallback = routeRenderFallback,
                bandUnavailable = bandUnavailable,
                centralUnavailable = centralUnavailable,
                outlierRoute = outlierRoute,
                nonPositiveInput = nonPositiveInput,
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
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                    outlierRoute = PkCalibrationRoute.INJECTION
                }
                PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE -> {
                    states[PkCalibrationRoute.ORAL] =
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
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
            PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS
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
    data class Unavailable(val reason: PkCalibrationDebugSourceUnavailableReason) :
        PkCalibrationDebugSourceResult
    data class Available(val snapshot: PkCalibrationDebugSnapshot) :
        PkCalibrationDebugSourceResult
}

fun interface PkCalibrationDebugScenarioSource {
    fun loadFixture(scenario: PkCalibrationDebugScenario): PkCalibrationDebugSourceResult
}

class DefaultPkCalibrationDebugScenarioSource(
    /** Injectable so identical loads are bit-deterministic in tests. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PkCalibrationDebugScenarioSource {
    override fun loadFixture(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSourceResult {
        PkCalibrationDebugSyntheticScenarios.buildRenderFaultIfSupported(scenario)
            ?.let { result -> return result }
        PkCalibrationDebugSyntheticScenarios.buildIfSupported(scenario)?.let { result ->
            return result
        }
        return PkCalibrationDebugSourceResult.Available(
            PkCalibrationDebugFixtures.build(scenario, nowMillis())
        )
    }
}

/**
 * Direct UI-contract fixtures for combinations that would require fragile synthetic lab
 * construction or deliberate numerical corruption. They remain valid production contract
 * objects and are explicitly classified as contract vs fault fixtures in every snapshot.
 */
internal object PkCalibrationDebugFixtures {
    private const val DebugForwardModelVersion = "debug:pk-forward/v1"
    private const val DebugCalibrationModelVersion = "debug:route-calibration/v10"
    private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    private val DomainDigest = requireNotNull(CanonicalDigest.create(
        PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA,
        "SHA-256",
        "d".repeat(64),
    ))

    fun build(
        scenario: PkCalibrationDebugScenario,
        nowMillis: Long = System.currentTimeMillis(),
    ): PkCalibrationDebugSnapshot {
        val result = buildResult(scenario)
        val render = if (result.globalState == PkCalibrationGlobalState.READY) {
            buildRender(result, scenario, nowMillis)
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
        nowMillis: Long = System.currentTimeMillis(),
    ): PkCalibrationDebugSnapshot {
        require(
            scenario.centralUnavailable || scenario.bandUnavailable ||
                    scenario.routeRenderFallback != null
        )
        return PkCalibrationDebugSnapshot(
            result = result,
            render = if (result.globalState == PkCalibrationGlobalState.READY) {
                buildRender(result, scenario, nowMillis)
            } else {
                null
            },
            kind = PkCalibrationDebugSnapshotKind.VALIDATED_FAULT_FIXTURE,
            scenario = scenario,
            reviewDispositionByResultId = reviewDispositionByResultId,
        )
    }

    /** ASCII "DEBUGLAB" — shared msb namespace for every synthetic debug lab id. */
    private const val DebugLabIdMsb = 0x44454255474c4142L

    fun outlierId(route: PkCalibrationRoute): UUID = UUID(
        DebugLabIdMsb,
        when (route) {
            PkCalibrationRoute.INJECTION -> 1L
            PkCalibrationRoute.PATCH -> 2L
            PkCalibrationRoute.GEL -> 3L
            PkCalibrationRoute.ORAL -> 4L
            PkCalibrationRoute.SUBLINGUAL -> 5L
        },
    )

    fun nonPositiveLabId(): UUID = UUID(DebugLabIdMsb, 6L)

    private fun buildResult(scenario: PkCalibrationDebugScenario): PkCalibrationResult {
        val globalState = scenario.globalState
        if (globalState != PkCalibrationGlobalState.READY) {
            val reason = when (globalState) {
                PkCalibrationGlobalState.NO_USABLE_LABS -> PkCalibrationReason.NO_USABLE_LABS
                PkCalibrationGlobalState.SHARED_INPUT_INVALID ->
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
                scenario.outlierRoute != route -> scenario.routeStateByRoute.getValue(route)
                scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO ->
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                else -> PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS
            }
            routeResult(
                route = route,
                state = state,
                unreviewedOutlier = scenario.outlierRoute == route &&
                        scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO,
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
        val promotedBetaCovariance = if (promotedRoutes.isEmpty()) {
            null
        } else {
            val variances = promotedRoutes.map { route ->
                requireNotNull(
                    routeResults.first { item -> item.route == route }.laplaceVarianceBeta
                )
            }
            requireNotNull(PkCalibrationPromotedCovariance.create(
                routes = promotedRoutes,
                values = List(promotedRoutes.size) { row ->
                    List(promotedRoutes.size) { column ->
                        if (row == column) variances[row] else 0.0
                    }
                },
            ))
        }
        return requireNotNull(PkCalibrationResult.create(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = routeResults,
            promotedRoutes = promotedRoutes,
            displayParams = requireNotNull(PkPersonalParams.create(displayBetas)),
            promotedBetaCovariance = promotedBetaCovariance,
            invalidNonpositiveLabIds = if (scenario.nonPositiveInput) {
                setOf(nonPositiveLabId())
            } else {
                emptySet()
            },
            forwardModelVersion = DebugForwardModelVersion,
            calibrationModelVersion = DebugCalibrationModelVersion,
        ))
    }

    private fun routeResult(
        route: PkCalibrationRoute,
        state: PkRouteCalibrationDisplayState,
        unreviewedOutlier: Boolean,
    ): PkRouteCalibrationResult {
        val promoted = !state.isPopulationState()
        val fittedBeta = if (promoted) ln(1.25) else null
        val provisional = state == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
        val reasons = when (state) {
            PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS ->
                setOf(PkCalibrationReason.NO_SUPPORTING_LABS)
            PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE ->
                setOf(PkCalibrationReason.NUMERIC_FAILURE)
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL -> if (unreviewedOutlier) {
                setOf(PkCalibrationReason.UNREVIEWED_OUTLIER)
            } else {
                setOf(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE)
            }
            PkRouteCalibrationDisplayState.LAB_CALIBRATED -> emptySet()
        }
        return requireNotNull(PkRouteCalibrationResult.create(
            route = route,
            fittedBeta = fittedBeta,
            displayBeta = fittedBeta ?: 0.0,
            betaPosteriorSd = fittedBeta?.let { if (provisional) 0.3 else 0.1 },
            betaUncertaintyReduction = fittedBeta?.let { 0.5 },
            laplaceVarianceBeta = fittedBeta?.let { if (provisional) 0.09 else 0.01 },
            displayState = state,
            reasons = reasons,
            supportingLabCount = if (promoted) 3 else 0,
            drugSignalLogRange = fittedBeta?.let { 1.0 },
            robustRmseLog = fittedBeta?.let { 0.1 },
            minStudentTWeight = fittedBeta?.let { if (unreviewedOutlier) 0.1 else 0.8 },
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
        nowMillis: Long,
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
        // Phase-3 #9: forced curves span now ± 24 h so QA actually sees the
        // band inside the chart window instead of at a fixed 2023 epoch.
        val centralCurve = listOf(
            requireNotNull(PkCurvePoint.create(nowMillis - DAY_MILLIS, 90.0)),
            requireNotNull(PkCurvePoint.create(nowMillis, 120.0)),
            requireNotNull(PkCurvePoint.create(nowMillis + DAY_MILLIS, 105.0)),
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
