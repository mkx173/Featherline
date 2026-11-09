package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

private const val NoteTimelineTextFieldTagPrefix = "note-timeline-text-field-"

/**
 * Every note card in the timeline shares one [NoteEditorController] (single editor open at a time),
 * and follows its own saves in one [NoteSaves]. A save can still be in flight when the user moves on to another
 * card, so a failed write must recover the failed card WITHOUT yanking the editor away from the one
 * the user is actively typing in: stealing it would close that card and its close-revert would
 * silently discard the in-progress draft. The failed card keeps its own draft held for retry. Each
 * row's editor state also stays with its note when the rows around it change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NoteEditorFailureRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 6, 16)
    private val noteA = Note(id = "june-15", date = today.minusDays(1), text = "Note A")
    private val noteB = Note(id = "june-14", date = today.minusDays(2), text = "Note B")
    private val fieldA = "$NoteTimelineTextFieldTagPrefix${noteA.id}"
    private val fieldB = "$NoteTimelineTextFieldTagPrefix${noteB.id}"

    // Every save stays in flight until a test settles it, under the id it returned.
    private val noteSaves = NoteSaves()
    private val saveIds = mutableListOf<Long>()
    private val sendSave: (LocalDate, String) -> Long = { _, _ -> noteSaves.start().also { saveIds += it } }

    private fun settle(index: Int, landed: Boolean) = composeRule.runOnIdle { noteSaves.settle(saveIds[index], landed) }

    @Test
    fun failedSave_doesNotStealActiveEditor_orWipeItsDraft() {
        // Settles A's save directly so the failure can be timed AFTER the user has opened the
        // second editor — the real race the guard protects against.
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                NotesTimeline(
                    notes = listOf(noteA, noteB),
                    today = today,
                    onSave = sendSave,
                    noteSaves = noteSaves,
                )
            }
        }

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
        settle(0, landed = false)

        // B's draft is intact: the failed save did not reopen A over it and wipe B's draft.
        composeRule.onNodeWithTag(fieldB).assertTextContains("B in progress")
    }

    // A delete of the row above (sent before you opened this one) lands while you type: the rows
    // shift up, and your edit stays on its own note.
    @Test
    fun anEditOpenInTheTimeline_staysOnItsNote_whenTheRowAboveIsRemoved() {
        var notes by mutableStateOf(listOf(noteA, noteB))
        val saves = mutableListOf<String>()
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                NotesTimeline(
                    notes = notes,
                    today = today,
                    onSave = { date, text -> saves += "$date: $text"; noteSaves.start() },
                    noteSaves = noteSaves,
                )
            }
        }
        composeRule.onNodeWithTag(fieldB).performClick()
        composeRule.onNodeWithTag(fieldB).performTextClearance()
        composeRule.onNodeWithTag(fieldB).performTextInput("B edited")

        composeRule.runOnIdle { notes = listOf(noteB) }

        composeRule.onNodeWithTag(fieldB).assertIsFocused()
        composeRule.onNodeWithTag(fieldB).assertTextEquals("B edited")
        composeRule.onNodeWithTag(fieldB).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("${noteB.date}: B edited"), saves) }
    }

    // In All Notes, a failure that arrives in selection mode keeps the row's draft held instead of
    // opening its editor, so no row is both editing and selectable. A tap after the selection ends
    // reopens the draft, not the saved text.
    @Test
    fun allNotes_aFailureInSelectionMode_keepsTheDraftHeld_untilATapReopensIt() {
        var selected by mutableStateOf(emptySet<LocalDate>())
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        today = today,
                        monthGroups = listOf(MonthGroupUiState(YearMonth.from(today), listOf(noteA, noteB))),
                        selectedDates = selected,
                    ),
                    onNavigateBack = {},
                    onSaveNote = sendSave,
                    noteSaves = noteSaves,
                )
            }
        }
        // The row merges its text into its own semantics while it is clickable.
        val rowA = { composeRule.onNodeWithTag(fieldA, useUnmergedTree = true) }
        rowA().performClick()
        rowA().performTextClearance()
        rowA().performTextInput("A edited")
        rowA().performImeAction()

        // You start selecting, and A's write fails.
        composeRule.runOnIdle { selected = setOf(noteB.date) }
        settle(0, landed = false)

        val save = context.getString(R.string.save)
        composeRule.onNodeWithContentDescription(save).assertDoesNotExist()
        rowA().assertTextEquals("A edited")

        // The selection ends, and you tap A.
        composeRule.runOnIdle { selected = emptySet() }
        rowA().performClick()
        composeRule.onNodeWithContentDescription(save).assertIsDisplayed()
        rowA().assertTextEquals("A edited")
    }

    // A failure reaches only the card whose save failed. Both cards' saves are in flight, B's fails:
    // B reopens for a retry, and A stays held for its own save, which then lands.
    @Test
    fun aFailure_reachesOnlyTheCardWhoseSaveFailed() {
        var notes by mutableStateOf(listOf(noteA, noteB))
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                NotesTimeline(notes = notes, today = today, onSave = sendSave, noteSaves = noteSaves)
            }
        }
        for ((field, text) in listOf(fieldA to "A edited", fieldB to "B edited")) {
            composeRule.onNodeWithTag(field).performClick()
            composeRule.onNodeWithTag(field).performTextClearance()
            composeRule.onNodeWithTag(field).performTextInput(text)
            composeRule.onNodeWithTag(field).performImeAction()
        }

        settle(1, landed = false)

        composeRule.onNodeWithTag(fieldB).assertIsFocused()
        composeRule.onNodeWithTag(fieldB).assertTextEquals("B edited")
        composeRule.onNodeWithTag(fieldA).assertTextEquals("A edited")
        composeRule.onNodeWithTag(fieldA).assertIsNotFocused()
        // A's save lands: A shows the saved note, and B's open retry is untouched.
        composeRule.runOnIdle { notes = listOf(noteA.copy(text = "A edited"), noteB) }
        settle(0, landed = true)
        composeRule.onNodeWithTag(fieldA).assertTextEquals("A edited")
        composeRule.onNodeWithTag(fieldB).assertTextEquals("B edited")
        composeRule.onNodeWithTag(fieldB).assertIsFocused()
    }
}
