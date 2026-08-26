package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PkCalibrationDebugScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forcedState_showsResetRow_andFixtureReviewActions() {
        val viewModel = fixtureViewModel()
        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PkCalibrationDebugBody(
                    uiState = viewModel.uiState.value,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onAllNodesWithTag(PkCalibrationDebugResetTag)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag(PkCalibrationDebugReviewActionTag)
            .assertCountEquals(2)
        composeRule.onAllNodes(hasText("Keep", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Exclude", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Re-include", substring = true)).assertCountEquals(0)
    }

    @Test
    fun noForcedState_hidesResetRow_butKeepsTheScenarioControls() {
        val viewModel = fixtureViewModel()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PkCalibrationDebugBody(
                    uiState = viewModel.uiState.value,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onAllNodesWithTag(PkCalibrationDebugResetTag)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag(PkCalibrationDebugReviewActionTag)
            .assertCountEquals(0)
        assertTrue(
            composeRule.onAllNodesWithTag(PkCalibrationDebugControlTag)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun fixtureViewModel(): PkCalibrationDebugViewModel {
        return PkCalibrationDebugViewModel(
            scenarioSource = DefaultPkCalibrationDebugScenarioSource(),
            uiFixtureBridge = PkCalibrationUiFixtureBridge(),
            scenarioStore = PkCalibrationDebugScenarioStore(),
        )
    }
}
