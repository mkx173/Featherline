package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugFixtures
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugPreset
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapper-level assertions for every §14 state the status surface consumes.
 * Fixtures come from [PkCalibrationDebugFixtures], so every input is a
 * validated production contract object, never a hand-built shape.
 */
class PkCalibrationUiStateTest {

    private fun ui(scenario: PkCalibrationDebugScenario): PkCalibrationUiState {
        val snapshot = PkCalibrationDebugFixtures.build(scenario)
        return pkCalibrationUiState(snapshot.result, snapshot.render)
    }

    @Test
    fun heroDerivation_coversEveryRouteState_underReadyGlobal() {
        val base = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        for (state in PkRouteCalibrationDisplayState.entries) {
            val uiState = ui(base.withRouteState(PkCalibrationRoute.INJECTION, state))
            val adjusted = state == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ||
                state == PkRouteCalibrationDisplayState.LAB_CALIBRATED

            assertEquals(
                "hero for $state",
                if (adjusted) PkCalibrationHeroKind.ADJUSTED else PkCalibrationHeroKind.POPULATION,
                uiState.heroKind,
            )
            assertEquals(
                "limited confidence for $state",
                state == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                uiState.limitedConfidence,
            )
            // Exactly five rows in canonical order, first row carrying the state.
            assertEquals(PkCalibrationRoute.entries, uiState.routeRows.map { it.route })
            assertEquals(state, uiState.routeRows.first().displayState)
        }
    }

    @Test
    fun nonReadyGlobalStates_forcePopulationHero_andEmptyRows() {
        val mixed = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        val nonReady = PkCalibrationGlobalState.entries - PkCalibrationGlobalState.READY
        for (globalState in nonReady) {
            val uiState = ui(mixed.withGlobalState(globalState))
            assertEquals(globalState, uiState.globalState)
            assertEquals(PkCalibrationHeroKind.POPULATION, uiState.heroKind)
            assertFalse(uiState.limitedConfidence)
            assertTrue(uiState.routeRows.isEmpty())
            assertTrue(uiState.effectivePromotedRoutes.isEmpty())
            // renderFor is null for non-READY evaluations; the mapper must not
            // synthesize a personalized render from a stale value.
            assertEquals(PkCalibrationRenderState.POPULATION, uiState.renderState)
            assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, uiState.bandState)
        }
    }

    @Test
    fun mixedPreset_adjustedHero_withLimitedConfidence() {
        val uiState = ui(PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL))
        assertEquals(PkCalibrationHeroKind.ADJUSTED, uiState.heroKind)
        assertTrue(uiState.limitedConfidence)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            uiState.effectivePromotedRoutes,
        )
        assertEquals(PkCalibrationRenderState.PERSONALIZED, uiState.renderState)
        assertEquals(PkCalibrationBandState.READY, uiState.bandState)
    }

    @Test
    fun calibratedOnlyPreset_adjustedHero_withoutLimitedConfidence() {
        val uiState = ui(PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_CALIBRATED))
        assertEquals(PkCalibrationHeroKind.ADJUSTED, uiState.heroKind)
        assertFalse(uiState.limitedConfidence)
    }

    @Test
    fun routeRenderFallback_narrowsHero_withoutRewritingRouteRows() {
        val scenario = PkCalibrationDebugScenario
            .preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
            .withRouteRenderFallback(PkCalibrationRoute.INJECTION)
        val uiState = ui(scenario)

        assertEquals(listOf(PkCalibrationRoute.INJECTION), uiState.routeRenderFallbacks)
        assertFalse(uiState.effectivePromotedRoutes.contains(PkCalibrationRoute.INJECTION))
        // Oral remains effective, so the hero stays adjusted.
        assertEquals(PkCalibrationHeroKind.ADJUSTED, uiState.heroKind)
        // §11: a range-local fallback never rewrites the fit-level status row.
        assertEquals(
            PkRouteCalibrationDisplayState.LAB_CALIBRATED,
            uiState.routeRows.first { it.route == PkCalibrationRoute.INJECTION }.displayState,
        )
    }

    @Test
    fun bandFailure_keepsAdjustedHero_andRouteRows() {
        val base = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        val uiState = ui(base.withBandUnavailable(true))

        assertEquals(PkCalibrationBandState.NUMERIC_UNAVAILABLE, uiState.bandState)
        assertEquals(PkCalibrationRenderState.PERSONALIZED, uiState.renderState)
        assertEquals(PkCalibrationHeroKind.ADJUSTED, uiState.heroKind)
        assertEquals(ui(base).routeRows, uiState.routeRows)
    }

    @Test
    fun centralRenderFailure_dropsHeroToPopulation_withoutRewritingRouteRows() {
        val base = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        val uiState = ui(base.withCentralUnavailable(true))

        assertEquals(PkCalibrationRenderState.NUMERIC_UNAVAILABLE, uiState.renderState)
        // Effective personalized parameters are cleared, so the hero cannot
        // claim adjustment; the fit-level rows stay untouched (§6, §13.3).
        assertEquals(PkCalibrationHeroKind.POPULATION, uiState.heroKind)
        assertTrue(uiState.effectivePromotedRoutes.isEmpty())
        assertEquals(ui(base).routeRows, uiState.routeRows)
    }

    @Test
    fun nullRenderOnReadyEvaluation_usesFitLevelPromotion() {
        val snapshot = PkCalibrationDebugFixtures.build(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        )
        val uiState = pkCalibrationUiState(snapshot.result, render = null)

        assertEquals(PkCalibrationHeroKind.ADJUSTED, uiState.heroKind)
        assertEquals(snapshot.result.promotedRoutes, uiState.effectivePromotedRoutes)
        assertEquals(PkCalibrationRenderState.POPULATION, uiState.renderState)
    }

    @Test
    fun uiProjection_withholdsDiagnosticFitFields() {
        // Handoff §5.4: raw fit diagnostics never reach normal product
        // surfaces. Mirrors the Phase-1 debug-screen withholding precedent.
        val forbidden = setOf(
            "fittedBeta",
            "displayBeta",
            "betaPosteriorSd",
            "betaUncertaintyReduction",
            "laplaceVarianceBeta",
            "drugSignalLogRange",
            "robustRmseLog",
            "minStudentTWeight",
            "displayParams",
            "promotedBetaCovariance",
            "effectiveDisplayParams",
        )
        val exposed = (
            PkCalibrationRouteRowUiState::class.java.declaredFields +
                PkCalibrationUiState::class.java.declaredFields
            ).map { it.name }.toSet()

        assertTrue(
            "UI state must not expose diagnostic fit fields: ${exposed intersect forbidden}",
            (exposed intersect forbidden).isEmpty(),
        )
    }
}
