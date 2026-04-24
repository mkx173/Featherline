package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

enum class MedicationLogEntrySourceType {
    MANUAL,
    GROUP_MANUAL;

    companion object {
        fun fromStorageValue(value: String?): MedicationLogEntrySourceType {
            return entries.firstOrNull { it.name == value } ?: MANUAL
        }
    }
}

data class MedicationLogEntry(
    val uuid: UUID,
    val details: MedicationDetails,
    val dosageMgAsEstradiol: Double?,
    val sourceType: MedicationLogEntrySourceType,
    val sourceGroupUuid: UUID?,
    val appliedAt: Instant,
    val appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    val scheduledFor: LocalDateTime? = null,
    val count: Int = 1,
) {
    init {
        require(count > 0) { "Medication log count must be at least 1." }
    }

    val category: MedicationCategory
        get() = details.category

    val applicationType: MedicationApplicationType
        get() = details.applicationType

    val selection: MedicationSelection
        get() = details.selection

    val dose: MedicationDose
        get() = details.dose
}
