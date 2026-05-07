package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.medication.MedicationLogScheduleOffset
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

    @Test
    fun selectedDayScheduleOffset_omitsSubHourDelta() {
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(59),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusMinutes(59),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(1).minusSeconds(1),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(1).plusSeconds(1),
            )
        )
    }

    @Test
    fun selectedDayScheduleOffset_keepsExactHourDelta() {
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_later,
                value = 1,
            ),
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(1),
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_earlier,
                value = 1,
            ),
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(1),
            )
        )
    }
}
