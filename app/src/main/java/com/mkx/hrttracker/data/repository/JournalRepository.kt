package com.mkx.hrttracker.data.repository

import androidx.room.withTransaction
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.NoteEntity
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.PinOrder
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val clock: Clock,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val anchorSnapshotStore: AnchorSnapshotStore,
    @param:AppScope private val appScope: CoroutineScope,
) {
    // Warm, synchronously-readable caches of the journal's reactive sources.
    // Eagerly shared on appScope (the repository is constructed at app start via
    // HomeRepository), so they hold real data long before the user opens the
    // Journal tab. The Journal ViewModel seeds its first frame from these to
    // avoid flashing the loading indicator while its own cold combine spins up.
    // null = not yet loaded (the genuine cold-start loading state); the live
    // observe* flows below are unchanged and remain the UI's reactive source.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val trackedDatesCache: StateFlow<List<TrackedDate>?> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf<List<TrackedDate>?>(null)
            } else {
                db.journalDao()
                    .observeTrackedDates()
                    .map<List<TrackedDateEntity>, List<TrackedDate>?> { rows ->
                        rows.map { it.toModel() }
                    }
                    .catchRecoverableDatabaseError(emptyList())
            }
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pinnedTrackedDatesCache: StateFlow<List<TrackedDate>?> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf<List<TrackedDate>?>(null)
            } else {
                db.journalDao()
                    .observePinnedTrackedDates()
                    .map<List<TrackedDateEntity>, List<TrackedDate>?> { rows ->
                        rows.map { it.toModel() }
                    }
                    .catchRecoverableDatabaseError(emptyList())
            }
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val notesCache: StateFlow<List<Note>?> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf<List<Note>?>(null)
            } else {
                db.journalDao()
                    .observeNotes()
                    .map<List<NoteEntity>, List<Note>?> { rows -> rows.map { it.toModel() } }
                    .catchRecoverableDatabaseError(emptyList())
            }
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    fun getCachedTrackedDates(): List<TrackedDate>? = trackedDatesCache.value

    // The anchor home surfaces (widget, pinned shortcuts, config picker) must never read
    // the not-yet-loaded window as "the journal is empty" — that is what disables pinned
    // shortcuts and blanks widgets at cold start. This forces the database open and waits
    // for the first real row set; after it returns, an empty list genuinely means empty.
    suspend fun awaitTrackedDates(): List<TrackedDate> {
        withContext(Dispatchers.IO) { databaseHolder.get() }
        return trackedDatesCache.filterNotNull().first()
    }

    // Like observeTrackedDates but skips the not-loaded (null-cache) window entirely, and
    // does not force the database open — emissions start once something else opens it.
    fun observeLoadedTrackedDates(): Flow<List<TrackedDate>> = trackedDatesCache.filterNotNull()

    // Cold-start read for the Milestones timeline: emit the persisted snapshot (last-known
    // anchors) while the database is still opening, then hand over to the live cache. Never
    // emits the fake empty list from the not-loaded window, and never lets the snapshot
    // shadow loaded data — the cache re-check after the file read closes that race.
    fun observeTrackedDatesWithSnapshotSeed(): Flow<List<TrackedDate>> = flow {
        if (trackedDatesCache.value == null) {
            val snapshot = anchorSnapshotSeed.value
                ?: runCatching { anchorSnapshotStore.read() }.getOrNull()
            if (snapshot != null && trackedDatesCache.value == null) {
                emit(snapshot)
            }
        }
        emitAll(trackedDatesCache.filterNotNull())
    }

    // In-memory copy of the persisted anchor snapshot, filled by the deep-link preload so
    // MilestonesViewModel's synchronous initialValue seed can render real data on the very
    // first composed frame (the async seeded-flow emission alone leaves a loading frame).
    private val anchorSnapshotSeed = MutableStateFlow<List<TrackedDate>?>(null)

    // Called from MainActivity's milestones deep-link branch, before compose init: the
    // snapshot file read then runs in parallel with activity/compose startup instead of
    // after the ViewModel is built. No-op when live data or a previous preload is ready.
    fun preloadAnchorSnapshotSeed() {
        appScope.launch {
            if (trackedDatesCache.value != null || anchorSnapshotSeed.value != null) return@launch
            anchorSnapshotSeed.value = runCatching { anchorSnapshotStore.read() }.getOrNull()
        }
    }

    fun getPreloadedAnchorSnapshot(): List<TrackedDate>? = anchorSnapshotSeed.value

    fun getCachedPinnedTrackedDates(): List<TrackedDate>? = pinnedTrackedDatesCache.value

    fun getCachedNotes(): List<Note>? = notesCache.value

    init {
        // Keep the cold-start Home snapshot in step with the journal hero (the first pinned
        // date), reactively — mirroring HomeSnapshotRepository's home-E2-settings observer.
        // Running on appScope decouples this from the journal write, so a snapshot/DataStore
        // failure can never fail (or crash) the committed mutation, and we avoid the expensive
        // full runHomeDataMutation. The warm cache's null = not-yet-loaded; the first loaded
        // value is the process-start baseline (the normal Home build already covers startup),
        // so only later hero changes force a rebuild. Best-effort: appScope has no exception
        // handler, so a DataStore failure must not tear down this long-lived collector.
        appScope.launch {
            var baselined = false
            var previousHero: TrackedDate? = null
            pinnedTrackedDatesCache.collect { pinned ->
                if (pinned == null) return@collect
                val hero = pinned.firstOrNull()
                if (!baselined) {
                    baselined = true
                    previousHero = hero
                    return@collect
                }
                if (hero == previousHero) return@collect
                previousHero = hero
                runCatching {
                    homeSnapshotRepository.invalidateHomeSnapshot()
                    homeSnapshotRepository.refreshHomeSnapshotAsync(force = true)
                }.onFailure { if (it is CancellationException) throw it }
            }
        }

        // Persist every loaded tracked-dates emission so the next cold start can seed the
        // Milestones timeline before the database opens. Overwrite-on-change also clears
        // the snapshot when the journal empties. write() is best-effort by contract
        // (logs and swallows failures), so a DataStore/keystore failure can neither fail
        // the journal write nor tear down this long-lived collector.
        appScope.launch {
            trackedDatesCache.filterNotNull().distinctUntilChanged().collect { dates ->
                anchorSnapshotStore.write(dates)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTrackedDates(): Flow<List<TrackedDate>> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf(emptyList())
            } else {
                db.journalDao()
                    .observeTrackedDates()
                    .map { rows -> rows.map { it.toModel() } }
                    .catchRecoverableDatabaseError(emptyList())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePinnedTrackedDates(): Flow<List<TrackedDate>> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf(emptyList())
            } else {
                db.journalDao()
                    .observePinnedTrackedDates()
                    .map { rows -> rows.map { it.toModel() } }
                    .catchRecoverableDatabaseError(emptyList())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotesOnOrAfter(fromDate: LocalDate): Flow<List<Note>> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf(emptyList())
            } else {
                db.journalDao()
                    .observeNotesOnOrAfter(fromDate.toString())
                    .map { rows -> rows.map { it.toModel() } }
                    .catchRecoverableDatabaseError(emptyList())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNoteForDate(date: LocalDate): Flow<Note?> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf(null)
            } else {
                db.journalDao()
                    .observeNoteForDate(date.toString())
                    .map { it?.toModel() }
                    .catchRecoverableDatabaseError(null)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllNotesCount(): Flow<Int> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf(0)
            } else {
                db.journalDao()
                    .observeAllNotesCount()
                    .catchRecoverableDatabaseError(0)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotesCountBefore(beforeDate: LocalDate): Flow<Int> =
        databaseHolder.databaseFlow.flatMapLatest { db ->
            if (db == null) {
                flowOf(0)
            } else {
                db.journalDao()
                    .observeNotesCountBefore(beforeDate.toString())
                    .catchRecoverableDatabaseError(0)
            }
        }

    suspend fun getTrackedDateEntities(): List<TrackedDateEntity> =
        databaseHolder.get().journalDao().getTrackedDates()

    suspend fun getNoteEntities(): List<NoteEntity> =
        databaseHolder.get().journalDao().getNotes()

    suspend fun addTrackedDate(
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
        pinned: Boolean,
    ) {
        val database = databaseHolder.get()
        database.withTransaction {
            val dao = database.journalDao()
            val now = clock.millis()
            // The caller decides whether to pin (the add sheet's toggle, defaulted so the
            // first date pins and later ones don't). Pin-on-create appends to the bottom of
            // the pinned list; an unpinned date is timeline-only.
            val pinnedOrder = if (pinned) {
                PinOrder.appendOrderAfterMax(dao.getMaxPinnedOrder())
            } else {
                null
            }
            dao.upsertTrackedDate(
                TrackedDateEntity(
                    uuid = UUID.randomUUID().toString(),
                    name = name,
                    iconKey = AnchorIcon.fromStorageValue(icon).storageKey,
                    dateIso = date.toString(),
                    paletteKey = paletteKey,
                    heroBackgroundKey = null,
                    pinnedOrder = pinnedOrder,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
        }
    }

    suspend fun updateTrackedDate(
        id: String,
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
    ) {
        val database = databaseHolder.get()
        database.withTransaction {
            val dao = database.journalDao()
            val existing = dao.getTrackedDates().firstOrNull { it.uuid == id } ?: return@withTransaction
            dao.upsertTrackedDate(
                existing.copy(
                    name = name,
                    iconKey = AnchorIcon.fromStorageValue(icon).storageKey,
                    dateIso = date.toString(),
                    paletteKey = paletteKey,
                    updatedAtEpochMillis = clock.millis(),
                )
            )
        }
    }

    suspend fun deleteTrackedDate(id: String) {
        databaseHolder.get().journalDao().deleteTrackedDate(id)
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val database = databaseHolder.get()
        database.withTransaction {
            val dao = database.journalDao()
            val all = dao.getTrackedDates()
            val target = all.firstOrNull { it.uuid == id } ?: return@withTransaction
            if (pinned) {
                if (target.pinnedOrder != null) return@withTransaction
                dao.updatePinnedOrder(
                    id,
                    PinOrder.appendOrderAfterMax(dao.getMaxPinnedOrder()),
                    clock.millis(),
                )
            } else {
                if (target.pinnedOrder == null) return@withTransaction
                val now = clock.millis()
                dao.updatePinnedOrder(id, null, now)
                val remaining = all
                    .filter { it.pinnedOrder != null && it.uuid != id }
                    .sortedWith(
                        compareBy<TrackedDateEntity> { it.pinnedOrder ?: Int.MAX_VALUE }
                            .thenBy { it.dateIso }
                            .thenBy { it.createdAtEpochMillis }
                            .thenBy { it.uuid }
                    )
                val normalized = PinOrder.normalize(remaining.map { it.uuid })
                remaining.forEach { row ->
                    val order = normalized.getValue(row.uuid)
                    if (row.pinnedOrder != order) {
                        dao.updatePinnedOrder(row.uuid, order, now)
                    }
                }
            }
        }
    }

    suspend fun setHeroBackground(id: String, background: HeroBackground) {
        databaseHolder.get().journalDao().updateHeroBackground(id, background.storageKey, clock.millis())
    }

    suspend fun reorderPinned(idsInOrder: List<String>) {
        val database = databaseHolder.get()
        database.withTransaction {
            val dao = database.journalDao()
            val pinnedRows = dao.getTrackedDates()
                .filter { it.pinnedOrder != null }
                .sortedWith(
                    compareBy<TrackedDateEntity> { it.pinnedOrder ?: Int.MAX_VALUE }
                        .thenBy { it.dateIso }
                        .thenBy { it.createdAtEpochMillis }
                        .thenBy { it.uuid }
                )
            val currentPinnedIds = pinnedRows.map { it.uuid }
            if (idsInOrder.size != currentPinnedIds.size) return@withTransaction
            if (idsInOrder.toSet().size != idsInOrder.size) return@withTransaction
            if (idsInOrder.toSet() != currentPinnedIds.toSet()) return@withTransaction

            val normalized = PinOrder.normalize(idsInOrder)
            val changedOrders = pinnedRows.mapNotNull { row ->
                val order = normalized.getValue(row.uuid)
                if (row.pinnedOrder == order) null else row.uuid to order
            }
            if (changedOrders.isEmpty()) return@withTransaction

            val now = clock.millis()
            changedOrders.forEach { (uuid, order) ->
                dao.updatePinnedOrder(uuid, order, now)
            }
        }
    }

    suspend fun saveNoteForDate(date: LocalDate, text: String) {
        val database = databaseHolder.get()
        database.withTransaction {
            val dao = database.journalDao()
            val now = clock.millis()
            dao.upsertNote(
                NoteEntity(
                    uuid = UUID.randomUUID().toString(),
                    dateIso = date.toString(),
                    text = text,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
        }
    }

    suspend fun deleteNoteForDate(date: LocalDate) {
        databaseHolder.get().journalDao().deleteNoteForDate(date.toString())
    }

    suspend fun deleteNotesForDates(dates: Set<LocalDate>) {
        if (dates.isEmpty()) return
        databaseHolder.get().journalDao().deleteNotesForDates(dates.map { it.toString() })
    }
}

// Terminal catch guards Room/SQLCipher flow failures during restore or database
// swaps without swallowing cancellation or fatal VM errors.
private fun <T> Flow<T>.catchRecoverableDatabaseError(fallback: T): Flow<T> =
    catch { error ->
        if (error is CancellationException) throw error
        if (error !is Exception) throw error
        emit(fallback)
    }
