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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    @AppScope appScope: CoroutineScope,
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

    fun getCachedPinnedTrackedDates(): List<TrackedDate>? = pinnedTrackedDatesCache.value

    fun getCachedNotes(): List<Note>? = notesCache.value

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

    // The snapshot caches only the FIRST pinned date (the Home hero). Rebuild it
    // exactly when that date's identity or rendered content changes — comparing the
    // whole entity also catches hero rename / icon / date / palette / background edits.
    // Async because the live UI already reads the anchor from the Room source; this
    // only refreshes the cold-start cache for next launch.
    private suspend fun <T> refreshingSnapshotIfHeroChanges(block: suspend () -> T): T {
        val before = databaseHolder.get().journalDao().getFirstPinnedTrackedDate()
        val result = block()
        withContext(NonCancellable) {
            val after = databaseHolder.get().journalDao().getFirstPinnedTrackedDate()
            if (before != after) {
                homeSnapshotRepository.invalidateHomeSnapshot()
                homeSnapshotRepository.refreshHomeSnapshotAsync(force = true)
            }
        }
        return result
    }

    suspend fun addTrackedDate(
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
        pinned: Boolean,
    ) =
        refreshingSnapshotIfHeroChanges {
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
    ) = refreshingSnapshotIfHeroChanges {
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

    suspend fun deleteTrackedDate(id: String) = refreshingSnapshotIfHeroChanges {
        databaseHolder.get().journalDao().deleteTrackedDate(id)
    }

    suspend fun setPinned(id: String, pinned: Boolean) = refreshingSnapshotIfHeroChanges {
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

    suspend fun setHeroBackground(id: String, background: HeroBackground) = refreshingSnapshotIfHeroChanges {
        databaseHolder.get().journalDao().updateHeroBackground(id, background.storageKey, clock.millis())
    }

    suspend fun reorderPinned(idsInOrder: List<String>) = refreshingSnapshotIfHeroChanges {
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
}

// Terminal catch guards Room/SQLCipher flow failures during restore or database
// swaps without swallowing cancellation or fatal VM errors.
private fun <T> Flow<T>.catchRecoverableDatabaseError(fallback: T): Flow<T> =
    catch { error ->
        if (error is CancellationException) throw error
        if (error !is Exception) throw error
        emit(fallback)
    }
