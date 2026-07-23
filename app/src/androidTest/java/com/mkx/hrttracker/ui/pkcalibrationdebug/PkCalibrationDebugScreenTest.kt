package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test

class PkCalibrationDebugScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun routeRows_renderOnlyForReadyCanonicalResult() {
        val viewModel = fixtureViewModel(PkCalibrationDebugPreset.INJECTION_CALIBRATED)
        val state = mutableStateOf(viewModel.uiState.value)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PkCalibrationDebugBody(
                    uiState = state.value,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onAllNodesWithTag(PkCalibrationDebugRouteRowTag)
            .assertCountEquals(5)
        composeRule.onAllNodesWithTag(PkCalibrationDebugCurveTag)
            .assertCountEquals(1)
        composeRule.onAllNodes(
            hasText("snapshotKind=SYNTHETIC_ENGINE_EVALUATION")
        ).assertCountEquals(1)

        composeRule.runOnUiThread {
            state.value = state.value.copy(
                globalState = PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED,
                // Deliberately preserve the previous rows to prove the UI fails closed.
                routeRows = state.value.routeRows,
            )
        }
        composeRule.onAllNodesWithTag(PkCalibrationDebugRouteRowTag)
            .assertCountEquals(0)

        composeRule.runOnUiThread {
            state.value = state.value.copy(
                globalState = PkCalibrationGlobalState.READY,
                routeRows = state.value.routeRows.dropLast(1),
            )
        }
        composeRule.onAllNodesWithTag(PkCalibrationDebugRouteRowTag)
            .assertCountEquals(0)
    }

    @Test
    fun liveReviewInFlight_disablesEveryControlAndApplicableAction() {
        val viewModel = fixtureViewModel(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        val uiState = viewModel.uiState.value.copy(liveReviewActionInFlight = true)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PkCalibrationDebugBody(
                    uiState = uiState,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onAllNodesWithTag(PkCalibrationDebugControlTag)
            .assertCountEquals(72)
            .assertAll(isNotEnabled())
        composeRule.onAllNodesWithTag(PkCalibrationDebugReviewActionTag)
            .assertCountEquals(2)
            .assertAll(isNotEnabled())
        composeRule.onAllNodes(hasText("Keep", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Exclude", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Re-include", substring = true)).assertCountEquals(0)
    }

    private fun fixtureViewModel(
        preset: PkCalibrationDebugPreset,
    ): PkCalibrationDebugViewModel {
        val gate = PkCalibrationDebugGate { true }
        return PkCalibrationDebugViewModel(
            scenarioSource = DefaultPkCalibrationDebugScenarioSource(debugGate = gate),
            debugGate = gate,
            autoLoadLive = false,
        ).also { viewModel ->
            viewModel.applyPreset(preset)
        }
    }
}
