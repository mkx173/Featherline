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
        WHERE category = :category
          AND appliedAtEpochMillis <= :onOrBeforeEpochMillis
        ORDER BY appliedAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLatestEntryByCategoryOnOrBefore(
        category: String,
        onOrBeforeEpochMillis: Long,
    ): MedicationLogEntryEntity?

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
        SELECT COUNT(*) FROM medication_log_entries
        WHERE sourceGroupUuid = :groupUuid
        """
    )
    suspend fun getEntryCountForGroup(groupUuid: String): Int

    @Query(
        """
        SELECT COUNT(DISTINCT scheduledForIso) FROM medication_log_entries
        WHERE sourceGroupUuid = :groupUuid
          AND scheduledForIso IS NOT NULL
          AND scheduledForIso >= :onOrAfterScheduledForIso
        """
    )
    suspend fun getCurrentOrFuturePlannedSlotCountForGroup(
        groupUuid: String,
        onOrAfterScheduledForIso: String,
    ): Int

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
        WHERE sourceGroupUuid = :groupUuid
        """
    )
    suspend fun deleteEntriesForGroup(groupUuid: String)

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
            scheduleTimeUuid = NULL,
            scheduledForIso = NULL
        WHERE sourceGroupUuid = :groupUuid
        """
    )
    suspend fun reclassifyEntriesForDeletedGroup(groupUuid: String)

    @Query(
        """
        SELECT uuid FROM medication_log_entries
        WHERE sourceGroupUuid = :groupUuid
          AND scheduledForIso IS NOT NULL
          AND (
              scheduleTimeUuid = :scheduleTimeUuid
              OR (scheduleTimeUuid IS NULL AND substr(scheduledForIso, 12) = :oldTimeIso)
          )
        """
    )
    suspend fun getPlannedEntryIdsForGroupSlotTime(
        groupUuid: String,
        scheduleTimeUuid: String,
        oldTimeIso: String,
    ): List<String>

    @Query(
        """
        UPDATE medication_log_entries
        SET scheduledForIso = substr(scheduledForIso, 1, 11) || :newTimeIso
        WHERE uuid IN (:entryUuids)
        """
    )
    suspend fun updateScheduledForTimeForEntries(
        entryUuids: List<String>,
        newTimeIso: String,
    )

    @Query(
        """
        UPDATE medication_log_entries
        SET appliedAtEpochMillis = :appliedAtEpochMillis,
            appliedAtTimeZoneId = :appliedAtTimeZoneId,
            scheduledForIso = :scheduledForIso,
            count = :count
        WHERE uuid = :uuid
        """
    )
    suspend fun updateEditableLogFields(
        uuid: String,
        appliedAtEpochMillis: Long,
        appliedAtTimeZoneId: String,
        scheduledForIso: String?,
        count: Int,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MedicationLogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<MedicationLogEntryEntity>)
}
