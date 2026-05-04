package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isScheduledOn
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.PlanCalendarDayUiState
import com.mkx.hrttracker.ui.plan.buildPlanCalendarDayUiState
import com.mkx.hrttracker.ui.plan.isEntryWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.ui.plan.isPlanOffPlanEntry
import com.mkx.hrttracker.ui.plan.planCalendarDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

internal fun canSelectHistoryCalendarDate(
    date: LocalDate,
    today: LocalDate,
    targetMonth: YearMonth?,
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
): Boolean {
    if (!canSelectHistoryCalendarDate(date = date, today = today)) {
        return false
    }
    val adjacentTargetMonth = targetMonth ?: return true
    return !adjacentTargetMonth.isBefore(calendarStartMonth) &&
        !adjacentTargetMonth.isAfter(calendarEndMonth)
}

internal fun resolveHistoryEffectiveSelectedDate(
    displayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
    selectedDate: LocalDate?,
    pendingSelectionResetTargetMonth: YearMonth? = null,
): LocalDate? {
    if (pendingSelectionResetTargetMonth == displayedMonth) {
        return null
    }
    if (pendingSelectionResetTargetMonth != null) {
        return selectedDate
    }
    if (pendingSelectedDate != null) {
        return if (YearMonth.from(pendingSelectedDate) == displayedMonth) {
            pendingSelectedDate
        } else {
            selectedDate
        }
    }
    return selectedDate?.takeIf { date -> YearMonth.from(date) == displayedMonth }
}

internal fun resolveHistoryCalendarSelectedDate(
    displayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
    selectedDate: LocalDate?,
    pendingSelectionResetTargetMonth: YearMonth? = null,
): LocalDate? {
    if (pendingSelectionResetTargetMonth == displayedMonth) {
        return null
    }
    return pendingSelectedDate ?: selectedDate
}

internal fun shouldCommitPendingHistorySelection(
    settledDisplayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
    selectedDate: LocalDate?,
): Boolean {
    val pendingDate = pendingSelectedDate ?: return false
    return YearMonth.from(pendingDate) == settledDisplayedMonth &&
        selectedDate != pendingDate
}

internal fun shouldClearPendingHistorySelection(
    settledDisplayedMonth: YearMonth,
    navigationMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
): Boolean {
    val targetMonth = pendingSelectedDate?.let(YearMonth::from) ?: return false
    return targetMonth != settledDisplayedMonth && targetMonth != navigationMonth
}

internal fun shouldClearHistorySelectionOnMonthChange(
    displayedMonth: YearMonth,
    pendingSelectedDate: LocalDate?,
    selectedDate: LocalDate?,
    pendingSelectionResetTargetMonth: YearMonth? = null,
): Boolean {
    if (pendingSelectionResetTargetMonth != null) {
        return displayedMonth == pendingSelectionResetTargetMonth
    }
    return when {
        pendingSelectedDate != null -> YearMonth.from(pendingSelectedDate) != displayedMonth
        selectedDate != null -> YearMonth.from(selectedDate) != displayedMonth
        else -> true
    }
}

internal fun canResetHistoryCalendar(
    navigationMonth: YearMonth,
    currentMonth: YearMonth,
    hasResettableSelection: Boolean,
): Boolean {
    return navigationMonth != currentMonth || hasResettableSelection
}

internal fun shouldAnimateHistoryCalendarReset(
    navigationMonth: YearMonth,
    currentMonth: YearMonth,
    animationThresholdMonths: Long = historyCalendarResetAnimationThresholdMonths,
): Boolean {
    return kotlin.math.abs(ChronoUnit.MONTHS.between(navigationMonth, currentMonth)) <=
        animationThresholdMonths
}

internal fun historyMonthPickerYearOptions(
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
): List<Int> {
    return (calendarStartMonth.year..calendarEndMonth.year).toList()
}

internal fun historyMonthPickerMonthOptions(
    selectedYear: Int,
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
): List<Int> {
    val firstMonth = if (selectedYear == calendarStartMonth.year) {
        calendarStartMonth.monthValue
    } else {
        1
    }
    val lastMonth = if (selectedYear == calendarEndMonth.year) {
        calendarEndMonth.monthValue
    } else {
        12
    }
    return if (firstMonth <= lastMonth) {
        (firstMonth..lastMonth).toList()
    } else {
        emptyList()
    }
}

internal fun coerceHistoryMonthPickerSelection(
    selectedYear: Int,
    selectedMonthValue: Int,
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
): YearMonth {
    return YearMonth.of(
        selectedYear.coerceIn(calendarStartMonth.year, calendarEndMonth.year),
        selectedMonthValue.coerceIn(1, 12)
    ).coerceIn(calendarStartMonth, calendarEndMonth)
}

private const val historyCalendarResetAnimationThresholdMonths = 6L

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
        val appliedDate = entry.appliedAt.atZone(zoneId).toLocalDate()
        (!entryDate.isBefore(startDate) && !entryDate.isAfter(endDate)) ||
            (!appliedDate.isBefore(startDate) && !appliedDate.isAfter(endDate))
    }
    val planDayStates = buildPlanCalendarDayUiState(
        groups = groups,
        entries = rangeEntries,
        startDate = startDate,
        endDate = endDate,
        zoneId = zoneId
    )
    val entriesByPlanDate = rangeEntries.groupBy { entry ->
        entry.planCalendarDate(zoneId)
    }
    val entriesByAppliedDate = rangeEntries.groupBy { entry ->
        entry.appliedAt.atZone(zoneId).toLocalDate()
    }

    val dayStates = linkedMapOf<LocalDate, HistoryCalendarDayUiState>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
        val scheduledGroups = groups.filter { group -> group.schedule.isScheduledOn(currentDate) }
        val planDateEntries = entriesByPlanDate[currentDate].orEmpty()
        val appliedDateEntries = entriesByAppliedDate[currentDate].orEmpty()
        val primaryState = planDayStates[currentDate] ?: PlanCalendarDayUiState()
        val hasOffPlanRecord = planDateEntries.any { entry ->
            isPlanOffPlanEntry(
                entry = entry,
                scheduledGroups = scheduledGroups,
                date = currentDate,
                zoneId = zoneId,
            )
        } || appliedDateEntries.any { entry ->
            isHistoryAppliedDateOffPlanRecord(
                entry = entry,
                groups = groups,
                date = currentDate,
                zoneId = zoneId,
            )
        }
        dayStates[currentDate] = HistoryCalendarDayUiState(
            status = if (primaryState.status == PlanCalendarDayStatus.NONE && hasOffPlanRecord) {
                PlanCalendarDayStatus.OFFPLAN
            } else {
                primaryState.status
            },
            hasOffPlanRecord = hasOffPlanRecord
        )
        currentDate = currentDate.plusDays(1)
    }

    return dayStates
}

private fun isHistoryAppliedDateOffPlanRecord(
    entry: MedicationLogEntry,
    groups: List<MedicationGroup>,
    date: LocalDate,
    zoneId: ZoneId,
): Boolean {
    if (entry.appliedAt.atZone(zoneId).toLocalDate() != date) {
        return false
    }
    val sourceGroupUuid = entry.sourceGroupUuid ?: return false
    if (entry.scheduledFor == null) {
        return false
    }
    val group = groups.firstOrNull { group -> group.uuid == sourceGroupUuid } ?: return false
    return !isEntryWithinScheduleFulfillmentWindow(
        group = group,
        entry = entry,
        zoneId = zoneId,
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
