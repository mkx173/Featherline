package com.mkx.hrttracker.model.medication

import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
    val archivedAtLocal: LocalDateTime? = archivedAt
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDateTime(),
    val includePastScheduledSlots: Boolean = true,
    val replacedByGroupUuid: UUID? = null,
    val recreatedFromGroupUuid: UUID? = null,
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
    val timeSlots: List<MedicationGroupScheduleTime> = times.mapIndexed { index, time ->
        MedicationGroupScheduleTime(
            uuid = defaultScheduleTimeUuid(index = index, time = time),
            time = time,
            effectiveFrom = since.atStartOfDay(),
        )
    },
)

data class MedicationGroupScheduleTime(
    val uuid: UUID,
    val time: LocalTime,
    val effectiveFrom: LocalDateTime,
)

private fun defaultScheduleTimeUuid(
    index: Int,
    time: LocalTime,
): UUID {
    return UUID.nameUUIDFromBytes(
        "schedule-time:$index:${time.withSecond(0).withNano(0)}"
            .toByteArray(StandardCharsets.UTF_8)
    )
}
