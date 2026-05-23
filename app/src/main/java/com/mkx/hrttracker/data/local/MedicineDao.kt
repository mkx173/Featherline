package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query(
        """
        SELECT * FROM medicines
        WHERE archivedAtEpochMillis IS NULL
        ORDER BY category ASC, updatedAtEpochMillis DESC, createdAtEpochMillis DESC
        """
    )
    fun observeAllActive(): Flow<List<MedicineEntity>>

    @Query(
        """
        SELECT * FROM medicines
        WHERE archivedAtEpochMillis IS NOT NULL
        ORDER BY archivedAtEpochMillis DESC, updatedAtEpochMillis DESC
        """
    )
    fun observeAllArchived(): Flow<List<MedicineEntity>>

    @Query(
        """
        SELECT * FROM medicines
        ORDER BY createdAtEpochMillis ASC, uuid ASC
        """
    )
    suspend fun getAll(): List<MedicineEntity>

    @Query(
        """
        SELECT * FROM medicines
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    fun observeByUuid(uuid: String): Flow<MedicineEntity?>

    @Query(
        """
        SELECT * FROM medicines
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    suspend fun getByUuid(uuid: String): MedicineEntity?

    @Query(
        """
        SELECT * FROM medicines
        WHERE uuid IN (:uuids)
        """
    )
    suspend fun getByUuids(uuids: List<String>): List<MedicineEntity>

    @Query(
        """
        SELECT * FROM medicines
        WHERE identityKey = :identityKey
        LIMIT 1
        """
    )
    suspend fun getByIdentityKey(identityKey: String): MedicineEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MedicineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MedicineEntity>)

    @Query(
        """
        UPDATE medicines
        SET archivedAtEpochMillis = NULL,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun unarchive(
        uuid: String,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medicines
        SET displayName = :displayName,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updateDisplayName(
        uuid: String,
        displayName: String?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medicines
        SET preparationType = :preparationType,
            strengthMgPerTablet = :strengthMgPerTablet,
            strengthMgPerVial = :strengthMgPerVial,
            concentrationMgPerMl = :concentrationMgPerMl,
            vialVolumeMl = :vialVolumeMl,
            concentrationPercent = :concentrationPercent,
            sachetWeightGrams = :sachetWeightGrams,
            containerWeightGrams = :containerWeightGrams,
            patchTotalMg = :patchTotalMg,
            patchReleaseRateMcgPerDay = :patchReleaseRateMcgPerDay,
            identityKey = :identityKey,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updatePreparationFields(
        uuid: String,
        preparationType: String,
        strengthMgPerTablet: Double?,
        strengthMgPerVial: Double?,
        concentrationMgPerMl: Double?,
        vialVolumeMl: Double?,
        concentrationPercent: Double?,
        sachetWeightGrams: Double?,
        containerWeightGrams: Double?,
        patchTotalMg: Double?,
        patchReleaseRateMcgPerDay: Double?,
        identityKey: String,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medicines
        SET archivedAtEpochMillis = :archivedAtEpochMillis,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun archive(
        uuid: String,
        archivedAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    )

    @Query("SELECT COUNT(*) FROM medication_log_entries WHERE medicineUuid = :uuid")
    suspend fun logReferenceCount(uuid: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM medication_group_items AS item
        INNER JOIN medication_groups AS grp ON grp.uuid = item.groupUuid
        WHERE item.medicineUuid = :uuid
          AND grp.archivedAtEpochMillis IS NULL
        """
    )
    suspend fun activeGroupReferenceCount(uuid: String): Int

    @Query(
        """
        DELETE FROM medicines
        """
    )
    suspend fun deleteAll()
}
