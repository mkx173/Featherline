package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.dayBoundary
import com.mkx.hrttracker.model.medication.hasMatchingSlotRecord
import com.mkx.hrttracker.model.medication.isPastDue
import com.mkx.hrttracker.model.medication.isPlanOffPlanEntry
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.planCalendarDate
import com.mkx.hrttracker.model.medication.scheduledGroupsForPlanDay
import com.mkx.hrttracker.model.medication.scheduledSlotsForPlanDay
import java.time.LocalDate
import java.time.LocalDateTime
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
    /** Untaken slots that aren't past due yet: not missed, still to come. */
    val pendingOccurrenceCount: Int = 0,
) {
    /** A dose is missed only once it's past due, the moment the Plan tab row reads missed. */
    val hasMissedDose: Boolean
        get() = expectedOccurrenceCount - matchedOccurrenceCount - pendingOccurrenceCount > 0

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
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
    /** Every plan a record can come from, archived ones too; [groups] may be filtered. */
    groupsByUuid: Map<UUID, MedicationGroup>,
): Map<LocalDate, PlanCalendarDayUiState> {
    // Each record sits on the day its own plan's ending gives it; one not from a plan on its calendar day.
    val entriesByDate = entries.groupBy { entry ->
        entry.planCalendarDate(entry.dayBoundary(groupsByUuid), zoneId)
    }

    val dayStates = linkedMapOf<LocalDate, PlanCalendarDayUiState>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
        val dayEntries = entriesByDate[currentDate].orEmpty()
        val scheduledGroups = groups.scheduledGroupsForPlanDay(
            date = currentDate,
            entries = dayEntries,
            zoneId = zoneId,
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
                    zoneId = zoneId,
                )
            }
        }
        val matchedOccurrenceCount = scheduledSlotsByGroup.entries.sumOf { (group, slots) ->
            slots.count { slot ->
                isSlotFulfilled(group, slot, dayEntries, zoneId)
            }
        }.coerceAtMost(expectedOccurrenceCount)
        val pendingOccurrenceCount = scheduledSlotsByGroup.entries.sumOf { (group, slots) ->
            slots.count { slot ->
                !isPastDue(slot.scheduledFor, now) && !isSlotFulfilled(group, slot, dayEntries, zoneId)
            }
        }

        dayStates[currentDate] = PlanCalendarDayUiState(
            expectedOccurrenceCount = expectedOccurrenceCount,
            matchedOccurrenceCount = matchedOccurrenceCount,
            hasOffPlanRecord = hasOffPlanRecord,
            hasMatchingScheduledRecord = hasMatchingScheduledRecord,
            pendingOccurrenceCount = pendingOccurrenceCount,
        )
        currentDate = currentDate.plusDays(1)
    }

    return dayStates
}
