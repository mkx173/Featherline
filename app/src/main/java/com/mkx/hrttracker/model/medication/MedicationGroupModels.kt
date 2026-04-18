package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalTime
import java.time.DayOfWeek
import java.util.UUID

data class MedicationGroup(
    val uuid: UUID,
    val name: String,
    val schedule: MedicationGroupSchedule,
    val medications: List<MedicationGroupMedication>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MedicationGroupMedication(
    val uuid: UUID,
    val routeOfAdministration: RouteOfAdministration,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
)

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
    val weeklyDayOfWeek: DayOfWeek?,
    val times: List<LocalTime>,
)
