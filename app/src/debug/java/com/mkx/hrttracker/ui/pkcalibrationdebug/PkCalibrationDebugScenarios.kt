package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationPromotedCovariance
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCurvePoint
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
        bandUnavailable = bandUnavailable,
        centralUnavailable = centralUnavailable,
        outlierRoute = outlierRoute.takeUnless { it == route },
        nonPositiveInput = false,
        fixtureDisposition = PkCalibrationDebugFixtureDisposition.AUTO,
    )

    fun withBandUnavailable(value: Boolean): PkCalibrationDebugScenario {
        val states = if (value && routeStateByRoute.values.all { it.isAdjusted.not() }) {
            routeStateByRoute +
                    (PkCalibrationRoute.INJECTION to
                            PkRouteCalibrationDisplayState.LAB_CALIBRATED)
        } else {
            routeStateByRoute
        }
        return requireNotNull(create(
            globalState = PkCalibrationGlobalState.READY,
            routeStateByRoute = states,
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
            bandUnavailable = bandUnavailable,
            centralUnavailable = centralUnavailable,
            outlierRoute = route,
            nonPositiveInput = false,
            fixtureDisposition = PkCalibrationDebugFixtureDisposition.AUTO,
        ))

    fun withNonPositiveInput(value: Boolean): PkCalibrationDebugScenario = requireNotNull(create(
        globalState = PkCalibrationGlobalState.READY,
        routeStateByRoute = routeStateByRoute,
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
            PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL
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
    private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

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
                result.globalState == PkCalibrationGlobalState.NUMERIC_FAILURE ||
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
        require(scenario.centralUnavailable || scenario.bandUnavailable)
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
        if (scenario.globalState != PkCalibrationGlobalState.READY) {
            return PkCalibrationResult(globalState = scenario.globalState)
        }
        val routeResults = PkCalibrationRoute.entries.map { route ->
            val state = when {
                scenario.outlierRoute != route -> scenario.routeStateByRoute.getValue(route)
                scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO ->
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                else -> PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL
            }
            routeResult(
                route = route,
                state = state,
                unreviewedOutlier = scenario.outlierRoute == route &&
                        scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO,
            )
        }
        val promoted = routeResults.filter { it.displayState.isAdjusted }
        return PkCalibrationResult(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = routeResults,
            promotedBetaCovariance = if (promoted.isEmpty()) {
                null
            } else {
                PkCalibrationPromotedCovariance(
                    routes = promoted.map(PkRouteCalibrationResult::route),
                    values = List(promoted.size) { row ->
                        List(promoted.size) { column ->
                            if (row == column) {
                                val sd = requireNotNull(promoted[row].betaPosteriorSd)
                                sd * sd
                            } else {
                                0.0
                            }
                        }
                    },
                )
            },
            ignoredLabs = if (scenario.nonPositiveInput) {
                mapOf(nonPositiveLabId() to PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE)
            } else {
                emptyMap()
            },
        )
    }

    private fun routeResult(
        route: PkCalibrationRoute,
        state: PkRouteCalibrationDisplayState,
        unreviewedOutlier: Boolean,
    ): PkRouteCalibrationResult {
        val promoted = state.isAdjusted
        val fittedBeta = if (promoted) ln(1.25) else null
        val provisional = state == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
        val reasons = when {
            !provisional -> emptySet()
            unreviewedOutlier -> setOf(PkCalibrationReason.UNREVIEWED_OUTLIER)
            else -> setOf(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE)
        }
        return PkRouteCalibrationResult(
            route = route,
            displayState = state,
            reasons = reasons,
            fittedBeta = fittedBeta,
            betaPosteriorSd = fittedBeta?.let { if (provisional) 0.3 else 0.1 },
            supportingLabCount = if (promoted) 3 else 0,
            minStudentTWeight = fittedBeta?.let { if (unreviewedOutlier) 0.1 else 0.8 },
            unreviewedOutlierLabIds = if (unreviewedOutlier) setOf(outlierId(route)) else emptySet(),
        )
    }

    private fun buildRender(
        result: PkCalibrationResult,
        scenario: PkCalibrationDebugScenario,
        nowMillis: Long,
    ): PkCalibrationRenderResult {
        if (scenario.centralUnavailable) {
            return PkCalibrationRenderResult(
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        }
        // Forced curves span now ± 24 h so QA sees the band inside the chart window.
        val centralCurve = listOf(
            PkCurvePoint(nowMillis - DAY_MILLIS, 90.0),
            PkCurvePoint(nowMillis, 120.0),
            PkCurvePoint(nowMillis + DAY_MILLIS, 105.0),
        )
        val promoted = result.promotedRoutes
        if (promoted.isEmpty()) {
            return PkCalibrationRenderResult(
                renderState = PkCalibrationRenderState.POPULATION,
                centralCurve = centralCurve,
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        }
        return PkCalibrationRenderResult(
            renderState = PkCalibrationRenderState.PERSONALIZED,
            effectivePromotedRoutes = promoted,
            effectiveDisplayParams = result.displayParams,
            centralCurve = centralCurve,
            bandState = if (scenario.bandUnavailable) {
                PkCalibrationBandState.NUMERIC_UNAVAILABLE
            } else {
                PkCalibrationBandState.READY
            },
            bandKnots = if (scenario.bandUnavailable) emptyList() else centralCurve.map { point ->
                PkPredictiveBandKnot(
                    epochMillis = point.epochMillis,
                    p025Pgml = point.concentrationPgMl * 0.7,
                    p158655254Pgml = point.concentrationPgMl * 0.85,
                    p50Pgml = point.concentrationPgMl,
                    p841344746Pgml = point.concentrationPgMl * 1.15,
                    p975Pgml = point.concentrationPgMl * 1.3,
                )
            },
        )
    }
}
