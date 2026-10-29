package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun schedule_preview_relative_date_label_returns_today_and_tomorrow_only() {
        val currentDate = LocalDate.of(2026, 5, 4)

        assertEquals(
            "Today",
            schedulePreviewRelativeDateLabel(
                date = currentDate,
                currentDate = currentDate,
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow"
            )
        )
        assertEquals(
            "Tomorrow",
            schedulePreviewRelativeDateLabel(
                date = currentDate.plusDays(1),
                currentDate = currentDate,
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow"
            )
        )
        assertNull(
            schedulePreviewRelativeDateLabel(
                date = currentDate.plusDays(2),
                currentDate = currentDate,
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow"
            )
        )
    }

    @Test
    fun upcoming_dose_day_label_names_the_plans_own_day() {
        // Thursday Oct 29, 16:30; a night-owl plan's 03:00 doses are on the evening before.
        val now = LocalDateTime.of(2026, 10, 29, 16, 30)
        fun label(occurrence: LocalDateTime, boundary: MedicationDayBoundary, at: LocalDateTime = now) =
            upcomingDoseDayLabel(
                occurrence = occurrence,
                now = at,
                boundary = boundary,
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                dateFormatter = { date -> date.toString() },
            )

        val fridayThree = LocalDateTime.of(2026, 10, 30, 3, 0)
        assertEquals("Today", label(fridayThree, MedicationDayBoundary.FourAm))
        assertEquals("Tomorrow", label(fridayThree, MedicationDayBoundary.Midnight))
        // Far off too: Sunday 03:00 is Saturday night.
        assertEquals("2026-10-31", label(LocalDateTime.of(2026, 11, 1, 3, 0), MedicationDayBoundary.FourAm))
        // At 01:00 Friday the night-owl plan's today is still Thursday.
        val fridayOne = LocalDateTime.of(2026, 10, 30, 1, 0)
        assertEquals("Today", label(fridayThree, MedicationDayBoundary.FourAm, at = fridayOne))
        assertEquals("Tomorrow", label(LocalDateTime.of(2026, 10, 30, 22, 0), MedicationDayBoundary.FourAm, at = fridayOne))
    }
}
