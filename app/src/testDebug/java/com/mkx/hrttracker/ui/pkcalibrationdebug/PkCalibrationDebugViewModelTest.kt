package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationDebugViewModelTest {
    private val fixtureSource = DefaultPkCalibrationDebugScenarioSource(
        nowMillis = { 1_800_000_000_000L },
    )

    @Test
    fun applyingAScenario_publishesTheValidatedFixtureThroughTheBridge() {
        val bridge = PkCalibrationUiFixtureBridge()
        val store = PkCalibrationDebugScenarioStore()
        val viewModel = viewModel(bridge = bridge, store = store)
        assertNull(bridge.fixture.value)
        assertFalse(viewModel.uiState.value.forcedStateActive)

        assertEquals(
            PkCalibrationDebugDispatchResult.ACCEPTED,
            viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_CALIBRATED),
        )

        val state = viewModel.uiState.value
        assertTrue(state.forcedStateActive)
        val fixture = requireNotNull(bridge.fixture.value)
        assertSame(state.rawResult, fixture.result)
        assertSame(state.rawRender, fixture.render)
        assertEquals(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_CALIBRATED),
            store.scenario.value,
        )
        assertTrue(fixture.excludedResultIds.isEmpty())
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION),
            fixture.result.promotedRoutes,
        )
    }

    @Test
    fun everyGlobalAndRouteEnum_reachesTheViewModelWithExactCardinality() {
        val viewModel = viewModel()

        PkCalibrationGlobalState.entries.forEach { globalState ->
            assertEquals(
                PkCalibrationDebugDispatchResult.ACCEPTED,
                viewModel.selectGlobalState(globalState),
            )
            val result = requireNotNull(viewModel.uiState.value.rawResult)
            assertEquals(globalState, result.globalState)
            assertEquals(
                if (globalState == PkCalibrationGlobalState.READY) 5 else 0,
                result.routeResults.size,
            )
        }

        PkCalibrationRoute.entries.forEach { route ->
            PkRouteCalibrationDisplayState.entries.forEach { displayState ->
                assertEquals(
                    PkCalibrationDebugDispatchResult.ACCEPTED,
                    viewModel.selectRouteState(route, displayState),
                )
                val result = requireNotNull(viewModel.uiState.value.rawResult)
                assertEquals(PkCalibrationGlobalState.READY, result.globalState)
                assertEquals(
                    PkCalibrationRoute.entries.toList(),
                    result.routeResults.map { it.route },
                )
                assertEquals(
                    displayState,
                    result.routeResults.single { row -> row.route == route }.displayState,
                )
            }
        }
    }

    @Test
    fun presets_mixedStateAndFaultToggles_republishValidatedFixtureFields() {
        val viewModel = viewModel()

        PkCalibrationDebugPreset.entries.forEach { preset ->
            assertEquals(
                PkCalibrationDebugDispatchResult.ACCEPTED,
                viewModel.applyPreset(preset),
            )
            assertEquals(
                PkCalibrationDebugScenario.preset(preset),
                viewModel.uiState.value.scenario,
            )
        }

        viewModel.applyPreset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            viewModel.uiState.value.rawResult?.promotedRoutes,
        )

        viewModel.setRouteRenderFallback(PkCalibrationRoute.INJECTION)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION),
            viewModel.uiState.value.rawRender?.routeRenderFallbacks,
        )
        assertEquals(
            listOf(PkCalibrationRoute.ORAL),
            viewModel.uiState.value.rawRender?.effectivePromotedRoutes,
        )

        viewModel.setBandUnavailable(true)
        assertEquals(
            PkCalibrationBandState.NUMERIC_UNAVAILABLE,
            viewModel.uiState.value.rawRender?.bandState,
        )
        assertTrue(viewModel.uiState.value.rawRender?.bandKnots.orEmpty().isEmpty())
        assertTrue(viewModel.uiState.value.rawRender?.centralCurve.orEmpty().isNotEmpty())

        viewModel.setCentralUnavailable(true)
        assertEquals(
            PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
            viewModel.uiState.value.rawRender?.renderState,
        )
        assertTrue(viewModel.uiState.value.rawRender?.centralCurve.orEmpty().isEmpty())

        viewModel.setCentralUnavailable(false)
        viewModel.setNonPositiveInput(true)
        assertEquals(
            PkCalibrationGlobalState.READY,
            viewModel.uiState.value.rawResult?.globalState,
        )
        assertEquals(
            setOf(PkCalibrationDebugFixtures.nonPositiveLabId()),
            viewModel.uiState.value.rawResult?.invalidNonpositiveLabIds,
        )
    }

    @Test
    fun fixtureReviewCycle_mutatesDispositions_andPublishedExclusions() {
        val bridge = PkCalibrationUiFixtureBridge()
        val viewModel = viewModel(bridge = bridge)
        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)

        assertEquals(
            listOf(PkCalibrationDebugReviewAction.EXCLUDE),
            viewModel.uiState.value.applicableActionCommands.map { it.action },
        )
        assertTrue(bridge.fixture.value?.excludedResultIds.orEmpty().isEmpty())

        viewModel.performReviewAction(
            PkCalibrationDebugActionCommand(PkCalibrationDebugReviewAction.EXCLUDE, resultId)
        )
        assertEquals(
            E2CalibrationDisposition.EXCLUDED,
            viewModel.uiState.value.reviewDispositionByResultId[resultId],
        )
        assertEquals(setOf(resultId), bridge.fixture.value?.excludedResultIds)
        assertEquals(
            listOf(PkCalibrationDebugReviewAction.REINCLUDE),
            viewModel.uiState.value.applicableActionCommands.map { it.action },
        )

        viewModel.performReviewAction(
            PkCalibrationDebugActionCommand(PkCalibrationDebugReviewAction.REINCLUDE, resultId)
        )
        assertEquals(
            E2CalibrationDisposition.AUTO,
            viewModel.uiState.value.reviewDispositionByResultId[resultId],
        )
        assertTrue(bridge.fixture.value?.excludedResultIds.orEmpty().isEmpty())
    }

    @Test
    fun inapplicableReviewCommand_isRejectedWithoutMutatingState() {
        val viewModel = viewModel()
        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        val before = viewModel.uiState.value

        assertEquals(
            PkCalibrationDebugDispatchResult.REJECTED_NOT_APPLICABLE,
            viewModel.performReviewAction(
                PkCalibrationDebugActionCommand(
                    PkCalibrationDebugReviewAction.REINCLUDE,
                    PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION),
                )
            ),
        )
        assertSame(before, viewModel.uiState.value)
    }

    @Test
    fun outlierToggle_keepsUiCommandsAlignedWithFittedEvidence() {
        val viewModel = viewModel()
        val route = PkCalibrationRoute.GEL
        val resultId = PkCalibrationDebugFixtures.outlierId(route)

        viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY)
        viewModel.setOutlierRoute(route)
        val outlierState = viewModel.uiState.value
        assertEquals(route, outlierState.scenario?.outlierRoute)
        assertEquals(
            listOf(PkCalibrationDebugReviewAction.EXCLUDE),
            outlierState.applicableActionCommands.map { command -> command.action },
        )
        assertEquals(
            setOf(resultId),
            requireNotNull(outlierState.rawResult)
                .routeResults.single { row -> row.route == route }
                .unreviewedOutlierLabIds,
        )

        viewModel.setOutlierRoute(null)
        val clearedState = viewModel.uiState.value
        assertNull(clearedState.scenario?.outlierRoute)
        assertTrue(
            requireNotNull(clearedState.rawResult)
                .routeResults.single { row -> row.route == route }
                .unreviewedOutlierLabIds.isEmpty()
        )
        assertFalse(resultId in clearedState.reviewDispositionByResultId)
        assertTrue(clearedState.applicableActionCommands.isEmpty())
    }

    @Test
    fun resetForcedState_publishesNull_andClearsTheHarness() {
        val bridge = PkCalibrationUiFixtureBridge()
        val viewModel = viewModel(bridge = bridge)
        viewModel.applyPreset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        assertTrue(viewModel.uiState.value.forcedStateActive)

        assertEquals(
            PkCalibrationDebugDispatchResult.ACCEPTED,
            viewModel.resetForcedState(),
        )

        assertNull(bridge.fixture.value)
        val state = viewModel.uiState.value
        assertFalse(state.forcedStateActive)
        assertNull(state.scenario)
        assertNull(state.rawResult)
        assertNull(state.rawRender)
        assertTrue(state.applicableActionCommands.isEmpty())
    }

    @Test
    fun freshViewModel_restoresTheForcedScenarioPublishedByAPredecessor() {
        val bridge = PkCalibrationUiFixtureBridge()
        val store = PkCalibrationDebugScenarioStore()
        val first = viewModel(bridge = bridge, store = store)
        first.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        first.performReviewAction(
            PkCalibrationDebugActionCommand(
                PkCalibrationDebugReviewAction.EXCLUDE,
                PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION),
            )
        )
        val published = requireNotNull(bridge.fixture.value)
        val publishedScenario = requireNotNull(store.scenario.value)

        // Simulates navigating away (ViewModel cleared) and re-entering the harness.
        val second = viewModel(bridge = bridge, store = store)

        val restored = second.uiState.value
        assertEquals(publishedScenario, restored.scenario)
        assertEquals(published.result, restored.rawResult)
        assertEquals(
            published.excludedResultIds,
            restored.reviewDispositionByResultId
                .filterValues { it == E2CalibrationDisposition.EXCLUDED }
                .keys,
        )
        assertEquals(published, bridge.fixture.value)
    }

    @Test
    fun sourceFailures_keepTheLastGoodForcedState_andSurfaceTheFailure() {
        val bridge = PkCalibrationUiFixtureBridge()
        val source = SwitchableSource()
        val viewModel = viewModel(source = source, bridge = bridge)
        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_CALIBRATED)
        val goodState = viewModel.uiState.value
        val goodFixture = bridge.fixture.value

        source.throwing = IllegalStateException("fixture failure")
        assertEquals(
            PkCalibrationDebugDispatchResult.REJECTED_SOURCE_EXCEPTION,
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
        )
        assertEquals(
            PkCalibrationDebugSourceUnavailableReason.SOURCE_EXCEPTION,
            viewModel.uiState.value.loadFailure,
        )
        assertSame(goodState.rawResult, viewModel.uiState.value.rawResult)
        assertSame(goodFixture, bridge.fixture.value)

        source.throwing = null
        source.override = PkCalibrationDebugSourceResult.Unavailable(
            PkCalibrationDebugSourceUnavailableReason.SOURCE_CONTRACT_MISMATCH
        )
        assertEquals(
            PkCalibrationDebugDispatchResult.REJECTED_SOURCE_UNAVAILABLE,
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
        )
        assertEquals(
            PkCalibrationDebugSourceUnavailableReason.SOURCE_CONTRACT_MISMATCH,
            viewModel.uiState.value.loadFailure,
        )
        assertSame(goodState.rawResult, viewModel.uiState.value.rawResult)

        val scenarioLessSnapshot = (fixtureSource.loadFixture(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        ) as PkCalibrationDebugSourceResult.Available).snapshot.copy(scenario = null)
        source.override = PkCalibrationDebugSourceResult.Available(scenarioLessSnapshot)
        assertEquals(
            PkCalibrationDebugDispatchResult.REJECTED_SOURCE_CONTRACT_MISMATCH,
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
        )
        assertSame(goodState.rawResult, viewModel.uiState.value.rawResult)

        // Recovery clears the surfaced failure.
        source.override = null
        assertEquals(
            PkCalibrationDebugDispatchResult.ACCEPTED,
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
        )
        assertNull(viewModel.uiState.value.loadFailure)
    }

    @Test
    fun cancellation_isRethrownWithoutBeingMisclassifiedAsAnOrdinaryFailure() {
        val source = SwitchableSource()
        val viewModel = viewModel(source = source)
        source.throwing = CancellationException("cancel fixture")

        assertThrows(CancellationException::class.java) {
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY)
        }
        assertNull(viewModel.uiState.value.loadFailure)
        assertFalse(viewModel.uiState.value.forcedStateActive)
    }

    private fun viewModel(
        source: PkCalibrationDebugScenarioSource = fixtureSource,
        bridge: PkCalibrationUiFixtureBridge = PkCalibrationUiFixtureBridge(),
        store: PkCalibrationDebugScenarioStore = PkCalibrationDebugScenarioStore(),
    ): PkCalibrationDebugViewModel = PkCalibrationDebugViewModel(
        scenarioSource = source,
        uiFixtureBridge = bridge,
        scenarioStore = store,
    )

    private inner class SwitchableSource : PkCalibrationDebugScenarioSource {
        var throwing: Exception? = null
        var override: PkCalibrationDebugSourceResult? = null

        override fun loadFixture(
            scenario: PkCalibrationDebugScenario,
        ): PkCalibrationDebugSourceResult {
            throwing?.let { throw it }
            return override ?: fixtureSource.loadFixture(scenario)
        }
    }
}
