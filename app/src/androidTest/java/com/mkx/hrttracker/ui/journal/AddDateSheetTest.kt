package com.mkx.hrttracker.ui.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

class AddDateSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_confirmReturnsNameIconDateAndPalette() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedDate = LocalDate.of(2026, 6, 16)
        var submitted: SubmittedDate? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheetContent(
                    title = context.getString(R.string.journal_date_sheet_add_title),
                    confirmButtonText = context.getString(R.string.save),
                    initialName = "",
                    initialIcon = AnchorIcon.EVENT,
                    initialDate = selectedDate,
                    initialPalette = null,
                    today = selectedDate,
                    onDismissRequest = {},
                    onConfirm = { name, icon, date, paletteKey ->
                        submitted = SubmittedDate(name, icon, date, paletteKey)
                    },
                )
            }
        }

        composeRule.onNodeWithTag(AddDateNameFieldTestTag)
            .performTextInput("First injection")
        composeRule.onNodeWithText(AnchorIcon.PILL.storageKey)
            .performClick()
        composeRule.onNodeWithText(MedicationGroupColorKey.ROSE.name)
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.save))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                SubmittedDate(
                    name = "First injection",
                    icon = AnchorIcon.PILL.storageKey,
                    date = selectedDate,
                    paletteKey = MedicationGroupColorKey.ROSE.name,
                ),
                submitted,
            )
        }
    }

    @Test
    fun content_prefillsEditValuesAndDeleteCallsCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedDate = LocalDate.of(2024, 4, 1)
        var deleted = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheetContent(
                    title = context.getString(R.string.journal_date_sheet_edit_title),
                    confirmButtonText = context.getString(R.string.save),
                    initialName = "On estradiol",
                    initialIcon = AnchorIcon.MEDICATION,
                    initialDate = selectedDate,
                    initialPalette = MedicationGroupColorKey.ROSE,
                    today = LocalDate.of(2026, 6, 16),
                    onDismissRequest = {},
                    onConfirm = { _, _, _, _ -> },
                    onDelete = { deleted = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_date_sheet_edit_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("On estradiol").assertIsDisplayed()
        composeRule.onNodeWithText(AnchorIcon.MEDICATION.storageKey).assertIsDisplayed()
        composeRule.onNodeWithText(MedicationGroupColorKey.ROSE.name).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.journal_delete_date))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(deleted)
        }
    }

    private data class SubmittedDate(
        val name: String,
        val icon: String,
        val date: LocalDate,
        val paletteKey: String?,
    )
}
