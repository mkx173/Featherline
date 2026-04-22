package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DoseFormattingTest {
    @Test
    fun format_dose_trims_trailing_zeroes() {
        assertEquals("12.5", 12.5.formatDose(Locale.US))
        assertEquals("6.25", 6.25.formatDose(Locale.US))
        assertEquals("2", 2.0.formatDose(Locale.US))
        assertEquals("0.06", 0.06.formatDose(Locale.US))
    }

    @Test
    fun format_dose_uses_locale_decimal_separator() {
        assertEquals("12,5", 12.5.formatDose(Locale.GERMANY))
    }
}
