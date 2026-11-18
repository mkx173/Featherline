package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Journal at the edge of its 30-day window, on the real JournalScreen, NoteEditorCard,
 * NoteEditorController and JournalViewModel: a Today session held on a day the window has left
 * (review 2026-11-18 Medium 13 (b), second review #5), and a timeline row that leaves at midnight
 * with its editor open (Medium 13 (a)). One list of notes stands behind the flows and the saves;
 * the screen leaves and comes back through a SaveableStateHolder, as the NavHost does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class JournalWindowEdgeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val june16 = LocalDate.of(2026, 6, 16)
    private val clock = FakeAppTimeSource(LocalDateTime.of(2026, 6, 16, 12, 0), tokyo)
    private val notes = MutableStateFlow<List<Note>>(emptyList())
    private val writes = mutableListOf<String>()
    private val unsavedChanges = UnsavedChangesState()
    private val composerField = "today-composer-text-field"
    private var journalShown by mutableStateOf(true)

    // Days whose by-date read emits nothing, as a failed read does while it retries.
    private val failingReads = mutableSetOf<LocalDate>()

    // False: the window's read hasn't emitted yet (a cold start).
    private val windowLoaded = MutableStateFlow(true)

    private val repository: JournalRepository = mockk {
        every { getCachedTrackedDates() } returns null
        every { getCachedPinnedTrackedDates() } returns null
        every { getCachedNotes() } returns null
        every { observeTrackedDates() } returns flowOf(emptyList())
        every { observePinnedTrackedDates() } returns flowOf(emptyList())
        every { observeNotesCountBefore(any()) } returns flowOf(0)
        every { observeNotesOnOrAfter(any()) } answers {
            val start = firstArg<LocalDate>()
            windowLoaded.flatMapLatest { loaded ->
                if (loaded) notes.map { all -> all.filter { !it.date.isBefore(start) } } else emptyFlow()
            }
        }
        every { observeNoteForDate(any()) } answers {
            val date = firstArg<LocalDate>()
            if (date in failingReads) emptyFlow() else notes.map { all -> all.firstOrNull { it.date == date } }
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
    }

    @After
    fun releaseMocks() = clearAllMocks()

    private var viewModel = JournalViewModel(repository, clock)
    private val restorationTester = StateRestorationTester(composeRule)

    private fun setJournal() {
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

    private fun string(id: Int) = context.getString(id)
    private fun setClock(at: LocalDateTime) {
        composeRule.runOnIdle { clock.setCurrentSnapshot(at, tokyo) }
        composeRule.waitForIdle()
    }

    private fun leaveAndComeBack() {
        composeRule.runOnIdle { journalShown = false }
        composeRule.runOnIdle { journalShown = true }
        composeRule.waitForIdle()
    }

    @Test
    fun anUntouchedEditorOnAnExistingNote_heldThirtyDays_stillShowsItsNote() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setJournal()
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")

        // 29 days on, the note is still the window's oldest.
        setClock(LocalDateTime.of(2026, 7, 15, 0, 0))
        composeRule.onNodeWithTag(composerField).assertIsDisplayed().assertTextEquals("Morning")

        // A day later the window has left it; the session hasn't.
        setClock(LocalDateTime.of(2026, 7, 16, 0, 0))
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")
    }

    private fun aDraftSavedThirtyDaysOn() {
        setJournal()
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        composeRule.onNodeWithTag(composerField).performTextInput("Late entry")
        setClock(LocalDateTime.of(2026, 7, 16, 12, 0))
        composeRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeRule.waitForIdle()
        // The write lands on the session's day, as the rollover rule says.
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Late entry"), writes) }
    }

    // Once its save has landed, the session is over and the composer shows today.
    @Test
    fun aDraftSavedThirtyDaysOn_movesTheComposerToToday() {
        aDraftSavedThirtyDaysOn()

        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).assertIsDisplayed()
    }

    // It doesn't take leaving the screen, and coming back keeps it on today.
    @Test
    fun aDraftSavedThirtyDaysOn_staysOnTodayWhenTheScreenComesBack() {
        aDraftSavedThirtyDaysOn()
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).assertIsDisplayed()

        leaveAndComeBack()

        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).assertIsDisplayed()
    }

    // The draft is dropped with its row, an accepted limit (JournalViewModel's ponytail); nothing
    // is left open to ask about afterwards. The row is the timeline's only one, so the timeline
    // leaves with it.
    @Test
    fun aDirtyEditorOnTheWindowsOldestRow_goneAtMidnight_leavesNothingToAskAbout() {
        aDirtyEditorOnTheOldestRow_goneAtMidnight(otherNotes = emptyList())
    }

    // The same with another row left in the timeline.
    @Test
    fun aDirtyEditorOnTheWindowsOldestRow_goneAtMidnight_withRowsLeft_leavesNothingToAskAbout() {
        aDirtyEditorOnTheOldestRow_goneAtMidnight(
            otherNotes = listOf(Note(id = "june-10", date = june16.minusDays(6), text = "Newer")),
        )
    }

    private fun aDirtyEditorOnTheOldestRow_goneAtMidnight(otherNotes: List<Note>) {
        clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 16, 23, 59), tokyo)
        val oldest = Note(id = "may-18", date = june16.minusDays(29), text = "Oldest")
        val field = "note-timeline-text-field-${oldest.id}"
        notes.value = listOf(oldest) + otherNotes
        setJournal()
        composeRule.onNodeWithTag(field).performClick()
        composeRule.onNodeWithTag(field).performTextInput("!")
        composeRule.waitForIdle()

        setClock(LocalDateTime.of(2026, 6, 17, 0, 0))
        composeRule.onNodeWithTag(field).assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.unsaved_changes_confirmation)).assertDoesNotExist()
        composeRule.onNodeWithTag(composerField).assertIsDisplayed()
    }

    // A row still listed that leaves with the screen keeps its open editor and draft for the return.
    @Test
    fun anOpenTimelineRow_leavingWithTheScreen_isStillOpenWhenItComesBack() {
        val note = Note(id = "june-10", date = june16.minusDays(6), text = "Row")
        val field = "note-timeline-text-field-${note.id}"
        notes.value = listOf(note)
        setJournal()
        composeRule.onNodeWithTag(field).performClick()
        composeRule.onNodeWithTag(field).performTextInput("!")
        composeRule.waitForIdle()

        leaveAndComeBack()

        composeRule.onNodeWithTag(field).assertTextEquals("Row!")
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.unsaved_changes_confirmation)).assertIsDisplayed()
    }

    private fun composerText(): String = composeRule.onNodeWithTag(composerField).fetchSemanticsNode()
        .config[SemanticsProperties.EditableText].text

    private fun openTheExistingNote() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setJournal()
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")
    }

    private fun typeAndSave(text: String) {
        composeRule.onNodeWithTag(composerField).performTextInput(text)
        composeRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeRule.waitForIdle()
    }

    // A hold that starts while its by-date read emits nothing shows the window's copy.
    @Test
    fun aHoldStartingInsideTheWindow_whileItsReadEmitsNothing_showsTheWindowsCopy() {
        openTheExistingNote()
        composeRule.runOnIdle { failingReads += june16 }

        setClock(LocalDateTime.of(2026, 6, 17, 0, 0))

        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")
    }

    // Held past the window, the screen comes back while the by-date read fails: there is no copy to
    // show, so the Journal waits, loading, and nothing can be saved over the note until it loads.
    @Test
    fun heldPastTheWindow_screenComesBack_whileItsReadFails_waitsForTheNote() {
        openTheExistingNote()
        setClock(LocalDateTime.of(2026, 7, 16, 0, 0))
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")
        composeRule.runOnIdle { failingReads += june16 }

        leaveAndComeBack()
        composeRule.onNodeWithTag(composerField).assertDoesNotExist()

        // A read that works, as the failed one's retry would be.
        composeRule.runOnIdle { failingReads -= june16 }
        leaveAndComeBack()
        assertEquals("Morning", composerText())
        typeAndSave("X")
        composeRule.runOnIdle { assertEquals(listOf("save $june16: MorningX"), writes) }
    }

    // A clock jump past the window, before the by-date read first emits, keeps the open editor, its
    // draft and its note: the composition still has the note it showed.
    @Test
    fun aClockJumpPastTheWindow_whileItsReadFails_keepsTheOpenEditorAndItsDraft() {
        openTheExistingNote()
        composeRule.onNodeWithTag(composerField).performTextInput("!")
        composeRule.runOnIdle { failingReads += june16 }

        setClock(LocalDateTime.of(2026, 8, 1, 9, 0))

        assertEquals("Morning!", composerText())
        typeAndSave("?")
        composeRule.runOnIdle { assertEquals(listOf("save $june16: Morning!?"), writes) }
    }

    // Process death while held inside the window: the new process's window read lands before the
    // by-date read (its first frame is still loading), and the composer shows the window's copy.
    @Test
    fun heldInsideTheWindow_processDeath_windowLoadsBeforeTheByDateRead_showsTheWindowsCopy() {
        openTheExistingNote()
        setClock(LocalDateTime.of(2026, 6, 17, 0, 0))
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")

        composeRule.runOnIdle {
            failingReads += june16
            windowLoaded.value = false
            viewModel = JournalViewModel(repository, FakeAppTimeSource(LocalDateTime.of(2026, 6, 17, 8, 0), tokyo))
        }
        restorationTester.emulateSavedInstanceStateRestore()
        // While the ViewModel loads, the page waits: no composer to show an empty note.
        composeRule.onNodeWithTag(composerField).assertDoesNotExist()
        composeRule.runOnIdle { windowLoaded.value = true }
        composeRule.waitForIdle()

        assertEquals("Morning", composerText())
        typeAndSave("X")
        composeRule.runOnIdle { assertEquals(listOf("save $june16: MorningX"), writes) }
    }
}

