package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class PlanWeekCalendarStateTest {
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.US)

    @Test
    fun resolvePlanInitialVisibleWeekDate_usesTodayWithoutSelection() {
        val today = LocalDate.of(2026, 4, 27)

        val initialDate = resolvePlanInitialVisibleWeekDate(
            selectedDate = null,
            calendarStartDate = LocalDate.of(2026, 4, 20),
            calendarEndDate = LocalDate.of(2026, 5, 10),
            today = today,
        )

        assertEquals(today, initialDate)
    }

    @Test
    fun resolvePlanInitialVisibleWeekDate_usesSelectedDateWhenSelectionIsInsideRange() {
        val selectedDate = LocalDate.of(2026, 4, 26)

        val initialDate = resolvePlanInitialVisibleWeekDate(
            selectedDate = selectedDate,
            calendarStartDate = LocalDate.of(2026, 4, 13),
            calendarEndDate = LocalDate.of(2026, 5, 3),
            today = LocalDate.of(2026, 4, 27),
        )

        assertEquals(selectedDate, initialDate)
    }

    @Test
    fun resolvePlanInitialVisibleWeekDate_usesLastWeekWhenSelectedDateFallsOutOfRange() {
        val initialDate = resolvePlanInitialVisibleWeekDate(
            selectedDate = LocalDate.of(2026, 4, 13),
            calendarStartDate = LocalDate.of(2026, 4, 20),
            calendarEndDate = LocalDate.of(2026, 5, 10),
            today = LocalDate.of(2026, 4, 27),
        )

        assertEquals(LocalDate.of(2026, 4, 20), initialDate)
    }

    @Test
    fun planWeekHeaderMonthLabel_usesWeekStartMonthWhenWeekIsInOneMonth() {
        assertEquals(
            "April",
            planWeekHeaderMonthLabel(
                weekStartDate = LocalDate.of(2026, 4, 20),
                selectedDate = null,
                monthFormatter = { date -> date.format(monthFormatter) }
            )
        )
    }

    @Test
    fun planWeekHeaderMonthLabel_showsBothMonthsWhenWeekCrossesMonth() {
        assertEquals(
            "April / May",
            planWeekHeaderMonthLabel(
                weekStartDate = LocalDate.of(2026, 4, 27),
                selectedDate = null,
                monthFormatter = { date -> date.format(monthFormatter) }
            )
        )
    }

    @Test
    fun planWeekHeaderMonthLabel_usesSelectedDateMonthWhenSelected() {
        assertEquals(
            "May",
            planWeekHeaderMonthLabel(
                weekStartDate = LocalDate.of(2026, 4, 27),
                selectedDate = LocalDate.of(2026, 5, 1),
                monthFormatter = { date -> date.format(monthFormatter) }
            )
        )
    }
}
