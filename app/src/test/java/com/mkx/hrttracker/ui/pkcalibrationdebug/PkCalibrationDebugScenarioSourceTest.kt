package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationEngineEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationDebugScenarioSourceTest {
    private val enabled = PkCalibrationDebugGate { true }
    private val source = DefaultPkCalibrationDebugScenarioSource(debugGate = enabled)

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
                assertTrue(snapshot.result.globalReasons.isNotEmpty())
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
                val scenario = requireNotNull(PkCalibrationDebugScenario.create())
                    .withRouteState(route, displayState)
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
                    assertEquals(0.0.toBits(), routeResult.displayBeta.toBits())
                    assertFalse(route in result.promotedRoutes)
                } else {
                    assertTrue(route in result.promotedRoutes)
                    assertEquals(routeResult.displayBeta, result.displayParams.logScaleFor(route), 0.0)
                }
                assertNotNull(snapshot.render)
            }
        }
    }

    @Test
    fun everyRoute_canReachAnExactDisplayCapBoundaryContract() {
        PkCalibrationRoute.entries.forEach { route ->
            val scenario = requireNotNull(PkCalibrationDebugScenario.create())
                .withDisplayCapBoundaryRoute(route)
            val snapshot = source.loadFixture(scenario).availableSnapshot()
            val routeResult = snapshot.result.routeResults.single { it.route == route }

            assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, routeResult.displayState)
            assertTrue(routeResult.atDisplayCapBoundary)
            assertTrue(PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY in routeResult.reasons)
            assertTrue(route in snapshot.result.promotedRoutes)
            snapshot.result.routeResults
                .filterNot { it.route == route }
                .forEach { other ->
                    assertFalse(other.atDisplayCapBoundary)
                    assertFalse(PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY in other.reasons)
                }
        }
    }

    @Test
    fun arbitraryMixedCombination_preservesCanonicalRouteOrderAndIsolation() {
        val mixed = requireNotNull(PkCalibrationDebugScenario.create())
            .withRouteState(
                PkCalibrationRoute.INJECTION,
                PkRouteCalibrationDisplayState.LAB_CALIBRATED,
            )
            .withRouteState(
                PkCalibrationRoute.PATCH,
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            )
            .withRouteState(
                PkCalibrationRoute.GEL,
                PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
            )
            .withRouteState(
                PkCalibrationRoute.ORAL,
                PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
            )
            .withRouteState(
                PkCalibrationRoute.SUBLINGUAL,
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
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
                        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE
            ),
            PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE to mapOf(
                PkCalibrationRoute.ORAL to
                        PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED
            ),
        )

        PkCalibrationDebugPreset.entries.forEach { preset ->
            val scenario = PkCalibrationDebugScenario.preset(preset)
            val snapshot = source.loadFixture(scenario).availableSnapshot()
            val expected = expectedStates.getValue(preset)

            PkCalibrationRoute.entries.forEach { route ->
                assertEquals(
                    expected[route]
                        ?: PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
                    snapshot.result.routeResults.single { it.route == route }.displayState,
                )
            }
            if (preset == PkCalibrationDebugPreset.INJECTION_REVIEW_FIT) {
                assertEquals(PkCalibrationRoute.INJECTION, scenario.outlierRoute)
            }
        }
    }

    @Test
    fun routeFallback_bandUnavailable_andCentralUnavailable_areRenderLocal() {
        val calibrated = PkCalibrationDebugScenario.preset(
            PkCalibrationDebugPreset.INJECTION_CALIBRATED
        )
        val baseline = source.loadFixture(calibrated).availableSnapshot()

        val routeFallback = source.loadFixture(
            calibrated.withRouteRenderFallback(PkCalibrationRoute.INJECTION)
        ).availableSnapshot()
        assertEquals(baseline.result, routeFallback.result)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION),
            routeFallback.render?.routeRenderFallbacks,
        )
        assertEquals(PkCalibrationRenderState.POPULATION, routeFallback.render?.renderState)

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
    fun outlier_nonpositive_andGuardDropToggles_areExplicitAndDeterministic() {
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
        assertEquals(PkCalibrationGlobalState.SHARED_INPUT_INVALID, nonpositive.result.globalState)
        assertEquals(
            setOf(PkCalibrationReason.INVALID_NONPOSITIVE_E2),
            nonpositive.result.globalReasons,
        )
        assertTrue(nonpositive.result.routeResults.isEmpty())

        val guardDropped = source.loadFixture(
            requireNotNull(PkCalibrationDebugScenario.create())
                .withGuardDroppedRoute(PkCalibrationRoute.GEL)
        ).availableSnapshot()
        val gel = guardDropped.result.routeResults.single { it.route == PkCalibrationRoute.GEL }
        assertEquals(1, gel.dominantCandidateLabCount)
        assertEquals(0, gel.dominantLabCount)
    }

    @Test
    fun sourceBoundariesRejectRelease_andNeverTouchLiveProvider() = runTest {
        var evaluationCalls = 0
        var domainCalls = 0
        val provider = object : PkCalibrationDebugLiveSnapshotProvider {
            override suspend fun currentEvaluation(): PkCalibrationEngineEvaluation? {
                evaluationCalls += 1
                return null
            }

            override suspend fun currentRenderDomain(): PkChartDomain? {
                domainCalls += 1
                return null
            }
        }
        val disabledSource = DefaultPkCalibrationDebugScenarioSource(
            liveSnapshotProvider = provider,
            debugGate = PkCalibrationDebugGate { false },
        )

        assertSame(PkCalibrationDebugSourceResult.DebugDisabled, disabledSource.loadLive())
        assertSame(
            PkCalibrationDebugSourceResult.DebugDisabled,
            disabledSource.loadFixture(requireNotNull(PkCalibrationDebugScenario.create())),
        )
        assertEquals(0, evaluationCalls)
        assertEquals(0, domainCalls)
    }

    @Test
    fun liveSourceUsesBoundEvaluationAndItsRenderer() = runTest {
        val fixture = source.loadFixture(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_CALIBRATED)
        ).availableSnapshot()
        val domain = requireNotNull(PkChartDomain.create(
            rangeStartEpochMillis = 1_700_000_000_000L,
            rangeEndEpochMillis = 1_700_007_200_000L,
            samplingIntervalMillis = 3_600_000L,
            chartGridVersion = "debug-test-grid/v1",
        ))
        val evaluation = mockk<PkCalibrationEngineEvaluation>()
        every { evaluation.isReady } returns true
        every { evaluation.result } returns fixture.result
        every { evaluation.renderFor(domain) } returns fixture.render
        val provider = object : PkCalibrationDebugLiveSnapshotProvider {
            override suspend fun currentEvaluation(): PkCalibrationEngineEvaluation = evaluation
            override suspend fun currentRenderDomain(): PkChartDomain = domain
            override suspend fun currentReviewDispositions() =
                fixture.reviewDispositionByResultId
        }
        val liveSource = DefaultPkCalibrationDebugScenarioSource(provider, enabled)

        val live = liveSource.loadLive().availableSnapshot()

        assertEquals(PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION, live.kind)
        assertSame(fixture.result, live.result)
        assertSame(fixture.render, live.render)
        assertNull(live.scenario)
    }

    private fun PkCalibrationDebugSourceResult.availableSnapshot(): PkCalibrationDebugSnapshot {
        return (this as PkCalibrationDebugSourceResult.Available).snapshot
    }

    private fun PkRouteCalibrationDisplayState.isPopulationForTest(): Boolean {
        return this != PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL &&
                this != PkRouteCalibrationDisplayState.LAB_CALIBRATED
    }
}
