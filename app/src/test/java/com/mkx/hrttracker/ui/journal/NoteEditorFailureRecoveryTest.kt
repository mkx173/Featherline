package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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

private const val NoteTimelineTextFieldTagPrefix = "note-timeline-text-field-"

/**
 * Every note card in the timeline shares one [NoteEditorController] (single editor open at a time)
 * and one save-failure signal. A save can still be in flight when the user moves on to another
 * card, so a failed write must recover the failed card WITHOUT yanking the editor away from the one
 * the user is actively typing in: stealing it would close that card and its close-revert would
 * silently discard the in-progress draft. The failed card keeps its own draft held for retry.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NoteEditorFailureRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedSave_doesNotStealActiveEditor_orWipeItsDraft() {
        val today = LocalDate.of(2026, 6, 16)
        // Drives the save-failure signal directly so the failure can be timed AFTER the user has
        // opened the second editor — the real race the guard protects against.
        var token by mutableIntStateOf(0)
        val noteA = Note(id = "june-15", date = today.minusDays(1), text = "Note A")
        val noteB = Note(id = "june-14", date = today.minusDays(2), text = "Note B")

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                NotesTimeline(
                    notes = listOf(noteA, noteB),
                    today = today,
                    // A's write stays in flight; its failure is signalled via the token bump below.
                    onSave = { _, _ -> },
                    saveFailureToken = token,
                )
            }
        }

        val fieldA = "$NoteTimelineTextFieldTagPrefix${noteA.id}"
        val fieldB = "$NoteTimelineTextFieldTagPrefix${noteB.id}"

        // Edit and commit A: its save is now pending and A's editor closes.
        composeRule.onNodeWithTag(fieldA).performClick()
        composeRule.onNodeWithTag(fieldA).performTextClearance()
        composeRule.onNodeWithTag(fieldA).performTextInput("A edited")
        composeRule.onNodeWithTag(fieldA).performImeAction()

        // Open B and start typing before A's write returns.
        composeRule.onNodeWithTag(fieldB).performClick()
        composeRule.onNodeWithTag(fieldB).performTextClearance()
        composeRule.onNodeWithTag(fieldB).performTextInput("B in progress")

        // A's write fails. A must recover itself without stealing B's open editor.
        composeRule.runOnIdle { token++ }

        // B's draft is intact: the failed save did not reopen A over it and wipe B's draft.
        composeRule.onNodeWithTag(fieldB).assertTextContains("B in progress")
    }
}
