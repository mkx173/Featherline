package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Query(
        """
        SELECT * FROM medication_log_entries
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    fun observeEntries(): Flow<List<MedicationLogEntryEntity>>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    suspend fun getEntry(uuid: String): MedicationLogEntryEntity?

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE uuid IN (:uuids)
        """
    )
    suspend fun getEntriesByIds(uuids: List<String>): List<MedicationLogEntryEntity>

    @Query(
        """
        SELECT * FROM medication_log_entries
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    suspend fun getEntries(): List<MedicationLogEntryEntity>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE sourceGroupUuid IS NOT NULL
          AND scheduledForIso IS NOT NULL
          AND scheduledForIso >= :sinceIso
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    suspend fun getScheduledGroupEntriesSince(sinceIso: String): List<MedicationLogEntryEntity>

    @Query(
        """
        DELETE FROM medication_log_entries
        WHERE uuid IN (:uuids)
        """
    )
    suspend fun deleteEntries(uuids: List<String>)

    @Query(
        """
        DELETE FROM medication_log_entries
        """
    )
    suspend fun deleteAllEntries()

    @Query(
        """
        UPDATE medication_log_entries
        SET sourceGroupUuid = NULL,
            scheduledForIso = NULL
        WHERE sourceGroupUuid = :groupUuid
        """
    )
    suspend fun reclassifyEntriesForDeletedGroup(groupUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MedicationLogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<MedicationLogEntryEntity>)
}
