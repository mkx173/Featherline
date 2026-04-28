package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlanScreenComponentsTest {
    @Test
    fun selectedDayLoggedDayOffsetDays_returnsNullForSameDayLog() {
        assertNull(
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 18, 23, 30)
            )
        )
    }

    @Test
    fun selectedDayLoggedDayOffsetDays_returnsSignedDayOffsetForDifferentDayLog() {
        assertEquals(
            1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 19, 0, 15)
            )
        )
        assertEquals(
            -1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 17, 23, 45)
            )
        )
    }
}
