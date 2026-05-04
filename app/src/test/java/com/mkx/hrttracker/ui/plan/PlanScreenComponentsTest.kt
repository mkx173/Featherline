package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    @Test
    fun selectedDayLoggedTimeLabel_appendsDayOffsetAfterLoggedTime() {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        assertEquals(
            "01:05 AM (+1)",
            selectedDayLoggedTimeLabel(
                loggedAt = LocalDateTime.of(2026, 4, 19, 1, 5),
                fallbackScheduledTime = LocalTime.of(23, 0),
                timeFormatter = timeFormatter,
                loggedDayOffsetText = "+1"
            )
        )
    }

    @Test
    fun selectedDayLoggedTimeLabel_omitsDayOffsetWhenSameDay() {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        assertEquals(
            "09:30 PM",
            selectedDayLoggedTimeLabel(
                loggedAt = LocalDateTime.of(2026, 4, 18, 21, 30),
                fallbackScheduledTime = LocalTime.of(21, 0),
                timeFormatter = timeFormatter,
                loggedDayOffsetText = null
            )
        )
    }
}
