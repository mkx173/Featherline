package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanWeekCalendarStateTest {
    @Test
    fun resolvePlanInitialVisibleWeekDate_preservesVisibleWeekInsideNewRange() {
        val visibleWeekDate = LocalDate.of(2026, 4, 27)

        val initialDate = resolvePlanInitialVisibleWeekDate(
            previousVisibleWeekDate = visibleWeekDate,
            calendarStartDate = LocalDate.of(2026, 4, 20),
            calendarEndDate = LocalDate.of(2026, 5, 10),
            today = LocalDate.of(2026, 4, 27),
        )

        assertEquals(visibleWeekDate, initialDate)
    }

    @Test
    fun resolvePlanInitialVisibleWeekDate_usesLastWeekWhenPreviousVisibleWeekFallsOutOfRange() {
        val initialDate = resolvePlanInitialVisibleWeekDate(
            previousVisibleWeekDate = LocalDate.of(2026, 4, 13),
            calendarStartDate = LocalDate.of(2026, 4, 20),
            calendarEndDate = LocalDate.of(2026, 5, 10),
            today = LocalDate.of(2026, 4, 27),
        )

        assertEquals(LocalDate.of(2026, 4, 20), initialDate)
    }

    @Test
    fun resolvePlanInitialVisibleWeekDate_usesTodayForInitialLoad() {
        val today = LocalDate.of(2026, 4, 26)

        val initialDate = resolvePlanInitialVisibleWeekDate(
            previousVisibleWeekDate = null,
            calendarStartDate = LocalDate.of(2026, 4, 13),
            calendarEndDate = LocalDate.of(2026, 5, 3),
            today = today,
        )

        assertEquals(today, initialDate)
    }
}