// NotesTimeline alone, its controller hoisted above it with no state holder, so the order of a
// saved state and a row's onDispose can't hide what isListed decides.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class NotesTimelineIsListedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val june16 = LocalDate.of(2026, 6, 16)
    private val note = Note(id = "june-10", date = june16.minusDays(6), text = "Row")
    private var shown by mutableStateOf(true)
    private lateinit var controller: NoteEditorController

    private fun leaveWith(listed: Boolean) {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalUnsavedChanges provides UnsavedChangesState()) {
                    controller = rememberNoteEditorController()
                    if (shown) {
                        NotesTimeline(
                            notes = listOf(note),
                            today = june16,
                            onSave = { _, _ -> 0L },
                            noteSaves = NoteSaves(),
                            editorController = controller,
                            isListed = { listed },
                        )
                    }
                }
            }
        }
        val field = "note-timeline-text-field-${note.id}"
        composeRule.onNodeWithTag(field).performClick()
        composeRule.onNodeWithTag(field).performTextInput("!")
        composeRule.waitForIdle()
        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()
    }

    @Test
    fun aRowStillListed_leavingComposition_keepsItsEditorOpen() {
        leaveWith(listed = true)
        composeRule.runOnIdle { assertEquals(true to true, controller.isEditing(note.id) to controller.hasUnsavedDraft) }
    }

    @Test
    fun aRowNoLongerListed_leavingComposition_isClosed() {
        leaveWith(listed = false)
        composeRule.runOnIdle { assertEquals(false to false, controller.isEditing(note.id) to controller.hasUnsavedDraft) }
    }
}
