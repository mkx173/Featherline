package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.util.UUID

data class MedicationGroup(
    val uuid: UUID,
    val name: String,
    val colorKey: MedicationGroupColorKey = MedicationGroupColorKey.ROSE,
    val schedule: MedicationGroupSchedule,
    val medications: List<MedicationGroupMedication>,
    val notificationsEnabled: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MedicationGroupMedication(
    val uuid: UUID,
    val details: MedicationDetails,
) {
    val category: MedicationCategory
        get() = details.category

    val applicationType: MedicationApplicationType
        get() = details.applicationType

    val selection: MedicationSelection
        get() = details.selection

    val dose: MedicationDose
        get() = details.dose
}

enum class MedicationGroupScheduleType {
    WEEKLY,
    DAILY;

    companion object {
        fun fromStorageValue(value: String?): MedicationGroupScheduleType {
            return entries.firstOrNull { it.name == value } ?: WEEKLY
        }
    }
}

data class MedicationGroupSchedule(
    val type: MedicationGroupScheduleType,
    val interval: Int,
    val since: LocalDate,
    val weeklyDaysOfWeek: Set<DayOfWeek>,
    val times: List<LocalTime>,
)
