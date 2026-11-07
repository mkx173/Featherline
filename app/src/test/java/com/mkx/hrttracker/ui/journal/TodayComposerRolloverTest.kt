package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
 * real [JournalViewModel]. An edit session begun on one day belongs to that day: its draft, open
 * editor and in-flight save survive the rollover (H3), and its save or delete writes the day the
 * composer shows, never the day the clock has moved on to (H4). A held session is dated, not
 * labelled Today; an open editor with nothing unsaved is not held, and the composer moves on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class TodayComposerRolloverTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val june15 = LocalDate.of(2026, 6, 15)
    private val june16 = LocalDate.of(2026, 6, 16)
    private val june17 = LocalDate.of(2026, 6, 17)
    private val clock = FakeAppTimeSource(LocalDateTime.of(2026, 6, 16, 23, 59), tokyo)
    private val notes = MutableStateFlow<List<Note>>(emptyList())

    // A day whose note query has not emitted yet: the clock has rolled over to it, but the screen
    // still shows the previous day (the window in which H4 misfiles a write).
    private var unloadedDay: LocalDate? = null
    private val writes = mutableListOf<String>()

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
        coEvery { saveNoteForDate(any(), any()) } answers {
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

    @Test
    fun unchangedNewNoteEditor_isReleasedAtMidnight_andShowsTheNewDay() {
        setContent()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        assertComposerShowsEmptyToday(june17)
    }

    @Test
    fun unchangedNewNoteEditor_restoredAfterProcessDeathOnTheNextDay_isReleased() {
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

        assertComposerShowsEmptyToday(june17)
    }

    @Test
    fun unchangedExistingNoteEditor_isReleasedAtMidnight_andTheNoteMovesToTheTimeline() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        composeRule.onNodeWithText("Morning").performClick()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        assertComposerShowsEmptyToday(june17)
        // June 16's note is a closed timeline row now, not an editor still open on it.
        composeRule.onNodeWithText("Morning").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), writes) }
    }

    @Test
    fun heldDraft_showsItsDateInsteadOfToday_andStillSavesToThatDay() {
        setContent()
        startNewNote("Late entry")

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithText(context.getString(R.string.journal_today)).assertDoesNotExist()
        composeRule.onNodeWithText(dateLabel(june16)).assertIsDisplayed()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Late entry"), writes) }
    }

    // An unchanged draft is released at a rollover, but an open delete confirmation still holds
    // its day: confirming it deletes the note it was asked about.
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
    }

    @Test
    fun newNoteDraft_survivesMidnight_savesToItsOwnDay_andTheNewDayStartsEmpty() {
        setContent()
        startNewNote("Late entry")

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithText("Late entry").assertExists()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Late entry"), writes) }
        // Once the session ends the composer moves on to the new day, empty.
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertIsDisplayed()
    }

    @Test
    fun existingNoteEdit_survivesMidnight_andSavesToItsOwnDay() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setContent()
        composeRule.onNodeWithText("Morning").performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextClearance()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Morning, and evening")

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithText("Morning, and evening").assertExists()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Morning, and evening"), writes) }
    }

    @Test
    fun saveInFlightAtMidnight_thatFails_reopensItsDraft() {
        val writeResult = CompletableDeferred<Unit>()
        coEvery { repository.saveNoteForDate(any(), any()) } coAnswers { writeResult.await() }
        setContent()
        startNewNote("Late entry")
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()

        rollClockTo(LocalDateTime.of(2026, 6, 17, 0, 0))
        composeRule.runOnIdle { writeResult.completeExceptionally(IOException("disk full")) }

        composeRule.onNodeWithText("Late entry").assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsDisplayed()
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
}
