package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PkCalibrationDao {
    @Query("SELECT * FROM e2_calibration_metadata ORDER BY resultUuid ASC")
    suspend fun getAllMetadata(): List<E2CalibrationMetadataEntity>

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
}
