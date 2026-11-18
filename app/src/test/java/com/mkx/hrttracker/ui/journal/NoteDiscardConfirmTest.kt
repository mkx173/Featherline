package com.mkx.hrttracker.ui.journal

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * J1 and J8: a route that would drop a changed, unsaved note draft asks first, with the app's
 * unsaved-changes dialog, in the Journal and in All Notes. Keeping it returns to the editor, draft
 * intact; discarding does what the route did. An unchanged editor, and a draft held for a save, are
 * left as they were. J6: Save on an existing note's cleared text offers to delete the note.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class NoteDiscardConfirmTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val june15 = LocalDate.of(2026, 6, 15)
    private val june16 = LocalDate.of(2026, 6, 16)
    private val june17 = LocalDate.of(2026, 6, 17)
    private val clock = FakeAppTimeSource(LocalDateTime.of(2026, 6, 16, 23, 59), tokyo)
    private val notes = MutableStateFlow<List<Note>>(emptyList())
    private val writes = mutableListOf<String>()
    private val unsavedChanges = UnsavedChangesState()
    private val composerField = "today-composer-text-field"
    private val yesterday = Note(id = "june-15", date = june15, text = "Yesterday")
    private val yesterdayField = "note-timeline-text-field-${yesterday.id}"

    // When set, a save waits on it before it lands (a write still in flight).
    private var saveGate: CompletableDeferred<Unit>? = null

    // A delete sent while a save is in flight, which Room runs after it.
    private var deleteAfterSave: LocalDate? = null

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
            saveGate?.await()
            val date = firstArg<LocalDate>()
            val text = secondArg<String>()
            writes += "save $date: $text"
            notes.update { all ->
                val id = all.firstOrNull { it.date == date }?.id ?: "note-$date"
                all.filter { it.date != date } + Note(id = id, date = date, text = text)
            }
            // Room runs the delete sent meanwhile right after, and the flow emits only the result.
            deleteAfterSave?.let { day ->
                deleteAfterSave = null
                writes += "delete $day"
                notes.update { all -> all.filter { it.date != day } }
            }
        }
        coEvery { deleteNoteForDate(any()) } answers {
            val date = firstArg<LocalDate>()
            if (saveGate?.isCompleted == false) {
                deleteAfterSave = date
            } else {
                writes += "delete $date"
                notes.update { all -> all.filter { it.date != date } }
            }
        }
    }

    @After
    fun releaseMocks() = clearAllMocks()

    private val restoration = StateRestorationTester(composeRule)
    private lateinit var focusManager: FocusManager

    private fun setJournal() {
        val viewModel = JournalViewModel(repository, clock)
        restoration.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalUnsavedChanges provides unsavedChanges) {
                    focusManager = LocalFocusManager.current
                    JournalScreen(onOpenMilestones = {}, onAddDate = {}, onOpenAllNotes = {}, viewModel = viewModel)
                    UnsavedChangesDialog(hazeState = null)
                }
            }
        }
    }

    private fun string(id: Int) = context.getString(id)
    private fun tapCancel() = composeRule.onNodeWithContentDescription(string(R.string.cancel)).performClick()
    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
    private fun assertAsking() = composeRule.onNodeWithText(string(R.string.unsaved_changes_confirmation)).assertIsDisplayed()
    private fun assertNotAsking() = composeRule.onNodeWithText(string(R.string.unsaved_changes_confirmation)).assertDoesNotExist()
    private fun keepEditing() = composeRule.onNodeWithText(string(R.string.cancel)).performClick()
    private fun discard() = composeRule.onNodeWithText(string(R.string.unsaved_changes_discard)).performClick()
    private fun assertPromptShown() = composeRule.onNodeWithText(string(R.string.journal_write_about_today)).assertIsDisplayed()
    private fun assertComposerOpenWith(text: String) {
        composeRule.onNodeWithTag(composerField).assertTextEquals(text)
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertIsDisplayed()
    }
    private fun dateLabel(date: LocalDate): String =
        medicationGroupScheduleDateFormatter(context.resources.configuration.locales[0], date)(date)

    private fun startNewNote(text: String) {
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        composeRule.onNodeWithTag(composerField).performTextInput(text)
    }

    @Test
    fun cancel_onAChangedDraft_asks_keepEditingKeepsIt_andDiscardDropsIt() {
        setJournal()
        startNewNote("Slept badly")

        tapCancel()
        assertAsking()
        keepEditing()
        assertNotAsking()
        assertComposerOpenWith("Slept badly")

        tapCancel()
        discard()
        assertPromptShown()
        composeRule.onNodeWithText("Slept badly").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), writes) }
    }

    @Test
    fun back_onAChangedDraft_asks_andDiscardCloses() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setJournal()
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextInput(", and evening")

        pressBack()
        assertAsking()
        keepEditing()
        assertComposerOpenWith("Morning, and evening")

        pressBack()
        discard()
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertDoesNotExist()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")
    }

    // J1's example: a draft in Today, then a tap on yesterday's note to fix a typo.
    @Test
    fun openingAnotherNote_overAChangedDraft_asks_andDiscardOpensIt() {
        notes.value = listOf(yesterday)
        setJournal()
        startNewNote("Headache at noon")

        composeRule.onNodeWithTag(yesterdayField).performClick()
        assertAsking()
        keepEditing()
        assertComposerOpenWith("Headache at noon")
        composeRule.onNodeWithTag(yesterdayField).assertIsNotFocused()
        composeRule.onNodeWithTag(composerField).assertIsFocused()
        // Typing goes to the open composer, not the note you tapped.
        composeRule.onNodeWithTag(composerField).performTextInput("!")
        composeRule.onNodeWithTag(composerField).assertTextEquals("Headache at noon!")
        composeRule.onNodeWithTag(yesterdayField).assertTextEquals("Yesterday")

        composeRule.onNodeWithTag(yesterdayField).performClick()
        discard()
        assertPromptShown()
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertIsDisplayed()
        composeRule.onNodeWithTag(yesterdayField).assertIsFocused()
    }

    @Test
    fun anUnchangedEditor_closesWithoutAsking() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"), yesterday)
        setJournal()
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(yesterdayField).performClick()
        assertNotAsking()
        composeRule.onNodeWithTag(composerField).assertTextEquals("Morning")

        tapCancel()
        assertNotAsking()
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertDoesNotExist()

        // Nor does a draft typed back to its saved text.
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()
        composeRule.onNodeWithTag(composerField).performTextInput("Morning")
        pressBack()
        assertNotAsking()
    }

    // A draft held for a save in flight, or held after it fails, isn't what these routes drop.
    @Test
    fun aHeldDraft_isLeftAsItWas_andItsReopenedCopyOnlyAsksOnceChanged() {
        val gate = CompletableDeferred<Unit>()
        saveGate = gate
        notes.value = listOf(yesterday)
        setJournal()
        startNewNote("x")
        composeRule.onNodeWithTag(composerField).performImeAction()

        // Held: opening another note doesn't ask, and the held text stays.
        composeRule.onNodeWithTag(yesterdayField).performClick()
        assertNotAsking()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
        tapCancel()

        // Reopened, unchanged: Cancel goes back to the held text without asking.
        composeRule.onNodeWithTag(composerField).performClick()
        tapCancel()
        assertNotAsking()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
        // Typed over, it asks, and discarding goes back to the held text.
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextInput("y")
        tapCancel()
        discard()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")

        // The save fails with another note open: still held, and nothing asks.
        composeRule.onNodeWithTag(yesterdayField).performClick()
        composeRule.runOnIdle { gate.completeExceptionally(IOException("disk full")) }
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
        tapCancel()
        assertNotAsking()
        composeRule.onNodeWithTag(composerField).assertTextEquals("x")
    }

    // While the dialog is up, the editor is still open, so its session keeps its day.
    @Test
    fun theDialogAcrossMidnight_keepsTheComposerOnItsDay() {
        setJournal()
        startNewNote("Late entry")
        tapCancel()

        composeRule.runOnIdle { clock.setCurrentSnapshot(LocalDateTime.of(2026, 6, 17, 0, 0), tokyo) }
        assertAsking()
        composeRule.onNodeWithText(dateLabel(june16)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.journal_today)).assertDoesNotExist()

        keepEditing()
        assertComposerOpenWith("Late entry")
        composeRule.onNodeWithText(dateLabel(june16)).assertIsDisplayed()
        tapCancel()
        discard()
        // Ended, the session moves on to the new day.
        composeRule.onNodeWithText(dateLabel(june17)).assertIsDisplayed()
        assertPromptShown()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), writes) }
    }

    // J6: an existing note's text cleared, then Save.
    @Test
    fun savingAClearedNote_offersToDeleteIt_keepingReturnsToTheEditor_andConfirmingDeletes() {
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setJournal()
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()

        composeRule.onNodeWithTag(composerField).performImeAction()
        composeRule.onNodeWithText(string(R.string.journal_delete_note_confirmation)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.cancel)).performClick()
        composeRule.onNodeWithText(string(R.string.journal_delete_note_confirmation)).assertDoesNotExist()
        assertComposerOpenWith("")

        composeRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeRule.onNodeWithText(string(R.string.delete_entries_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(listOf("delete $june16"), writes) }
        assertPromptShown()
    }

    @Test
    fun savingANewNoteWithNoText_justCloses() {
        setJournal()
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeRule.onNodeWithText(string(R.string.journal_delete_note_confirmation)).assertDoesNotExist()
        assertPromptShown()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), writes) }
    }

    // All Notes ---------------------------------------------------------------------------------

    private val noteA = Note(id = "june-15", date = june15, text = "Note A")
    private val noteB = Note(id = "june-14", date = june15.minusDays(1), text = "Note B")
    private val rowA get() = composeRule.onNodeWithTag("note-timeline-text-field-${noteA.id}", useUnmergedTree = true)
    private val rowB get() = composeRule.onNodeWithTag("note-timeline-text-field-${noteB.id}", useUnmergedTree = true)
    private var navigatedBack = 0
    private val selections = mutableListOf<LocalDate>()

    private var allNotesShown by mutableStateOf(true)
    private val deletes = mutableListOf<LocalDate>()
    private val allNotesSaves = NoteSaves()
    private val allNotesSaveIds = mutableListOf<Long>()

    private fun setAllNotes(
        groups: List<MonthGroupUiState> = listOf(MonthGroupUiState(YearMonth.from(june16), listOf(noteA, noteB))),
    ) {
        restoration.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalUnsavedChanges provides unsavedChanges) {
                    if (allNotesShown) {
                        AllNotesScreenContent(
                            uiState = AllNotesUiState(isLoading = false, today = june16, monthGroups = groups),
                            onNavigateBack = { navigatedBack++; true },
                            onSaveNote = { _, _ -> allNotesSaves.start().also { allNotesSaveIds += it } },
                            noteSaves = allNotesSaves,
                            onDeleteNote = { deletes += it },
                            onEnterSelection = { selections += it },
                        )
                    }
                    UnsavedChangesDialog(hazeState = null)
                }
            }
        }
    }

    private fun editA(text: String) {
        rowA.performClick()
        rowA.performTextClearance()
        rowA.performTextInput(text)
    }

    @Test
    fun allNotes_theTopBarBackArrow_asksOverAChangedDraft() {
        setAllNotes()
        val back = { composeRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick() }
        rowA.performClick()
        back()
        assertNotAsking()
        composeRule.runOnIdle { assertEquals(1, navigatedBack) }

        editA("A edited")
        back()
        assertAsking()
        keepEditing()
        rowA.assertTextEquals("A edited")
        composeRule.runOnIdle { assertEquals(1, navigatedBack) }
        back()
        discard()
        composeRule.runOnIdle { assertEquals(2, navigatedBack) }
    }

    // A navigation tab and a Home deep link leave All Notes through the back stack's leave.
    @Test
    fun allNotes_aTabOrDeepLink_asksOverAChangedDraft_only() {
        setAllNotes()
        rowA.performClick()
        composeRule.runOnIdle {
            assertFalse(unsavedChanges.backStackHasUnsavedChanges)
            assertNull(unsavedChanges.leaveBackStack { true })
        }

        rowA.performTextInput("more ")
        var left = 0
        composeRule.runOnIdle {
            assertTrue(unsavedChanges.backStackHasUnsavedChanges)
            assertNotNull(unsavedChanges.leaveBackStack { left++; true })
        }
        assertAsking()
        discard()
        composeRule.runOnIdle { assertEquals(1, left) }
    }

    @Test
    fun allNotes_openingAnotherRow_cancel_back_andSelecting_ask() {
        setAllNotes()
        editA("A edited")

        rowB.performClick()
        assertAsking()
        keepEditing()
        rowA.assertTextEquals("A edited")

        tapCancel()
        assertAsking()
        keepEditing()

        pressBack()
        assertAsking()
        keepEditing()

        rowB.performTouchInput { longClick() }
        assertAsking()
        keepEditing()
        rowA.assertTextEquals("A edited")
        composeRule.runOnIdle { assertEquals(emptyList<LocalDate>(), selections) }

        rowB.performTouchInput { longClick() }
        discard()
        composeRule.runOnIdle { assertEquals(listOf(noteB.date), selections) }
    }

    // The guard asks about a draft the controller reports in the same frame, before the screen's
    // own registration catches up.
    @Test
    fun askFirst_asksAtOnce_forADraftReportedThisFrame() {
        val guard = UnsavedChangesState()
        val controller = NoteEditorController().apply {
            unsavedChanges = guard
            begin("a")
            reportUnsaved("a", true)
        }
        var ran = false
        controller.askFirst { ran = true }
        assertFalse(ran)
        assertNotNull(guard.pendingLeave)
    }

    @Test
    fun allNotes_aTapOnAnotherRowsChrome_asks() {
        setAllNotes()
        editA("A edited")
        // The row's date header, above its text.
        composeRule.onNodeWithTag("note-timeline-row-${noteB.id}").performTouchInput { click(percentOffset(0.5f, 0.1f)) }
        assertAsking()
        keepEditing()
        rowA.assertTextEquals("A edited")
    }

    // A discard that leaves the screen stops counting its draft while the screen animates out.
    @Test
    fun allNotes_theBackArrowsDiscard_stopsAskingAboutTheLeftDraft() {
        setAllNotes()
        editA("A edited")
        composeRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        discard()
        composeRule.runOnIdle {
            assertEquals(1, navigatedBack)
            assertFalse(unsavedChanges.backStackHasUnsavedChanges)
        }
    }

    // Once All Notes has left, its draft no longer asks, or every tab would ask for ever.
    @Test
    fun allNotes_leavingTheComposition_stopsAsking() {
        setAllNotes()
        editA("A edited")
        composeRule.runOnIdle { assertTrue(unsavedChanges.backStackHasUnsavedChanges) }
        composeRule.runOnIdle { allNotesShown = false }
        composeRule.runOnIdle { assertFalse(unsavedChanges.backStackHasUnsavedChanges) }
    }

    @Test
    fun theTimelineCard_overToTheComposer_asks() {
        notes.value = listOf(yesterday)
        setJournal()
        composeRule.onNodeWithTag(yesterdayField).performClick()
        composeRule.onNodeWithTag(yesterdayField).performTextClearance()
        composeRule.onNodeWithTag(yesterdayField).performTextInput("Edited")
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        assertAsking()
        keepEditing()
        composeRule.onNodeWithTag(yesterdayField).assertTextEquals("Edited")
        composeRule.onNodeWithText(string(R.string.journal_write_about_today)).performClick()
        discard()
        assertComposerOpenWith("")
        composeRule.onNodeWithTag(yesterdayField).assertTextEquals("Yesterday")
    }

    @Test
    fun allNotes_savingAClearedNote_offersToDeleteIt() {
        setAllNotes()
        editA("")
        composeRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeRule.onNodeWithText(string(R.string.cancel)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(emptyList<LocalDate>(), deletes) }

        composeRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeRule.onNodeWithText(string(R.string.delete_entries_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(listOf(noteA.date), deletes) }
    }

    // Unfocused and scrolled away, the open composer keeps its draft through a state save: its lazy
    // item is pinned.
    @Test
    @Config(qualifiers = "w411dp-h500dp")
    fun anOpenDraft_scrolledAwayUnfocused_survivesAStateSave() {
        notes.value = (1L..12L).map { Note(id = "n$it", date = june16.minusDays(it), text = "Note $it") }
        setJournal()
        startNewNote("Slept badly")
        composeRule.runOnIdle { focusManager.clearFocus() }
        val list = composeRule.onNodeWithTag("journal-screen-list")
        list.performScrollToNode(hasText(string(R.string.journal_no_earlier_notes)))

        restoration.emulateSavedInstanceStateRestore()
        list.performScrollToIndex(0)

        assertComposerOpenWith("Slept badly")
    }

    // The same for a failed draft held on a closed All Notes row.
    @Test
    @Config(qualifiers = "w411dp-h500dp")
    fun aHeldFailedDraft_scrolledAway_survivesAStateSave() {
        val many = (20 downTo 1).map { Note(id = "june-$it", date = LocalDate.of(2026, 6, it), text = "Note $it") }
        setAllNotes(listOf(MonthGroupUiState(YearMonth.of(2026, 6), many)))
        val first = { composeRule.onNodeWithTag("note-timeline-text-field-june-20", useUnmergedTree = true) }
        first().performClick()
        first().performTextClearance()
        first().performTextInput("Edited")
        first().performImeAction()
        // Another row is open when the save fails: the draft stays held, closed.
        composeRule.onNodeWithTag("note-timeline-text-field-june-19", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { allNotesSaves.settle(allNotesSaveIds.single(), landed = false) }
        tapCancel()
        val list = { composeRule.onNode(hasScrollAction()) }
        list().performScrollToNode(hasTestTag("note-timeline-row-june-1"))

        restoration.emulateSavedInstanceStateRestore()
        list().performScrollToIndex(0)

        first().assertTextEquals("Edited")
        first().performClick()
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertIsDisplayed()
        first().assertTextEquals("Edited")
    }

    // A month filter that hides the open row asks first, and discarding closes its editor.
    @Test
    fun allNotes_aFilterHidingTheOpenRow_asks() {
        val june = Note(id = "june-10", date = LocalDate.of(2026, 6, 10), text = "June note")
        val may = Note(id = "may-15", date = LocalDate.of(2026, 5, 15), text = "May note")
        val mayRow = { composeRule.onNodeWithTag("note-timeline-text-field-${may.id}", useUnmergedTree = true) }
        setAllNotes(
            listOf(
                MonthGroupUiState(YearMonth.of(2026, 6), listOf(june)),
                MonthGroupUiState(YearMonth.of(2026, 5), listOf(may)),
            ),
        )
        mayRow().performClick()
        mayRow().performTextInput(" edited")
        // The picker starts on the latest month, June, which hides May's open row.
        val filterToJune = {
            composeRule.onNodeWithContentDescription(string(R.string.journal_filter_by_month)).performClick()
            composeRule.onNodeWithText(string(R.string.confirm)).performClick()
        }
        filterToJune()
        assertAsking()
        keepEditing()
        mayRow().assertTextEquals("May note edited")

        filterToJune()
        discard()
        mayRow().assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.save)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.journal_clear_month_filter)).performClick()
        mayRow().assertTextEquals("May note")
    }

    // A delete while the card's own save is in flight: the note never shows that save's text
    // before the delete removes it, and the card lets go anyway.
    @Test
    fun aDelete_whileTheCardsSaveIsInFlight_releasesItsLatch() {
        val gate = CompletableDeferred<Unit>()
        saveGate = gate
        notes.value = listOf(Note(id = "june-16", date = june16, text = "Morning"))
        setJournal()
        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()
        composeRule.onNodeWithTag(composerField).performTextInput("Evening")
        composeRule.onNodeWithTag(composerField).performImeAction()

        composeRule.onNodeWithTag(composerField).performClick()
        composeRule.onNodeWithTag(composerField).performTextClearance()
        composeRule.onNodeWithTag(composerField).performImeAction()
        composeRule.onNodeWithText(string(R.string.delete_entries_confirm)).performClick()
        composeRule.runOnIdle { gate.complete(Unit) }

        composeRule.runOnIdle {
            assertEquals(listOf("save $june16: Evening", "delete $june16"), writes)
            assertEquals(emptyList<Note>(), notes.value)
        }
        assertPromptShown()
        composeRule.onNodeWithText("Evening").assertDoesNotExist()
    }
}
