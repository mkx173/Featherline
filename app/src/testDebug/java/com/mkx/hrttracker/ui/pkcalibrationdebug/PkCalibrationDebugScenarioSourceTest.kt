package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationDebugScenarioSourceTest {
    private val source = DefaultPkCalibrationDebugScenarioSource(
        nowMillis = { 1_800_000_000_000L },
    )

    @Test
    fun everyGlobalState_preservesContractCardinality() {
        PkCalibrationGlobalState.entries.forEach { globalState ->
            val scenario = requireNotNull(PkCalibrationDebugScenario.create())
                .withGlobalState(globalState)
            val snapshot = source.loadFixture(scenario).availableSnapshot()

            assertEquals(globalState, snapshot.result.globalState)
            if (globalState == PkCalibrationGlobalState.READY) {
                assertEquals(
                    PkCalibrationRoute.entries.toList(),
                    snapshot.result.routeResults.map { it.route },
                )
                assertNotNull(snapshot.render)
            } else {
                assertTrue(snapshot.result.routeResults.isEmpty())
                assertTrue(snapshot.result.promotedRoutes.isEmpty())
                assertTrue(snapshot.result.displayParams.routeLogScale.isEmpty())
                assertNull(snapshot.render)
            }
        }
    }

    @Test
    fun everyRouteAndEveryDisplayState_isReachableThroughValidatedContracts() {
        PkCalibrationRoute.entries.forEach { route ->
            PkRouteCalibrationDisplayState.entries.forEach { displayState ->
                val scenario = requireNotNull(
                    requireNotNull(PkCalibrationDebugScenario.create())
                        .withRouteState(route, displayState)
                )
                val snapshot = source.loadFixture(scenario).availableSnapshot()
                val result = snapshot.result

                assertEquals(PkCalibrationGlobalState.READY, result.globalState)
                assertEquals(
                    PkCalibrationRoute.entries.toList(),
                    result.routeResults.map { it.route },
                )
                val routeResult = result.routeResults.single { it.route == route }
                assertEquals(displayState, routeResult.displayState)
                if (displayState.isPopulationForTest()) {
                    assertNull(routeResult.fittedBeta)
                    assertFalse(route in result.promotedRoutes)
                } else {
                    assertTrue(route in result.promotedRoutes)
                    assertEquals(routeResult.fittedBeta, result.displayParams.logScaleFor(route))
                }
                assertNotNull(snapshot.render)
            }
        }
    }

    @Test
    fun arbitraryMixedCombination_preservesCanonicalRouteOrderAndIsolation() {
        val mixed = requireNotNull(
            requireNotNull(PkCalibrationDebugScenario.create())
                .withRouteState(
                    PkCalibrationRoute.INJECTION,
                    PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                )
                ?.withRouteState(
                    PkCalibrationRoute.PATCH,
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                )
                ?.withRouteState(
                    PkCalibrationRoute.GEL,
                    PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                )
                ?.withRouteState(
                    PkCalibrationRoute.ORAL,
                    PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
                )
                ?.withRouteState(
                    PkCalibrationRoute.SUBLINGUAL,
                    PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                )
        )

        val snapshot = source.loadFixture(mixed).availableSnapshot()

        assertEquals(
            PkCalibrationRoute.entries.toList(),
            snapshot.result.routeResults.map { it.route },
        )
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.PATCH),
            snapshot.result.promotedRoutes,
        )
        assertEquals(PkCalibrationRenderState.PERSONALIZED, snapshot.render?.renderState)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.PATCH),
            snapshot.render?.effectivePromotedRoutes,
        )
    }

    @Test
    fun namedPresets_matchTheDeclaredTaxonomy() {
        val expectedStates = mapOf(
            PkCalibrationDebugPreset.POPULATION_ONLY to emptyMap(),
            PkCalibrationDebugPreset.INJECTION_CALIBRATED to mapOf(
                PkCalibrationRoute.INJECTION to PkRouteCalibrationDisplayState.LAB_CALIBRATED
            ),
            PkCalibrationDebugPreset.MIXED_INJECTION_ORAL to mapOf(
                PkCalibrationRoute.INJECTION to PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                PkCalibrationRoute.ORAL to
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            ),
            PkCalibrationDebugPreset.INJECTION_REVIEW_FIT to mapOf(
                PkCalibrationRoute.INJECTION to
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
            ),
            PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE to mapOf(
                PkCalibrationRoute.ORAL to
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
            ),
        )

        PkCalibrationDebugPreset.entries.forEach { preset ->
            val scenario = PkCalibrationDebugScenario.preset(preset)
            val snapshot = source.loadFixture(scenario).availableSnapshot()
            val expected = expectedStates.getValue(preset)

            assertEquals(
                PkCalibrationDebugSnapshotKind.SYNTHETIC_ENGINE_EVALUATION,
                snapshot.kind,
            )

            PkCalibrationRoute.entries.forEach { route ->
                assertEquals(
                    expected[route]
                        ?: PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                    snapshot.result.routeResults.single { it.route == route }.displayState,
                )
            }
            if (preset == PkCalibrationDebugPreset.INJECTION_REVIEW_FIT) {
                assertEquals(PkCalibrationRoute.INJECTION, scenario.outlierRoute)
            }
            assertHealthyRenderForResult(preset.name, snapshot)
        }
    }

    @Test
    fun reviewPreset_recomputesExcludedStateThroughTheEngine() {
        val base = PkCalibrationDebugScenario.preset(
            PkCalibrationDebugPreset.INJECTION_REVIEW_FIT
        )
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        val snapshots = PkCalibrationDebugFixtureDisposition.entries.associateWith { disposition ->
            source.loadFixture(base.withFixtureDisposition(disposition)).availableSnapshot()
        }

        snapshots.forEach { (disposition, snapshot) ->
            assertEquals(
                PkCalibrationDebugSnapshotKind.SYNTHETIC_ENGINE_EVALUATION,
                snapshot.kind,
            )
            assertEquals(
                when (disposition) {
                    PkCalibrationDebugFixtureDisposition.AUTO ->
                        E2CalibrationDisposition.AUTO
                    PkCalibrationDebugFixtureDisposition.EXCLUDED ->
                        E2CalibrationDisposition.EXCLUDED
                },
                snapshot.reviewDispositionByResultId[resultId],
            )
            val injection = snapshot.result.routeResults.single { result ->
                result.route == PkCalibrationRoute.INJECTION
            }
            assertEquals(
                when (disposition) {
                    PkCalibrationDebugFixtureDisposition.AUTO ->
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
                    PkCalibrationDebugFixtureDisposition.EXCLUDED ->
                        PkRouteCalibrationDisplayState.LAB_CALIBRATED
                },
                injection.displayState,
            )
            snapshot.result.routeResults
                .filterNot { result -> result.route == PkCalibrationRoute.INJECTION }
                .forEach { result ->
                    assertEquals(
                        PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                        result.displayState,
                    )
                }
            assertHealthyRenderForResult(disposition.name, snapshot)
        }
        assertTrue(
            resultId in snapshots.getValue(PkCalibrationDebugFixtureDisposition.AUTO)
                .result.routeResults
                .single { result -> result.route == PkCalibrationRoute.INJECTION }
                .unreviewedOutlierLabIds
        )
        assertFalse(
            resultId in snapshots.getValue(PkCalibrationDebugFixtureDisposition.EXCLUDED)
                .result.routeResults
                .single { result -> result.route == PkCalibrationRoute.INJECTION }
                .unreviewedOutlierLabIds
        )
    }

    @Test
    fun bandUnavailable_andCentralUnavailable_areRenderLocal() {
        val calibrated = PkCalibrationDebugScenario.preset(
            PkCalibrationDebugPreset.INJECTION_CALIBRATED
        )
        val baseline = source.loadFixture(calibrated).availableSnapshot()

        val bandUnavailable = source.loadFixture(
            calibrated.withBandUnavailable(true)
        ).availableSnapshot()
        assertEquals(baseline.result, bandUnavailable.result)
        assertEquals(PkCalibrationBandState.NUMERIC_UNAVAILABLE, bandUnavailable.render?.bandState)
        assertTrue(bandUnavailable.render?.bandKnots.orEmpty().isEmpty())
        assertTrue(bandUnavailable.render?.centralCurve.orEmpty().isNotEmpty())

        val centralUnavailable = source.loadFixture(
            calibrated.withCentralUnavailable(true)
        ).availableSnapshot()
        assertEquals(baseline.result, centralUnavailable.result)
        assertEquals(
            PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
            centralUnavailable.render?.renderState,
        )
        assertTrue(centralUnavailable.render?.centralCurve.orEmpty().isEmpty())
        assertEquals(
            PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            centralUnavailable.render?.bandState,
        )
    }

    @Test
    fun outlierAndNonpositiveToggles_areExplicitAndDeterministic() {
        val outlierScenario = requireNotNull(PkCalibrationDebugScenario.create())
            .withOutlierRoute(PkCalibrationRoute.ORAL)
        val first = source.loadFixture(outlierScenario).availableSnapshot()
        val second = source.loadFixture(outlierScenario).availableSnapshot()
        val outlierId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.ORAL)

        assertEquals(first, second)
        assertEquals(
            setOf(outlierId),
            first.result.routeResults.single { it.route == PkCalibrationRoute.ORAL }
                .unreviewedOutlierLabIds,
        )
        assertEquals(
            E2CalibrationDisposition.AUTO,
            first.reviewDispositionByResultId[outlierId],
        )

        val nonpositive = source.loadFixture(
            outlierScenario.withNonPositiveInput(true)
        ).availableSnapshot()
        // A non-positive lab is ignored by the fit and flagged, never a
        // global failure: the rest of the evaluation stays READY.
        assertEquals(PkCalibrationGlobalState.READY, nonpositive.result.globalState)
        assertEquals(
            mapOf(
                PkCalibrationDebugFixtures.nonPositiveLabId() to
                    com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE
            ),
            nonpositive.result.ignoredLabs,
        )
        assertEquals(first.result.routeResults, nonpositive.result.routeResults)
    }

    private fun PkCalibrationDebugSourceResult.availableSnapshot(): PkCalibrationDebugSnapshot {
        return (this as PkCalibrationDebugSourceResult.Available).snapshot
    }

    private fun assertHealthyRenderForResult(
        label: String,
        snapshot: PkCalibrationDebugSnapshot,
    ) {
        val render = requireNotNull(snapshot.render)
        assertTrue(label, render.centralCurve.isNotEmpty())
        assertEquals(snapshot.result.promotedRoutes, render.effectivePromotedRoutes)
        assertEquals(snapshot.result.displayParams, render.effectiveDisplayParams)
        if (snapshot.result.promotedRoutes.isEmpty()) {
            assertEquals(PkCalibrationRenderState.POPULATION, render.renderState)
            assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, render.bandState)
            assertTrue(render.bandKnots.isEmpty())
        } else {
            assertEquals(PkCalibrationRenderState.PERSONALIZED, render.renderState)
            assertEquals(PkCalibrationBandState.READY, render.bandState)
            assertTrue(render.bandKnots.isNotEmpty())
        }
    }

    private fun PkRouteCalibrationDisplayState.isPopulationForTest(): Boolean {
        return this != PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL &&
                this != PkRouteCalibrationDisplayState.LAB_CALIBRATED
    }
}
