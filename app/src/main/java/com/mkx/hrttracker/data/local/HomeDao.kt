package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeDao {
    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        WHERE archivedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, updatedAtEpochMillis DESC, uuid ASC
        """
    )
    fun observeActiveGroups(): Flow<List<MedicationGroupWithItemsEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        WHERE archivedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, updatedAtEpochMillis DESC, uuid ASC
        """
    )
    suspend fun getActiveGroups(): List<MedicationGroupWithItemsEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        WHERE archivedAtEpochMillis IS NOT NULL
        ORDER BY createdAtEpochMillis ASC, updatedAtEpochMillis DESC, uuid ASC
        """
    )
    fun observeArchivedGroups(): Flow<List<MedicationGroupWithItemsEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        WHERE archivedAtEpochMillis IS NOT NULL
        ORDER BY createdAtEpochMillis ASC, updatedAtEpochMillis DESC, uuid ASC
        """
    )
    suspend fun getArchivedGroups(): List<MedicationGroupWithItemsEntity>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE (
            scheduledForIso IS NOT NULL
            AND scheduledForIso >= :scheduledStartIso
            AND scheduledForIso <= :scheduledEndIso
        ) OR (
            scheduledForIso IS NULL
            AND appliedAtEpochMillis >= :manualStartEpochMillis
            AND appliedAtEpochMillis < :manualEndEpochMillis
        )
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    fun observeScheduleEntries(
        scheduledStartIso: String,
        scheduledEndIso: String,
        manualStartEpochMillis: Long,
        manualEndEpochMillis: Long,
    ): Flow<List<MedicationLogEntryEntity>>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE (
            scheduledForIso IS NOT NULL
            AND scheduledForIso >= :scheduledStartIso
            AND scheduledForIso <= :scheduledEndIso
        ) OR (
            scheduledForIso IS NULL
            AND appliedAtEpochMillis >= :manualStartEpochMillis
            AND appliedAtEpochMillis < :manualEndEpochMillis
        )
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    suspend fun getScheduleEntries(
        scheduledStartIso: String,
        scheduledEndIso: String,
        manualStartEpochMillis: Long,
        manualEndEpochMillis: Long,
    ): List<MedicationLogEntryEntity>

    @Query(
        """
        SELECT * FROM medication_log_entries AS entry
        WHERE entry.category = 'ANTIANDROGEN'
          AND entry.appliedAtEpochMillis <= :onOrBeforeEpochMillis
          AND NOT EXISTS (
            SELECT 1 FROM medication_log_entries AS newer
            WHERE newer.category = entry.category
              AND newer.appliedAtEpochMillis <= :onOrBeforeEpochMillis
              AND (
                newer.sourceGroupUuid = entry.sourceGroupUuid OR
                (newer.sourceGroupUuid IS NULL AND entry.sourceGroupUuid IS NULL)
              )
              AND newer.applicationType = entry.applicationType
              AND newer.medicineUuid = entry.medicineUuid
              AND (
                newer.appliedAtEpochMillis > entry.appliedAtEpochMillis OR
                (
                  newer.appliedAtEpochMillis = entry.appliedAtEpochMillis
                  AND newer.uuid > entry.uuid
                )
              )
          )
        ORDER BY entry.appliedAtEpochMillis DESC
        """
    )
    fun observeLatestAntiandrogenEntriesOnOrBefore(
        onOrBeforeEpochMillis: Long,
    ): Flow<List<MedicationLogEntryEntity>>

    @Query(
        """
        SELECT * FROM medication_log_entries AS entry
        WHERE entry.category = 'ANTIANDROGEN'
          AND entry.appliedAtEpochMillis <= :onOrBeforeEpochMillis
          AND NOT EXISTS (
            SELECT 1 FROM medication_log_entries AS newer
            WHERE newer.category = entry.category
              AND newer.appliedAtEpochMillis <= :onOrBeforeEpochMillis
              AND (
                newer.sourceGroupUuid = entry.sourceGroupUuid OR
                (newer.sourceGroupUuid IS NULL AND entry.sourceGroupUuid IS NULL)
              )
              AND newer.applicationType = entry.applicationType
              AND newer.medicineUuid = entry.medicineUuid
              AND (
                newer.appliedAtEpochMillis > entry.appliedAtEpochMillis OR
                (
                  newer.appliedAtEpochMillis = entry.appliedAtEpochMillis
                  AND newer.uuid > entry.uuid
                )
              )
          )
        ORDER BY entry.appliedAtEpochMillis DESC
        """
    )
    suspend fun getLatestAntiandrogenEntriesOnOrBefore(
        onOrBeforeEpochMillis: Long,
    ): List<MedicationLogEntryEntity>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE category = 'ESTRADIOL'
          AND appliedAtEpochMillis >= :startEpochMillis
          AND appliedAtEpochMillis <= :endEpochMillis
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    fun observeEstradiolPkEntries(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): Flow<List<MedicationLogEntryEntity>>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE category = 'ESTRADIOL'
          AND appliedAtEpochMillis >= :startEpochMillis
          AND appliedAtEpochMillis <= :endEpochMillis
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    suspend fun getEstradiolPkEntries(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<MedicationLogEntryEntity>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE category = 'ESTRADIOL'
          AND appliedAtEpochMillis <= :onOrBeforeEpochMillis
        ORDER BY appliedAtEpochMillis DESC
        LIMIT 1
        """
    )
    fun observeLatestEstradiolEntryOnOrBefore(
        onOrBeforeEpochMillis: Long,
    ): Flow<MedicationLogEntryEntity?>

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE category = 'ESTRADIOL'
          AND appliedAtEpochMillis <= :onOrBeforeEpochMillis
        ORDER BY appliedAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLatestEstradiolEntryOnOrBefore(
        onOrBeforeEpochMillis: Long,
    ): MedicationLogEntryEntity?

    @Query(
        """
        SELECT * FROM user_profile
        WHERE id = :id
        LIMIT 1
        """
    )
    fun observeProfile(id: String = UserProfileEntity.SINGLETON_ID): Flow<UserProfileEntity?>
}
