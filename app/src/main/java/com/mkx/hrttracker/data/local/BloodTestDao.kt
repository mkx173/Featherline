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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPanel(panel: BloodTestPanelEntity)

    // Insert a new panel or update an existing one in place. This never
    // deletes-then-reinserts the row, so ON DELETE CASCADE does not fire and
    // child results are preserved across an imported-panel update.
    @Upsert
    suspend fun upsertPanel(panel: BloodTestPanelEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPanels(panels: List<BloodTestPanelEntity>)

    @Query(
        """
        DELETE FROM blood_test_results
        WHERE panelUuid = :panelUuid
        """
    )
    suspend fun deleteResultsForPanel(panelUuid: String)

    @Query("SELECT * FROM blood_test_results WHERE panelUuid = :panelUuid")
    suspend fun getResultsForPanel(panelUuid: String): List<BloodTestResultEntity>

    @Query("SELECT * FROM blood_test_results WHERE uuid IN (:uuids)")
    suspend fun getResultsByUuids(uuids: List<String>): List<BloodTestResultEntity>

    @Query("SELECT * FROM blood_test_results WHERE panelUuid IN (:panelUuids)")
    suspend fun getResultsForPanels(panelUuids: List<String>): List<BloodTestResultEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResults(results: List<BloodTestResultEntity>)

    @Update
    suspend fun updateResults(results: List<BloodTestResultEntity>): Int

    @Query("DELETE FROM blood_test_results WHERE uuid IN (:uuids)")
    suspend fun deleteResultsByUuid(uuids: List<String>)

    @Query("DELETE FROM e2_calibration_metadata WHERE resultUuid IN (:resultUuids)")
    suspend fun deleteCalibrationMetadataForResults(resultUuids: List<String>)

    // A direct swap violates both unique(panelUuid, displayOrder) and, for an
    // analyte swap, unique(panelUuid, builtinAnalyteKey/customAnalyteUuid).
    // Park the affected rows inside the surrounding transaction before their
    // final in-place updates; no observer can see this intermediate state.
    @Query(
        """
        UPDATE blood_test_results
        SET displayOrder = -rowid,
            builtinAnalyteKey = NULL,
            customAnalyteUuid = NULL
        WHERE panelUuid IN (:panelUuids)
        """
    )
    suspend fun parkResultUniqueKeys(panelUuids: List<String>)

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
        require(results.all { result -> result.panelUuid == panel.uuid })
        require(results.map(BloodTestResultEntity::uuid).distinct().size == results.size)

        val existing = getResultsForPanel(panel.uuid)
        val incomingByUuid = results.associateBy(BloodTestResultEntity::uuid)
        val removedUuids = existing.mapNotNull { result ->
            result.uuid.takeUnless(incomingByUuid::containsKey)
        }
        val identityChangedUuids = existing.mapNotNull { result ->
            val replacement = incomingByUuid[result.uuid] ?: return@mapNotNull null
            result.uuid.takeIf {
                result.builtinAnalyteKey != replacement.builtinAnalyteKey ||
                    result.customAnalyteUuid != replacement.customAnalyteUuid
            }
        }
        // Explicit exclusions survive edits; only an analyte-identity change
        // (the row is no longer the same E2 result) drops review metadata.

        upsertPanel(panel)
        if (removedUuids.isNotEmpty()) deleteResultsByUuid(removedUuids)
        if (identityChangedUuids.isNotEmpty()) {
            deleteCalibrationMetadataForResults(identityChangedUuids)
        }

        val survivingUuids = existing.mapTo(mutableSetOf(), BloodTestResultEntity::uuid)
            .intersect(incomingByUuid.keys)
        if (survivingUuids.isNotEmpty()) {
            parkResultUniqueKeys(listOf(panel.uuid))
            val survivingResults = results.filter { result -> result.uuid in survivingUuids }
            check(updateResults(survivingResults) == survivingResults.size) {
                "Every surviving blood result must update in place."
            }
        }
        val newResults = results.filterNot { result -> result.uuid in survivingUuids }
        if (newResults.isNotEmpty()) insertResults(newResults)
    }

    /** In-place bulk upsert used by external reconciliation; it never deletes rows. */
    @Transaction
    suspend fun upsertResultsInPlace(results: List<BloodTestResultEntity>) {
        if (results.isEmpty()) return
        require(results.map(BloodTestResultEntity::uuid).distinct().size == results.size)

        val replacementsByUuid = results.associateBy(BloodTestResultEntity::uuid)
        val oldByUuid = getResultsByUuids(replacementsByUuid.keys.toList())
            .associateBy(BloodTestResultEntity::uuid)
        val affectedPanels = buildSet {
            addAll(results.map(BloodTestResultEntity::panelUuid))
            addAll(oldByUuid.values.map(BloodTestResultEntity::panelUuid))
        }.toList()
        val currentRows = getResultsForPanels(affectedPanels)
        val identityChangedUuids = oldByUuid.values.mapNotNull { existing ->
            val replacement = replacementsByUuid.getValue(existing.uuid)
            existing.uuid.takeIf {
                existing.builtinAnalyteKey != replacement.builtinAnalyteKey ||
                    existing.customAnalyteUuid != replacement.customAnalyteUuid
            }
        }
        if (identityChangedUuids.isNotEmpty()) {
            deleteCalibrationMetadataForResults(identityChangedUuids)
        }
        if (currentRows.isNotEmpty()) {
            parkResultUniqueKeys(affectedPanels)
            val updatedRows = currentRows.map { row -> replacementsByUuid[row.uuid] ?: row }
            check(updateResults(updatedRows) == updatedRows.size) {
                "Every affected blood result must update in place."
            }
        }
        val newResults = results.filterNot { result -> result.uuid in oldByUuid }
        if (newResults.isNotEmpty()) insertResults(newResults)
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
