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

internal fun historyEntryTapAction(
    selectedEntryIds: Set<UUID>
): HistoryEntryTapAction {
    return if (selectedEntryIds.isEmpty()) {
        HistoryEntryTapAction.OPEN_EDITOR
    } else {
        HistoryEntryTapAction.TOGGLE_SELECTION
    }
}
