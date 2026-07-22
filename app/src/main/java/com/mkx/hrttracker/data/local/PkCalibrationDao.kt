package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PkCalibrationDao {
    @Query("SELECT * FROM e2_calibration_metadata ORDER BY resultUuid ASC")
    suspend fun getAllMetadata(): List<E2CalibrationMetadataEntity>

    @Query("SELECT * FROM e2_calibration_metadata ORDER BY resultUuid ASC")
    fun observeAllMetadata(): Flow<List<E2CalibrationMetadataEntity>>

    @Query("SELECT * FROM e2_calibration_metadata WHERE resultUuid = :resultUuid LIMIT 1")
    suspend fun getMetadata(resultUuid: String): E2CalibrationMetadataEntity?

    @Query("SELECT builtinAnalyteKey FROM blood_test_results WHERE uuid = :resultUuid LIMIT 1")
    suspend fun getBuiltinAnalyteKey(resultUuid: String): String?

    @Upsert
    suspend fun upsertMetadata(metadata: E2CalibrationMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMetadata(metadata: List<E2CalibrationMetadataEntity>)

    @Query("DELETE FROM e2_calibration_metadata")
    suspend fun deleteAllMetadata()

    @Query(
        "SELECT * FROM pk_calibration_display_artifact " +
            "WHERE singletonId = :singletonId LIMIT 1"
    )
    suspend fun getDisplayArtifact(
        singletonId: Int = PK_CALIBRATION_DISPLAY_SINGLETON_ID,
    ): PkCalibrationDisplayArtifactEntity?

    @Upsert
    suspend fun upsertDisplayArtifact(artifact: PkCalibrationDisplayArtifactEntity)

    @Query(
        "DELETE FROM pk_calibration_display_artifact " +
            "WHERE singletonId = :singletonId"
    )
    suspend fun deleteDisplayArtifact(
        singletonId: Int = PK_CALIBRATION_DISPLAY_SINGLETON_ID,
    )
}
