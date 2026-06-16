package com.mkx.hrttracker.ui.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private const val TodayComposerTextFieldTag = "today-composer-text-field"

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

        composeRule.onNodeWithText(context.getString(R.string.journal_no_dates)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.journal_add_date))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("${context.getString(R.string.journal_see_all_notes)} · 2 earlier")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(milestonesOpened)
            assertTrue(allNotesOpened)
        }
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
        composeRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()

        composeRule.onNodeWithTag(TodayComposerTextFieldTag).performTextInput("A new day")
        composeRule.onNodeWithText(context.getString(R.string.save))
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
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

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
}
