package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicines",
    indices = [
        Index(value = ["identityKey"], unique = true),
        Index(value = ["archivedAtEpochMillis"]),
        Index(value = ["category"]),
    ],
)
data class MedicineEntity(
    @PrimaryKey val uuid: String,
    val selectionKind: String,
    val medicationKey: String?,
    val customMedicationName: String?,
    val customMedicationNameNormalized: String?,
    val category: String,
    val preparationType: String,
    val strengthMgPerTablet: Double?,
    val strengthMgPerVial: Double?,
    val concentrationMgPerMl: Double?,
    val vialVolumeMl: Double?,
    val concentrationPercent: Double?,
    val sachetWeightGrams: Double?,
    val containerWeightGrams: Double?,
    val patchTotalMg: Double?,
    val patchReleaseRateMcgPerDay: Double?,
    val displayName: String?,
    val identityKey: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
)
