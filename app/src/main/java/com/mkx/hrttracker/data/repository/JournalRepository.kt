package com.mkx.hrttracker.data.repository

import androidx.room.withTransaction
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.NoteEntity
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.PinOrder
import com.mkx.hrttracker.model.journal.TrackedDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val clock: Clock,
) {
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

    suspend fun getTrackedDateEntities(): List<TrackedDateEntity> =
        databaseHolder.get().journalDao().getTrackedDates()

    suspend fun getNoteEntities(): List<NoteEntity> =
        databaseHolder.get().journalDao().getNotes()

    suspend fun addTrackedDate(name: String, icon: String, date: LocalDate, paletteKey: String?) {
        val database = databaseHolder.get()
        database.withTransaction {
            val dao = database.journalDao()
            val now = clock.millis()
            dao.upsertTrackedDate(
                TrackedDateEntity(
                    uuid = UUID.randomUUID().toString(),
                    name = name,
                    iconKey = AnchorIcon.fromStorageValue(icon).storageKey,
                    dateIso = date.toString(),
                    paletteKey = paletteKey,
                    pinnedOrder = PinOrder.appendOrderAfterMax(dao.getMaxPinnedOrder()),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
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
}

// Terminal catch guards Room/SQLCipher flow failures during restore or database
// swaps without swallowing cancellation or fatal VM errors.
private fun <T> Flow<T>.catchRecoverableDatabaseError(fallback: T): Flow<T> =
    catch { error ->
        if (error is CancellationException) throw error
        if (error !is Exception) throw error
        emit(fallback)
    }
