package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MedicationGroupScheduleFormattingTest {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    @Test
    fun formatSummary_formats_weekly_schedule_as_interval_days_and_time_parts() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = LocalDate.of(2026, 4, 20),
            weeklyDaysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.MONDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        assertEquals(
            "Every week · Mon/Tue · 9:00 AM",
            schedule.formatSummary(
                locale = Locale.US,
                timeFormatter = timeFormatter,
                dailyIntervalLabel = "Every day",
                weeklyIntervalLabel = "Every week"
            )
        )
    }

    @Test
    fun formatSummary_formats_daily_schedule_as_interval_and_time_parts() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 2,
            since = LocalDate.of(2026, 4, 20),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0), LocalTime.MIDNIGHT)
        )

        assertEquals(
            "Every 2 days · 9:00 AM/12:00 AM",
            schedule.formatSummary(
                locale = Locale.US,
                timeFormatter = timeFormatter,
                dailyIntervalLabel = "Every 2 days",
                weeklyIntervalLabel = "Every 2 weeks"
            )
        )
    }

    @Test
    fun formatSummary_lists_times_before_four_last_when_the_day_ends_at_four() {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        val daily = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 20),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(1, 0), LocalTime.of(22, 0))
        )
        val weekly = daily.copy(
            type = MedicationGroupScheduleType.WEEKLY,
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
            times = listOf(LocalTime.of(1, 0))
        )
        fun MedicationGroupSchedule.summary(boundary: MedicationDayBoundary) = copy(dayBoundary = boundary).formatSummary(
            locale = Locale.US,
            timeFormatter = formatter,
            dailyIntervalLabel = "Every day",
            weeklyIntervalLabel = "Every week",
        )

        assertEquals("Every day · 22:00/01:00", daily.summary(MedicationDayBoundary.FourAm))
        assertEquals("Every week · Mon · 01:00", weekly.summary(MedicationDayBoundary.FourAm))
        assertEquals("Every day · 01:00/22:00", daily.summary(MedicationDayBoundary.Midnight))
    }
}
