package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DailyScheduleEditorTest {
    @Test
    fun can_remove_daily_time_is_false_for_last_remaining_slot() {
        assertFalse(canRemoveDailyTime(0))
        assertFalse(canRemoveDailyTime(1))
    }

    @Test
    fun can_remove_daily_time_is_true_when_multiple_slots_exist() {
        assertTrue(canRemoveDailyTime(2))
        assertTrue(canRemoveDailyTime(3))
    }

    @Test
    fun upcoming_dose_day_counts_the_plans_own_day_and_its_night() {
        // Thursday Oct 29, 16:30.
        val now = LocalDateTime.of(2026, 10, 29, 16, 30)
        val thursday = LocalDate.of(2026, 10, 29)
        val fridayThree = LocalDateTime.of(2026, 10, 30, 3, 0)

        // A night-owl plan's Friday 03:00 is Thursday night: Tonight.
        assertEquals(
            UpcomingDoseDay(day = thursday, daysAway = 0, isNight = true),
            upcomingDoseDay(fridayThree, now, MedicationDayBoundary.FourAm),
        )
        // Without night owl it's plain Tomorrow.
        assertEquals(
            UpcomingDoseDay(day = thursday.plusDays(1), daysAway = 1, isNight = false),
            upcomingDoseDay(fridayThree, now, MedicationDayBoundary.Midnight),
        )
        // Far off: Monday 03:00 is Sunday night, three days away.
        assertEquals(
            UpcomingDoseDay(day = LocalDate.of(2026, 11, 1), daysAway = 3, isNight = true),
            upcomingDoseDay(LocalDateTime.of(2026, 11, 2, 3, 0), now, MedicationDayBoundary.FourAm),
        )
        // At 01:00 Friday the night-owl plan's today is still Thursday.
        val fridayOne = LocalDateTime.of(2026, 10, 30, 1, 0)
        assertEquals(0L, upcomingDoseDay(fridayThree, fridayOne, MedicationDayBoundary.FourAm).daysAway)
        assertEquals(
            UpcomingDoseDay(day = thursday.plusDays(1), daysAway = 1, isNight = false),
            upcomingDoseDay(LocalDateTime.of(2026, 10, 30, 22, 0), fridayOne, MedicationDayBoundary.FourAm),
        )
    }
}
