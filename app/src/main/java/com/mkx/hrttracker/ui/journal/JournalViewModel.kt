package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val appTimeSource: AppTimeSource,
) : ViewModel() {
    private fun today(): LocalDate = appTimeSource.currentMinute.value.toLocalDate()

    val uiState: StateFlow<JournalUiState> = run {
        val initialToday = today()
        val todayFlow: Flow<LocalDate> = appTimeSource.currentMinute
            .map { it.toLocalDate() }
            .distinctUntilChanged()
        combine(
            journalRepository.observeTrackedDates(),
            journalRepository.observePinnedTrackedDates(),
            todayFlow.flatMapLatest { today ->
                val windowStart = today.minusDays(29)
                combine(
                    journalRepository.observeNotesOnOrAfter(windowStart),
                    journalRepository.observeNoteForDate(today),
                    journalRepository.observeNotesCountBefore(windowStart),
                ) { recent, todayNote, olderNotesCount ->
                    JournalDateUiState(
                        today = today,
                        recentNotes = recent,
                        todayNote = todayNote,
                        olderNotesCount = olderNotesCount,
                    )
                }
            },
        ) { trackedDates, pinned, dateState ->
            JournalUiState(
                isLoading = false,
                today = dateState.today,
                hasTrackedDates = trackedDates.isNotEmpty(),
                pinnedAnchors = pinned.map { it.toAnchorRowUiState(dateState.today) },
                recentNotes = dateState.recentNotes,
                todayNote = dateState.todayNote,
                olderNotesCount = dateState.olderNotesCount,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = JournalUiState(today = initialToday),
        )
    }

    fun saveTodayNote(text: String) = viewModelScope.launch {
        journalRepository.saveNoteForDate(today(), text)
    }

    fun saveNote(date: LocalDate, text: String) = viewModelScope.launch {
        journalRepository.saveNoteForDate(date, text)
    }
}

private data class JournalDateUiState(
    val today: LocalDate,
    val recentNotes: List<Note>,
    val todayNote: Note?,
    val olderNotesCount: Int,
)
