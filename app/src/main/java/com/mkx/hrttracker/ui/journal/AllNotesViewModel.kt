package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val noteMutationError = MutableStateFlow<JournalNoteMutation?>(null)
    private val noteSaveFailureToken = MutableStateFlow(0)

    val uiState: StateFlow<AllNotesUiState> = combine(
        combine(
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
        },
        noteMutationError,
        noteSaveFailureToken,
    ) { state, error, token ->
        state.copy(noteMutationError = error, noteSaveFailureToken = token)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AllNotesUiState(today = appTimeSource.currentMinute.value.toLocalDate()),
    )

    fun saveNote(date: LocalDate, text: String) = viewModelScope.launch {
        runCatching { withContext(NonCancellable) { journalRepository.saveNoteForDate(date, text) } }
            .onFailure { failNoteWrite(it, JournalNoteMutation.SAVE) }
    }

    fun deleteNote(date: LocalDate) = viewModelScope.launch {
        runCatching { withContext(NonCancellable) { journalRepository.deleteNoteForDate(date) } }
            .onFailure { failNoteWrite(it, JournalNoteMutation.DELETE) }
    }

    fun consumeNoteMutationError() {
        noteMutationError.value = null
    }

    private fun failNoteWrite(error: Throwable, mutation: JournalNoteMutation) {
        if (error is CancellationException) throw error
        if (error !is Exception) throw error
        noteMutationError.value = mutation
        if (mutation == JournalNoteMutation.SAVE) {
            noteSaveFailureToken.value += 1
        }
    }
}
