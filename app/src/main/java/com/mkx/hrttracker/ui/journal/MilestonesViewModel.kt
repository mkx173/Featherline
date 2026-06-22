package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
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

    // The most recent tracked dates this ViewModel has observed — the exact snapshot
    // uiState is built from. reconcilePendingAgainstSource() reconciles against this
    // (not the repository's separate warm cache, which can lag or be null and so miss
    // a pin-set change the screen already saw). Main-confined: written in onEach and
    // read from the post-write continuation, both on viewModelScope.
    private var latestTrackedDates: List<TrackedDate>? = null

    // The date-derived state (sort, pinned set, timeline nodes, hero). Kept separate from
    // editMode so that toggling edit mode — a pure UI flag — doesn't re-sort and rebuild the
    // whole node list; only a real dates/today/pendingEdits change recomputes this.
    private val core = combine(
        journalRepository.observeTrackedDates()
            .onEach { all ->
                latestTrackedDates = all
                pendingEdits.update { reconcilePendingEdits(it, all) }
            },
        todayFlow,
        pendingEdits,
    ) { all, today, pending ->
        buildUiState(all, today, pending)
    }

    val uiState: StateFlow<MilestonesUiState> = combine(core, editMode) { state, isEdit ->
        state.copy(isEditMode = isEdit)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Seed the first frame from the repository's warm cache (loaded at app start) so an
        // already-ready database renders real data instead of flashing the loading indicator
        // for the frames until the cold combine lands its first emission. A null cache stays
        // in the loading state. The warm cache is fine for this one-frame seed even though
        // reconciliation deliberately avoids it (the live flow replaces it immediately).
        initialValue = seedUiState(appTimeSource.currentMinute.value.toLocalDate()),
    )

    private fun seedUiState(today: LocalDate): MilestonesUiState {
        val cached = journalRepository.getCachedTrackedDates()
            ?: return MilestonesUiState(today = today)
        return buildUiState(cached, today, PendingEdits())
    }

    // The date-derived UI state (sort, pinned set, hero, timeline). Shared by the live flow
    // and the warm-cache seed so the two can't drift; isEditMode is applied separately.
    private fun buildUiState(
        all: List<TrackedDate>,
        today: LocalDate,
        pending: PendingEdits,
    ): MilestonesUiState {
        val effective = applyPendingEdits(all, pending)
        val sorted = effective.sortedWith(compareBy<TrackedDate> { it.date }.thenBy { it.name })
        val pinned = pinnedSorted(effective)
        val pinnedIds = pinned.map { it.id }.toSet()
        val hero = pinned.firstOrNull()
        val heroRow = hero?.toAnchorRowUiState(today)
        return MilestonesUiState(
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
        )
    }

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
        viewModelScope.launch {
            journalRepository.setHeroBackground(id, background)
            reconcilePendingAgainstSource()
        }
    }

    fun reorderPinned(ids: List<String>) {
        pendingEdits.update { it.copy(pinnedOrder = ids) }
        viewModelScope.launch {
            journalRepository.reorderPinned(ids)
            reconcilePendingAgainstSource()
        }
    }

    fun addDate(
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
        pinned: Boolean,
    ) = viewModelScope.launch {
        journalRepository.addTrackedDate(name, icon, date, paletteKey, pinned)
    }

    fun updateDate(
        id: String,
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
    ) {
        // Convert the storage strings to model values the same way the Room mapper does,
        // so the overlay matches what observeTrackedDates will eventually emit.
        val edit = PendingDateEdit(
            name = name,
            icon = AnchorIcon.fromStorageValue(icon),
            date = date,
            palette = MedicationGroupColorKey.fromStorageValueOrNull(paletteKey),
        )
        pendingEdits.update { it.copy(dateEdits = it.dateEdits + (id to edit)) }
        viewModelScope.launch {
            journalRepository.updateTrackedDate(id, name, icon, date, paletteKey)
            reconcilePendingAgainstSource()
        }
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
        val withFields = if (pending.heroBackground.isEmpty() && pending.dateEdits.isEmpty()) {
            dates
        } else {
            dates.map { date ->
                var edited = date
                pending.heroBackground[date.id]?.let { edited = edited.copy(heroBackground = it) }
                pending.dateEdits[date.id]?.let { e ->
                    edited = edited.copy(name = e.name, icon = e.icon, date = e.date, palette = e.palette)
                }
                edited
            }
        }
        val order = pending.pinnedOrder ?: return withFields
        val pinnedIds = withFields.filter { it.pinnedOrder != null }.map { it.id }
        // Only apply an order that still describes exactly the current pinned set;
        // a pin/unpin landing first makes the override stale, so defer to the source.
        if (order.toSet() != pinnedIds.toSet()) return withFields
        val indexById = order.withIndex().associate { (index, id) -> id to index }
        return withFields.map { date ->
            indexById[date.id]?.let { date.copy(pinnedOrder = it) } ?: date
        }
    }

    // A no-op write (a reorder the repository rejected, a background save for a
    // since-deleted id) doesn't re-emit, so the onEach reconcile above never runs for
    // it. Reconcile against the last observed snapshot once the write settles so a stale
    // override can't linger and later resurrect over the real source order.
    private fun reconcilePendingAgainstSource() {
        val source = latestTrackedDates ?: return
        pendingEdits.update { reconcilePendingEdits(it, source) }
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
        val remainingDateEdits = pending.dateEdits.filterNot { (id, edit) ->
            val date = byId[id]
            date == null || (
                date.name == edit.name &&
                    date.icon == edit.icon &&
                    date.date == edit.date &&
                    date.palette == edit.palette
                )
        }
        val sourcePinnedOrder = pinnedSorted(source).map { it.id }
        val remainingOrder = pending.pinnedOrder?.takeUnless { order ->
            order == sourcePinnedOrder || order.toSet() != sourcePinnedOrder.toSet()
        }
        return if (
            remainingBackground == pending.heroBackground &&
            remainingDateEdits == pending.dateEdits &&
            remainingOrder == pending.pinnedOrder
        ) {
            pending
        } else {
            PendingEdits(
                pinnedOrder = remainingOrder,
                heroBackground = remainingBackground,
                dateEdits = remainingDateEdits,
            )
        }
    }

    private data class PendingEdits(
        val pinnedOrder: List<String>? = null,
        val heroBackground: Map<String, HeroBackground> = emptyMap(),
        val dateEdits: Map<String, PendingDateEdit> = emptyMap(),
    )

    private data class PendingDateEdit(
        val name: String,
        val icon: AnchorIcon,
        val date: LocalDate,
        val palette: MedicationGroupColorKey?,
    )
}
