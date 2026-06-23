package com.mkx.hrttracker.ui.journal

import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private const val TodayComposerTextFieldTag = "today-composer-text-field"

/**
 * A note left mid-edit must survive process death and navigation the way the calibration notes
 * field does: the editor reopens with the in-progress draft instead of discarding it (the draft is
 * the user's unsaved work, so "Couldn't save / Try again" stays honest after a restart). But the
 * reopen is silent — it must not grab focus and raise the keyboard on restore (only a deliberate
 * tap does), so the user isn't dropped into an IME they never asked for.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NoteEditorRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openEditor_reopensWithDraftButUnfocused_afterStateRestoration() {
        val today = LocalDate.of(2026, 6, 16)
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                // Uses the default rememberNoteEditorController(), so the saveable controller is
                // exercised exactly as the real screens wire it.
                TodayComposer(
                    today = today,
                    note = Note(id = "june-16", date = today, text = "Saved text"),
                    onSave = {},
                )
            }
        }

        // Open the editor and type an unsaved draft over the saved text.
        composeRule.onNodeWithText("Saved text").performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextClearance()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Half-typed entry")

        // Emulate process death (the same saved-state path as navigate-away-and-back).
        restorationTester.emulateSavedInstanceStateRestore()

        // The editor reopens with the in-progress draft intact — not reverted to the saved text...
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertTextContains("Half-typed entry")
        // ...but it did not grab focus, so the keyboard stays down until the user taps the field.
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertIsNotFocused()
    }
}
