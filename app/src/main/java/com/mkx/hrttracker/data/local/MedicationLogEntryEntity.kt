package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_log_entries")
data class MedicationLogEntryEntity(
    @PrimaryKey val uuid: String,
    val routeOfAdministration: String,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
    val dosageMgAsEstradiol: Double?,
    val appliedAtEpochMillis: Long,
)
