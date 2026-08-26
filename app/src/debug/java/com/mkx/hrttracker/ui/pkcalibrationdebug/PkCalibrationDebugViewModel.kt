package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.lifecycle.ViewModel
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import java.util.UUID
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

enum class PkCalibrationDebugDispatchResult {
    ACCEPTED,
    REJECTED_NOT_APPLICABLE,
    REJECTED_SOURCE_UNAVAILABLE,
    REJECTED_SOURCE_EXCEPTION,
    REJECTED_SOURCE_CONTRACT_MISMATCH,
}

data class PkCalibrationDebugUiState(
    val scenario: PkCalibrationDebugScenario? = null,
    val loadFailure: PkCalibrationDebugSourceUnavailableReason? = null,
    val rawResult: PkCalibrationResult? = null,
    val rawRender: PkCalibrationRenderResult? = null,
    val reviewDispositionByResultId: Map<UUID, E2CalibrationDisposition> = emptyMap(),
    val applicableActionCommands: List<PkCalibrationDebugActionCommand> = emptyList(),
) {
    val forcedStateActive: Boolean get() = rawResult != null
}

/**
 * State holder for the debug-only "force UI state" harness. Picking a scenario
 * publishes a validated fixture through [PkCalibrationUiFixtureBridge] so the
 * real Home and Calibration screens render it. [resetForcedState] is the only
 * path that clears the forced state: navigating away clears this ViewModel but
 * deliberately not the bridge, and re-entry replays the published scenario so
 * the picker reflects what the app is currently showing.
 */
