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
        ORDER BY createdAtEpochMillis ASC, uuid ASC
        """
    )
    suspend fun getAll(): List<MedicineEntity>

    @Query(
        """
        SELECT * FROM medicines
        WHERE archivedAtEpochMillis IS NULL AND trackingEnabled = 1
        """
    )
    suspend fun getAllActiveTrackedEntities(): List<MedicineEntity>

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

    // A change-only signal: Room's invalidation tracker re-fires this Flow on
    // ANY mutation to the medicines table. Used by repositories that resolve
    // medicines as a separate fetch off a group/log table observation — without
    // this, displayName / preparation / archive edits don't propagate until the
    // primary table also changes (i.e., effectively only on app relaunch).
    @Query("SELECT COUNT(*) FROM medicines")
    fun observeMedicineChangeVersion(): Flow<Int>

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
            displayDoseUnit = :displayDoseUnit,
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
        displayDoseUnit: String,
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

    @Query(
        """
        UPDATE medicines SET
            trackingEnabled = :trackingEnabled,
            stockUnitsRemaining = :stockUnitsRemaining,
            stockUnitsLastTotal = :stockUnitsLastTotal,
            openContainerAmount = :openContainerAmount,
            warnAtDaysRemaining = :warnAtDaysRemaining,
            stockGeneration = :stockGeneration,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updateStockFields(
        uuid: String,
        trackingEnabled: Boolean,
        stockUnitsRemaining: Double?,
        stockUnitsLastTotal: Double?,
        openContainerAmount: Double?,
        warnAtDaysRemaining: Int,
        stockGeneration: Long,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medicines SET
            warnAtDaysRemaining = :warnAtDaysRemaining,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updateWarnAtDaysRemaining(
        uuid: String,
        warnAtDaysRemaining: Int,
        updatedAtEpochMillis: Long,
    )
}
