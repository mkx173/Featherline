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
import kotlinx.coroutines.flow.update
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

    private val selectedDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val isDeletingSelected = MutableStateFlow(false)
    private val deleteSelectedSuccessCount = MutableStateFlow<Int?>(null)

    // The three selection/delete flows are grouped into one inner combine so the outer combine
    // stays within Kotlin's typed `combine` overload limit (max 5 sources) alongside the
    // notes+today and error/token flows.
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
        combine(selectedDates, isDeletingSelected, deleteSelectedSuccessCount) { selected, deletingSelected, successCount ->
            NoteSelectionUiState(selected, deletingSelected, successCount)
        },
    ) { state, error, token, selection ->
        // Reconcile the selection against the notes actually present (mirrors History's
        // visibleSelection). A selected date whose note disappeared while away — the VM is
        // activity-scoped, so the selection outlives navigating off this screen and deleting the
        // note elsewhere — is dropped, so the count never inflates and selection mode can't strand
        // on a row that no longer exists.
        val existingDates = state.monthGroups.flatMapTo(mutableSetOf()) { group ->
            group.notes.map { it.date }
        }
        state.copy(
            noteMutationError = error,
            noteSaveFailureToken = token,
            selectedDates = selection.selectedDates intersect existingDates,
            isDeletingSelected = selection.isDeletingSelected,
            deleteSelectedSuccessCount = selection.deleteSelectedSuccessCount,
        )
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

    fun toggleSelection(date: LocalDate) {
        selectedDates.update { toggleNoteSelection(it, date) }
    }

    fun selectDates(dates: Set<LocalDate>) {
        selectedDates.update { selectAllNoteDates(it, dates) }
    }

    fun clearSelection() {
        selectedDates.value = emptySet()
    }

    fun deleteSelectedNotes() = viewModelScope.launch {
        val snapshot = selectedDates.value
        if (snapshot.isEmpty() || isDeletingSelected.value) return@launch
        isDeletingSelected.value = true
        try {
            runCatching { withContext(NonCancellable) { journalRepository.deleteNotesForDates(snapshot) } }
                .onSuccess {
                    selectedDates.value = emptySet()
                    deleteSelectedSuccessCount.value = snapshot.size
                }
                .onFailure { failNoteWrite(it, JournalNoteMutation.DELETE) }
        } finally {
            isDeletingSelected.value = false
        }
    }

    fun consumeNoteMutationError() {
        noteMutationError.value = null
    }

    fun consumeDeleteSelectedSuccess() {
        deleteSelectedSuccessCount.value = null
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

// Groups the three selection/delete flows so the outer uiState combine stays within the typed
// `combine` arity limit (mirrors History's private HistoryDeletionUiState grouping).
private data class NoteSelectionUiState(
    val selectedDates: Set<LocalDate>,
    val isDeletingSelected: Boolean,
    val deleteSelectedSuccessCount: Int?,
)
