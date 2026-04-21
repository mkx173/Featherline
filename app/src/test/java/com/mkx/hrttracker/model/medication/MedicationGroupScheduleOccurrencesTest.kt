package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationGroupScheduleOccurrencesTest {
    @Test
    fun isScheduledOn_returns_true_for_each_selected_weekday() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = LocalDate.of(2026, 4, 14),
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 16)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 20)))
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 21)))
    }

    @Test
    fun isScheduledOn_groups_selected_days_within_same_iso_week_cycle() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 2,
            since = LocalDate.of(2026, 4, 14),
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 13)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 16)))

        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 20)))
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 23)))

        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 27)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 30)))

        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 5, 4)))
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 5, 7)))
    }

    @Test
    fun occurrencesBetween_returns_all_selected_weekdays_at_shared_time() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = LocalDate.of(2026, 4, 14),
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        val occurrences = schedule.occurrencesBetween(
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 23)
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 16, 9, 0),
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 23, 9, 0),
            ),
            occurrences
        )
    }
}
