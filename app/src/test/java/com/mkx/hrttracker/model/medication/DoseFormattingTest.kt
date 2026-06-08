package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun format_stock_count_rounds_half_up_not_half_even() {
        // Stock numbers must round the same direction as formatDose (HALF_UP), so the
        // same value never shows a different last digit on a dose vs a stock surface.
        // Plain NumberFormat would round these to even ("0.12" / "2.12").
        assertEquals("0.13", formatStockCount(0.125, Locale.US))
        assertEquals("2.13", formatStockCount(2.125, Locale.US))
    }

    @Test
    fun format_stock_count_hides_negligible_residue_and_localizes() {
        // A sub-0.005 residue (e.g. float dust from stock math) reads as a clean "0",
        // unlike formatDose which would surface it via significant-figure fallback.
        assertEquals("0", formatStockCount(0.001, Locale.US))
        // Trailing zeros trimmed; locale decimal separator applied.
        assertEquals("1,5", formatStockCount(1.5, Locale.GERMANY))
        assertEquals("1", formatStockCount(1.0, Locale.GERMANY))
    }

    @Test
    fun reduce_tablet_portion_folds_count_and_formats_fraction_or_decimal() {
        assertFoldedPortion(1, 8, "1/8", reduceTabletPortion(1, 8, 1))
        assertFoldedPortion(1, 4, "1/4", reduceTabletPortion(1, 8, 2))
        assertFoldedPortion(1, 4, "1/4", reduceTabletPortion(1, 4, 1))
        assertFoldedPortion(1, 2, "1/2", reduceTabletPortion(1, 2, 1))
        assertFoldedPortion(3, 2, "1.5", reduceTabletPortion(1, 2, 3))
        assertFoldedPortion(1, 3, "1/3", reduceTabletPortion(1, 3, 1))
        assertFoldedPortion(2, 3, "2/3", reduceTabletPortion(1, 3, 2))
        assertFoldedPortion(3, 8, "3/8", reduceTabletPortion(3, 8, 1))
        assertFoldedPortion(3, 4, "3/4", reduceTabletPortion(3, 4, 1))
        assertFoldedPortion(5, 4, "1.25", reduceTabletPortion(1, 4, 5))
        assertFoldedPortion(2, 1, "2", reduceTabletPortion(1, 1, 2))
        assertFoldedPortion(3, 1, "3", reduceTabletPortion(1, 1, 3))
        assertFoldedPortion(1, 1, "1", reduceTabletPortion(1, 2, 2))
        assertTrue(reduceTabletPortion(1, 2, 2).isWholeOne())
        assertFoldedPortion(4, 3, "4/3", reduceTabletPortion(1, 3, 4))
        assertFoldedPortion(9, 8, "9/8", reduceTabletPortion(9, 8, 1))
    }

    @Test
    fun reduce_tablet_portion_handles_arbitrary_denominators() {
        assertFoldedPortion(1, 5, "1/5", reduceTabletPortion(1, 5, 1))
        assertFoldedPortion(7, 5, "1.4", reduceTabletPortion(1, 5, 7))
        assertFoldedPortion(1, 7, "1/7", reduceTabletPortion(1, 7, 1))
        assertFoldedPortion(9, 7, "9/7", reduceTabletPortion(1, 7, 9))
    }

    @Test
    fun reduce_tablet_portion_uses_long_math() {
        val portion = reduceTabletPortion(Int.MAX_VALUE, 1, 1_000_000)

        assertEquals(2_147_483_647_000_000L, portion.numerator)
        assertEquals(1L, portion.denominator)
    }

    @Test
    fun format_portion_formats_large_integer_exactly() {
        assertEquals(
            "4611686014132420609",
            reduceTabletPortion(Int.MAX_VALUE, 1, Int.MAX_VALUE).formatPortion(Locale.US),
        )
    }

    @Test
    fun format_portion_formats_large_terminating_decimal_exactly() {
        assertEquals(
            "9223372032,25",
            FoldedPortion(36_893_488_129L, 4L).formatPortion(Locale.GERMANY),
        )
    }

    @Test
    fun format_portion_uses_locale_decimal_separator() {
        assertEquals("1,5", reduceTabletPortion(1, 2, 3).formatPortion(Locale.GERMANY))
    }

    private fun assertFoldedPortion(
        numerator: Long,
        denominator: Long,
        formatted: String,
        portion: FoldedPortion,
    ) {
        assertEquals(numerator, portion.numerator)
        assertEquals(denominator, portion.denominator)
        assertEquals(formatted, portion.formatPortion(Locale.US))
    }
}
