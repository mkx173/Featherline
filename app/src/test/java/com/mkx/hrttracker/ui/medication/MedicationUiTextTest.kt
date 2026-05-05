package com.mkx.hrttracker.ui.medication

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationUiTextTest {
    @Test
    fun medicationCountIndicatorText_formats_count_with_times_suffix() {
        assertEquals("1x", medicationCountIndicatorText(1))
        assertEquals("3x", medicationCountIndicatorText(3))
    }
}
