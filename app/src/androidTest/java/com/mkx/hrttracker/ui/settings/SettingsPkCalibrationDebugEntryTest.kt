package com.mkx.hrttracker.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.widget.WidgetAppearance
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsPkCalibrationDebugEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calibrationDebugEntry_isOnlyInTheGatedDiagnosticsSection() {
        val showDiagnostics = mutableStateOf(true)
        var clicked = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                SettingsScreenContent(
                    uiState = SettingsUiState(),
                    widgetAppearance = WidgetAppearance.Default,
                    hasNotificationAccess = true,
                    reminderSupportState = SettingsReminderSupportState.NONE,
                    onWeightSave = { _, _ -> },
                    onWeightClear = {},
                    onRemindersEnabledChange = {},
                    onRequestExactAlarmAccess = {},
                    onScreenLockProtectionToggle = {},
                    onAppLockGracePeriodOptionChange = {},
                    onHideScreenContentEnabledChange = {},
                    onAppLanguageOptionChange = {},
                    onFirstDayOfWeekOptionChange = {},
                    onDarkModeOptionChange = {},
                    onAdaptiveColorEnabledChange = {},
                    onPureBlackEnabledChange = {},
                    onCjkTextOffsetEnabledChange = {},
                    onHazeBlurEnabledChange = {},
                    onShowArchivedGroupRecordsChange = {},
                    onHideReferenceRangesChange = {},
                    onHideMedicationDetailsChange = {},
                    onWidgetAppearanceChange = {},
                    onBackupToFileClick = {},
                    onRestoreFromFileClick = {},
                    onImportExternalTrackerClick = {},
                    showDiagnosticsExport = showDiagnostics.value,
                    onExportDiagnosticLogsClick = {},
                    onCalibrationClick = {},
                    onPkCalibrationDebugClick = { clicked = true },
                )
            }
        }

        composeRule
            .onNode(hasText("Calibration (debug)") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(clicked)

        composeRule.runOnUiThread { showDiagnostics.value = false }
        composeRule.onAllNodesWithText("Calibration (debug)").assertCountEquals(0)
    }
}
