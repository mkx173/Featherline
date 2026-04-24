package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.PlanCalendarDayUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class HistoryMonthSummary(
    val logged: Int = 0,
    val onTrack: Int = 0,
    val partial: Int = 0,
    val missed: Int = 0,
    val offPlan: Int = 0,
)

data class HistoryCollapsedEntry(
    val representativeEntry: MedicationLogEntry,
    val entryIds: Set<UUID>,
    val count: Int,
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
    dayStates: Map<LocalDate, PlanCalendarDayUiState>,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): HistoryMonthSummary {
    val logged = collapseHistoryEntries(
        entries.filter { entry ->
            YearMonth.from(entry.appliedAt.atZone(zoneId).toLocalDate()) == displayedMonth
        }
    ).size

    var onTrack = 0
    var partial = 0
    var missed = 0
    var offPlan = 0
    var currentDate = displayedMonth.atDay(1)
    val endDate = displayedMonth.atEndOfMonth()

    while (!currentDate.isAfter(endDate)) {
        if (!currentDate.isAfter(today)) {
            when (dayStates[currentDate]?.status ?: PlanCalendarDayStatus.NONE) {
                PlanCalendarDayStatus.FULFILLED -> onTrack++
                PlanCalendarDayStatus.PARTIAL -> partial++
                PlanCalendarDayStatus.MISSED -> {
                    if (currentDate.isBefore(today)) {
                        missed++
                    }
                }
                PlanCalendarDayStatus.OFFPLAN -> offPlan++
                PlanCalendarDayStatus.NONE,
                -> Unit
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

internal fun collapseHistoryEntries(
    entries: List<MedicationLogEntry>
): List<HistoryCollapsedEntry> {
    return entries
        .sortedByDescending { entry -> entry.appliedAt }
        .map { entry ->
            HistoryCollapsedEntry(
                representativeEntry = entry,
                entryIds = setOf(entry.uuid),
                count = entry.count
            )
        }
}

internal fun groupHistoryEntriesByDate(
    collapsedEntries: List<HistoryCollapsedEntry>,
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<LocalDate, List<HistoryCollapsedEntry>> {
    return collapsedEntries
        .groupBy { collapsedEntry ->
            collapsedEntry.representativeEntry.appliedAt.atZone(zoneId).toLocalDate()
        }
        .mapValues { (_, dateEntries) ->
            dateEntries.sortedBy { collapsedEntry -> collapsedEntry.representativeEntry.appliedAt }
        }
}

internal fun isHistoryCollapsedEntrySelected(
    selectedEntryIds: Set<UUID>,
    entryIds: Set<UUID>
): Boolean {
    return entryIds.any { entryId -> entryId in selectedEntryIds }
}

internal fun toggleHistoryEntrySelection(
    currentSelection: Set<UUID>,
    entryIds: Set<UUID>
): Set<UUID> {
    return if (isHistoryCollapsedEntrySelected(currentSelection, entryIds)) {
        currentSelection - entryIds
    } else {
        currentSelection + entryIds
    }
}

internal fun countSelectedCollapsedEntries(
    selectedEntryIds: Set<UUID>,
    collapsedEntries: List<HistoryCollapsedEntry>
): Int {
    return collapsedEntries.count { collapsedEntry ->
        isHistoryCollapsedEntrySelected(
            selectedEntryIds = selectedEntryIds,
            entryIds = collapsedEntry.entryIds
        )
    }
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
