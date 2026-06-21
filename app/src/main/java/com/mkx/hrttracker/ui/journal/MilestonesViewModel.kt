package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MilestonesViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val editMode = MutableStateFlow(false)
    private val todayFlow = appTimeSource.currentMinute
        .map { it.toLocalDate() }
        .distinctUntilChanged()

    // The Room flow re-emits only after a write commits. Reflecting reorders and
    // hero-background saves through this overlay makes them appear instantly, so the
    // tray never waits on Room latency — which otherwise lets a quick "finish" snap
    // the reorder back, or delays the saved background. Entries are dropped once the
    // source catches up (see reconcilePendingEdits).
    private val pendingEdits = MutableStateFlow(PendingEdits())

    val uiState: StateFlow<MilestonesUiState> = combine(
        journalRepository.observeTrackedDates()
            .onEach { all -> pendingEdits.update { reconcilePendingEdits(it, all) } },
        editMode,
        todayFlow,
        pendingEdits,
    ) { all, isEdit, today, pending ->
        val effective = applyPendingEdits(all, pending)
        val sorted = effective.sortedWith(compareBy<TrackedDate> { it.date }.thenBy { it.name })
        val pinned = pinnedSorted(effective)
        val pinnedIds = pinned.map { it.id }.toSet()
        val hero = pinned.firstOrNull()
        val heroRow = hero?.toAnchorRowUiState(today)
        MilestonesUiState(
            isLoading = false,
            today = today,
            hero = heroRow,
            heroNextMilestone = hero?.let { nextMilestoneUiState(it.date, today) },
            pinnedTray = pinned.map { it.toAnchorRowUiState(today) },
            timeline = sorted.map {
                TimelineNodeUiState(
                    anchor = it.toAnchorRowUiState(today),
                    isPinned = it.id in pinnedIds,
                )
            },
            todayDividerIndex = sorted.count { it.date.isBefore(today) },
            isEditMode = isEdit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MilestonesUiState(today = appTimeSource.currentMinute.value.toLocalDate()),
    )

    fun toggleEditMode() {
        editMode.value = !editMode.value
    }

    fun exitEditMode() {
        editMode.value = false
    }

    fun setPinned(id: String, pinned: Boolean) = viewModelScope.launch {
        journalRepository.setPinned(id, pinned)
    }

    fun setHeroBackground(id: String, background: HeroBackground) {
        pendingEdits.update { it.copy(heroBackground = it.heroBackground + (id to background)) }
        viewModelScope.launch { journalRepository.setHeroBackground(id, background) }
    }

    fun reorderPinned(ids: List<String>) {
        pendingEdits.update { it.copy(pinnedOrder = ids) }
        viewModelScope.launch { journalRepository.reorderPinned(ids) }
    }

    fun addDate(
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
    ) = viewModelScope.launch {
        journalRepository.addTrackedDate(name, icon, date, paletteKey)
    }

    fun updateDate(
        id: String,
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
    ) = viewModelScope.launch {
        journalRepository.updateTrackedDate(id, name, icon, date, paletteKey)
    }

    fun deleteDate(id: String) = viewModelScope.launch {
        journalRepository.deleteTrackedDate(id)
    }

    private fun pinnedSorted(dates: List<TrackedDate>): List<TrackedDate> =
        dates
            .filter { it.pinnedOrder != null }
            .sortedWith(
                compareBy<TrackedDate> { it.pinnedOrder ?: Int.MAX_VALUE }
                    .thenBy { it.date }
                    .thenBy { it.createdAtEpochMillis }
                    .thenBy { it.id }
            )

    private fun applyPendingEdits(
        dates: List<TrackedDate>,
        pending: PendingEdits,
    ): List<TrackedDate> {
        val withBackground = if (pending.heroBackground.isEmpty()) {
            dates
        } else {
            dates.map { date ->
                pending.heroBackground[date.id]?.let { date.copy(heroBackground = it) } ?: date
            }
        }
        val order = pending.pinnedOrder ?: return withBackground
        val pinnedIds = withBackground.filter { it.pinnedOrder != null }.map { it.id }
        // Only apply an order that still describes exactly the current pinned set;
        // a pin/unpin landing first makes the override stale, so defer to the source.
        if (order.toSet() != pinnedIds.toSet()) return withBackground
        val indexById = order.withIndex().associate { (index, id) -> id to index }
        return withBackground.map { date ->
            indexById[date.id]?.let { date.copy(pinnedOrder = it) } ?: date
        }
    }

    // Drop overrides the source already reflects (so a later independent change
    // wins) and ones it can no longer apply (the pinned set changed underneath).
    private fun reconcilePendingEdits(
        pending: PendingEdits,
        source: List<TrackedDate>,
    ): PendingEdits {
        val byId = source.associateBy { it.id }
        val remainingBackground = pending.heroBackground.filterNot { (id, background) ->
            val date = byId[id]
            date == null || date.heroBackground == background
        }
        val sourcePinnedOrder = pinnedSorted(source).map { it.id }
        val remainingOrder = pending.pinnedOrder?.takeUnless { order ->
            order == sourcePinnedOrder || order.toSet() != sourcePinnedOrder.toSet()
        }
        return if (
            remainingBackground == pending.heroBackground &&
            remainingOrder == pending.pinnedOrder
        ) {
            pending
        } else {
            PendingEdits(pinnedOrder = remainingOrder, heroBackground = remainingBackground)
        }
    }

    private data class PendingEdits(
        val pinnedOrder: List<String>? = null,
        val heroBackground: Map<String, HeroBackground> = emptyMap(),
    )
}
