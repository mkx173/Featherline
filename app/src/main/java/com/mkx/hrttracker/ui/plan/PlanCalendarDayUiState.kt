package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isScheduledOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
        val scheduledGroups = groups.filter { group -> group.schedule.isScheduledOn(currentDate) }
        val dayEntries = entriesByDate[currentDate].orEmpty()
        val hasOffPlanRecord = dayEntries.any { entry ->
            isPlanOffPlanEntry(
                entry = entry,
                scheduledGroups = scheduledGroups,
                date = currentDate,
                zoneId = zoneId,
            )
        }
        val scheduledTimesByGroup = scheduledGroups.associateWith { group ->
            group.scheduledTimesForPlanDay(
                date = currentDate,
                entries = dayEntries,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
                unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
            )
        }
        val expectedOccurrenceCount = scheduledTimesByGroup.values.sumOf { times -> times.size }
        val hasMatchingScheduledRecord = scheduledTimesByGroup.any { (group, times) ->
            times.any { time ->
                hasMatchingSlotRecord(
                    group = group,
                    date = currentDate,
                    time = time,
                    entries = dayEntries
                )
            }
        }
        val matchedOccurrenceCount = scheduledTimesByGroup.entries.sumOf { (group, times) ->
            times.count { time ->
                isSlotFulfilled(group, currentDate, time, dayEntries)
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
    if (!group.hasScheduledSlot(scheduledFor, zoneId)) {
        return true
    }

    val requiredSignatures = group.medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .keys
    return MedicationSignature.fromLogEntry(entry) !in requiredSignatures
}

internal fun MedicationGroup.scheduledTimesInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalTime> {
    if (!schedule.isScheduledOn(date)) {
        return emptyList()
    }

    return schedule.times.filter { time ->
        isMedicationGroupSlotInPlanWindow(
            group = this,
            slotDateTime = LocalDateTime.of(date, time),
            zoneId = zoneId,
        )
    }
}

internal fun MedicationGroup.scheduledTimesForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): List<LocalTime> {
    val visibleTimes = scheduledTimesInPlanWindow(date, zoneId).filter { time ->
        isArchivedPlanSlotVisible(
            slotDateTime = LocalDateTime.of(date, time),
            includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
            unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
        )
    }
    val loggedTimes = entries.mapNotNull { entry ->
        val scheduledFor = entry.scheduledFor ?: return@mapNotNull null
        if (entry.sourceGroupUuid == uuid &&
            scheduledFor.toLocalDate() == date &&
            isMedicationGroupSlotInPlanWindow(
                group = this,
                slotDateTime = scheduledFor,
                zoneId = zoneId,
            ) &&
            isArchivedPlanSlotVisible(
                slotDateTime = scheduledFor,
                includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
                unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
            ) &&
            hasScheduledSlot(scheduledFor, zoneId) &&
            hasMedicationSignatureFor(entry)
        ) {
            scheduledFor.toLocalTime()
        } else {
            null
        }
    }

    return (visibleTimes + loggedTimes).distinct().sorted()
}

private fun MedicationGroup.isArchivedPlanSlotVisible(
    slotDateTime: LocalDateTime,
    includeUnloggedArchivedSlots: Boolean,
    unloggedArchivedSlotCutoff: LocalDateTime?,
): Boolean {
    return archivedAt == null ||
        includeUnloggedArchivedSlots ||
        unloggedArchivedSlotCutoff?.let { cutoff -> slotDateTime.isBefore(cutoff) } == true
}

private fun isMedicationGroupSlotInPlanWindow(
    group: MedicationGroup,
    slotDateTime: LocalDateTime,
    zoneId: ZoneId,
): Boolean {
    if (!group.includePastScheduledSlots) {
        val createdAt = group.createdAt.atZone(zoneId).toLocalDateTime()
        if (slotDateTime.isBefore(createdAt)) {
            return false
        }
    }

    val archivedAt = group.archivedAt ?: return true
    val archivedDate = archivedAt.atZone(zoneId).toLocalDate()
    return !slotDateTime.toLocalDate().isAfter(archivedDate)
}

private fun MedicationGroup.hasScheduledSlot(
    scheduledFor: LocalDateTime,
    zoneId: ZoneId,
): Boolean {
    val date = scheduledFor.toLocalDate()
    if (!schedule.isScheduledOn(date) || scheduledFor.toLocalTime() !in schedule.times) {
        return false
    }

    val archivedAt = this.archivedAt ?: return true
    val archivedDate = archivedAt.atZone(zoneId).toLocalDate()
    return !date.isAfter(archivedDate)
}

private fun MedicationGroup.hasMedicationSignatureFor(entry: MedicationLogEntry): Boolean {
    val requiredSignatures = medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .keys
    return MedicationSignature.fromLogEntry(entry) in requiredSignatures
}

internal fun isSlotFulfilled(
    group: MedicationGroup,
    date: LocalDate,
    time: LocalTime,
    entries: List<MedicationLogEntry>
): Boolean {
    if (group.medications.isEmpty()) {
        return false
    }

    val slotLogs = slotRecords(group, date, time, entries)
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
    entries: List<MedicationLogEntry>
): Boolean {
    if (group.medications.isEmpty()) {
        return false
    }

    val slotLogs = slotRecords(group, date, time, entries)
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
    entries: List<MedicationLogEntry>
): List<MedicationLogEntry> {
    val slotDateTime = LocalDateTime.of(date, time)
    return entries.filter { entry ->
        entry.sourceGroupUuid == group.uuid && entry.scheduledFor == slotDateTime
    }
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
