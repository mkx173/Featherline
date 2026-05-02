package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class PlanDayScheduleEntry(
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val scheduleTimeUuid: UUID? = null,
    val scheduledTime: LocalTime,
    val scheduledFor: LocalDateTime = LocalDateTime.of(LocalDate.now(), scheduledTime),
    val medication: MedicationGroupMedication,
    val fulfillingEntryUuids: List<UUID>,
    val loggedAt: LocalDateTime? = null,
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
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): PlanDaySchedule {
    val scheduledGroups = groups.scheduledGroupsForPlanDay(
        date = date,
        entries = entries.filter { entry -> entry.planCalendarDate(zoneId) == date },
    )
    val scheduledEntries = scheduledGroups
        .flatMap { group ->
            val medicationsBySignature = group.medications
                .groupBy(MedicationSignature::fromGroupMedication)
            group.scheduledSlotsForPlanDay(
                date = date,
                entries = entries,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
                unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
            ).flatMap { slot ->
                val slotDateTime = slot.scheduledFor
                val slotLogs = entries.filter { entry ->
                    isEntryForPlanSlot(
                        group = group,
                        slot = slot,
                        entry = entry,
                    )
                }
                val logsBySignature = slotLogs.groupBy(MedicationSignature::fromLogEntry)
                val isDueSoonSlot = isDueSoon(slotDateTime, now)

                medicationsBySignature.map { (signature, medicationsForSignature) ->
                    val requiredCount = medicationsForSignature.sumOf { medication -> medication.count }
                    val matchingLogs = logsBySignature[signature].orEmpty()
                        .sortedBy(MedicationLogEntry::appliedAt)
                    val loggedCount = matchingLogs.sumOf { entry -> entry.count }
                    val isFulfilled = loggedCount >= requiredCount
                    PlanDayScheduleEntry(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        groupColorKey = group.colorKey,
                        scheduleTimeUuid = slot.scheduleTimeUuid,
                        scheduledFor = slotDateTime,
                        scheduledTime = slot.time,
                        medication = medicationsForSignature.first().copy(count = requiredCount),
                        fulfillingEntryUuids = matchingLogs.map { it.uuid },
                        loggedAt = matchingLogs.lastOrNull()
                            ?.appliedAt
                            ?.atZone(zoneId)
                            ?.toLocalDateTime(),
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
            entry.planCalendarDate(zoneId) == date &&
                isPlanOffPlanEntry(
                    entry = entry,
                    scheduledGroups = scheduledGroups,
                    date = date,
                    zoneId = zoneId,
                )
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
