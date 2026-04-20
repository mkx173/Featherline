package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.model.medication.isScheduledOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
            group.schedule.times.count { time ->
                isSlotFulfilled(group, currentDate, time, entries)
            }
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

internal fun isSlotFulfilled(
    group: MedicationGroup,
    date: LocalDate,
    time: LocalTime,
    entries: List<MedicationLogEntry>
): Boolean {
    if (group.medications.isEmpty()) {
        return false
    }

    val slotDateTime = LocalDateTime.of(date, time)
    val slotLogs = entries.filter { entry ->
        entry.sourceGroupUuid == group.uuid && entry.scheduledFor == slotDateTime
    }
    if (slotLogs.isEmpty()) {
        return false
    }

    val requiredCounts = group.medications
        .groupingBy(MedicationSignature::fromGroupMedication)
        .eachCount()
    val loggedCounts = slotLogs
        .groupingBy(MedicationSignature::fromLogEntry)
        .eachCount()

    return requiredCounts.all { (signature, requiredCount) ->
        loggedCounts.getOrDefault(signature, 0) >= requiredCount
    }
}

internal data class MedicationSignature(
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
