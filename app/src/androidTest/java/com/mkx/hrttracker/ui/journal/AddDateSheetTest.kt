package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
class AddDateSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun colorDescription(context: Context, key: MedicationGroupColorKey): String =
        context.getString(
            R.string.group_color_picker_swatch_content_description,
            MedicationGroupColorKey.assignmentOrder.indexOf(key) + 1,
        )

    @Test
    fun addConfirmReturnsNameIconDateAndPalette() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        var submitted: SubmittedDate? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheet(
                    today = today,
                    anchor = null,
                    initiallyPinned = true,
                    onDismissRequest = {},
                    onConfirm = { name, icon, date, paletteKey, pinned ->
                        submitted = SubmittedDate(name, icon, date, paletteKey, pinned)
                    },
                )
            }
        }
        composeRule.awaitSheetReady(context)

        composeRule.onNodeWithTag(AddDateNameFieldTestTag).performTextInput("First injection")
        // Typing focuses the field and raises the soft IME, which overlaps the bottom
        // Save row (the ModalBottomSheet does not IME-resize). Dismiss it before tapping.
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            context.getString(anchorIconLabelRes(AnchorIcon.PILL)),
        ).performClick()
        composeRule.onNodeWithContentDescription(
            colorDescription(context, MedicationGroupColorKey.ROSE),
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.runOnIdle {
            assertEquals(
                SubmittedDate(
                    name = "First injection",
                    icon = AnchorIcon.PILL.storageKey,
                    date = today,
                    paletteKey = MedicationGroupColorKey.ROSE.name,
                    pinned = true,
                ),
                submitted,
            )
        }
    }

    @Test
    fun pinToggleStartsFromInitialState_andReturnsChosenValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        var submitted: SubmittedDate? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheet(
                    today = today,
                    anchor = null,
                    initiallyPinned = true,
                    onDismissRequest = {},
                    onConfirm = { name, icon, date, paletteKey, pinned ->
                        submitted = SubmittedDate(name, icon, date, paletteKey, pinned)
                    },
                )
            }
        }
        composeRule.awaitSheetReady(context)

        composeRule.onNodeWithTag(AddDateNameFieldTestTag).performTextInput("Anniversary")
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        // Defaulted on (initiallyPinned = true); tapping the row flips it off.
        composeRule.onNodeWithText(context.getString(R.string.journal_pin_date)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.runOnIdle {
            assertEquals(false, submitted?.pinned)
        }
    }

    @Test
    fun saveDisabledUntilNameEntered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheet(
                    today = LocalDate.of(2026, 6, 16),
                    anchor = null,
                    initiallyPinned = false,
                    onDismissRequest = {},
                    onConfirm = { _, _, _, _, _ -> },
                )
            }
        }
        composeRule.awaitSheetReady(context)

        composeRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()
        composeRule.onNodeWithTag(AddDateNameFieldTestTag).performTextInput("On estradiol")
        composeRule.onNodeWithText(context.getString(R.string.save)).assertIsEnabled()
    }

    @Test
    fun editPrefillsValuesAndDeleteRequiresConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var deleted = false
        val anchor = AnchorRowUiState(
            id = "a1",
            name = "On estradiol",
            icon = AnchorIcon.MEDICATION,
            palette = MedicationGroupColorKey.ROSE,
            date = LocalDate.of(2024, 4, 1),
            dayMagnitude = 100,
            isFuture = false,
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheet(
                    today = LocalDate.of(2026, 6, 16),
                    anchor = anchor,
                    initiallyPinned = true,
                    onDismissRequest = {},
                    onConfirm = { _, _, _, _, _ -> },
                    onDelete = { deleted = true },
                )
            }
        }
        composeRule.awaitSheetReady(context)

        composeRule.onNodeWithText(context.getString(R.string.journal_date_sheet_edit_title))
            .assertIsDisplayed()
        // The name appears in both the preview hero and the editable field; assert the
        // field is prefilled to disambiguate the two matching nodes.
        composeRule.onNodeWithTag(AddDateNameFieldTestTag).assert(hasText("On estradiol"))
        // Prefilled icon + palette show as the selected variants.
        composeRule.onNodeWithContentDescription(
            context.getString(anchorIconLabelRes(AnchorIcon.MEDICATION)),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.group_color_picker_swatch_selected_content_description,
                MedicationGroupColorKey.assignmentOrder.indexOf(MedicationGroupColorKey.ROSE) + 1,
            ),
        ).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.journal_delete_date)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_delete_date_title))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!deleted) }
        composeRule.onNodeWithTag(AddDateDeleteConfirmButtonTestTag).performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

    private data class SubmittedDate(
        val name: String,
        val icon: String,
        val date: LocalDate,
        val paletteKey: String?,
        val pinned: Boolean,
    )
}

// AddDateSheet's content lives in a ModalBottomSheet's separate window that attaches
// asynchronously, outside the Compose test clock. Poll until the always-present Save
// button is displayed before interacting, mirroring AdjustStockSheetTest.
private fun ComposeContentTestRule.awaitSheetReady(context: Context) {
    val save = context.getString(R.string.save)
    waitUntil(timeoutMillis = 5_000) {
        try {
            onNodeWithText(save).assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}
