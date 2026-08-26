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
    val updatedAtEpochMillis: Long,
)
