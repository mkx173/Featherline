package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
