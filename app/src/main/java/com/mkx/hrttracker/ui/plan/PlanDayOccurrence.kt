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
    val medication: MedicationGroupMedication,
    val fulfillingEntryUuids: List<UUID>,
    val loggedCount: Int = 0,
    val isFulfilled: Boolean,
    val isDueSoon: Boolean,
    val isPastDue: Boolean,
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
            val medicationsBySignature = group.medications
                .groupBy(MedicationSignature::fromGroupMedication)
            group.schedule.times.sorted().flatMap { time ->
                val slotDateTime = LocalDateTime.of(date, time)
                val slotLogs = entries.filter { entry ->
                    entry.sourceGroupUuid == group.uuid && entry.scheduledFor == slotDateTime
                }
                val logsBySignature = slotLogs.groupBy(MedicationSignature::fromLogEntry)
                val isDueSoonSlot = isDueSoon(slotDateTime, now)

                medicationsBySignature.map { (signature, medicationsForSignature) ->
                    val requiredCount = medicationsForSignature.sumOf { medication -> medication.count }
                    val matchingLogs = logsBySignature[signature].orEmpty()
                        .sortedBy(MedicationLogEntry::appliedAt)
                    val loggedCount = matchingLogs.sumOf { entry -> entry.count }
                    val isFulfilled = loggedCount >= requiredCount
                    val fulfillingEntries = matchingLogs
                        .sortedBy(MedicationLogEntry::appliedAt)
                    PlanDayScheduleEntry(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        groupColorKey = group.colorKey,
                        scheduledTime = time,
                        medication = medicationsForSignature.first().copy(count = requiredCount),
                        fulfillingEntryUuids = fulfillingEntries.map { it.uuid },
                        loggedCount = loggedCount,
                        isFulfilled = isFulfilled,
                        isDueSoon = !isFulfilled && isDueSoonSlot,
                        isPastDue = !isFulfilled && isPastDue(slotDateTime, now)
                    )
                }
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

internal fun isPastDue(
    scheduledAt: LocalDateTime,
    now: LocalDateTime
): Boolean {
    return scheduledAt.isBefore(now)
}
