package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.lifecycle.ViewModel
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import java.util.UUID
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PkCalibrationDebugReviewAction {
    EXCLUDE,
    REINCLUDE,
}

data class PkCalibrationDebugActionCommand(
    val action: PkCalibrationDebugReviewAction,
    val resultId: UUID,
)

data class PkCalibrationDebugUiState(
    val scenario: PkCalibrationDebugScenario? = null,
    val rawResult: PkCalibrationResult? = null,
    val rawRender: PkCalibrationRenderResult? = null,
    val reviewDispositionByResultId: Map<UUID, E2CalibrationDisposition> = emptyMap(),
) {
    val forcedStateActive: Boolean get() = rawResult != null

    /** One command per fixture outlier lab: exclude while AUTO, re-include while EXCLUDED. */
    val applicableActionCommands: List<PkCalibrationDebugActionCommand>
        get() = reviewDispositionByResultId.map { (resultId, disposition) ->
            PkCalibrationDebugActionCommand(
                action = when (disposition) {
                    E2CalibrationDisposition.AUTO -> PkCalibrationDebugReviewAction.EXCLUDE
                    E2CalibrationDisposition.EXCLUDED -> PkCalibrationDebugReviewAction.REINCLUDE
                },
                resultId = resultId,
            )
        }
}

/**
 * State holder for the debug-only "force UI state" harness. Picking a scenario
 * publishes a fixture through [PkCalibrationUiFixtureBridge] so the real Home
 * and Calibration screens render it. [resetForcedState] is the only path that
 * clears the forced state: navigating away clears this ViewModel but not the
 * bridge, and re-entry replays the published scenario.
 */
@HiltViewModel
class PkCalibrationDebugViewModel @Inject constructor(
    private val uiFixtureBridge: PkCalibrationUiFixtureBridge,
    private val scenarioStore: PkCalibrationDebugScenarioStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PkCalibrationDebugUiState())
    val uiState: StateFlow<PkCalibrationDebugUiState> = _uiState.asStateFlow()

    init {
        if (uiFixtureBridge.fixture.value != null) {
            scenarioStore.scenario.value?.let(::replaceFixture)
        }
    }

    fun resetForcedState() {
        uiFixtureBridge.publish(null)
        scenarioStore.scenario.value = null
        _uiState.value = PkCalibrationDebugUiState()
    }

    fun selectGlobalState(state: PkCalibrationGlobalState) =
        updateFixture { it.withGlobalState(state) }

    fun selectRouteState(route: PkCalibrationRoute, state: PkRouteCalibrationDisplayState) =
        updateFixture { it.withRouteState(route, state) }

    fun applyPreset(preset: PkCalibrationDebugPreset) =
        replaceFixture(PkCalibrationDebugScenario.preset(preset))

    fun setBandUnavailable(value: Boolean) = updateFixture { it.withBandUnavailable(value) }

    fun setCentralUnavailable(value: Boolean) =
        updateFixture { it.withCentralUnavailable(value) }

    fun setOutlierRoute(route: PkCalibrationRoute?) = updateFixture { it.withOutlierRoute(route) }

    fun setNonPositiveInput(value: Boolean) = updateFixture { it.withNonPositiveInput(value) }

    /** Fixture-only review path: mutates the in-memory fixture disposition, never storage. */
    fun performReviewAction(command: PkCalibrationDebugActionCommand) {
        val state = _uiState.value
        if (command !in state.applicableActionCommands) return
        val disposition = when (command.action) {
            PkCalibrationDebugReviewAction.EXCLUDE ->
                PkCalibrationDebugFixtureDisposition.EXCLUDED
            PkCalibrationDebugReviewAction.REINCLUDE ->
                PkCalibrationDebugFixtureDisposition.AUTO
        }
        replaceFixture(requireNotNull(state.scenario).withFixtureDisposition(disposition))
    }

    private fun updateFixture(
        transform: (PkCalibrationDebugScenario) -> PkCalibrationDebugScenario,
    ) {
        val base = _uiState.value.scenario
            ?: PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        replaceFixture(transform(base))
    }

    private fun replaceFixture(scenario: PkCalibrationDebugScenario) {
        val snapshot = PkCalibrationDebugFixtures.build(scenario)
        _uiState.value = PkCalibrationDebugUiState(
            scenario = scenario,
            rawResult = snapshot.result,
            rawRender = snapshot.render,
            reviewDispositionByResultId = snapshot.reviewDispositionByResultId,
        )
        scenarioStore.scenario.value = scenario
        uiFixtureBridge.publish(
            PkCalibrationUiFixture(
                result = snapshot.result,
                render = snapshot.render,
                excludedResultIds = snapshot.reviewDispositionByResultId
                    .filterValues { it == E2CalibrationDisposition.EXCLUDED }
                    .keys,
            )
        )
    }
}
