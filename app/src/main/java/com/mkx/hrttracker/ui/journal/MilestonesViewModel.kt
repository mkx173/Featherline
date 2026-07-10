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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MilestonesViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val editMode = MutableStateFlow(false)
    private val dateMutationError = MutableStateFlow<MilestoneMutation?>(null)
    private val todayFlow = appTimeSource.currentMinute
        .map { it.toLocalDate() }
        .distinctUntilChanged()

    // The Room flow re-emits only after a write commits. Reflecting every tracked-date
    // mutation — pin/unpin, reorder, hero-background, and field edits — through this
    // overlay makes them appear instantly, so the tray never waits on Room latency,
    // which otherwise lets a quick "finish" snap a reorder back, delays a saved
    // background, or makes a pin toggle lag the rename it was applied with. Entries are
    // dropped once the source catches up (see reconcilePendingEdits).
    private val pendingEdits = MutableStateFlow(PendingEdits())

    // The most recent tracked dates this ViewModel has observed — the exact snapshot
    // uiState is built from. reconcilePendingAgainstSource() reconciles against this
    // (not the repository's separate warm cache, which can lag or be null and so miss
    // a pin-set change the screen already saw). Main-confined: written in onEach and
    // read by the combine transform and the post-write continuation, all on
    // viewModelScope.
    private var latestTrackedDates: List<TrackedDate>? = null

    // The date-derived state (sort, pinned set, timeline nodes, hero). Kept separate from
    // editMode so that toggling edit mode — a pure UI flag — doesn't re-sort and rebuild the
    // whole node list; only a real dates/today/pendingEdits change recomputes this.
    private val core = combine(
        // The snapshot-seeded flow renders last-known anchors at cold start before the
        // database opens, and never emits the raw flow's fake empty list from the
        // not-loaded window (which flashed a false "empty journal" frame).
        journalRepository.observeTrackedDatesWithSnapshotSeed()
            .onEach { all ->
                latestTrackedDates = all
                pendingEdits.update { reconcilePendingEdits(it, all) }
            },
        todayFlow,
        pendingEdits,
    ) { all, today, pending ->
        // combine gives no ordering guarantee between its inputs: when onEach above
        // retires a pending edit, the cleared pendingEdits can reach this transform
        // before the source list that justified the clearing, building one frame from
        // (stale list, no overlay) — the pre-edit state (the hero-watermark blink).
        // latestTrackedDates and pendingEdits are only ever updated together (both
        // main-confined), so building from latestTrackedDates keeps the pair
        // consistent; `all` then just drives collection and first-emission gating.
        buildUiState(latestTrackedDates ?: all, today, pending)
    }

    val uiState: StateFlow<MilestonesUiState> = combine(core, editMode, dateMutationError) { state, isEdit, error ->
        state.copy(isEditMode = isEdit, dateMutationError = error)
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
        // Warm cache first (live truth), then the deep-link-preloaded anchor snapshot: at
        // cold start the cache is null, and without the snapshot fallback the loading
        // indicator composes for the frames until the async seeded flow lands.
        val cached = journalRepository.getCachedTrackedDates()
            ?: journalRepository.getPreloadedAnchorSnapshot()
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
        // Same-day dates tie-break by creation time (then id), matching pinnedSorted and
        // JournalRepository — not by name, which scrambled the order users added them in.
        val sorted = effective.sortedWith(
            compareBy<TrackedDate> { it.date }
                .thenBy { it.createdAtEpochMillis }
                .thenBy { it.id }
        )
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
            anchors = sorted,
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

    fun setPinned(id: String, pinned: Boolean) {
        pendingEdits.update { it.copy(pinnedState = it.pinnedState + (id to pinned)) }
        viewModelScope.launch {
            runCatching { withContext(NonCancellable) { journalRepository.setPinned(id, pinned) } }
                .onSuccess { reconcilePendingAgainstSource() }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (it !is Exception) throw it
                    // Roll back the optimistic pin so the tray snaps back to the source of truth.
                    pendingEdits.update { e -> e.copy(pinnedState = e.pinnedState - id) }
                    dateMutationError.value = MilestoneMutation.SAVE
                }
        }
    }

    fun setHeroBackground(id: String, background: HeroBackground) {
        pendingEdits.update { it.copy(heroBackground = it.heroBackground + (id to background)) }
        viewModelScope.launch {
            runCatching { withContext(NonCancellable) { journalRepository.setHeroBackground(id, background) } }
                .onSuccess { reconcilePendingAgainstSource() }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (it !is Exception) throw it
                    pendingEdits.update { e -> e.copy(heroBackground = e.heroBackground - id) }
                    dateMutationError.value = MilestoneMutation.SAVE
                }
        }
    }

    fun reorderPinned(ids: List<String>) {
        pendingEdits.update { it.copy(pinnedOrder = ids) }
        viewModelScope.launch {
            runCatching { withContext(NonCancellable) { journalRepository.reorderPinned(ids) } }
                .onSuccess { reconcilePendingAgainstSource() }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (it !is Exception) throw it
                    // Coarse rollback: clears ANY pending order, not just this one. Acceptable because
                    // user actions serialize and Room latency is short (design §3 edge case).
                    pendingEdits.update { e -> e.copy(pinnedOrder = null) }
                    dateMutationError.value = MilestoneMutation.SAVE
                }
        }
    }

    fun addDate(
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
        pinned: Boolean,
    ) = viewModelScope.launch {
        runCatching {
            withContext(NonCancellable) {
                journalRepository.addTrackedDate(name, icon, date, paletteKey, pinned)
            }
        }.onFailure {
            if (it is CancellationException) throw it
            if (it !is Exception) throw it
            dateMutationError.value = MilestoneMutation.SAVE
        }
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
            runCatching {
                withContext(NonCancellable) {
                    journalRepository.updateTrackedDate(id, name, icon, date, paletteKey)
                }
            }
                .onSuccess { reconcilePendingAgainstSource() }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (it !is Exception) throw it
                    pendingEdits.update { e -> e.copy(dateEdits = e.dateEdits - id) }
                    dateMutationError.value = MilestoneMutation.SAVE
                }
        }
    }

    fun deleteDate(id: String) = viewModelScope.launch {
        runCatching { withContext(NonCancellable) { journalRepository.deleteTrackedDate(id) } }
            .onFailure {
                if (it is CancellationException) throw it
                if (it !is Exception) throw it
                dateMutationError.value = MilestoneMutation.DELETE
            }
    }

    fun consumeDateMutationError() {
        dateMutationError.value = null
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
        // Apply optimistic pin/unpin before the order override so a freshly-pinned row is
        // part of the pinned set the order is matched against. A newly-pinned row appends
        // after the current max order (mirrors JournalRepository.setPinned); unpinning
        // clears the order.
        val withPins = if (pending.pinnedState.isEmpty()) {
            withFields
        } else {
            var nextOrder = (withFields.mapNotNull { it.pinnedOrder }.maxOrNull() ?: -1) + 1
            withFields.map { date ->
                when (pending.pinnedState[date.id]) {
                    true -> if (date.pinnedOrder == null) date.copy(pinnedOrder = nextOrder++) else date
                    false -> if (date.pinnedOrder != null) date.copy(pinnedOrder = null) else date
                    null -> date
                }
            }
        }
        val order = pending.pinnedOrder ?: return withPins
        val pinnedIds = withPins.filter { it.pinnedOrder != null }.map { it.id }
        // Only apply an order that still describes exactly the current pinned set;
        // a pin/unpin landing first makes the override stale, so defer to the source.
        if (order.toSet() != pinnedIds.toSet()) return withPins
        val indexById = order.withIndex().associate { (index, id) -> id to index }
        return withPins.map { date ->
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
        val remainingPinnedState = pending.pinnedState.filterNot { (id, pinned) ->
            val date = byId[id]
            date == null || (date.pinnedOrder != null) == pinned
        }
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
            remainingPinnedState == pending.pinnedState &&
            remainingBackground == pending.heroBackground &&
            remainingDateEdits == pending.dateEdits &&
            remainingOrder == pending.pinnedOrder
        ) {
            pending
        } else {
            PendingEdits(
                pinnedOrder = remainingOrder,
                pinnedState = remainingPinnedState,
                heroBackground = remainingBackground,
                dateEdits = remainingDateEdits,
            )
        }
    }

    private data class PendingEdits(
        val pinnedOrder: List<String>? = null,
        val pinnedState: Map<String, Boolean> = emptyMap(),
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
