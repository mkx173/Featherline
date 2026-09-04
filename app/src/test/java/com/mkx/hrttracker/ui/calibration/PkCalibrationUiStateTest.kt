package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugFixtures
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugPreset
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

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
        // GEL is the one canonical-order-earliest route whose display cap can
        // hold an extreme scale, so every display state (including the
        // extreme-fallback POPULATION_INSUFFICIENT_SUPPORTING_LABS) is
        // representable on it under the one-lab promotion floor.
        val base = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        for (state in PkRouteCalibrationDisplayState.entries) {
            val uiState = ui(
                base.withRouteState(PkCalibrationRoute.GEL, state)
            )
            val adjusted = state == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ||
                state == PkRouteCalibrationDisplayState.LAB_CALIBRATED

            assertEquals(
                "hero for $state",
                adjusted,
                uiState.adjusted,
            )
            assertEquals(
                "limited confidence for $state",
                state == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                uiState.limitedConfidence,
            )
            // Exactly five rows in canonical order, the forced row carrying
            // the state.
            assertEquals(PkCalibrationRoute.entries, uiState.routeRows.map { it.route })
            assertEquals(
                state,
                uiState.routeRows
                    .single { row -> row.route == PkCalibrationRoute.GEL }
                    .displayState,
            )
        }
    }

    @Test
    fun nonReadyGlobalStates_forcePopulationHero_andEmptyRows() {
        val mixed = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        val nonReady = PkCalibrationGlobalState.entries - PkCalibrationGlobalState.READY
        for (globalState in nonReady) {
            val uiState = ui(mixed.withGlobalState(globalState))
            assertEquals(globalState, uiState.globalState)
            assertFalse(uiState.adjusted)
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
        assertTrue(uiState.adjusted)
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
        assertTrue(uiState.adjusted)
        assertFalse(uiState.limitedConfidence)
    }

    @Test
    fun bandFailure_keepsAdjustedHero_andRouteRows() {
        val base = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        val uiState = ui(base.withBandUnavailable(true))

        assertEquals(PkCalibrationBandState.NUMERIC_UNAVAILABLE, uiState.bandState)
        assertEquals(PkCalibrationRenderState.PERSONALIZED, uiState.renderState)
        assertTrue(uiState.adjusted)
        assertEquals(ui(base).routeRows, uiState.routeRows)
    }

    @Test
    fun centralRenderFailure_dropsHeroToPopulation_withoutRewritingRouteRows() {
        val base = PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        val uiState = ui(base.withCentralUnavailable(true))

        assertEquals(PkCalibrationRenderState.NUMERIC_UNAVAILABLE, uiState.renderState)
        // Effective personalized parameters are cleared, so the hero cannot
        // claim adjustment; the fit-level rows stay untouched (§6, §13.3).
        assertFalse(uiState.adjusted)
        assertTrue(uiState.effectivePromotedRoutes.isEmpty())
        assertEquals(ui(base).routeRows, uiState.routeRows)
    }

    @Test
    fun nullRenderOnReadyEvaluation_usesFitLevelPromotion() {
        val snapshot = PkCalibrationDebugFixtures.build(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        )
        val uiState = pkCalibrationUiState(snapshot.result, render = null)

        assertTrue(uiState.adjusted)
        assertEquals(snapshot.result.promotedRoutes, uiState.effectivePromotedRoutes)
        assertEquals(PkCalibrationRenderState.POPULATION, uiState.renderState)
    }

    @Test
    fun debugFixtureCurves_spanTheInjectedClock() {
        // Phase-3 #9: forced bands must be visible in QA, so fixture geometry
        // anchors at the injected clock (now ± 24 h), not a fixed 2023 epoch.
        val nowMillis = 1_800_000_000_000L
        val dayMillis = 24L * 60L * 60L * 1_000L
        val snapshot = PkCalibrationDebugFixtures.build(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL),
            nowMillis = nowMillis,
        )
        val render = requireNotNull(snapshot.render)

        assertEquals(nowMillis - dayMillis, render.centralCurve.first().epochMillis)
        assertEquals(nowMillis + dayMillis, render.centralCurve.last().epochMillis)
        assertEquals(nowMillis - dayMillis, render.bandKnots.first().epochMillis)
        assertEquals(nowMillis + dayMillis, render.bandKnots.last().epochMillis)
    }

    @Test
    fun labRowFlags_flagOnlyEngineClassifiedInvalidNonpositiveLabs() {
        // Phase-3 finding #4: the blocking footer keys off the engine's per-lab
        // classification, never the raw canonical value — a nonpositive lab the
        // engine left Unassigned (no-drug window) must not be flagged.
        val scenario = PkCalibrationDebugScenario
            .preset(PkCalibrationDebugPreset.POPULATION_ONLY)
            .withNonPositiveInput(true)
        val snapshot = PkCalibrationDebugFixtures.build(scenario)
        val uiState = pkCalibrationUiState(snapshot.result, snapshot.render)
        val blockingId = PkCalibrationDebugFixtures.nonPositiveLabId()
        assertEquals(
            mapOf(blockingId to PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE),
            uiState.ignoredLabs,
        )

        val blockingPanel = panel(resultId = blockingId, canonicalValue = 0.0)
        val unassignedPanel = panel(resultId = UUID.randomUUID(), canonicalValue = 0.0)
        val flags = pkCalibrationLabRowFlags(
            state = PkCalibrationScreenState(ui = uiState, excludedResultIds = emptySet()),
            panels = listOf(blockingPanel, unassignedPanel),
        )

        assertEquals(
            mapOf<UUID, PkCalibrationLabRowFlag>(
                blockingPanel.uuid to PkCalibrationLabRowFlag.Ignored(
                    blockingId,
                    PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE,
                ),
            ),
            flags,
        )
    }

    private fun panel(resultId: UUID, canonicalValue: Double): BloodTestPanel {
        return BloodTestPanel(
            uuid = UUID.randomUUID(),
            collectedAt = Instant.EPOCH,
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(
                BloodTestResult(
                    uuid = resultId,
                    createdAt = Instant.EPOCH,
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = canonicalValue,
                    unitSnapshot = "pg/mL",
                    canonicalValue = canonicalValue,
                ),
            ),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    @Test
    fun routeConfidence_tiersAnchorToExistingThresholdsOnly() {
        // HIGH = no warning raised; LOW = provisional with a
        // posterior wider than the sd threshold (the fixture's provisional rows use
        // sd 0.3 > 0.20); population rows carry no tier.
        val uiState = ui(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        )
        assertEquals(
            PkCalibrationRouteConfidence.HIGH,
            uiState.routeRows.single { it.route == PkCalibrationRoute.INJECTION }.confidence,
        )
        assertEquals(
            PkCalibrationRouteConfidence.LOW,
            uiState.routeRows.single { it.route == PkCalibrationRoute.ORAL }.confidence,
        )
        assertTrue(
            uiState.routeRows
                .filterNot { row -> row.displayState.isAdjusted }
                .all { row -> row.confidence == null }
        )

        // MEDIUM = provisional whose posterior already meets the
        // full-calibration sd threshold (only signal contrast still missing).
        val mediumRow = com.mkx.hrttracker.model.pk.PkRouteCalibrationResult(
            route = PkCalibrationRoute.ORAL,
            displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            reasons = setOf(
                com.mkx.hrttracker.model.pk.PkCalibrationReason
                    .UNCERTAIN
            ),
            fittedBeta = kotlin.math.ln(1.25),
            betaPosteriorSd = 0.15,
            supportingLabCount = 3,
        )
        assertEquals(
            PkCalibrationRouteConfidence.MEDIUM,
            pkRouteCalibrationConfidence(mediumRow),
        )

        // Consistency cap (decision 7): a kept outlier supporting the route
        // (min Student-t weight under the outlier threshold on a promoted
        // row) is never better than LOW — even at LAB_CALIBRATED, because the
        // posterior sd only measures curvature at the mode the outlier lost.
        val keptOutlierRow = com.mkx.hrttracker.model.pk.PkRouteCalibrationResult(
            route = PkCalibrationRoute.ORAL,
            displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
            fittedBeta = kotlin.math.ln(1.25),
            betaPosteriorSd = 0.1,
            supportingLabCount = 3,
            minStudentTWeight = 0.1,
        )
        assertEquals(
            PkCalibrationRouteConfidence.LOW,
            pkRouteCalibrationConfidence(keptOutlierRow),
        )
    }

    @Test
    fun adjustedRouteWithoutSupportingLabs_keepsItsRow_butDoesNotDriveTheHero() {
        // Warn-only: a route touched by a negligible share is still fitted and
        // shown on its row, but "adjusted toward your labs for Gel" would
        // overstate it, so the hero and status body name supported routes only.
        val rows = PkCalibrationRoute.entries.map { route ->
            if (route == PkCalibrationRoute.GEL) {
                PkRouteCalibrationResult(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                    reasons = setOf(PkCalibrationReason.NO_SUPPORTING_LABS),
                    fittedBeta = 0.01,
                    betaPosteriorSd = 0.3,
                    supportingLabCount = 0,
                )
            } else {
                PkRouteCalibrationResult(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                )
            }
        }
        val result = PkCalibrationResult(PkCalibrationGlobalState.READY, rows)
        val render = PkCalibrationRenderResult(
            renderState = PkCalibrationRenderState.PERSONALIZED,
            effectivePromotedRoutes = listOf(PkCalibrationRoute.GEL),
            centralCurve = emptyList(),
            bandState = PkCalibrationBandState.READY,
        )
        for (uiState in listOf(
            pkCalibrationUiState(result, render),
            pkCalibrationUiState(result, null),
        )) {
            assertFalse(uiState.adjusted)
            assertFalse(uiState.limitedConfidence)
            assertTrue(uiState.effectivePromotedRoutes.isEmpty())
            assertTrue(
                uiState.routeRows
                    .single { row -> row.route == PkCalibrationRoute.GEL }
                    .displayState.isAdjusted
            )
        }
    }

    @Test
    fun solverLevelFailure_isNumericFailure_forTheStatusCard() {
        // A joint-solve failure keeps READY so the lab rows survive; the
        // status card must still show the numeric-failure copy and retry.
        val rows = PkCalibrationRoute.entries.map { route ->
            PkRouteCalibrationResult(
                route = route,
                displayState = PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
            )
        }
        val solverFailed = pkCalibrationUiState(
            PkCalibrationResult(PkCalibrationGlobalState.READY, rows),
            null,
        )
        assertEquals(PkCalibrationGlobalState.READY, solverFailed.globalState)
        assertTrue(solverFailed.numericFailure)
        assertFalse(solverFailed.adjusted)

        val forwardFailed = pkCalibrationUiState(
            PkCalibrationResult(PkCalibrationGlobalState.NUMERIC_FAILURE),
            null,
        )
        assertTrue(forwardFailed.numericFailure)

        assertFalse(
            ui(PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL))
                .numericFailure
        )

        // Every row failed, not any row: an adjusted route next to a failed one
        // is a personalized curve, and the status card must not put the
        // numeric-failure copy and Retry over it.
        val mixedRows = rows.mapIndexed { index, row ->
            if (index == 0) {
                row.copy(
                    displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                    fittedBeta = 0.1,
                    supportingLabCount = 2,
                )
            } else {
                row
            }
        }
        val mixed = pkCalibrationUiState(
            PkCalibrationResult(PkCalibrationGlobalState.READY, mixedRows),
            null,
        )
        assertFalse(mixed.numericFailure)
        assertTrue(mixed.adjusted)
    }
}
