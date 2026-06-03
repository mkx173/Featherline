package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.ZoneId

@Entity(
    tableName = "medication_log_entries",
    indices = [
        Index(
            name = "index_medication_log_entries_category_appliedAtEpochMillis",
            value = ["category", "appliedAtEpochMillis"],
        ),
    ],
)
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
