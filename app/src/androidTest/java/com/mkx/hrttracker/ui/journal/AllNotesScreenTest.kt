package com.mkx.hrttracker.ui.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.monthHeaderFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AllNotesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersMonthSectionsAndBackNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        var navigatedBack = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(
                                    note("june-15", LocalDate.of(2026, 6, 15), "June note"),
                                    note("june-1", LocalDate.of(2026, 6, 1), "Earlier June"),
                                ),
                            ),
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 5),
                                notes = listOf(
                                    note("may-20", LocalDate.of(2026, 5, 20), "May note"),
                                ),
                            ),
                        ),
                    ),
                    onNavigateBack = { navigatedBack = true },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                )
            }
        }

        val title = context.getString(R.string.journal_all_notes)
        val june = monthLabel(YearMonth.of(2026, 6), locale).uppercase(locale)
        val may = monthLabel(YearMonth.of(2026, 5), locale).uppercase(locale)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(june).assertIsDisplayed()
        composeRule.onNodeWithText(may).assertIsDisplayed()
        composeRule.onAllNodesWithText("2").assertCountEquals(1)
        composeRule.onAllNodesWithText("1").assertCountEquals(1)
        composeRule.onNodeWithText("June note").assertIsDisplayed()
        composeRule.onNodeWithText("Earlier June").assertIsDisplayed()
        composeRule.onNodeWithText("May note").assertIsDisplayed()

        val juneTop = composeRule.onNodeWithText(june, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val juneNoteTop = composeRule.onNodeWithText("June note", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val mayTop = composeRule.onNodeWithText(may, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(juneTop < juneNoteTop)
        assertTrue(juneNoteTop < mayTop)

        composeRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(navigatedBack)
        }
    }

    @Test
    fun timelineRow_deleteRequiresConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deletedDates = mutableListOf<LocalDate>()
        val noteDate = LocalDate.of(2026, 6, 15)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(note("june-15", noteDate, "June note")),
                            )
                        ),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { deletedDates += it },
                )
            }
        }

        composeRule.onNodeWithText("June note").performClick()
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
            assertEquals(listOf(noteDate), deletedDates)
        }
    }

    private fun monthLabel(month: YearMonth, locale: java.util.Locale): String {
        return monthHeaderFormatter(locale, currentYear = Int.MIN_VALUE)(month.atDay(1))
    }

    private fun note(id: String, date: LocalDate, text: String): Note {
        return Note(id = id, date = date, text = text)
    }
}
