package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isScheduledOn
import com.mkx.hrttracker.ui.plan.MedicationSignature
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.PlanCalendarDayUiState
import com.mkx.hrttracker.ui.plan.buildPlanCalendarDayUiState
import com.mkx.hrttracker.ui.plan.planCalendarDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class HistoryCalendarDayUiState(
    val status: PlanCalendarDayStatus = PlanCalendarDayStatus.NONE,
    val hasOffPlanRecord: Boolean = false,
)

data class HistoryMonthSummary(
    val logged: Int = 0,
    val onTrack: Int = 0,
    val partial: Int = 0,
    val missed: Int = 0,
    val offPlan: Int = 0,
)

internal enum class HistoryEntryTapAction {
    OPEN_EDITOR,
    TOGGLE_SELECTION,
}

internal fun buildHistoryVisibleEntries(
    entries: List<MedicationLogEntry>,
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<MedicationLogEntry> {
    return entries
        .filter { entry ->
            val entryDate = entry.appliedAt.atZone(zoneId).toLocalDate()
            if (selectedDate != null) {
                entryDate == selectedDate
            } else {
                YearMonth.from(entryDate) == displayedMonth
            }
        }
        .sortedByDescending { it.appliedAt }
}

internal fun canSelectHistoryCalendarDate(
    date: LocalDate,
    today: LocalDate
): Boolean {
    return !date.isAfter(today)
}

internal fun resolveHistoryEffectiveSelectedDate(
    displayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
    selectedDate: LocalDate?
): LocalDate? {
    return if (
        pendingSelectedDate != null &&
        YearMonth.from(pendingSelectedDate) == displayedMonth
    ) {
        pendingSelectedDate
    } else {
        selectedDate
    }
}

internal fun resolveHistoryDisplayedSelectedDate(
    displayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
    selectedDate: LocalDate?
): LocalDate? {
    return if (pendingSelectedDate != null) {
        pendingSelectedDate.takeIf { YearMonth.from(it) == displayedMonth }
    } else {
        selectedDate?.takeIf { YearMonth.from(it) == displayedMonth }
    }
}

internal fun shouldClearHistorySelectionOnMonthChange(
    displayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?
): Boolean {
    return pendingSelectedDate?.let { YearMonth.from(it) != displayedMonth } ?: true
}

internal fun buildHistoryMonthSummary(
    entries: List<MedicationLogEntry>,
    displayedMonth: YearMonth,
    dayStates: Map<LocalDate, HistoryCalendarDayUiState>,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): HistoryMonthSummary {
    val logged = entries.count { entry ->
        YearMonth.from(entry.appliedAt.atZone(zoneId).toLocalDate()) == displayedMonth
    }

    var onTrack = 0
    var partial = 0
    var missed = 0
    var offPlan = 0
    var currentDate = displayedMonth.atDay(1)
    val endDate = displayedMonth.atEndOfMonth()

    while (!currentDate.isAfter(endDate)) {
        if (!currentDate.isAfter(today)) {
            val dayState = dayStates[currentDate] ?: HistoryCalendarDayUiState()
            when (dayState.status) {
                PlanCalendarDayStatus.FULFILLED -> onTrack++
                PlanCalendarDayStatus.PARTIAL -> partial++
                PlanCalendarDayStatus.MISSED -> {
                    if (currentDate.isBefore(today)) {
                        missed++
                    }
                }
                PlanCalendarDayStatus.OFFPLAN,
                PlanCalendarDayStatus.NONE -> Unit
            }
            if (dayState.hasOffPlanRecord) {
                offPlan++
            }
        }
        currentDate = currentDate.plusDays(1)
    }

    return HistoryMonthSummary(
        logged = logged,
        onTrack = onTrack,
        partial = partial,
        missed = missed,
        offPlan = offPlan
    )
}

internal fun buildHistoryCalendarDayUiState(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<LocalDate, HistoryCalendarDayUiState> {
    val rangeEntries = entries.filter { entry ->
        val entryDate = entry.planCalendarDate(zoneId)
        !entryDate.isBefore(startDate) && !entryDate.isAfter(endDate)
    }
    val planDayStates = buildPlanCalendarDayUiState(
        groups = groups,
        entries = rangeEntries,
        startDate = startDate,
        endDate = endDate,
        zoneId = zoneId
    )
    val entriesByDate = rangeEntries.groupBy { entry ->
        entry.planCalendarDate(zoneId)
    }

    val dayStates = linkedMapOf<LocalDate, HistoryCalendarDayUiState>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
        val scheduledGroups = groups.filter { group -> group.schedule.isScheduledOn(currentDate) }
        val dayEntries = entriesByDate[currentDate].orEmpty()
        val primaryState = planDayStates[currentDate] ?: PlanCalendarDayUiState()
        dayStates[currentDate] = HistoryCalendarDayUiState(
            status = primaryState.status,
            hasOffPlanRecord = dayEntries.any { entry ->
                isHistoryOffPlanEntry(
                    entry = entry,
                    scheduledGroups = scheduledGroups,
                    date = currentDate,
                )
            }
        )
        currentDate = currentDate.plusDays(1)
    }

    return dayStates
}

internal fun groupHistoryEntriesByDate(
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<LocalDate, List<MedicationLogEntry>> {
    return entries
        .groupBy { entry -> entry.appliedAt.atZone(zoneId).toLocalDate() }
        .mapValues { (_, dateEntries) ->
            dateEntries.sortedBy { entry -> entry.appliedAt }
        }
}

internal fun toggleHistoryEntrySelection(
    currentSelection: Set<UUID>,
    entryId: UUID
): Set<UUID> {
    return if (entryId in currentSelection) {
        currentSelection - entryId
    } else {
        currentSelection + entryId
    }
}

internal fun selectAllHistoryEntries(
    currentSelection: Set<UUID>,
    entryIds: Set<UUID>
): Set<UUID> {
    return currentSelection + entryIds
}

internal fun reverseHistoryEntrySelection(
    currentSelection: Set<UUID>,
    entryIds: Set<UUID>
): Set<UUID> {
    val preservedSelection = currentSelection - entryIds
    val reversedVisibleSelection = entryIds - currentSelection
    return preservedSelection + reversedVisibleSelection
}

internal fun historyEntryTapAction(
    selectedEntryIds: Set<UUID>
): HistoryEntryTapAction {
    return if (selectedEntryIds.isEmpty()) {
        HistoryEntryTapAction.OPEN_EDITOR
    } else {
        HistoryEntryTapAction.TOGGLE_SELECTION
    }
}

private fun isHistoryOffPlanEntry(
    entry: MedicationLogEntry,
    scheduledGroups: List<MedicationGroup>,
    date: LocalDate,
): Boolean {
    val sourceGroupUuid = entry.sourceGroupUuid ?: return true
    val scheduledFor = entry.scheduledFor ?: return true
    if (scheduledFor.toLocalDate() != date) {
        return true
    }

    val group = scheduledGroups.firstOrNull { scheduledGroup -> scheduledGroup.uuid == sourceGroupUuid }
        ?: return true
    if (scheduledFor.toLocalTime() !in group.schedule.times) {
        return true
    }

    val requiredSignatures = group.medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .keys
    return MedicationSignature.fromLogEntry(entry) !in requiredSignatures
}
