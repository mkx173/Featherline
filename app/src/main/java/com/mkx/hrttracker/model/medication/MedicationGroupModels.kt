package com.mkx.hrttracker.model.medication

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    val archivedAt: Instant? = null,
    val includePastScheduledSlots: Boolean = true,
)

data class MedicationGroupMedication(
    val uuid: UUID,
    val details: MedicationDetails,
    val count: Int = 1,
) {
    init {
        require(count > 0) { "Medication count must be at least 1." }
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

data class MedicationGroupMedicationInstance(
    val groupMedicationUuid: UUID,
    val details: MedicationDetails,
)

fun MedicationGroupMedication.expandedInstances(): List<MedicationGroupMedicationInstance> {
    return List(count) {
        MedicationGroupMedicationInstance(
            groupMedicationUuid = uuid,
            details = details
        )
    }
}

fun Iterable<MedicationGroupMedication>.expandedInstances(): List<MedicationGroupMedicationInstance> {
    return flatMap(MedicationGroupMedication::expandedInstances)
}

fun Iterable<MedicationGroupMedication>.totalMedicationCount(): Int {
    return sumOf { medication -> medication.count }
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
