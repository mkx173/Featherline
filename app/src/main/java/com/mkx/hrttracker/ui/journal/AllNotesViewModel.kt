package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class AllNotesViewModel @Inject constructor(
    journalRepository: JournalRepository,
) : ViewModel() {
    val uiState: StateFlow<AllNotesUiState> = journalRepository
        .observeNotesOnOrAfter(LocalDate.MIN)
        .map { notes ->
            AllNotesUiState(
                isLoading = false,
                monthGroups = notes
                    .sortedByDescending { it.date }
                    .groupBy { YearMonth.from(it.date) }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (month, monthNotes) ->
                        MonthGroupUiState(month = month, notes = monthNotes)
                    },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AllNotesUiState(),
        )
}
