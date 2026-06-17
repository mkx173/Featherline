package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class AllNotesViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val todayFlow = appTimeSource.currentMinute
        .map { it.toLocalDate() }
        .distinctUntilChanged()

    val uiState: StateFlow<AllNotesUiState> = combine(
        journalRepository.observeNotesOnOrAfter(LocalDate.MIN),
        todayFlow,
    ) { notes, today ->
        AllNotesUiState(
            isLoading = false,
            today = today,
            monthGroups = notes
                .sortedByDescending { it.date }
                .groupBy { YearMonth.from(it.date) }
                .entries
                .sortedByDescending { it.key }
                .map { (month, monthNotes) ->
                    MonthGroupUiState(month = month, notes = monthNotes)
                },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AllNotesUiState(today = appTimeSource.currentMinute.value.toLocalDate()),
    )

    fun saveNote(date: LocalDate, text: String) = viewModelScope.launch {
        journalRepository.saveNoteForDate(date, text)
    }
}
