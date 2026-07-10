package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
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

    @Test
    fun monthFilter_narrowsToSelectedMonthAndClears() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(note("june-15", LocalDate.of(2026, 6, 15), "June note")),
                            ),
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 5),
                                notes = listOf(note("may-20", LocalDate.of(2026, 5, 20), "May note")),
                            ),
                        ),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                )
            }
        }

        // Both months visible before filtering.
        composeRule.onNodeWithText("June note").assertIsDisplayed()
        composeRule.onNodeWithText("May note").assertIsDisplayed()

        // Open the picker and confirm; the default selection is the most recent month (June).
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_filter_by_month))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_filter_by_month))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.confirm)).performClick()

        // List is narrowed to June; the May note is filtered out.
        composeRule.onNodeWithText("June note").assertIsDisplayed()
        composeRule.onNodeWithText("May note").assertDoesNotExist()

        // The action flips to "show all"; clearing restores every month.
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_clear_month_filter))
            .performClick()
        composeRule.onNodeWithText("May note").assertIsDisplayed()
    }

    @Test
    fun longPress_entersSelectionMode_showsCountAndCloseAndIndicator() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstDate = LocalDate.of(2026, 6, 15)
        val secondDate = LocalDate.of(2026, 6, 1)
        var enteredDate: LocalDate? = null

        val groups = listOf(
            MonthGroupUiState(
                month = YearMonth.of(2026, 6),
                notes = listOf(
                    note("june-15", firstDate, "June note"),
                    note("june-1", secondDate, "Earlier June"),
                ),
            ),
        )
        // Drive the stateless content from a holder so we can long-press in normal mode, then
        // re-render in selection mode (as the VM would) to assert the chrome.
        val uiState = mutableStateOf(AllNotesUiState(isLoading = false, monthGroups = groups))

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = uiState.value,
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                    onEnterSelection = { enteredDate = it },
                )
            }
        }

        composeRule.onNodeWithTag(rowTag("june-15")).performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(firstDate, enteredDate)
        }

        composeRule.runOnUiThread {
            uiState.value = AllNotesUiState(
                isLoading = false,
                monthGroups = groups,
                selectedDates = setOf(firstDate),
            )
        }

        val title = context.resources.getQuantityString(R.plurals.journal_selected_notes_title, 1, 1)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_cancel_selection))
            .assertIsDisplayed()
        // The selected row shows the "selected" indicator; the unselected row shows the "select" one.
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_note_selected))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_select_note))
            .assertIsDisplayed()
    }

    @Test
    fun tapInSelectionMode_togglesSelection() {
        val firstDate = LocalDate.of(2026, 6, 15)
        val secondDate = LocalDate.of(2026, 6, 1)
        val toggledDates = mutableListOf<LocalDate>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(
                                    note("june-15", firstDate, "June note"),
                                    note("june-1", secondDate, "Earlier June"),
                                ),
                            ),
                        ),
                        selectedDates = setOf(firstDate),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                    onToggleSelection = { toggledDates += it },
                )
            }
        }

        // Tapping the unselected row toggles its date on; tapping the selected row toggles it off.
        composeRule.onNodeWithTag(rowTag("june-1")).performClick()
        composeRule.onNodeWithTag(rowTag("june-15")).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(secondDate, firstDate), toggledDates)
        }
    }

    @Test
    fun selectAll_invokesOnSelectAllWithDisplayedDates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstDate = LocalDate.of(2026, 6, 15)
        val secondDate = LocalDate.of(2026, 6, 1)
        var selectedAll: Set<LocalDate>? = null

        // One of two notes selected, so select-all is enabled and should report every displayed date.
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(
                                    note("june-15", firstDate, "June note"),
                                    note("june-1", secondDate, "Earlier June"),
                                ),
                            ),
                        ),
                        selectedDates = setOf(firstDate),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                    onSelectAll = { selectedAll = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.journal_select_all))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(firstDate, secondDate), selectedAll)
        }
    }

    @Test
    fun deleteFab_opensConfirmation_thenConfirmInvokesOnDeleteSelected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstDate = LocalDate.of(2026, 6, 15)
        var deleteSelectedCount = 0

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(note("june-15", firstDate, "June note")),
                            ),
                        ),
                        selectedDates = setOf(firstDate),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                    onDeleteSelected = { deleteSelectedCount++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.journal_delete_selected_notes_fab)
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_delete_selected_notes_title))
            .assertIsDisplayed()
        // The dialog only requests the delete on confirm.
        composeRule.runOnIdle {
            assertEquals(0, deleteSelectedCount)
        }

        composeRule.onNodeWithText(context.getString(R.string.delete_entries_confirm))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, deleteSelectedCount)
        }
    }

    @Test
    fun systemBackInSelectionMode_invokesOnCancelSelection() {
        val firstDate = LocalDate.of(2026, 6, 15)
        var cancelled = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(note("june-15", firstDate, "June note")),
                            ),
                        ),
                        selectedDates = setOf(firstDate),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                    onCancelSelection = { cancelled = true },
                )
            }
        }

        Espresso.pressBack()
        composeRule.runOnIdle {
            assertTrue(cancelled)
        }
    }

    @Test
    fun leavingScreenContent_invokesOnCancelSelectionAfterDisposal() {
        val firstDate = LocalDate.of(2026, 6, 15)
        val showContent = mutableStateOf(true)
        var cancelled = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                if (showContent.value) {
                    AllNotesScreenContent(
                        uiState = AllNotesUiState(
                            isLoading = false,
                            monthGroups = listOf(
                                MonthGroupUiState(
                                    month = YearMonth.of(2026, 6),
                                    notes = listOf(note("june-15", firstDate, "June note")),
                                ),
                            ),
                            selectedDates = setOf(firstDate),
                        ),
                        onNavigateBack = { },
                        onSaveNote = { _, _ -> },
                        onDeleteNote = { },
                        onCancelSelection = { cancelled = true },
                    )
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(false, cancelled)
            showContent.value = false
        }
        composeRule.runOnIdle {
            assertTrue(cancelled)
        }
    }

    @Test
    fun tapPastNoteText_beginsEditingWithCursorAtTap() {
        val noteDate = LocalDate.of(2026, 6, 15)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AllNotesScreenContent(
                    uiState = AllNotesUiState(
                        isLoading = false,
                        monthGroups = listOf(
                            MonthGroupUiState(
                                month = YearMonth.of(2026, 6),
                                notes = listOf(note("june-15", noteDate, "Hello")),
                            ),
                        ),
                    ),
                    onNavigateBack = { },
                    onSaveNote = { _, _ -> },
                    onDeleteNote = { },
                )
            }
        }

        // Tapping past the end of the note text must begin editing with the cursor at the tapped
        // character (here: the end), so a typed character APPENDS. The old fixed-offset-0 behaviour
        // would prepend ("!Hello"), so this asserts the cursor follows the tap rather than resetting.
        composeRule.onNodeWithText("Hello").performTouchInput { click(centerRight) }
        composeRule.onNodeWithTag(fieldTag("june-15")).performTextInput("!")
        composeRule.onNodeWithTag(fieldTag("june-15")).assertTextContains("Hello!")
    }

    private fun rowTag(noteId: String): String = "note-timeline-row-$noteId"

    private fun fieldTag(noteId: String): String = "note-timeline-text-field-$noteId"

    private fun monthLabel(month: YearMonth, locale: java.util.Locale): String {
        return monthHeaderFormatter(locale, currentYear = Int.MIN_VALUE)(month.atDay(1))
    }

    private fun note(id: String, date: LocalDate, text: String): Note {
        return Note(id = id, date = date, text = text)
    }
}
