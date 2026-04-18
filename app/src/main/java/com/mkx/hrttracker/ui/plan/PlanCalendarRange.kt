package com.mkx.hrttracker.ui.plan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class PlanCalendarRange(
    val firstDayOfWeek: DayOfWeek,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

fun buildPlanCalendarRange(
    today: LocalDate,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
): PlanCalendarRange {
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val currentWeekEnd = currentWeekStart.plusDays(6)

    return PlanCalendarRange(
        firstDayOfWeek = firstDayOfWeek,
        startDate = currentWeekStart.minusWeeks(1),
        endDate = currentWeekEnd.plusWeeks(1)
    )
}