@HiltViewModel
class PkCalibrationDebugViewModel @Inject constructor(
    private val scenarioSource: PkCalibrationDebugScenarioSource,
    private val uiFixtureBridge: PkCalibrationUiFixtureBridge,
    private val scenarioStore: PkCalibrationDebugScenarioStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PkCalibrationDebugUiState())
    val uiState: StateFlow<PkCalibrationDebugUiState> = _uiState.asStateFlow()

    init {
        if (uiFixtureBridge.fixture.value != null) {
            scenarioStore.scenario.value?.let { forced -> replaceFixture(forced) }
        }
    }

    fun resetForcedState(): PkCalibrationDebugDispatchResult {
        uiFixtureBridge.publish(null)
        scenarioStore.scenario.value = null
        _uiState.value = PkCalibrationDebugUiState()
        return PkCalibrationDebugDispatchResult.ACCEPTED
    }

    fun selectGlobalState(
        state: PkCalibrationGlobalState,
    ): PkCalibrationDebugDispatchResult = updateFixture { scenario ->
        scenario.withGlobalState(state)
    }

    fun selectRouteState(
        route: PkCalibrationRoute,
        state: PkRouteCalibrationDisplayState,
    ): PkCalibrationDebugDispatchResult = updateFixture { scenario ->
        scenario.withRouteState(route, state)
    }

    fun applyPreset(preset: PkCalibrationDebugPreset): PkCalibrationDebugDispatchResult =
        replaceFixture(PkCalibrationDebugScenario.preset(preset))

    fun setRouteRenderFallback(
        route: PkCalibrationRoute?,
    ): PkCalibrationDebugDispatchResult = updateFixture { scenario ->
        scenario.withRouteRenderFallback(route)
    }

    fun setBandUnavailable(value: Boolean): PkCalibrationDebugDispatchResult =
        updateFixture { scenario -> scenario.withBandUnavailable(value) }

    fun setCentralUnavailable(value: Boolean): PkCalibrationDebugDispatchResult =
        updateFixture { scenario -> scenario.withCentralUnavailable(value) }

    fun setOutlierRoute(route: PkCalibrationRoute?): PkCalibrationDebugDispatchResult =
        updateFixture { scenario -> scenario.withOutlierRoute(route) }

    fun setNonPositiveInput(value: Boolean): PkCalibrationDebugDispatchResult =
        updateFixture { scenario -> scenario.withNonPositiveInput(value) }

    /** Fixture-only review path: mutates the in-memory fixture disposition, never storage. */
    fun performReviewAction(
        command: PkCalibrationDebugActionCommand,
    ): PkCalibrationDebugDispatchResult {
        if (command !in _uiState.value.applicableActionCommands) {
            return PkCalibrationDebugDispatchResult.REJECTED_NOT_APPLICABLE
        }
        val scenario = _uiState.value.scenario
            ?: return PkCalibrationDebugDispatchResult.REJECTED_NOT_APPLICABLE
        val disposition = when (command.action) {
            PkCalibrationDebugReviewAction.EXCLUDE ->
                PkCalibrationDebugFixtureDisposition.EXCLUDED
            PkCalibrationDebugReviewAction.REINCLUDE ->
                PkCalibrationDebugFixtureDisposition.AUTO
        }
        return replaceFixture(scenario.withFixtureDisposition(disposition))
    }

    private fun updateFixture(
        transform: (PkCalibrationDebugScenario) -> PkCalibrationDebugScenario?,
    ): PkCalibrationDebugDispatchResult {
        val base = _uiState.value.scenario
            ?: PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        // An unrepresentable combination keeps the last good forced state and
        // surfaces a contract mismatch instead of crashing the harness.
        val next = transform(base)
            ?: return PkCalibrationDebugDispatchResult.REJECTED_SOURCE_CONTRACT_MISMATCH
        return replaceFixture(next)
    }

    // Failures keep the last good forced state (so the harness stays consistent
    // with what the bridge is publishing) and surface the reason instead.
    private fun replaceFixture(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugDispatchResult {
        val result = try {
            scenarioSource.loadFixture(scenario)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(
                loadFailure = PkCalibrationDebugSourceUnavailableReason.SOURCE_EXCEPTION
            )
            return PkCalibrationDebugDispatchResult.REJECTED_SOURCE_EXCEPTION
        }
        return when (result) {
            is PkCalibrationDebugSourceResult.Unavailable -> {
                _uiState.value = _uiState.value.copy(loadFailure = result.reason)
                PkCalibrationDebugDispatchResult.REJECTED_SOURCE_UNAVAILABLE
            }
            is PkCalibrationDebugSourceResult.Available -> {
                if (result.snapshot.scenario == null) {
                    _uiState.value = _uiState.value.copy(
                        loadFailure = PkCalibrationDebugSourceUnavailableReason
                            .SOURCE_CONTRACT_MISMATCH
                    )
                    PkCalibrationDebugDispatchResult.REJECTED_SOURCE_CONTRACT_MISMATCH
                } else {
                    applySnapshot(result.snapshot)
                    PkCalibrationDebugDispatchResult.ACCEPTED
                }
            }
        }
    }

    private fun applySnapshot(snapshot: PkCalibrationDebugSnapshot) {
        val dispositions = canonicalReviewDispositions(snapshot)
        _uiState.value = _uiState.value.copy(
            scenario = snapshot.scenario,
            loadFailure = null,
            rawResult = snapshot.result,
            rawRender = snapshot.render,
            reviewDispositionByResultId = dispositions,
            applicableActionCommands = applicableCommands(snapshot.result, dispositions),
        )
        scenarioStore.scenario.value = snapshot.scenario
        uiFixtureBridge.publish(
            PkCalibrationUiFixture(
                result = snapshot.result,
                render = snapshot.render,
                excludedResultIds = dispositions
                    .filterValues { disposition ->
                        disposition == E2CalibrationDisposition.EXCLUDED
                    }
                    .keys,
            )
        )
    }

    private fun canonicalReviewDispositions(
        snapshot: PkCalibrationDebugSnapshot,
    ): Map<UUID, E2CalibrationDisposition> {
        val current = snapshot.reviewDispositionByResultId.toMutableMap()
        snapshot.result.routeResults.forEach { routeResult ->
            routeResult.unreviewedOutlierLabIds.forEach { resultId ->
                current[resultId] = E2CalibrationDisposition.AUTO
            }
        }
        return current.entries
            .sortedBy { entry -> entry.key.toString() }
            .associateTo(linkedMapOf()) { entry -> entry.key to entry.value }
    }

    private fun applicableCommands(
        result: PkCalibrationResult,
        dispositions: Map<UUID, E2CalibrationDisposition>,
    ): List<PkCalibrationDebugActionCommand> {
        val currentOutlierIds = result.routeResults
            .flatMap(PkRouteCalibrationResult::unreviewedOutlierLabIds)
            .toSet()
        return dispositions.flatMap { (resultId, disposition) ->
            when (disposition) {
                E2CalibrationDisposition.AUTO -> if (resultId in currentOutlierIds) {
                    listOf(
                        PkCalibrationDebugActionCommand(
                            PkCalibrationDebugReviewAction.EXCLUDE,
                            resultId,
                        ),
                    )
                } else {
                    emptyList()
                }
                E2CalibrationDisposition.EXCLUDED -> listOf(
                    PkCalibrationDebugActionCommand(
                        PkCalibrationDebugReviewAction.REINCLUDE,
                        resultId,
                    )
                )
            }
        }
    }
}
