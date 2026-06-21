package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.TrackedDate
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
                heroNextMilestone = pinned.firstOrNull()?.let {
                    nextMilestoneUiState(it.date, dateState.today)
                },
                recentNotes = dateState.recentNotes,
                todayNote = dateState.todayNote,
                olderNotesCount = dateState.olderNotesCount,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // Seed the first frame from the repository's warm caches (loaded at
            // app start) so an already-ready database renders real data instead
            // of flashing the loading indicator for the frames until the cold
            // combine above lands its first emission.
            initialValue = buildSeedUiState(
                trackedDatesOrNull = journalRepository.getCachedTrackedDates(),
                pinnedOrNull = journalRepository.getCachedPinnedTrackedDates(),
                notesOrNull = journalRepository.getCachedNotes(),
                today = initialToday,
            ),
        )
    }

    // Mirrors the live combine's mapping, but derives the date-windowed note
    // values in memory from the full cached note list. A null cache means that
    // source has not loaded yet, so the seed stays in the loading state.
    private fun buildSeedUiState(
        trackedDatesOrNull: List<TrackedDate>?,
        pinnedOrNull: List<TrackedDate>?,
        notesOrNull: List<Note>?,
        today: LocalDate,
    ): JournalUiState {
        if (trackedDatesOrNull == null || pinnedOrNull == null || notesOrNull == null) {
            return JournalUiState(today = today)
        }
        val windowStart = today.minusDays(29)
        return JournalUiState(
            isLoading = false,
            today = today,
            hasTrackedDates = trackedDatesOrNull.isNotEmpty(),
            pinnedAnchors = pinnedOrNull.map { it.toAnchorRowUiState(today) },
            heroNextMilestone = pinnedOrNull.firstOrNull()?.let {
                nextMilestoneUiState(it.date, today)
            },
            recentNotes = notesOrNull.filter { !it.date.isBefore(windowStart) },
            todayNote = notesOrNull.firstOrNull { it.date == today },
            olderNotesCount = notesOrNull.count { it.date.isBefore(windowStart) },
        )
    }

    fun saveTodayNote(text: String) = viewModelScope.launch {
        journalRepository.saveNoteForDate(today(), text)
    }

    fun saveNote(date: LocalDate, text: String) = viewModelScope.launch {
        journalRepository.saveNoteForDate(date, text)
    }

    fun deleteTodayNote() = viewModelScope.launch {
        journalRepository.deleteNoteForDate(today())
    }

    fun deleteNote(date: LocalDate) = viewModelScope.launch {
        journalRepository.deleteNoteForDate(date)
    }
}

private data class JournalDateUiState(
    val today: LocalDate,
    val recentNotes: List<Note>,
    val todayNote: Note?,
    val olderNotesCount: Int,
)
