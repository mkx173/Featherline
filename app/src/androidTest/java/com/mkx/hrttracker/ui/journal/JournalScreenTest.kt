package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.dateLabelFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private const val TodayComposerTextFieldTag = "today-composer-text-field"
private const val NoteTimelineTextFieldTagPrefix = "note-timeline-text-field-"
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
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.journal_see_all_notes_earlier, 2, 2)
        )
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsNotEnabled()

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
            context.getString(R.string.journal_today).uppercase(appLocale)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Today timeline note").assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-16").assertIsNotDisplayed()
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsNotEnabled()
        composeRule.onNodeWithTag("${NoteTimelineTextFieldTagPrefix}june-15").performTextClearance()
        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()

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
}
