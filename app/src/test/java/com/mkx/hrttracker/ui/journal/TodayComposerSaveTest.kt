package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.components.LocalUnsavedChanges
import com.mkx.hrttracker.ui.components.UnsavedChangesDialog
import com.mkx.hrttracker.ui.components.UnsavedChangesState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A Today composer save while it is in flight, when it fails, and when the screen leaves and comes
 * back, on the real JournalScreen and JournalViewModel. The save latch holds the text the save
 * sent until that text lands or the save fails; once the failed draft is back in your hands, Cancel
 * clears it and the composer moves on at midnight (review M7). Coming back to the screen is not the
 * save landing, and an untouched editor takes a note edited while it was away. These are the paths
 * TLC found in .claude/skills/verify-journal-composer/model/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class TodayComposerSaveTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val restorationTester = StateRestorationTester(composeRule)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val june16 = LocalDate.of(2026, 6, 16)
    private val june17 = LocalDate.of(2026, 6, 17)
    private val clock = FakeAppTimeSource(LocalDateTime.of(2026, 6, 16, 21, 0), tokyo)
    // The database. A var, so a test can give a new ViewModel (process death) one the old never saw.
    private var notes = MutableStateFlow<List<Note>>(emptyList())
    private val composerField = "today-composer-text-field"
    private val saves = mutableListOf<String>()

    // One result per save, in the order sent: each stays in flight until the test lands or fails it.
    private val writeResults = mutableListOf<CompletableDeferred<Unit>>()

    // When set, a save commits and settles at once, before the next frame, and the note's flow
    // emits it only when the test calls putNote, as Room's invalidation does.
    private var savesSettleAtOnce = false

    // False while the Journal is off screen (you opened All Notes): its saved state is kept, as the
    // NavHost keeps a back stack entry's.
    private var journalShown by mutableStateOf(true)

    private val repository: JournalRepository = mockk {
        every { getCachedTrackedDates() } returns null
        every { getCachedPinnedTrackedDates() } returns null
        every { getCachedNotes() } returns null
        every { observeTrackedDates() } returns flowOf(emptyList())
        every { observePinnedTrackedDates() } returns flowOf(emptyList())
        every { observeNotesCountBefore(any()) } returns flowOf(0)
        every { observeNotesOnOrAfter(any()) } answers {
            val start = firstArg<LocalDate>()
            notes.map { all -> all.filter { !it.date.isBefore(start) } }
        }
        every { observeNoteForDate(any()) } answers {
            val date = firstArg<LocalDate>()
            notes.map { all -> all.firstOrNull { it.date == date } }
        }
        coEvery { saveNoteForDate(any(), any()) } coAnswers {
            val date = firstArg<LocalDate>()
            val text = secondArg<String>()
            saves += "save $date: $text"
            if (savesSettleAtOnce) return@coAnswers
            CompletableDeferred<Unit>().also { writeResults += it }.await()
            putNote(date, text)
        }
    }

    // MockK keeps every mock's stub for the rest of the run, and these answers capture this test, so
    // without this each test's activity (a tall window) stays reachable until the test JVM exits.
    @After
    fun releaseMocks() = clearAllMocks()

    private fun putNote(date: LocalDate, text: String) = notes.update { all ->
        val id = all.firstOrNull { it.date == date }?.id ?: "note-$date"
        all.filter { it.date != date } + Note(id = id, date = date, text = text)
    }

    // Read on each composition, so a test can stand a new one in for process death.
    private var viewModel = JournalViewModel(repository, clock)

    private val unsavedChanges = UnsavedChangesState()

    private fun setContent() {
        restorationTester.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalUnsavedChanges provides unsavedChanges) {
                    val stateHolder = rememberSaveableStateHolder()
                    if (journalShown) {
                        stateHolder.SaveableStateProvider("journal") {
                            JournalScreen(onOpenMilestones = {}, onAddDate = {}, onOpenAllNotes = {}, viewModel = viewModel)
                        }
                    }
                    UnsavedChangesDialog(hazeState = null)
                }
            }
        }
    }

    // Each step runs once the app is idle, so a save completed in one has landed by the next.
    private fun navigateAwayAndBack(vararg whileAway: () -> Unit) {
        composeRule.runOnIdle { journalShown = false }
        whileAway.forEach { composeRule.runOnIdle(it) }
        composeRule.runOnIdle { journalShown = true }
    }

    private fun failSave(index: Int = 0) =
        composeRule.runOnIdle { writeResults[index].completeExceptionally(IOException("disk full")) }

    private fun landSave(index: Int = 0) = composeRule.runOnIdle { writeResults[index].complete(Unit) }

    private fun assertNoEditorOpen() =
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertDoesNotExist()

    private fun tapCancel() = composeRule.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()

    // Cancel on a changed draft asks first (J1).
    private fun cancelAndDiscard() {
        tapCancel()
        composeRule.onNodeWithText(context.getString(R.string.unsaved_changes_discard)).performClick()
    }

    private fun dateLabel(date: LocalDate): String =
        medicationGroupScheduleDateFormatter(context.resources.configuration.locales[0], date)(date)

    private fun assertComposerOpenWith(text: String) {
        composeRule.onNodeWithTag(composerField).assertTextEquals(text)
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsDisplayed()
    }

    private fun startNewNoteAndSave(text: String) {
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).performClick()
        composeRule.onNodeWithTag(composerField).performTextInput(text)
        composeRule.onNodeWithTag(composerField).performImeAction()
    }

    private fun editTodaysNoteAndSave(text: String) {
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()
        composeRule.onNodeWithTag(composerField).performTextInput(text)
        composeRule.onNodeWithTag(composerField).performImeAction()
    }

    /** NoStuckSaveLatch and ClosedComposerShowsItsNote: the save fails while the composer is reopened. */
    @Test
    fun aSaveThatFailsWhileTheComposerIsReopened_isReleased_soCancelClearsItsDraft() {
        setContent()
        startNewNoteAndSave("x")
        // You tap the held field, and the save fails while it is open: the draft stays editable.
        composeRule.onNodeWithTag(composerField).performClick()
        failSave()
        assertComposerOpenWith("x")

        cancelAndDiscard()

        composeRule.onNodeWithText("x").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertIsDisplayed()
        // Nothing holds the composer, so at midnight it moves on to the new day.
        composeRule.runOnIdle { clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 17, 0, 0), tokyo) }
        composeRule.onNodeWithText(dateLabel(june17)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(emptyList<Note>(), notes.value) }
    }

    /** The review's M7 path: the save fails while a timeline note is open. */
    @Test
    fun aSaveThatFailsWhileATimelineNoteIsOpen_holdsItsDraft_untilYouReopenIt() {
        val yesterday = Note(id = "june-15", date = june16.minusDays(1), text = "Yesterday")
        notes.value = listOf(yesterday)
        setContent()
        startNewNoteAndSave("x")
        // You open yesterday's note in the timeline, and the save fails: the composer keeps its draft.
        composeRule.onNodeWithTag("note-timeline-text-field-${yesterday.id}").performClick()
        failSave()
        // You close yesterday's note: the failed draft still waits in the composer.
        tapCancel()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")

        // You tap it, and it opens for a retry. Cancel then clears it.
        composeRule.onNodeWithTag(composerField).performClick()
        assertComposerOpenWith("x")
        cancelAndDiscard()

        composeRule.onNodeWithText("x").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertIsDisplayed()
    }

    /** FailedSaveKeepsItsText: an edit of today's note, reopened and cancelled while its save is in flight. */
    @Test
    fun aCancelWhileTheSaveIsInFlight_keepsTheEdit_andAFailureReopensIt() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        editTodaysNoteAndSave("Morning, and evening")
        // You tap the held field, then Cancel: the edit stays, held for its save.
        composeRule.onNodeWithTag(composerField).performClick()
        tapCancel()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")

        failSave()

        assertComposerOpenWith("Morning, and evening")
        composeRule.runOnIdle { assertEquals("Morning", notes.value.single().text) }
    }

    /** The same path on a new note, which the review's fix (clear the latch on a tap) would discard. */
    @Test
    fun aNewNoteReopenedAndCancelledWhileItsSaveIsInFlight_isReopenedWhenTheSaveFails() {
        setContent()
        startNewNoteAndSave("x")
        composeRule.onNodeWithTag(composerField).performClick()
        tapCancel()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")

        failSave()

        assertComposerOpenWith("x")
    }

    /** Cancel over a save in flight goes back to the text it sent, not to what you typed after. */
    @Test
    fun aCancelAfterTypingOverASaveInFlight_showsTheSentText_andAFailureReopensIt() {
        setContent()
        startNewNoteAndSave("AB")
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextInput("C")

        cancelAndDiscard()

        composeRule.onNodeWithTag(composerField).assertTextEquals("AB")
        failSave()
        assertComposerOpenWith("AB")
    }

    /** FailedDraftWaits: coming back to the screen is not the save landing. */
    @Test
    fun aFailedDraftHeldBehindATimelineNote_survivesNavigatingAwayAndBack_andMidnight() {
        val yesterday = Note(id = "june-15", date = june16.minusDays(1), text = "Yesterday")
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"), yesterday)
        setContent()
        editTodaysNoteAndSave("Morning, and evening")
        composeRule.onNodeWithTag("note-timeline-text-field-${yesterday.id}").performClick()
        failSave()
        tapCancel()

        // You open See all notes and go back, and midnight passes.
        navigateAwayAndBack()
        composeRule.runOnIdle { clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 17, 0, 0), tokyo) }

        // The failed draft is still held, on its own day, and a tap reopens it.
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")
        composeRule.onNodeWithText(dateLabel(june16)).assertIsDisplayed()
        composeRule.onNodeWithTag(composerField).performClick()
        assertComposerOpenWith("Morning, and evening")
    }

    /** A save still in flight across navigation stays held, and its failure reopens the draft. */
    @Test
    fun aSaveInFlight_survivesNavigatingAwayAndBack_andItsFailureReopensTheDraft() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        editTodaysNoteAndSave("Morning, and evening")

        navigateAwayAndBack()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")
        assertNoEditorOpen()
        failSave()

        assertComposerOpenWith("Morning, and evening")
    }

    /** The same across a recreation: the save is still in flight, and its failure reopens the draft. */
    @Test
    fun aSaveInFlight_survivesARecreation_andItsFailureReopensTheDraft() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        editTodaysNoteAndSave("Morning, and evening")

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")
        assertNoEditorOpen()
        failSave()

        assertComposerOpenWith("Morning, and evening")
    }

    /** After process death the save is gone with the old ViewModel: its draft reopens for a retry. */
    @Test
    fun aSaveInFlightWhenTheProcessDies_reopensItsDraftOnTheNewViewModel() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        editTodaysNoteAndSave("Morning, and evening")

        viewModel = JournalViewModel(repository, clock)
        restorationTester.emulateSavedInstanceStateRestore()

        assertComposerOpenWith("Morning, and evening")
        composeRule.runOnIdle { assertEquals("Morning", notes.value.single().text) }
    }

    /**
     * A save that committed just before the process died, before the note's flow emitted it: the
     * new ViewModel doesn't know its id, so it counts as failed, but the note shows its text, so
     * nothing is lost and no editor reopens by itself.
     */
    @Test
    fun aSaveThatCommittedJustBeforeTheProcessDied_isLetGo_asTheNoteShowsIt() {
        savesSettleAtOnce = true
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        editTodaysNoteAndSave("Morning, and evening")

        notes = MutableStateFlow(listOf(Note(id = "june-16", date = june16, text = "Morning, and evening")))
        viewModel = JournalViewModel(repository, clock)
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")
        assertNoEditorOpen()
    }

    /** NoStuckSaveLatch: a save lands while the screen is away, then All Notes edits the note. */
    @Test
    fun aSaveThatLandsWhileAway_thenAnEditInAllNotes_releasesTheComposer() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "A"))
        setContent()
        editTodaysNoteAndSave("AB")

        // You open See all notes; the save lands, and you edit today's note there to "C".
        navigateAwayAndBack({ writeResults[0].complete(Unit) }, { putNote(june16, "C") })

        // The composer shows "C", is not held, and moves on at midnight.
        composeRule.onNodeWithTag(composerField).assertTextEquals("C")
        assertNoEditorOpen()
        composeRule.runOnIdle { clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 17, 0, 0), tokyo) }
        composeRule.onNodeWithText(dateLabel(june17)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: AB"), saves) }
    }

    /** The same with the note deleted in All Notes: the composer is back to the prompt. */
    @Test
    fun aSaveThatLandsWhileAway_thenADeleteInAllNotes_releasesTheComposer() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "A"))
        setContent()
        editTodaysNoteAndSave("AB")

        navigateAwayAndBack({ writeResults[0].complete(Unit) }, { notes.value = emptyList() })

        composeRule.onNodeWithText("AB").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertIsDisplayed()
        assertNoEditorOpen()
    }

    /** A new note's save that settles before the next frame still holds its text until the note shows it. */
    @Test
    fun aNewNoteSaveThatSettlesBeforeTheNextFrame_holdsItsTextUntilTheNoteShowsIt() {
        savesSettleAtOnce = true
        setContent()
        startNewNoteAndSave("x")

        // Settled, but the note's flow hasn't emitted it yet: no prompt blinks back.
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertDoesNotExist()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
        composeRule.runOnIdle { putNote(june16, "x") }
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
        assertNoEditorOpen()
    }

    /** The same for an edit: the card keeps the edit on screen, not the old text, until the note shows it. */
    @Test
    fun anEditThatSettlesBeforeTheNextFrame_holdsItsTextUntilTheNoteShowsIt() {
        savesSettleAtOnce = true
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        editTodaysNoteAndSave("Morning, and evening")

        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")
        composeRule.runOnIdle { putNote(june16, "Morning, and evening") }
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning, and evening")
        assertNoEditorOpen()
    }

    /**
     * The latch is the card's, not its note's. A new note's first save lands, changing the
     * composer's identity under a second save with more text, which stays held: its failure reopens
     * that text.
     */
    @Test
    fun aNewNotesSecondSave_staysHeld_whenItsFirstLandsUnderIt() {
        setContent()
        startNewNoteAndSave("x")
        // You reopen it while the save is in flight, add to it, and save again.
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()
        composeRule.onNodeWithTag(composerField).performTextInput("xy")
        composeRule.onNodeWithTag(composerField).performImeAction()

        // The first lands: the day has a note now, and "xy" is still held for the second.
        landSave(0)
        composeRule.onNodeWithTag(composerField).assertTextEquals("xy")
        failSave(1)
        assertComposerOpenWith("xy")
    }

    /**
     * The draft is the card's too. A new note's first save lands, the second (with more text) fails
     * and reopens, and only then does the first's note appear under the open editor: the editor
     * keeps the failed text.
     */
    @Test
    fun aFailedSecondSave_keepsItsTextOpen_whenTheFirstSavesNoteAppearsUnderIt() {
        setContent()
        // The first save commits at once; the note's flow will emit it later.
        savesSettleAtOnce = true
        startNewNoteAndSave("x")
        savesSettleAtOnce = false
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()
        composeRule.onNodeWithTag(composerField).performTextInput("xy")
        composeRule.onNodeWithTag(composerField).performImeAction()
        failSave(0)
        assertComposerOpenWith("xy")

        composeRule.runOnIdle { putNote(june16, "x") }

        assertComposerOpenWith("xy")
    }

    /** J4: two saves in flight. The first fails, you Cancel, and the second fails: the text reopens. */
    @Test
    fun twoSavesInFlight_theFirstFailsAndYouCancel_theSecondFailureReopensTheText() {
        setContent()
        startNewNoteAndSave("x")
        // You tap the held field and tap Save again: two saves in flight.
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performImeAction()
        composeRule.runOnIdle { assertEquals(2, writeResults.size) }
        // The first fails: the second is still in flight, so the composer stays held.
        failSave(0)
        assertNoEditorOpen()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
        // You tap it and Cancel: it is still held for the second save.
        composeRule.onNodeWithTag(composerField).performClick()
        tapCancel()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")

        failSave(1)

        assertComposerOpenWith("x")
    }

    /** SaveWritesWhatYouTyped: an open editor you haven't touched takes a note edited while away. */
    @Test
    fun anUntouchedEditor_takesTheNoteEditedWhileAway_soSaveWritesNothing() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "A"))
        setContent()
        composeRule.onNodeWithTag(composerField).performClick()

        // You open See all notes, edit today's note there, and go back.
        navigateAwayAndBack({ putNote(june16, "C from All Notes") })

        assertComposerOpenWith("C from All Notes")
        composeRule.onNodeWithTag(composerField).performImeAction()
        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), saves)
            assertEquals("C from All Notes", notes.value.single().text)
        }
    }

    /** NoPhantomEditor: a new note lands while its field is reopened. */
    @Test
    fun aNewNoteThatLandsWhileItsFieldIsReopened_keepsTheEditorOnTheNote() {
        setContent()
        startNewNoteAndSave("x")
        composeRule.onNodeWithTag(composerField).performClick()

        landSave()

        // The editor is open on the note it became, with the keyboard up.
        assertComposerOpenWith("x")
        composeRule.onNodeWithTag(composerField).assertIsFocused()
        tapCancel()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertDoesNotExist()
        composeRule.onNodeWithTag(composerField).assertIsNotFocused()
        // Nothing opens by itself on the next day.
        composeRule.runOnIdle { clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 17, 0, 0), tokyo) }
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertDoesNotExist()
    }
}
