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
    fun format_dose_rounds_repeating_decimals() {
        // 1/3 of a 2mg tablet must not render as 0.6666666666666666.
        assertEquals("0.67", (2.0 / 3.0).formatDose(Locale.US))
        assertEquals("0.33", (1.0 / 3.0).formatDose(Locale.US))
    }

    @Test
    fun format_dose_preserves_tiny_values_instead_of_flooring_to_zero() {
        // A dose finer than 0.01 must keep its significant digits, not show "0".
        assertEquals("0.0002", 0.0002.formatDose(Locale.US))
        assertEquals("0.00025", 0.00025.formatDose(Locale.US))
        assertEquals("0.000067", 0.0000666.formatDose(Locale.US))
    }

    @Test
    fun format_dose_uses_locale_decimal_separator() {
        assertEquals("12,5", 12.5.formatDose(Locale.GERMANY))
    }
}
