package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "e2_calibration_metadata",
    foreignKeys = [
        ForeignKey(
            entity = BloodTestResultEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["resultUuid"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class E2CalibrationMetadataEntity(
    @PrimaryKey val resultUuid: String,
    val disposition: String,
    val acceptedModelVersion: String?,
    val acceptedSourceValueBits: String?,
    val acceptedCollectedAtEpochMillis: Long?,
    val acceptedUnitId: String?,
    val updatedAtEpochMillis: Long,
)

/** A single row keeps promotion identity and every route beta atomic. */
@Entity(tableName = "pk_calibration_display_artifact")
data class PkCalibrationDisplayArtifactEntity(
    @PrimaryKey val singletonId: Int,
    /** Existing durable Home generation under which this artifact was published. */
    val homeSnapshotGeneration: Long,
    val schema: String,
    val calibrationModelVersion: String,
    val resultInputDigestSchema: String,
    val resultInputDigestAlgorithm: String,
    val resultInputDigestHexLower: String,
    val promotedRouteStableIds: String,
    val injectionLogScale: Double?,
    val patchLogScale: Double?,
    val gelLogScale: Double?,
    val oralLogScale: Double?,
    val sublingualLogScale: Double?,
)

internal const val PK_CALIBRATION_DISPLAY_SINGLETON_ID = 1
