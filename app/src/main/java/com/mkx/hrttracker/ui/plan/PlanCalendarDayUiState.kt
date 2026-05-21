package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.hasMatchingSlotRecord
import com.mkx.hrttracker.model.medication.isPlanOffPlanEntry
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.planCalendarDate
import com.mkx.hrttracker.model.medication.scheduledGroupsForPlanDay
import com.mkx.hrttracker.model.medication.scheduledSlotsForPlanDay
import java.time.LocalDate
import java.time.LocalDateTime
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
