package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupEditorIntervalStepperTest {
    @Test
    fun parse_schedule_interval_defaults_to_one_for_invalid_values() {
        assertEquals(1, parseScheduleInterval(""))
        assertEquals(1, parseScheduleInterval("0"))
        assertEquals(1, parseScheduleInterval("-2"))
        assertEquals(1, parseScheduleInterval("abc"))
    }

    @Test
    fun increment_schedule_interval_uses_clamped_base_value() {
        assertEquals("2", incrementScheduleInterval(""))
        assertEquals("4", incrementScheduleInterval("3"))
    }

    @Test
    fun decrement_schedule_interval_never_goes_below_one() {
        assertEquals("1", decrementScheduleInterval(""))
        assertEquals("1", decrementScheduleInterval("1"))
        assertEquals("2", decrementScheduleInterval("3"))
    }
}
