package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanScreenCountIndicatorTest {
    @Test
    fun medicationCountIndicatorText_formats_count_with_times_suffix() {
        assertEquals("1×", medicationCountIndicatorText(1))
        assertEquals("3×", medicationCountIndicatorText(3))
    }
}
