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
    val medicine: Medicine?,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val count: Int = 1,
) {
    init {
        require(count > 0) { "Medication count must be at least 1." }
        require(medicine != null || applicationType == MedicationApplicationType.PATCH_OFF) {
            "Only a PATCH_OFF slot may omit its medicine."
        }
        require(applicationType.isCompatibleWith(medicine?.preparation?.type)) {
            "applicationType=$applicationType is not compatible with preparation=${medicine?.preparation?.type}"
        }
        require(doseInstruction.isCompatibleWith(medicine?.preparation?.type)) {
            "doseInstruction=${doseInstruction.kind} is not compatible with preparation=${medicine?.preparation?.type}"
        }
    }

    val medicineUuid: UUID?
        get() = medicine?.uuid

    // PATCH_OFF slots carry no medicine; the catalog has only the estradiol patch.
    val category: MedicationCategory
        get() = medicine?.category ?: MedicationCategory.ESTRADIOL
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
