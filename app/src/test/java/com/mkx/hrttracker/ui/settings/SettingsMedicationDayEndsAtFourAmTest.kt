package com.mkx.hrttracker.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.widget.WidgetAppearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsMedicationDayEndsAtFourAmTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchTogglesMedicationDayEnding() {
        val context = RuntimeEnvironment.getApplication()
        val changes = mutableListOf<Boolean>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                SettingsScreenContent(
                    uiState = SettingsUiState(
                        userProfile = UserProfile(
                            weightKg = 72.0,
                            weightOriginalValue = 72.0,
                            weightOriginalUnit = WeightUnit.KILOGRAMS,
                        ),
                        settingsState = SettingsState(medicationDayEndsAtFourAm = false),
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
                    onMedicationDayEndsAtFourAmChange = { changes += it },
                    onDarkModeOptionChange = {},
                    onAdaptiveColorEnabledChange = {},
                    onPureBlackEnabledChange = {},
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

        val title = context.getString(R.string.settings_medication_day_ends_at_four_am)
        composeRule.onNodeWithText(title).performScrollTo().performClick()
        assertEquals(listOf(true), changes)
    }
}
