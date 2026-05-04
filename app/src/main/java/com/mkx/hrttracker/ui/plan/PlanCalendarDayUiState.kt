package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSlotOccurrence
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.model.medication.isScheduledOn
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
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

internal data class PlanScheduleTimeSlot(
    val scheduleTimeUuid: UUID?,
    val scheduledFor: LocalDateTime,
) {
    val time: LocalTime
        get() = scheduledFor.toLocalTime()
}

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
    return scheduledFor?.toLocalDate() ?: appliedAt.atZone(zoneId).toLocalDate()
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

internal fun isSlotFulfilled(
    group: MedicationGroup,
    date: LocalDate,
    time: LocalTime,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    return isSlotFulfilled(
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

internal fun isSlotFulfilled(
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

    val requiredCounts = group.medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .mapValues { (_, medications) -> medications.sumOf { medication -> medication.count } }
    val loggedCounts = slotLogs
        .groupBy(MedicationSignature::fromLogEntry)
        .mapValues { (_, entriesForSignature) ->
            entriesForSignature.sumOf { entry -> entry.count }
        }

    return requiredCounts.all { (signature, requiredCount) ->
        loggedCounts.getOrDefault(signature, 0) >= requiredCount
    }
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

private fun slotRecords(
    group: MedicationGroup,
    date: LocalDate,
    time: LocalTime,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLogEntry> {
    return slotRecords(
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

private fun slotRecords(
    group: MedicationGroup,
    slot: PlanScheduleTimeSlot,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLogEntry> {
    return entries.filter { entry ->
        isEntryFulfillingPlanSlot(
            group = group,
            slot = slot,
            entry = entry,
            zoneId = zoneId,
        )
    }
}

internal fun isEntryFulfillingPlanSlot(
    group: MedicationGroup,
    slot: PlanScheduleTimeSlot,
    entry: MedicationLogEntry,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    return isEntryForPlanSlot(
        group = group,
        slot = slot,
        entry = entry,
    ) && isEntryWithinScheduleFulfillmentWindow(
        group = group,
        entry = entry,
        zoneId = zoneId
    )
}

internal fun isEntryForPlanSlot(
    group: MedicationGroup,
    slot: PlanScheduleTimeSlot,
    entry: MedicationLogEntry,
): Boolean {
    val scheduledFor = entry.scheduledFor ?: return false
    if (entry.sourceGroupUuid != group.uuid) {
        return false
    }
    return if (slot.scheduleTimeUuid != null && entry.scheduleTimeUuid != null) {
        entry.scheduleTimeUuid == slot.scheduleTimeUuid &&
            scheduledFor.toLocalDate() == slot.scheduledFor.toLocalDate()
    } else {
        scheduledFor == slot.scheduledFor
    }
}

internal fun isEntryWithinScheduleFulfillmentWindow(
    group: MedicationGroup,
    entry: MedicationLogEntry,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val scheduledFor = entry.scheduledFor ?: return false
    val appliedAt = entry.appliedAt.atZone(zoneId).toLocalDateTime()
    return isWithinScheduleFulfillmentWindow(
        scheduledFor = scheduledFor,
        appliedAt = appliedAt,
        previousScheduledFor = group.previousScheduledForBefore(scheduledFor, zoneId = zoneId),
        nextScheduledFor = group.nextScheduledForAfter(scheduledFor, zoneId = zoneId)
    )
}

internal data class MedicationSignature(
    val category: String,
    val applicationType: String,
    val selectionKind: String,
    val medicationKey: String?,
    val normalizedCustomMedicationName: String?,
    val customDoseUnit: String?,
    val doseKind: String,
    val doseValueMg: Double?,
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
) {
    companion object {
        fun fromGroupMedication(medication: MedicationGroupMedication): MedicationSignature {
            return fromMedicationDetails(medication.details)
        }

        fun fromLogEntry(entry: MedicationLogEntry): MedicationSignature {
            return fromMedicationDetails(entry.details)
        }

        private fun fromMedicationDetails(details: MedicationDetails): MedicationSignature {
            val selection = details.selection
            val dose = details.dose
            return MedicationSignature(
                category = details.category.name,
                applicationType = details.applicationType.name,
                selectionKind = selection.kind.name,
                medicationKey = when (selection) {
                    is com.mkx.hrttracker.model.medication.MedicationSelection.Catalog ->
                        selection.medicationKey.name

                    is com.mkx.hrttracker.model.medication.MedicationSelection.Custom -> null
                },
                normalizedCustomMedicationName = when (selection) {
                    is com.mkx.hrttracker.model.medication.MedicationSelection.Catalog -> null
                    is com.mkx.hrttracker.model.medication.MedicationSelection.Custom ->
                        selection.medicationName.trim().lowercase()
                },
                customDoseUnit = when {
                    selection is com.mkx.hrttracker.model.medication.MedicationSelection.Custom &&
                        dose is MedicationDose.MgAsMedicine -> details.customDoseUnit.storageValue

                    else -> null
                },
                doseKind = dose.kind.name,
                doseValueMg = when (dose) {
                    is MedicationDose.MgAsMedicine -> dose.valueMg
                    is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg
                    is MedicationDose.PatchTotalMg -> dose.valueMg
                    else -> null
                },
                doseValuePercent = when (dose) {
                    is MedicationDose.GelPercentAndWeight -> dose.percent
                    else -> null
                },
                doseWeightGrams = when (dose) {
                    is MedicationDose.GelPercentAndWeight -> dose.weightGrams
                    else -> null
                },
                doseReleaseRateMcgPerDay = when (dose) {
                    is MedicationDose.PatchReleaseRateMcgPerDay -> dose.valueMcgPerDay
                    else -> null
                }
            )
        }
    }
}
