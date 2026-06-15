package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodTestDao {
    @Transaction
    @Query(
        """
        SELECT * FROM blood_test_panels
        ORDER BY collectedAtInstantEpochMillis DESC
        """
    )
    suspend fun getPanels(): List<BloodTestPanelWithResultsEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM blood_test_panels
        ORDER BY collectedAtInstantEpochMillis DESC
        """
    )
    fun observePanels(): Flow<List<BloodTestPanelWithResultsEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM blood_test_panels
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    suspend fun getPanel(uuid: String): BloodTestPanelWithResultsEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM blood_test_panels
        WHERE importSourceApp = :sourceApp
          AND importPanelKey = :panelKey
        LIMIT 1
        """
    )
    suspend fun getImportedPanel(
        sourceApp: String,
        panelKey: Long,
    ): BloodTestPanelWithResultsEntity?

    @Query(
        """
        SELECT * FROM blood_test_results
        WHERE importSourceApp = :sourceApp
          AND importExternalId = :externalId
        LIMIT 1
        """
    )
    suspend fun getImportedResult(
        sourceApp: String,
        externalId: String,
    ): BloodTestResultEntity?

    @Query(
        """
        SELECT * FROM blood_test_results
        WHERE importSourceApp = :sourceApp
        """
    )
    suspend fun getImportedResults(sourceApp: String): List<BloodTestResultEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM blood_test_panels
        WHERE importSourceApp = :sourceApp
        """
    )
    suspend fun getImportedPanels(sourceApp: String): List<BloodTestPanelWithResultsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanel(panel: BloodTestPanelEntity)

    // Insert a new panel or update an existing one in place. Unlike
    // insertPanel's REPLACE, this never deletes-then-reinserts the row, so the
    // ON DELETE CASCADE on blood_test_results does not fire and child results
    // (including user-owned ones) are preserved across an imported-panel update.
    @Upsert
    suspend fun upsertPanel(panel: BloodTestPanelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanels(panels: List<BloodTestPanelEntity>)

    @Query(
        """
        DELETE FROM blood_test_results
        WHERE panelUuid = :panelUuid
        """
    )
    suspend fun deleteResultsForPanel(panelUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<BloodTestResultEntity>)

    @Query(
        """
        DELETE FROM blood_test_panels
        WHERE uuid = :uuid
        """
    )
    suspend fun deletePanel(uuid: String)

    @Query(
        """
        DELETE FROM blood_test_panels
        WHERE importSourceApp IS NOT NULL
          AND uuid NOT IN (SELECT DISTINCT panelUuid FROM blood_test_results)
        """
    )
    suspend fun deleteEmptyImportedPanels()

    @Query(
        """
        DELETE FROM blood_test_results
        """
    )
    suspend fun deleteAllResults()

    @Query(
        """
        DELETE FROM blood_test_panels
        """
    )
    suspend fun deleteAllPanels()

    @Transaction
    suspend fun upsertPanelWithResults(
        panel: BloodTestPanelEntity,
        results: List<BloodTestResultEntity>,
    ) {
        insertPanel(panel)
        deleteResultsForPanel(panel.uuid)
        if (results.isNotEmpty()) {
            insertResults(results)
        }
    }

    @Query(
        """
        SELECT * FROM custom_blood_analytes
        WHERE archivedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, normalizedName ASC, normalizedUnitLabel ASC
        """
    )
    suspend fun getActiveCustomAnalytes(): List<CustomBloodAnalyteEntity>

    @Query(
        """
        SELECT * FROM custom_blood_analytes
        ORDER BY normalizedName ASC, normalizedUnitLabel ASC
        """
    )
    suspend fun getCustomAnalytes(): List<CustomBloodAnalyteEntity>

    @Query(
        """
        SELECT * FROM custom_blood_analytes
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    suspend fun getCustomAnalyte(uuid: String): CustomBloodAnalyteEntity?

    @Query(
        """
        SELECT * FROM custom_blood_analytes
        WHERE uuid IN (:uuids)
        """
    )
    suspend fun getCustomAnalytesByIds(uuids: List<String>): List<CustomBloodAnalyteEntity>

    @Query(
        """
        SELECT * FROM custom_blood_analytes
        WHERE normalizedName = :normalizedName
          AND normalizedUnitLabel = :normalizedUnitLabel
        ORDER BY archivedAtEpochMillis IS NULL DESC, updatedAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun getCustomAnalyteByNormalizedPair(
        normalizedName: String,
        normalizedUnitLabel: String,
    ): CustomBloodAnalyteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomAnalyte(analyte: CustomBloodAnalyteEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomAnalytes(analytes: List<CustomBloodAnalyteEntity>)

    @Update
    suspend fun updateCustomAnalyte(analyte: CustomBloodAnalyteEntity)

    @Query(
        """
        DELETE FROM custom_blood_analytes
        WHERE uuid = :uuid
        """
    )
    suspend fun deleteCustomAnalyte(uuid: String)

    @Query(
        """
        DELETE FROM custom_blood_analytes
        """
    )
    suspend fun deleteAllCustomAnalytes()

    @Query(
        """
        SELECT COUNT(*) FROM blood_test_results
        WHERE customAnalyteUuid = :customAnalyteUuid
        """
    )
    suspend fun countResultsForCustomAnalyte(customAnalyteUuid: String): Int

    @Query(
        """
        SELECT
            blood_test_results.panelUuid AS panelUuid,
            blood_test_results.uuid AS resultUuid,
            blood_test_panels.collectedAtInstantEpochMillis AS collectedAtInstantEpochMillis,
            blood_test_panels.collectedAtTimeZoneId AS collectedAtTimeZoneId,
            blood_test_results.value AS value,
            blood_test_results.unitSnapshot AS unitSnapshot,
            blood_test_results.canonicalValue AS canonicalValue
        FROM blood_test_results
        INNER JOIN blood_test_panels
            ON blood_test_panels.uuid = blood_test_results.panelUuid
        WHERE blood_test_results.builtinAnalyteKey = :builtinAnalyteKey
        ORDER BY blood_test_panels.collectedAtInstantEpochMillis ASC, blood_test_results.displayOrder ASC
        """
    )
    suspend fun getBuiltinTrendPoints(builtinAnalyteKey: String): List<BloodTestTrendPointEntity>

    @Query(
        """
        SELECT
            blood_test_results.panelUuid AS panelUuid,
            blood_test_results.uuid AS resultUuid,
            blood_test_panels.collectedAtInstantEpochMillis AS collectedAtInstantEpochMillis,
            blood_test_panels.collectedAtTimeZoneId AS collectedAtTimeZoneId,
            blood_test_results.value AS value,
            blood_test_results.unitSnapshot AS unitSnapshot,
            blood_test_results.canonicalValue AS canonicalValue
        FROM blood_test_results
        INNER JOIN blood_test_panels
            ON blood_test_panels.uuid = blood_test_results.panelUuid
        WHERE blood_test_results.customAnalyteUuid = :customAnalyteUuid
        ORDER BY blood_test_panels.collectedAtInstantEpochMillis ASC, blood_test_results.displayOrder ASC
        """
    )
    suspend fun getCustomTrendPoints(customAnalyteUuid: String): List<BloodTestTrendPointEntity>
}
