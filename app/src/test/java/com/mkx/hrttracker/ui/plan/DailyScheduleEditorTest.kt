package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
}
