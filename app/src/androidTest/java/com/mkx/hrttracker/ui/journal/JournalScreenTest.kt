package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private const val TodayComposerTextFieldTag = "today-composer-text-field"
private const val NoteTimelineTextFieldTagPrefix = "note-timeline-text-field-"
private const val NoteTimelineRowTagPrefix = "note-timeline-row-"
private const val NoteTimelineDotTagPrefix = "note-timeline-dot-"
private const val NoteTimelineLineBottomTagPrefix = "note-timeline-line-bottom-"
private const val JournalScreenListTag = "journal-screen-list"

class JournalScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyAnchors_showsCtaAndSeeAllNotesCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var milestonesOpened = false
        var allNotesOpened = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        olderNotesCount = 2,
                    ),
                    onOpenMilestones = { milestonesOpened = true },
                    onOpenAllNotes = { allNotesOpened = true },
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_no_dates))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_see_all_notes))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(milestonesOpened)
            assertTrue(allNotesOpened)
        }
    }

    @Test
    fun unpinnedDates_showsNothingPinnedPromptInsteadOfNoDates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        hasTrackedDates = true,
                        pinnedAnchors = emptyList(),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_nothing_pinned_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_no_dates))
            .assertIsNotDisplayed()
    }

    @Test
    fun populatedState_showsPinnedAnchorAndDoesNotDuplicateTodayNoteInTimeline() {
        val today = LocalDate.of(2026, 6, 16)
        val olderNote = Note(
            id = "june-15",
            date = LocalDate.of(2026, 6, 15),
            text = "Yesterday note",
        )
        val todayNote = Note(
            id = "june-16",
            date = today,
            text = "Today note",
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        pinnedAnchors = listOf(
                            AnchorRowUiState(
                                id = "estradiol",
                                name = "On estradiol",
                                icon = AnchorIcon.MEDICATION,
                                palette = null,
                                date = LocalDate.of(2024, 4, 1),
                                dayMagnitude = 806,
                                isFuture = false,
                            )
                        ),
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote, olderNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("On estradiol").assertIsDisplayed()
        composeRule.onNodeWithText("Today note").assertIsDisplayed()
        composeRule.onNodeWithText("Yesterday note").assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-16").assertIsNotDisplayed()
    }

    @Test
    fun scrollToTopSignal_returnsJournalListToTop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val notes = (1..24).map { index ->
            Note(
                id = "older-$index",
                date = today.minusDays(index.toLong()),
                text = "Older note $index",
            )
        }
        var scrollToTopSignal by mutableIntStateOf(0)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        recentNotes = notes,
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                    scrollToTopSignal = scrollToTopSignal,
                )
            }
        }

        // The notes now render as one rail item rather than one item per note, so scroll by
        // content to the oldest note (bottom of the rail) to push the top off-screen.
        composeRule.onNodeWithTag(JournalScreenListTag)
            .performScrollToNode(hasText("Older note 24"))
        composeRule.onNodeWithText("Older note 24").assertIsDisplayed()

        composeRule.runOnIdle {
            scrollToTopSignal++
        }

        val milestonesHeader = context.getString(R.string.journal_milestones_section)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(milestonesHeader)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(milestonesHeader).assertIsDisplayed()
    }

    @Test
    fun pinnedDatesCard_rendersPinnedAnchorsInOrderAndOpensMilestones() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2024, 6, 16)
        val expectedDateLabel = dateLabelFormatter(
            locale = context.resources.configuration.locales[0],
            today = today,
        )(LocalDate.of(2024, 4, 1))
        var opened = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedDatesCard(
                    today = today,
                    anchors = listOf(
                        AnchorRowUiState(
                            id = "estradiol",
                            name = "On estradiol",
                            icon = AnchorIcon.MEDICATION,
                            palette = MedicationGroupColorKey.ROSE,
                            date = LocalDate.of(2024, 4, 1),
                            dayMagnitude = 807,
                            isFuture = false,
                        ),
                        AnchorRowUiState(
                            id = "surgery",
                            name = "Surgery",
                            icon = AnchorIcon.EVENT,
                            palette = MedicationGroupColorKey.TEAL,
                            date = LocalDate.of(2026, 9, 15),
                            dayMagnitude = 91,
                            isFuture = true,
                        ),
                        AnchorRowUiState(
                            id = "labs",
                            name = "Labs",
                            icon = AnchorIcon.LABS,
                            palette = null,
                            date = LocalDate.of(2026, 6, 15),
                            dayMagnitude = 1,
                            isFuture = false,
                        ),
                    ),
                    onClick = { opened = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_pinned_section))
            .assertIsDisplayed()
        composeRule.onNodeWithText("On estradiol").assertIsDisplayed()
        composeRule.onNodeWithText("Surgery").assertIsDisplayed()
        composeRule.onNodeWithText("Labs").assertIsDisplayed()
        composeRule.onNodeWithText(expectedDateLabel).assertIsDisplayed()
        val estradiolTop = composeRule.onNodeWithText("On estradiol", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val surgeryTop = composeRule.onNodeWithText("Surgery", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val labsTop = composeRule.onNodeWithText("Labs", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue("Anchors should render in tray order", estradiolTop < surgeryTop)
        assertTrue("Anchors should render in tray order", surgeryTop < labsTop)
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.journal_milestone_days_past, 807, 807)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.journal_milestone_days_future, 91, 91)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.journal_milestone_days_past, 1, 1)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("100 days").assertIsNotDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.journal_pinned_section))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(opened)
        }
    }

    @Test
    fun todayPrompt_opensEditorAndSavesNonEmptyTextOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedTexts = mutableListOf<String>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = LocalDate.of(2026, 6, 16),
                        recentNotes = listOf(
                            Note(
                                id = "june-15",
                                date = LocalDate.of(2026, 6, 15),
                                text = "Older note",
                            )
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = { savedTexts += it },
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(savedTexts.isEmpty())
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("A new day")
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("A new day"), savedTexts)
        }
    }

    @Test
    fun todayEditor_cancelReturnsWithoutSaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var saved = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = LocalDate.of(2026, 6, 16),
                        recentNotes = listOf(
                            Note(
                                id = "june-15",
                                date = LocalDate.of(2026, 6, 15),
                                text = "Older note",
                            )
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = { saved = true },
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Unsaved draft")
        composeRule.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(saved)
        }
    }

    @Test
    fun todayEditor_savingNewNote_doesNotFlashPromptBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = LocalDate.of(2026, 6, 16),
                        recentNotes = listOf(
                            Note(
                                id = "june-15",
                                date = LocalDate.of(2026, 6, 15),
                                text = "Older note",
                            )
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    // Mirror the real ViewModel: the save round-trips through the repository, so the
                    // note text is NOT fed back synchronously. The editor must hold the field until
                    // the saved text lands rather than blinking back to the "write about today" prompt.
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("A new day")
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsEnabled()
            .performClick()

        // The prompt must not reappear while the saved text is in flight.
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertDoesNotExist()
    }

    @Test
    fun todaySavedNote_tapsOpenPrefilledEditor() {
        val today = LocalDate.of(2026, 6, 16)
        val todayNote = Note(
            id = "june-16",
            date = today,
            text = "Existing note",
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Existing note")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(TodayComposerTextFieldTag)
            .assertIsDisplayed()
            .assertTextContains("Existing note")
    }

    @Test
    fun todaySavedNote_saveButtonStaysEnabledButUnchangedSaveClosesWithoutWriting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val todayNote = Note(
            id = "june-16",
            date = today,
            text = "Existing note",
        )
        val savedTexts = mutableListOf<String>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = { savedTexts += it },
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsNotDisplayed()
        composeRule.runOnIdle {
            assertTrue(savedTexts.isEmpty())
        }
    }

    @Test
    fun todaySavedNote_deleteRequiresConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val todayNote = Note(
            id = "june-16",
            date = today,
            text = "Existing note",
        )
        val deletedDates = mutableListOf<LocalDate>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                    onDeleteTodayNote = { deletedDates += today },
                    onDeleteNote = { deletedDates += it },
                )
            }
        }

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_delete_note))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_delete_note_title))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(deletedDates.isEmpty())
        }

        composeRule.onNodeWithText(context.getString(R.string.delete_entries_confirm))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(today), deletedDates)
        }
    }

    @Test
    fun todayPromptAndTimelineRows_doNotSaveBeforeSaveAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var todayNoteSaved = false
        var olderNoteSaved = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = LocalDate.of(2026, 6, 16),
                        recentNotes = listOf(
                            Note(
                                id = "june-15",
                                date = LocalDate.of(2026, 6, 15),
                                text = "Older note",
                            )
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = { todayNoteSaved = true },
                    onSaveNote = { _, _ -> olderNoteSaved = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Older note")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertFalse(todayNoteSaved)
            assertFalse(olderNoteSaved)
        }
    }

    @Test
    fun notesTimelineRow_rendersHumanDateLabelAndText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appLocale = context.resources.configuration.locales[0]
        val today = LocalDate.of(2026, 6, 16)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                NotesTimeline(
                    notes = listOf(
                        Note(
                            id = "june-16",
                            date = today,
                            text = "Today timeline note",
                        )
                    ),
                    today = today,
                    onSave = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(
            medicationGroupScheduleDateFormatter(appLocale, today)(today)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Today timeline note").assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-16").assertIsNotDisplayed()
    }

    @Test
    fun notesTimelineRow_keepsSixDpGapBetweenOutgoingLineAndNextDot() {
        val today = LocalDate.of(2026, 6, 16)
        var expectedGapPx = 0f

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                expectedGapPx = with(LocalDensity.current) { 6.dp.toPx() }
                NotesTimeline(
                    notes = listOf(
                        Note(
                            id = "june-15",
                            date = today.minusDays(1),
                            text = "Yesterday note",
                        ),
                        Note(
                            id = "june-14",
                            date = today.minusDays(2),
                            text = "Earlier note",
                        ),
                    ),
                    today = today,
                    onSave = { _, _ -> },
                )
            }
        }

        val outgoingLineBottom = composeRule.onNodeWithTag(
            "${NoteTimelineLineBottomTagPrefix}june-15",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val dotTop = composeRule.onNodeWithTag(
            "${NoteTimelineDotTagPrefix}june-14",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        assertEquals(expectedGapPx.toDouble(), (dotTop - outgoingLineBottom).toDouble(), 0.5)
    }

    @Test
    fun notesTimelineRow_keepsDateLabelFlushWithIncomingRowTop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appLocale = context.resources.configuration.locales[0]
        val today = LocalDate.of(2026, 6, 16)
        val targetDate = today.minusDays(2)
        val targetDateLabel = medicationGroupScheduleDateFormatter(appLocale, today)(targetDate)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                NotesTimeline(
                    notes = listOf(
                        Note(
                            id = "june-15",
                            date = today.minusDays(1),
                            text = "Yesterday note",
                        ),
                        Note(
                            id = "june-14",
                            date = targetDate,
                            text = "Earlier note",
                        ),
                    ),
                    today = today,
                    onSave = { _, _ -> },
                )
            }
        }

        val incomingRowTop = composeRule.onNodeWithTag(
            "${NoteTimelineRowTagPrefix}june-14",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val dateLabelTop = composeRule.onNodeWithText(targetDateLabel, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        assertEquals(0.0, (dateLabelTop - incomingRowTop).toDouble(), 0.5)
    }

    @Test
    fun notesTimelineRow_editsTextAndCancelsWithoutNoOpWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val savedNotes = mutableListOf<Pair<LocalDate, String>>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        recentNotes = listOf(
                            Note(
                                id = "june-15",
                                date = today.minusDays(1),
                                text = "Yesterday note",
                            )
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { date, text ->
                        savedNotes += date to text
                    },
                )
            }
        }

        composeRule.onNodeWithText("Yesterday note")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15")
            .assertIsDisplayed()
            .assertTextContains("Yesterday note")
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsNotDisplayed()
        composeRule.runOnIdle {
            assertTrue(savedNotes.isEmpty())
        }
        composeRule.onNodeWithText("Yesterday note")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15").performTextClearance()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsNotDisplayed()
        composeRule.runOnIdle {
            assertTrue(savedNotes.isEmpty())
        }

        composeRule.onNodeWithText("Yesterday note")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15")
            .performTextClearance()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15")
            .performTextInput("Edited yesterday")
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(today.minusDays(1) to "Edited yesterday"), savedNotes)
        }
    }

    @Test
    fun notesTimelineRow_switchingEditorsDiscardsUnsavedDraft() {
        val today = LocalDate.of(2026, 6, 16)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        recentNotes = listOf(
                            Note(id = "june-15", date = today.minusDays(1), text = "Yesterday note"),
                            Note(id = "june-14", date = today.minusDays(2), text = "Earlier note"),
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        // Edit row A's text but don't save it.
        composeRule.onNodeWithText("Yesterday note").performClick()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15").performTextClearance()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15")
            .performTextInput("Abandoned edit")

        // Switch to row B without saving — the shared controller closes A without its cancel path.
        composeRule.onNodeWithText("Earlier note").performClick()

        // Re-open A: the abandoned draft must NOT survive; the field shows the persisted text.
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15").performClick()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15")
            .assertTextContains("Yesterday note")
        composeRule.onAllNodesWithText("Abandoned edit").assertCountEquals(0)
    }

    @Test
    fun todaySavedNote_failedDeleteKeepsSavedTextNotDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val todayNote = Note(id = "june-16", date = today, text = "Existing note")

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                    // Simulate a delete that fails to persist: the note stays in uiState.
                    onDeleteTodayNote = {},
                    onDeleteNote = {},
                )
            }
        }

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextClearance()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("Modified draft")
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_delete_note))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.delete_entries_confirm)).performClick()

        // The delete did not persist (row still present); the field must show the saved text, not
        // the unsaved modification it would otherwise keep displaying as if persisted.
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertTextContains("Existing note")
        composeRule.onAllNodesWithText("Modified draft").assertCountEquals(0)
    }

    @Test
    fun todayEditor_imeActionSavesAndCloses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val savedTexts = mutableListOf<String>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = { savedTexts += it },
                    onSaveNote = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag)
            .performTextInput("Done from keyboard")

        // The keyboard's action key follows the Save button's path: write the note, then close
        // the editor (clearing focus collapses it back to the prompt).
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performImeAction()

        composeRule.runOnIdle {
            assertEquals(listOf("Done from keyboard"), savedTexts)
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsNotDisplayed()
        // The editor collapses to view mode holding the saved text; it must not blink back to the
        // prompt while the committed text round-trips through the repository.
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertTextContains("Done from keyboard")
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertDoesNotExist()
    }

    @Test
    fun openingAnotherEditor_closesThePreviouslyOpenEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val todayNote = Note(id = "june-16", date = today, text = "Today entry")
        val pastNote = Note(id = "june-15", date = today.minusDays(1), text = "Yesterday entry")

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote, pastNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        // Open the Today editor; its Save control (only shown while editing) appears.
        composeRule.onNodeWithText("Today entry").performClick()
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.save))
            .assertCountEquals(1)

        // Opening the past note's editor must close Today's: a single note edits at a time, so
        // exactly one Save control is ever present — never one per opened card.
        composeRule.onNodeWithTag(JournalScreenListTag)
            .performScrollToNode(hasText("Yesterday entry"))
        composeRule.onNodeWithText("Yesterday entry").performClick()

        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15")
            .assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.save))
            .assertCountEquals(1)
    }

    @Test
    fun todayNoteWithoutPastNotes_showsNoEarlierNotesEndCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val todayNote = Note(id = "today", date = today, text = "Today note")

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = today,
                        todayNote = todayNote,
                        recentNotes = listOf(todayNote),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = {},
                    onSaveNote = { _, _ -> },
                )
            }
        }

        // With today's note saved but no past notes, the notes area must still close with an
        // end card ("No earlier notes") instead of vanishing into nothing.
        composeRule.onNodeWithText(context.getString(R.string.journal_no_earlier_notes))
            .assertIsDisplayed()
    }

    @Test
    fun todayEditor_failedSave_keepsDraftAndReopensForRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The composer's draft-recovery reacts to a bumped save-failure token. Simulate the VM:
        // the save never feeds text back (failed write) and the token bumps. The draft must stay
        // in the field and the editor must re-open for retry.
        var token by mutableIntStateOf(0)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                JournalScreenContent(
                    uiState = JournalUiState(
                        isLoading = false,
                        today = LocalDate.of(2026, 6, 16),
                        recentNotes = listOf(
                            Note(
                                id = "june-15",
                                date = LocalDate.of(2026, 6, 15),
                                text = "Older note",
                            )
                        ),
                    ),
                    onOpenMilestones = {},
                    onOpenAllNotes = {},
                    onSaveTodayNote = { token++ },
                    onSaveNote = { _, _ -> },
                    noteSaveFailureToken = token,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .performClick()
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("A new day")
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsEnabled()
            .performClick()

        // Draft preserved and the editor re-opened: the prompt is gone, the typed text remains,
        // and Save is available again to retry.
        composeRule.onNodeWithTag(TodayComposerTextFieldTag).assertTextContains("A new day")
        composeRule.onNodeWithText(context.getString(R.string.journal_write_about_today))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save))
            .assertIsDisplayed()
    }
}
