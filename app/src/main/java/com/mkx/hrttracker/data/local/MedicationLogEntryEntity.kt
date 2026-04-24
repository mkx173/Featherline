package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.ZoneId

@Entity(tableName = "medication_log_entries")
data class MedicationLogEntryEntity(
    @PrimaryKey val uuid: String,
    val category: String,
    val applicationType: String,
    val selectionKind: String,
    val medicationKey: String?,
    val customMedicationName: String?,
    val doseKind: String,
    val doseValueMg: Double?,
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
    val dosageMgAsEstradiol: Double?,
    val sourceType: String,
    val sourceGroupUuid: String?,
    val appliedAtEpochMillis: Long,
    val appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    val scheduledForIso: String? = null,
    val count: Int = 1,
)
