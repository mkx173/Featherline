package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.model.medication.isScheduledOn
import java.time.LocalDate
import java.time.ZoneId

enum class PlanCalendarDayStatus {
    NONE,
    UNPLANNED,
    SCHEDULED,
    PARTIAL,
    FULFILLED,
}

data class PlanCalendarDayUiState(
    val expectedOccurrenceCount: Int = 0,
    val matchedOccurrenceCount: Int = 0,
    val hasUnplannedRecord: Boolean = false,
) {
    val status: PlanCalendarDayStatus
        get() = when {
            expectedOccurrenceCount <= 0 && hasUnplannedRecord -> PlanCalendarDayStatus.UNPLANNED
            expectedOccurrenceCount <= 0 -> PlanCalendarDayStatus.NONE
            matchedOccurrenceCount <= 0 -> PlanCalendarDayStatus.SCHEDULED
            matchedOccurrenceCount < expectedOccurrenceCount -> PlanCalendarDayStatus.PARTIAL
            else -> PlanCalendarDayStatus.FULFILLED
        }
}

fun buildPlanCalendarDayUiState(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    startDate: LocalDate,
    endDate: LocalDate
): Map<LocalDate, PlanCalendarDayUiState> {
    val entriesByDate = entries.groupBy { entry ->
        entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    val dayStates = linkedMapOf<LocalDate, PlanCalendarDayUiState>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
        val scheduledGroups = groups.filter { group -> group.schedule.isScheduledOn(currentDate) }
        val dayEntries = entriesByDate[currentDate].orEmpty()
        val expectedOccurrenceCount = scheduledGroups.sumOf { group ->
            group.schedule.times.size
        }
        val matchedOccurrenceCount = scheduledGroups.sumOf { group ->
            countFulfilledOccurrences(
                group = group,
                dayEntries = dayEntries
            )
        }.coerceAtMost(expectedOccurrenceCount)

        dayStates[currentDate] = PlanCalendarDayUiState(
            expectedOccurrenceCount = expectedOccurrenceCount,
            matchedOccurrenceCount = matchedOccurrenceCount,
            hasUnplannedRecord = expectedOccurrenceCount == 0 && dayEntries.isNotEmpty()
        )
        currentDate = currentDate.plusDays(1)
    }

    return dayStates
}

private fun countFulfilledOccurrences(
    group: MedicationGroup,
    dayEntries: List<MedicationLogEntry>
): Int {
    if (group.medications.isEmpty()) {
        return 0
    }

    val requiredCounts = group.medications
        .groupingBy(MedicationSignature::fromGroupMedication)
        .eachCount()

    return dayEntries
        .groupBy { entry ->
            entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
        }
        .count { (_, entriesAtTime) ->
            val matchedEntries = entriesAtTime
                .filter { entry ->
                    entry.sourceGroupUuid == group.uuid ||
                        entry.sourceType == MedicationLogEntrySourceType.MANUAL
                }
                .groupingBy(MedicationSignature::fromLogEntry)
                .eachCount()

            requiredCounts.all { (signature, requiredCount) ->
                matchedEntries.getOrDefault(signature, 0) >= requiredCount
            }
        }
}

private data class MedicationSignature(
    val routeOfAdministration: RouteOfAdministration,
    val normalizedMedicineName: String,
    val dosageMgAsMedicine: Double,
) {
    companion object {
        fun fromGroupMedication(medication: MedicationGroupMedication): MedicationSignature {
            return MedicationSignature(
                routeOfAdministration = medication.routeOfAdministration,
                normalizedMedicineName = medication.medicineName.trim().lowercase(),
                dosageMgAsMedicine = medication.dosageMgAsMedicine
            )
        }

        fun fromLogEntry(entry: MedicationLogEntry): MedicationSignature {
            return MedicationSignature(
                routeOfAdministration = entry.routeOfAdministration,
                normalizedMedicineName = entry.medicineName.trim().lowercase(),
                dosageMgAsMedicine = entry.dosageMgAsMedicine
            )
        }
    }
}
