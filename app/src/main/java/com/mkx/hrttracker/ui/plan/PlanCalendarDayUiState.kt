package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationGroupSlotOccurrence
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.isScheduledOn
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.slotRecords
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class PlanCalendarDayStatus {
    NONE,
    OFFPLAN,
    MISSED,
    PARTIAL,
    FULFILLED,
}

data class PlanCalendarDayUiState(
    val expectedOccurrenceCount: Int = 0,
    val matchedOccurrenceCount: Int = 0,
    val hasOffPlanRecord: Boolean = false,
    val hasMatchingScheduledRecord: Boolean = false,
) {
    val status: PlanCalendarDayStatus
        get() = when {
            expectedOccurrenceCount <= 0 && hasOffPlanRecord -> PlanCalendarDayStatus.OFFPLAN
            expectedOccurrenceCount <= 0 -> PlanCalendarDayStatus.NONE
            matchedOccurrenceCount <= 0 && !hasMatchingScheduledRecord -> PlanCalendarDayStatus.MISSED
            matchedOccurrenceCount < expectedOccurrenceCount -> PlanCalendarDayStatus.PARTIAL
            else -> PlanCalendarDayStatus.FULFILLED
        }
}

internal typealias PlanScheduleTimeSlot = MedicationGroupSlotKey

internal val PlanScheduleTimeSlot.time: LocalTime
    get() = scheduledFor.toLocalTime()

fun buildPlanCalendarDayUiState(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): Map<LocalDate, PlanCalendarDayUiState> {
    val entriesByDate = entries.groupBy { entry ->
        entry.planCalendarDate(zoneId)
    }

    val dayStates = linkedMapOf<LocalDate, PlanCalendarDayUiState>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
        val dayEntries = entriesByDate[currentDate].orEmpty()
        val scheduledGroups = groups.scheduledGroupsForPlanDay(
            date = currentDate,
            entries = dayEntries,
        )
        val hasOffPlanRecord = dayEntries.any { entry ->
            isPlanOffPlanEntry(
                entry = entry,
                scheduledGroups = scheduledGroups,
                date = currentDate,
                zoneId = zoneId,
            )
        }
        val scheduledSlotsByGroup = scheduledGroups.associateWith { group ->
            group.scheduledSlotsForPlanDay(
                date = currentDate,
                entries = dayEntries,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
                unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
            )
        }
        val expectedOccurrenceCount = scheduledSlotsByGroup.values.sumOf { slots -> slots.size }
        val hasMatchingScheduledRecord = scheduledSlotsByGroup.any { (group, slots) ->
            slots.any { slot ->
                hasMatchingSlotRecord(
                    group = group,
                    slot = slot,
                    entries = dayEntries,
                    zoneId = zoneId
                )
            }
        }
        val matchedOccurrenceCount = scheduledSlotsByGroup.entries.sumOf { (group, slots) ->
            slots.count { slot ->
                isSlotFulfilled(group, slot, dayEntries, zoneId)
            }
        }.coerceAtMost(expectedOccurrenceCount)

        dayStates[currentDate] = PlanCalendarDayUiState(
            expectedOccurrenceCount = expectedOccurrenceCount,
            matchedOccurrenceCount = matchedOccurrenceCount,
            hasOffPlanRecord = hasOffPlanRecord,
            hasMatchingScheduledRecord = hasMatchingScheduledRecord
        )
        currentDate = currentDate.plusDays(1)
    }

    return dayStates
}

internal fun MedicationLogEntry.planCalendarDate(zoneId: ZoneId): LocalDate {
    return scheduledFor?.toLocalDate() ?: appliedAtAsLocalDateTime(this, zoneId).toLocalDate()
}

internal fun List<MedicationGroup>.scheduledGroupsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
): List<MedicationGroup> {
    return filter { group ->
        group.schedule.isScheduledOn(date) ||
            entries.any { entry ->
                entry.sourceGroupUuid == group.uuid &&
                    entry.scheduledFor?.toLocalDate() == date &&
                    group.hasMedicationSignatureFor(entry)
            }
    }
}

