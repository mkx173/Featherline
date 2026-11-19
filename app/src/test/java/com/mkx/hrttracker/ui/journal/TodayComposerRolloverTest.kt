package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
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
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

private const val TodayComposerTextFieldTag = "today-composer-text-field"

/**
 * The Today composer across a local-date rollover (midnight or a time-zone change), wired to the
 * real [JournalViewModel]. While a session is open (an editor, new or existing, changed or not,
 * empty or not; a save in flight; a failed draft; a delete confirmation), the composer stays on the
 * day the session began: the header names that date instead of Today, and a save or delete writes
 * it, never the day the clock has moved on to (H3, H4). Once the session ends, the composer moves on
 * to today, and the old day's note joins the timeline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class TodayComposerRolloverTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val june14 = LocalDate.of(2026, 6, 14)
    private val june15 = LocalDate.of(2026, 6, 15)
    private val june16 = LocalDate.of(2026, 6, 16)
    private val june17 = LocalDate.of(2026, 6, 17)
    private val clock = FakeAppTimeSource(LocalDateTime.of(2026, 6, 16, 23, 59), tokyo)
    private val notes = MutableStateFlow<List<Note>>(emptyList())

    // A day whose note query has not emitted yet: the clock has rolled over to it, but the screen
    // still shows the previous day (the window in which H4 misfiles a write).
    private var unloadedDay: LocalDate? = null
    private val writes = mutableListOf<String>()

    // When set, a save waits on it before it lands (a write still in flight).
    private var saveGate: CompletableDeferred<Unit>? = null

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
            if (date == unloadedDay) {
                emptyFlow()
            } else {
                notes.map { all -> all.firstOrNull { it.date == date } }
            }
        }
        coEvery { saveNoteForDate(any(), any()) } coAnswers {
            saveGate?.await()
            val date = firstArg<LocalDate>()
            val text = secondArg<String>()
            writes += "save $date: $text"
            notes.update { all ->
                val id = all.firstOrNull { it.date == date }?.id ?: "note-$date"
                all.filter { it.date != date } + Note(id = id, date = date, text = text)
            }
        }
        coEvery { deleteNoteForDate(any()) } answers {
            val date = firstArg<LocalDate>()
            writes += "delete $date"
            notes.update { all -> all.filter { it.date != date } }
        }
    }

    // MockK keeps every mock's stub for the rest of the run, and these answers capture this test, so
    // without this each test's activity (a tall window) stays reachable until the test JVM exits.
    @After
    fun releaseMocks() = clearAllMocks()

    private fun setContent() {
        val viewModel = JournalViewModel(repository, clock)
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreen(
                    onOpenMilestones = {},
                    onAddDate = {},
                    onOpenAllNotes = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun startNewNote(text: String) {
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput(text)
    }

    private fun rollClockTo(minute: LocalDateTime, zone: ZoneId = tokyo) {
        composeRule.runOnIdle { clock.setCurrentSnapshot(minute, zone) }
    }

    private fun tapCancel() =
        composeRule.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()

    // The composer header's date, in the app's own format (e.g. "Jun 17 Wed").
    private fun dateLabel(date: LocalDate): String =
        medicationGroupScheduleDateFormatter(context.resources.configuration.locales[0], date)(date)

    // The composer has moved on to [day], which has no note yet: the header dates it as Today and
    // the "write about" prompt is back in place of an open editor.
    private fun assertComposerShowsEmptyToday(day: LocalDate) {
        composeRule.onNodeWithText(dateLabel(day)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_today)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertIsDisplayed()
    }

    // The composer's editor is still open on [day], a day that is no longer today: the header names
    // that date, not Today.
    private fun assertEditorOpenOn(day: LocalDate) {
        composeRule.onNodeWithText(dateLabel(day)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_today)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsDisplayed()
    }

    @Test
    fun emptyNewNoteEditor_staysOpenAcrossMidnight_onItsDay_andSavesThere() {
        setContent()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        assertEditorOpenOn(june16)
        // It keeps focus, so the keyboard stays up, and what you then write is June 16's note.
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertIsFocused()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Just before midnight")
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Just before midnight"), writes) }
        // The session is over: the composer moves on, and June 16's note joins the timeline.
        assertComposerShowsEmptyToday(june17)
        composeRule.onNodeWithText("Just before midnight").assertIsDisplayed()
    }

    @Test
    fun newNoteDraft_openAcrossMidnight_staysOnItsDay_andSavesToIt() {
        setContent()
        startNewNote("Late entry")

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        assertEditorOpenOn(june16)
        composeRule.onNodeWithText("Late entry").assertExists()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Late entry"), writes) }
        assertComposerShowsEmptyToday(june17)
        composeRule.onNodeWithText("Late entry").assertIsDisplayed()
    }

    @Test
    fun unchangedExistingNoteEditor_staysOpenOnItsDay_andClosingItMovesTheComposerOn() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        composeRule.onNodeWithText("Morning").performClick()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        assertEditorOpenOn(june16)
        tapCancel()
        assertComposerShowsEmptyToday(june17)
        // June 16's note is a closed timeline row now.
        composeRule.onNodeWithText("Morning").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), writes) }
    }

    @Test
    fun emptyNewNoteEditor_restoredAfterProcessDeathOnTheNextDay_staysOnItsOldDay_untilClosed() {
        var viewModel = JournalViewModel(repository, clock)
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreen(
                    onOpenMilestones = {},
                    onAddDate = {},
                    onOpenAllNotes = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()

        // The process dies in the background and comes back the next morning: a fresh ViewModel
        // on the new day, with the open editor and its day restored from saved state.
        viewModel = JournalViewModel(
            repository,
            FakeAppTimeSource(LocalDateTime.of(2026, 6, 17, 8, 0), tokyo),
        )
        restorationTester.emulateSavedInstanceStateRestore()

        // Restored silently, as any open editor is: open on its own day, keyboard down.
        assertEditorOpenOn(june16)
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertIsNotFocused()
        tapCancel()
        assertComposerShowsEmptyToday(june17)
    }

    // An open delete confirmation holds its day: confirming it deletes the note it was asked about.
    @Test
    fun deleteConfirmationOpenAtMidnight_holdsItsDay_andDeletesThatNote() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        composeRule.onNodeWithText("Morning").performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_delete_note))
            .performClick()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithText(context.getString(R.string.delete_entries_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(listOf("delete $june16"), writes) }
        assertComposerShowsEmptyToday(june17)
    }

    // A save sent before midnight carries its day; once it lands, the composer moves on.
    @Test
    fun newNoteSaveInFlightAtMidnight_landsOnItsOwnDay_andTheComposerMovesOn() {
        val gate = CompletableDeferred<Unit>()
        saveGate = gate
        setContent()
        startNewNote("Late entry")
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))
        composeRule.runOnIdle { gate.complete(Unit) }

        composeRule.runOnIdle { assertEquals(listOf("save $june16: Late entry"), writes) }
        assertComposerShowsEmptyToday(june17)
        composeRule.onNodeWithText(dateLabel(june16)).assertIsDisplayed()
    }

    @Test
    fun existingNoteEdit_survivesMidnight_showsItsDate_andSavesToItsOwnDay() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        composeRule.onNodeWithText("Morning").performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextClearance()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Morning, and evening")

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithText("Morning, and evening").assertExists()
        assertEditorOpenOn(june16)
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Morning, and evening"), writes) }
    }

    @Test
    fun saveInFlightAtMidnight_thatFails_reopensItsDraft_onItsDay() {
        val writeResult = CompletableDeferred<Unit>()
        coEvery { repository.saveNoteForDate(any(), any()) } coAnswers { writeResult.await() }
        setContent()
        startNewNote("Late entry")
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))
        composeRule.runOnIdle { writeResult.completeExceptionally(IOException("disk full")) }

        composeRule.onNodeWithText("Late entry").assertExists()
        assertEditorOpenOn(june16)
    }

    @Test
    fun saveBeforeTheScreenCatchesUpWithAZoneChange_writesTheShownDay_notTheNewDaysNote() {
        notes.value = listOf(Note(id = "june-15", date = june15, text = "Yesterday's note"))
        clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 16, 0, 30), tokyo)
        setContent()
        startNewNote("Landed")

        // Flying east to west: the local date steps back to June 15, which already has a note.
        unloadedDay = june15
        rollClockTo(LocalDateTime.of(2026, 6, 15, 5, 30), ZoneId.of("Pacific/Honolulu"))

        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Landed"), writes) }
    }

    @Test
    fun deleteConfirmedBeforeTheScreenCatchesUpWithMidnight_deletesTheShownNote() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        composeRule.onNodeWithText("Morning").performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_delete_note))
            .performClick()

        unloadedDay = june17
        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithText(context.getString(R.string.delete_entries_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(listOf("delete $june16"), writes) }
    }

    // Flying west moves today back to a day whose note is open in the timeline. The composer waits
    // for that edit to end before moving to the day, so the note stays in the timeline under its
    // open editor, and what you type there is saved to it.
    @Test
    fun timelineEditOfTheNewToday_staysOpenAcrossAWestwardZoneChange() {
        notes.value = listOf(Note(id = "june-15", date = june15, text = "Yesterday"))
        clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 16, 0, 30), tokyo)
        setContent()
        val june15Field = "note-timeline-text-field-june-15"
        composeRule.onNodeWithTag(june15Field).performClick()
        composeRule.onNodeWithTag(june15Field).performTextInput(" edited")

        rollClockTo(LocalDateTime.of(2026, 6, 15, 5, 30), ZoneId.of("Pacific/Honolulu"))

        composeRule.onNodeWithTag(june15Field).assertIsFocused()
        composeRule.onNodeWithTag(june15Field).assertTextEquals("Yesterday edited")
        composeRule.onNodeWithTag(june15Field).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june15: Yesterday edited"), writes) }
    }

    // The same edit saved after the zone change: the row holds its save in the timeline, and the
    // composer doesn't move onto its day, so a failure reopens the edit there.
    @Test
    fun timelineSaveOfTheNewToday_isHeldInTheTimeline_andAFailureReopensIt() {
        val writeResult = CompletableDeferred<Unit>()
        coEvery { repository.saveNoteForDate(any(), any()) } coAnswers { writeResult.await() }
        notes.value = listOf(Note(id = "june-15", date = june15, text = "Yesterday"))
        clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 16, 0, 30), tokyo)
        setContent()
        val june15Field = "note-timeline-text-field-june-15"
        composeRule.onNodeWithTag(june15Field).performClick()
        composeRule.onNodeWithTag(june15Field).performTextInput(" edited")
        rollClockTo(LocalDateTime.of(2026, 6, 15, 5, 30), ZoneId.of("Pacific/Honolulu"))

        composeRule.onNodeWithTag(june15Field).performImeAction()
        composeRule.onNodeWithTag(june15Field).assertTextEquals("Yesterday edited")
        composeRule.runOnIdle { writeResult.completeExceptionally(IOException("disk full")) }

        composeRule.onNodeWithTag(june15Field).assertTextEquals("Yesterday edited")
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsDisplayed()
    }

    // Today's note joins the top of the timeline at midnight, shifting every row down: an edit open
    // in a row below stays on its own note, so what you type is saved to it.
    @Test
    fun timelineEditOpenAtMidnight_staysOnItsNote_whenTodaysNoteJoinsTheTimeline() {
        notes.value = listOf(
            Note(id = "june-16", date = june16, text = "Today"),
            Note(id = "june-15", date = june15, text = "Yesterday"),
            Note(id = "june-14", date = june14, text = "Two days ago"),
        )
        setContent()
        val june14Field = "note-timeline-text-field-june-14"
        composeRule.onNodeWithTag(june14Field).performClick()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithTag(june14Field).assertIsFocused()
        composeRule.onNodeWithTag(june14Field).performTextInput(" more")
        composeRule.onNodeWithTag(june14Field).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june14: Two days ago more"), writes) }
    }
}
