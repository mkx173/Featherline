package com.mkx.hrttracker.data.local

import androidx.room.ColumnInfo
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
    // Custom medicines remember the unit the user typed raw-mass fields in
    // (mg/μg/g). Catalog medicines keep the default `MG`. Rows that pre-date
    // this column default to `MG` via the migration.
    @ColumnInfo(defaultValue = "MG")
    val displayDoseUnit: String = "MG",

    // ----- Stock fields (v3, added by MIGRATION_2_3) -----

    @ColumnInfo(defaultValue = "0")
    val trackingEnabled: Boolean = false,

    val stockUnitsRemaining: Double? = null,
    val stockUnitsLastTotal: Double? = null,
    val openContainerAmount: Double? = null,

    @ColumnInfo(defaultValue = "14")
    val warnAtDaysRemaining: Int = 14,

    @ColumnInfo(defaultValue = "0")
    val stockGeneration: Long = 0L,

    @ColumnInfo(defaultValue = "0")
    val importedFromExternalTracker: Boolean = false,
)
