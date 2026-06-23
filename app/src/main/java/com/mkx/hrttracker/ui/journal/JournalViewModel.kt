package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val appTimeSource: AppTimeSource,
) : ViewModel() {
    private fun today(): LocalDate = appTimeSource.currentMinute.value.toLocalDate()

    private val noteMutationError = MutableStateFlow<JournalNoteMutation?>(null)
    private val noteSaveFailureToken = MutableStateFlow(0)

    val uiState: StateFlow<JournalUiState> = run {
        val initialToday = today()
        val todayFlow: Flow<LocalDate> = appTimeSource.currentMinute
            .map { it.toLocalDate() }
            .distinctUntilChanged()
        val core = combine(
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
        }
        combine(core, noteMutationError, noteSaveFailureToken) { state, error, token ->
            state.copy(noteMutationError = error, noteSaveFailureToken = token)
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
        runCatching { withContext(NonCancellable) { journalRepository.saveNoteForDate(today(), text) } }
            .onFailure { failNoteWrite(it, JournalNoteMutation.SAVE) }
    }

    fun saveNote(date: LocalDate, text: String) = viewModelScope.launch {
        runCatching { withContext(NonCancellable) { journalRepository.saveNoteForDate(date, text) } }
            .onFailure { failNoteWrite(it, JournalNoteMutation.SAVE) }
    }

    fun deleteTodayNote() = viewModelScope.launch {
        runCatching { withContext(NonCancellable) { journalRepository.deleteNoteForDate(today()) } }
            .onFailure { failNoteWrite(it, JournalNoteMutation.DELETE) }
    }

    fun deleteNote(date: LocalDate) = viewModelScope.launch {
        runCatching { withContext(NonCancellable) { journalRepository.deleteNoteForDate(date) } }
            .onFailure { failNoteWrite(it, JournalNoteMutation.DELETE) }
    }

    fun consumeNoteMutationError() {
        noteMutationError.value = null
    }

    // A confirmed note write either persists or surfaces a one-shot error event; the repository
    // keeps throwing (read flows recover separately). CancellationException is structural and must
    // propagate. A failed SAVE also bumps a monotonic token the composer watches to recover its
    // unsaved draft.
    private fun failNoteWrite(error: Throwable, mutation: JournalNoteMutation) {
        if (error is CancellationException) throw error
        noteMutationError.value = mutation
        if (mutation == JournalNoteMutation.SAVE) {
            noteSaveFailureToken.value += 1
        }
    }

    // Debug-only: seed date-stamped sample notes across the recent window, the previous few
    // months, and prior years, so the notes timeline, the "see all earlier notes" count, and the
    // All Notes month grouping can be exercised without hand-entering data. Reachable only from a
    // BuildConfig.DEBUG-gated long-press in the journal notes header; guarded here too so it stays
    // inert in release. Re-running overwrites the same dates (saveNoteForDate upserts).
    fun addDebugSampleNotes() = viewModelScope.launch {
        if (!BuildConfig.DEBUG) return@launch
        val today = today()
        val dates = buildList {
            listOf(1L, 3L, 8L, 15L, 27L).forEach { add(today.minusDays(it)) }
            (2L..7L).forEach { add(today.minusMonths(it).withDayOfMonth(15)) }
            (1..3).forEach { add(LocalDate.of(today.year - it, 6, 15)) }
        }
        runCatching {
            withContext(NonCancellable) {
                dates.forEach { date ->
                    journalRepository.saveNoteForDate(date, "Sample note for $date")
                }
            }
        }.onFailure { if (it is CancellationException) throw it }
    }
}

private data class JournalDateUiState(
    val today: LocalDate,
    val recentNotes: List<Note>,
    val todayNote: Note?,
    val olderNotesCount: Int,
)
