package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.PlanCalendarDayUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HistoryMonthSummary(
    val logged: Int = 0,
    val onTrack: Int = 0,
    val partial: Int = 0,
    val missed: Int = 0,
)

internal fun buildHistoryVisibleEntries(
    entries: List<MedicationLogEntry>,
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<MedicationLogEntry> {
    val effectiveSelectedDate = selectedDate?.takeIf { YearMonth.from(it) == displayedMonth }
    return entries
        .filter { entry ->
            val entryDate = entry.appliedAt.atZone(zoneId).toLocalDate()
            if (effectiveSelectedDate != null) {
                entryDate == effectiveSelectedDate
            } else {
                YearMonth.from(entryDate) == displayedMonth
            }
        }
        .sortedByDescending { it.appliedAt }
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
    var currentDate = displayedMonth.atDay(1)
    val endDate = displayedMonth.atEndOfMonth()

    while (!currentDate.isAfter(endDate)) {
        if (!currentDate.isAfter(today)) {
            when (dayStates[currentDate]?.status ?: PlanCalendarDayStatus.NONE) {
                PlanCalendarDayStatus.FULFILLED -> onTrack++
                PlanCalendarDayStatus.PARTIAL -> partial++
                PlanCalendarDayStatus.SCHEDULED -> {
                    if (currentDate.isBefore(today)) {
                        missed++
                    }
                }
                PlanCalendarDayStatus.NONE,
                PlanCalendarDayStatus.UNPLANNED -> Unit
            }
        }
        currentDate = currentDate.plusDays(1)
    }

    return HistoryMonthSummary(
        logged = logged,
        onTrack = onTrack,
        partial = partial,
        missed = missed
    )
}
