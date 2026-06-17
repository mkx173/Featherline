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
)
