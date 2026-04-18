package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlanCalendarRangeTest {
    @Test
    fun buildPlanCalendarRange_uses_full_previous_current_and_next_weeks() {
        val range = buildPlanCalendarRange(
            today = LocalDate.of(2026, 4, 18),
            firstDayOfWeek = DayOfWeek.MONDAY
        )

        assertEquals(DayOfWeek.MONDAY, range.firstDayOfWeek)
        assertEquals(LocalDate.of(2026, 4, 6), range.startDate)
        assertEquals(LocalDate.of(2026, 4, 26), range.endDate)
    }
}
