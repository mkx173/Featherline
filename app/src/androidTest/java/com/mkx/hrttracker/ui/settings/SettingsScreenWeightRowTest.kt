package com.mkx.hrttracker.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.widget.WidgetAppearance
import org.junit.Rule
import org.junit.Test

class SettingsScreenWeightRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun weightRowStaysVisuallyEnabledWhileWeightMutationIsInProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                SettingsScreenContent(
                    uiState = SettingsUiState(
                        userProfile = UserProfile(
                            weightKg = 72.0,
                            weightOriginalValue = 72.0,
                            weightOriginalUnit = WeightUnit.KILOGRAMS,
                        ),
                        isWeightMutationInProgress = true,
                    ),
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
                    onWidgetAppearanceChange = { },
                    onBackupToFileClick = {},
                    onRestoreFromFileClick = {},
                    onImportExternalTrackerClick = {},
                    showDiagnosticsExport = false,
                    onExportDiagnosticLogsClick = {},
                    onCalibrationClick = {},
                )
            }
        }

        composeRule
            .onNode(
                matcher = hasText(context.getString(R.string.personalization_weight)) and
                        hasClickAction(),
            )
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.personalization_weight_dialog_description))
            .assertDoesNotExist()
    }
}
