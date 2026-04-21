package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.util.UUID

data class MedicationGroup(
    val uuid: UUID,
    val name: String,
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
    constructor(
        uuid: UUID,
        routeOfAdministration: RouteOfAdministration,
        medicineName: String,
        dosageMgAsMedicine: Double,
    ) : this(
        uuid = uuid,
        details = legacyMedicationDetails(
            routeOfAdministration = routeOfAdministration,
            medicineName = medicineName,
            dosageMgAsMedicine = dosageMgAsMedicine
        ),
    )

    val category: MedicationCategory
        get() = details.category

    val applicationType: MedicationApplicationType
        get() = details.applicationType

    val selection: MedicationSelection
        get() = details.selection

    val dose: MedicationDose
        get() = details.dose

    val routeOfAdministration: RouteOfAdministration
        get() = details.legacyRouteOfAdministration()

    val medicineName: String
        get() = details.displayName()

    val dosageMgAsMedicine: Double
        get() = details.legacyDoseValueMg()
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
