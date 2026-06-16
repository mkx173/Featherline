package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM tracked_dates ORDER BY dateIso ASC, createdAtEpochMillis ASC")
    fun observeTrackedDates(): Flow<List<TrackedDateEntity>>

    @Query(
        """
        SELECT * FROM tracked_dates
        WHERE pinnedOrder IS NOT NULL
        ORDER BY pinnedOrder ASC, dateIso ASC, createdAtEpochMillis ASC, uuid ASC
        """
    )
    fun observePinnedTrackedDates(): Flow<List<TrackedDateEntity>>

    @Query("SELECT * FROM tracked_dates ORDER BY dateIso ASC")
    suspend fun getTrackedDates(): List<TrackedDateEntity>

    @Query("SELECT MAX(pinnedOrder) FROM tracked_dates")
    suspend fun getMaxPinnedOrder(): Int?

    @Upsert
    suspend fun upsertTrackedDate(entity: TrackedDateEntity)

    @Query("UPDATE tracked_dates SET pinnedOrder = :pinnedOrder, updatedAtEpochMillis = :updatedAt WHERE uuid = :uuid")
    suspend fun updatePinnedOrder(uuid: String, pinnedOrder: Int?, updatedAt: Long)

    @Query("DELETE FROM tracked_dates WHERE uuid = :uuid")
    suspend fun deleteTrackedDate(uuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedDates(entities: List<TrackedDateEntity>)

    @Query("DELETE FROM tracked_dates")
    suspend fun deleteAllTrackedDates()

    @Query("SELECT * FROM notes WHERE dateIso >= :fromDateIso ORDER BY dateIso DESC")
    fun observeNotesOnOrAfter(fromDateIso: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE dateIso = :dateIso LIMIT 1")
    fun observeNoteForDate(dateIso: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getNoteForDate(dateIso: String): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY dateIso DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Query("DELETE FROM notes WHERE dateIso = :dateIso")
    suspend fun deleteNoteForDate(dateIso: String)

    @Transaction
    suspend fun upsertNote(entity: NoteEntity) {
        val existingForDate = getNoteForDate(entity.dateIso)
        val entityToPersist = if (existingForDate != null && existingForDate.uuid != entity.uuid) {
            entity.copy(
                uuid = existingForDate.uuid,
                createdAtEpochMillis = existingForDate.createdAtEpochMillis,
            )
        } else {
            entity
        }
        insertOrReplaceNote(entityToPersist)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceNote(entity: NoteEntity)

    @Query("DELETE FROM notes WHERE uuid = :uuid")
    suspend fun deleteNote(uuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(entities: List<NoteEntity>)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