internal fun isPlanOffPlanEntry(
    entry: MedicationLogEntry,
    scheduledGroups: List<MedicationGroup>,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val sourceGroupUuid = entry.sourceGroupUuid ?: return true
    val scheduledFor = entry.scheduledFor ?: return true
    if (scheduledFor.toLocalDate() != date) {
        return true
    }

    val group = scheduledGroups.firstOrNull { scheduledGroup -> scheduledGroup.uuid == sourceGroupUuid }
        ?: return true
    return !group.hasMedicationSignatureFor(entry)
}

internal fun MedicationGroup.scheduledTimesInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalTime> {
    return scheduledSlotsInPlanWindow(date, zoneId).map(PlanScheduleTimeSlot::time)
}

internal fun MedicationGroup.scheduledTimesForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): List<LocalTime> {
    return scheduledSlotsForPlanDay(
        date = date,
        entries = entries,
        zoneId = zoneId,
        includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
        unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
    ).map(PlanScheduleTimeSlot::time)
}

internal fun MedicationGroup.scheduledSlotsInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<PlanScheduleTimeSlot> {
    return occurrencesBetweenInPlanWindow(date, date, zoneId).map { occurrence ->
        occurrence.toPlanScheduleTimeSlot()
    }
}

internal fun MedicationGroup.scheduledSlotsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): List<PlanScheduleTimeSlot> {
    val visibleSlots = scheduledSlotsInPlanWindow(date, zoneId).filter { slot ->
        isArchivedUnloggedPlanSlotVisible(
            slotDateTime = slot.scheduledFor,
            includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
            unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
        )
    }
    val loggedSlots = entries.mapNotNull { entry ->
        val scheduledFor = entry.scheduledFor ?: return@mapNotNull null
        if (entry.sourceGroupUuid == uuid &&
            scheduledFor.toLocalDate() == date &&
            hasMedicationSignatureFor(entry)
        ) {
            if (entry.scheduleTimeUuid != null &&
                visibleSlots.any { slot ->
                    slot.scheduleTimeUuid == entry.scheduleTimeUuid &&
                        slot.scheduledFor.toLocalDate() == scheduledFor.toLocalDate()
                }
            ) {
                return@mapNotNull null
            }
            PlanScheduleTimeSlot(
                scheduleTimeUuid = entry.scheduleTimeUuid,
                scheduledFor = scheduledFor,
            )
        } else {
            null
        }
    }

    return (visibleSlots + loggedSlots)
        .distinctBy(PlanScheduleTimeSlot::scheduledFor)
        .sortedBy { slot -> slot.scheduledFor }
}

private fun MedicationGroup.isArchivedUnloggedPlanSlotVisible(
    slotDateTime: LocalDateTime,
    includeUnloggedArchivedSlots: Boolean,
    unloggedArchivedSlotCutoff: LocalDateTime?,
): Boolean {
    return archivedAtLocal == null ||
        includeUnloggedArchivedSlots ||
        unloggedArchivedSlotCutoff?.let { cutoff -> slotDateTime.isBefore(cutoff) } == true
}

private fun MedicationGroup.hasMedicationSignatureFor(entry: MedicationLogEntry): Boolean {
    val requiredSignatures = medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .keys
    return MedicationSignature.fromLogEntry(entry) in requiredSignatures
}

private fun MedicationGroupSlotOccurrence.toPlanScheduleTimeSlot(): PlanScheduleTimeSlot {
    return PlanScheduleTimeSlot(
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledFor = scheduledFor,
    )
}

internal fun hasMatchingSlotRecord(
    group: MedicationGroup,
    date: LocalDate,
    time: LocalTime,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    return hasMatchingSlotRecord(
        group = group,
        slot = PlanScheduleTimeSlot(
            scheduleTimeUuid = group.schedule.timeSlots
                .firstOrNull { slot -> slot.time == time }
                ?.uuid,
            scheduledFor = LocalDateTime.of(date, time),
        ),
        entries = entries,
        zoneId = zoneId,
    )
}

internal fun hasMatchingSlotRecord(
    group: MedicationGroup,
    slot: PlanScheduleTimeSlot,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (group.medications.isEmpty()) {
        return false
    }

    val slotLogs = slotRecords(group, slot, entries, zoneId)
    if (slotLogs.isEmpty()) {
        return false
    }

    val requiredSignatures = group.medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .keys
    val loggedSignatures = slotLogs
        .map(MedicationSignature::fromLogEntry)
        .toSet()

    return requiredSignatures.any(loggedSignatures::contains)
}


