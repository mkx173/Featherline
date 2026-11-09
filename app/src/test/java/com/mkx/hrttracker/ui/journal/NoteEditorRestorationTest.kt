package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
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

    // Lives outside the content, as the ViewModel that owns it does.
    private val noteSaves = NoteSaves()

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
                    onSave = { _, _ -> noteSaves.start() },
                    noteSaves = noteSaves,
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

    /**
     * Committing an edit hands the write to the repository and closes the editor, but the result
     * lands a frame or more later. If the card is disposed in that window — the user navigates away
     * (or the process dies) before the write returns — the in-flight draft is still the user's
     * unsaved work and must survive the round-trip. Regression guard: the "save pending" latch used
     * to live in plain [remember], so it was dropped on disposal and the reopened card reverted the
     * draft to the stale saved text, silently destroying the edit the user was retrying. It later
     * survived, but the recreated card took the unchanged saved text for the save landing and
     * released it, so the save's failure no longer reopened the draft.
     */
    @Test
    fun pendingSaveDisposedBeforeResult_keepsDraftNotSavedText() {
        val today = LocalDate.of(2026, 6, 16)
        var saveId = 0L
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                TodayComposer(
                    today = today,
                    note = Note(id = "june-16", date = today, text = "Saved text"),
                    // The write is in flight: it has not fed the committed text back this frame.
                    onSave = { _, _ -> noteSaves.start().also { saveId = it } },
                    noteSaves = noteSaves,
                )
            }
        }

        // Open the editor, replace the saved text with an unsaved draft, and commit it (Done).
        composeRule.onNodeWithText("Saved text").performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextClearance()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Half-typed entry")
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()

        // The card is disposed and recreated while the save is still pending (navigate away / death).
        restorationTester.emulateSavedInstanceStateRestore()

        // The pending draft must survive — not be reverted to the saved text as if the save resolved.
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertTextContains("Half-typed entry")
        // ...and the latch with it: the editor stays closed while the save is in flight, and when
        // the save then fails, the draft reopens for a retry.
        val save = ApplicationProvider.getApplicationContext<Context>().getString(R.string.save)
        composeRule.onNodeWithContentDescription(save).assertDoesNotExist()
        composeRule.runOnIdle { noteSaves.settle(saveId, landed = false) }
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertTextContains("Half-typed entry")
        composeRule.onNodeWithContentDescription(save).assertIsDisplayed()
    }
}
