package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isScheduledOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class PlanDayScheduleEntry(
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val scheduledTime: LocalTime,
    val medications: List<MedicationGroupMedication>,
    val fulfillingEntryUuids: List<UUID>,
    val isFulfilled: Boolean,
    val isDueSoon: Boolean,
)

data class PlanDaySchedule(
    val date: LocalDate,
    val scheduledEntries: List<PlanDayScheduleEntry>,
    val unplannedEntries: List<MedicationLogEntry>,
)

fun buildPlanDaySchedule(
    date: LocalDate,
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime = LocalDateTime.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): PlanDaySchedule {
    val scheduledEntries = groups
        .filter { group -> group.schedule.isScheduledOn(date) }
        .flatMap { group ->
            group.schedule.times.sorted().map { time ->
                val slotDateTime = LocalDateTime.of(date, time)
                val fulfillingEntries = entries.filter { entry ->
                    entry.sourceGroupUuid == group.uuid && entry.scheduledFor == slotDateTime
                }
                val isFulfilled = isSlotFulfilled(group, date, time, entries)
                PlanDayScheduleEntry(
                    groupUuid = group.uuid,
                    groupName = group.name,
                    groupColorKey = group.colorKey,
                    scheduledTime = time,
                    medications = group.medications,
                    fulfillingEntryUuids = fulfillingEntries.map { it.uuid },
                    isFulfilled = isFulfilled,
                    isDueSoon = !isFulfilled && isDueSoon(slotDateTime, now)
                )
            }
        }
        .sortedBy { it.scheduledTime }

    val unplannedEntries = entries
        .filter { entry ->
            entry.scheduledFor == null &&
                entry.appliedAt.atZone(zoneId).toLocalDate() == date
        }
        .sortedByDescending { it.appliedAt }

    return PlanDaySchedule(
        date = date,
        scheduledEntries = scheduledEntries,
        unplannedEntries = unplannedEntries
    )
}

internal fun isDueSoon(
    scheduledAt: LocalDateTime,
    now: LocalDateTime
): Boolean {
    return !scheduledAt.isBefore(now) && !scheduledAt.isAfter(now.plusHours(1))
}
