package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.ZoneId

@Entity(tableName = "medication_log_entries")
data class MedicationLogEntryEntity(
    @PrimaryKey val uuid: String,
    val category: String,
    val medicineUuid: String?,
    val applicationType: String,
    val doseInstructionKind: String,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
    val equivalentE2Mg: Double?,
    val sourceGroupUuid: String?,
    val scheduleTimeUuid: String? = null,
    val appliedAtEpochMillis: Long,
    val appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    val scheduledForIso: String? = null,
    val count: Int = 1,
    val gelApplicationArea: String = "DEFAULT",
    val doseAmountDelta: Double? = null,
)
