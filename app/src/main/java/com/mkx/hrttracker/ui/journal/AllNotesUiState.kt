package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.model.journal.Note
import java.time.LocalDate
import java.time.YearMonth

data class MonthGroupUiState(
    val month: YearMonth,
    val notes: List<Note>,
)

data class AllNotesUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val monthGroups: List<MonthGroupUiState> = emptyList(),
    val noteMutationError: JournalNoteMutation? = null,
    val noteSaveFailureToken: Int = 0,
    val selectedDates: Set<LocalDate> = emptySet(),
    val isDeletingSelected: Boolean = false,
    // Non-null right after a successful bulk delete, carrying the count for the success Toast
    // (mirrors History's deleteSelectedEntriesResult). Consumed back to null once the Toast fires.
    val deleteSelectedSuccessCount: Int? = null,
) {
    val isSelectionMode: Boolean get() = selectedDates.isNotEmpty()
}

internal fun toggleNoteSelection(current: Set<LocalDate>, date: LocalDate): Set<LocalDate> =
    if (date in current) current - date else current + date

internal fun selectAllNoteDates(current: Set<LocalDate>, dates: Set<LocalDate>): Set<LocalDate> =
    current + dates
