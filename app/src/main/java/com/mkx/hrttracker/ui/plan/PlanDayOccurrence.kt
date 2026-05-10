package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class PlanDayScheduleEntry(
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val groupCreatedAt: Instant = Instant.EPOCH,
    val scheduleTimeUuid: UUID? = null,
    val scheduledTime: LocalTime,
    val scheduledFor: LocalDateTime = LocalDateTime.of(LocalDate.now(), scheduledTime),
    val medication: MedicationGroupMedication,
    val medicationSortOrder: Int = 0,
    val fulfillingEntryUuids: List<UUID>,
    val outsideScheduleWindowEntryUuids: List<UUID> = emptyList(),
    val loggedAt: LocalDateTime? = null,
    val lastFulfillingEntry: MedicationLogEntry? = null,
    val outsideScheduleWindowLoggedAt: LocalDateTime? = null,
    val loggedCount: Int = 0,
    val isFulfilled: Boolean,
    val isDueSoon: Boolean,
    val isPastDue: Boolean,
) {
    val hasOutsideScheduleWindowEntry: Boolean
        get() = outsideScheduleWindowEntryUuids.isNotEmpty()
}

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
                .withIndex()
                .groupBy { indexedMedication ->
                    MedicationSignature.fromGroupMedication(indexedMedication.value)
                }
            group.scheduledSlotsForPlanDay(
                date = date,
                entries = entries,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
                unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
            ).flatMap { slot ->
                val slotDateTime = slot.scheduledFor
                val matchingSlotLogs = entries.filter { entry ->
                    isEntryForPlanSlot(
                        group = group,
                        slot = slot,
                        entry = entry,
                    )
                }
                val slotLogs = matchingSlotLogs.filter { entry ->
                    isEntryFulfillingPlanSlot(
                        group = group,
                        slot = slot,
                        entry = entry,
                        zoneId = zoneId,
                    )
                }
                val logsBySignature = slotLogs.groupBy(MedicationSignature::fromLogEntry)
                val outsideWindowLogsBySignature = matchingSlotLogs
                    .filter { entry ->
                        !isEntryWithinScheduleFulfillmentWindow(
                            group = group,
                            entry = entry,
                            zoneId = zoneId,
                        )
                    }
                    .groupBy(MedicationSignature::fromLogEntry)
                val isDueSoonSlot = isDueSoon(slotDateTime, now)

                medicationsBySignature.map { (signature, indexedMedicationsForSignature) ->
                    val requiredCount = indexedMedicationsForSignature.sumOf { indexedMedication ->
                        indexedMedication.value.count
                    }
                    val medicationSortOrder = indexedMedicationsForSignature.minOf { indexedMedication ->
                        indexedMedication.index
                    }
                    val matchingLogs = logsBySignature[signature].orEmpty()
                        .sortedBy(MedicationLogEntry::appliedAt)
                    val outsideWindowLogs = outsideWindowLogsBySignature[signature].orEmpty()
                        .sortedBy(MedicationLogEntry::appliedAt)
                    val loggedCount = matchingLogs.sumOf { entry -> entry.count }
                    val isFulfilled = loggedCount >= requiredCount
                    val lastFulfillingEntry = matchingLogs.lastOrNull()
                    val lastOutsideWindowEntry = outsideWindowLogs.lastOrNull()
                    PlanDayScheduleEntry(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        groupColorKey = group.colorKey,
                        groupCreatedAt = group.createdAt,
                        scheduleTimeUuid = slot.scheduleTimeUuid,
                        scheduledFor = slotDateTime,
                        scheduledTime = slot.time,
                        medication = indexedMedicationsForSignature.first().value.copy(count = requiredCount),
                        medicationSortOrder = medicationSortOrder,
                        fulfillingEntryUuids = matchingLogs.map { it.uuid },
                        outsideScheduleWindowEntryUuids = outsideWindowLogs.map { it.uuid },
                        loggedAt = lastFulfillingEntry?.let { appliedAtAsLocalDateTime(it) },
                        lastFulfillingEntry = lastFulfillingEntry,
                        outsideScheduleWindowLoggedAt = lastOutsideWindowEntry?.let { appliedAtAsLocalDateTime(it) },
                        loggedCount = loggedCount,
                        isFulfilled = isFulfilled,
                        isDueSoon = !isFulfilled && isDueSoonSlot,
                        isPastDue = !isFulfilled && isPastDue(slotDateTime, now)
                    )
                }
            }
        }
        .sortedWith(
            compareBy<PlanDayScheduleEntry> { entry -> entry.scheduledFor }
                .thenBy { entry -> entry.groupCreatedAt }
                .thenBy { entry -> entry.medicationSortOrder }
        )

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
        .sortedBy { it.appliedAt }

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
    return Duration.between(scheduledAt, now).abs() < planDueSoonGracePeriod
}

internal fun isPastDue(
    scheduledAt: LocalDateTime,
    now: LocalDateTime
): Boolean {
    return !now.isBefore(scheduledAt.plus(planDueSoonGracePeriod))
}

private val planDueSoonGracePeriod: Duration = Duration.ofHours(1)
