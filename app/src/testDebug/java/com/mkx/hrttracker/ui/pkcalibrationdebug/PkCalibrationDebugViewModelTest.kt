package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationDebugViewModelTest {
    @Test
    fun applyingAScenario_publishesTheFixtureThroughTheBridge() {
        val bridge = PkCalibrationUiFixtureBridge()
        val store = PkCalibrationDebugScenarioStore()
        val viewModel = viewModel(bridge = bridge, store = store)
        assertNull(bridge.fixture.value)
        assertFalse(viewModel.uiState.value.forcedStateActive)

        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_CALIBRATED)

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
        assertEquals(listOf(PkCalibrationRoute.INJECTION), fixture.result.promotedRoutes)
    }

    @Test
    fun everyGlobalAndRouteEnum_reachesTheViewModelWithExactCardinality() {
        val viewModel = viewModel()

        PkCalibrationGlobalState.entries.forEach { globalState ->
            viewModel.selectGlobalState(globalState)
            val result = requireNotNull(viewModel.uiState.value.rawResult)
            assertEquals(globalState, result.globalState)
            assertEquals(
                if (globalState == PkCalibrationGlobalState.READY) 5 else 0,
                result.routeResults.size,
            )
        }

        PkCalibrationRoute.entries.forEach { route ->
            PkRouteCalibrationDisplayState.entries.forEach { displayState ->
                viewModel.selectRouteState(route, displayState)
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
    fun presets_mixedStateAndFaultToggles_republishFixtureFields() {
        val viewModel = viewModel()

        PkCalibrationDebugPreset.entries.forEach { preset ->
            viewModel.applyPreset(preset)
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
            mapOf(
                PkCalibrationDebugFixtures.nonPositiveLabId() to
                    PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE
            ),
            viewModel.uiState.value.rawResult?.ignoredLabs,
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
    fun inapplicableReviewCommand_doesNotMutateState() {
        val viewModel = viewModel()
        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        val before = viewModel.uiState.value

        viewModel.performReviewAction(
            PkCalibrationDebugActionCommand(
                PkCalibrationDebugReviewAction.REINCLUDE,
                PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION),
            )
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

        viewModel.resetForcedState()

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
    }

    private fun viewModel(
        bridge: PkCalibrationUiFixtureBridge = PkCalibrationUiFixtureBridge(),
        store: PkCalibrationDebugScenarioStore = PkCalibrationDebugScenarioStore(),
    ): PkCalibrationDebugViewModel = PkCalibrationDebugViewModel(
        uiFixtureBridge = bridge,
        scenarioStore = store,
    )
}
