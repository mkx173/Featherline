package com.mkx.hrttracker.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey
import com.mkx.hrttracker.data.importer.ExternalImportWarningReason
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.widget.WidgetAppearance
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class SettingsScreenExternalImportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsContentShowsExternalImportRowInBackupRestoreSection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var importClicked = false

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
                    onImportExternalTrackerClick = { importClicked = true },
                    showDiagnosticsExport = false,
                    onExportDiagnosticLogsClick = {},
                    onCalibrationClick = {},
                )
            }
        }

        composeRule
            .onNode(
                matcher = hasText(context.getString(R.string.settings_import_external_tracker_json)) and
                        hasClickAction(),
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(importClicked)
    }

    @Test
    fun reviewSheetShowsSummarySkippedSectionAndActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preview = sampleExternalImportPreviewForUi()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ExternalImportReviewSheet(
                    preview = preview,
                    isImporting = false,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    onDismissRequest = {},
                    onImportClick = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.external_import_review_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_detected_source, "NoMTF"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_medication_label))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_medication_summary, 2, 1))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_lab_label))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_lab_summary, 3, 4))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_skipped_rows_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.external_import_confirm)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).assertIsDisplayed()
    }

    @Test
    fun reviewSheetSkippedRowShowsCountAndCopyActionWithoutRawMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preview = sampleExternalImportPreviewForUi(
            warning = ExternalImportWarning(
                reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                externalId = "row-7",
                rowIndex = 7,
                messageKey = ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND,
                message = "RAW WARNING SHOULD NOT RENDER",
            )
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ExternalImportReviewSheet(
                    preview = preview,
                    isImporting = false,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    onDismissRequest = {},
                    onImportClick = {},
                )
            }
        }

        // The skipped count is shown inline, and the copy action is reachable...
        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(
                    R.plurals.external_import_skipped_rows_count,
                    1,
                    1,
                ),
            )
            .assertIsDisplayed()
        composeRule
            .onNode(
                matcher = hasContentDescription(
                    context.getString(R.string.external_import_skipped_rows_copy),
                ) and hasClickAction(),
            )
            .assertIsDisplayed()
            .performClick()
        // ...but the raw warning message is never rendered in the sheet (only the JSON,
        // copied to the clipboard, carries it). Serialization itself is unit-tested.
        composeRule.onAllNodesWithText("RAW WARNING SHOULD NOT RENDER").assertCountEquals(0)
    }

    private fun sampleExternalImportPreviewForUi(
        warning: ExternalImportWarning = ExternalImportWarning(
            reason = ExternalImportWarningReason.UNSUPPORTED_CATEGORY,
            externalId = "row-7",
            rowIndex = 7,
            message = "Skipped unsupported row",
        ),
    ): ExternalImportPreview {
        return ExternalImportPreview(
            parseResult = ExternalImportParseResult(
                sourceApp = ExternalTrackerSourceApp.NOMTF,
                exportVersion = "1",
                exportedAt = "2026-06-14T00:00:00Z",
                medicationDoses = emptyList(),
                labResults = emptyList(),
                warnings = listOf(warning),
            ),
            sourceAppLabel = "NoMTF",
            medicationRowsToCreate = 2,
            medicationRowsToUpdate = 1,
            labRowsToCreate = 3,
            labRowsToUpdate = 4,
            importedMedicinesToCreate = emptyList(),
            importedMedicinesToReuse = emptyList(),
            warnings = listOf(warning),
        )
    }
}
