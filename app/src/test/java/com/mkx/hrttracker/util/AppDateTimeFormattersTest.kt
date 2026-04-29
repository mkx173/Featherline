package com.mkx.hrttracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class AppDateTimeFormattersTest {
    @Test
    fun dateRangeLabelFormatter_omitsYearWhenBothEndsAreInCurrentYear() {
        val formatter = dateRangeLabelFormatter(
            locale = Locale.US,
            today = LocalDate.of(2026, 4, 29),
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 29),
        )

        assertEquals("Apr 1", formatter(LocalDate.of(2026, 4, 1)))
        assertEquals("Apr 29", formatter(LocalDate.of(2026, 4, 29)))
    }

    @Test
    fun dateRangeLabelFormatter_showsYearForBothEndsWhenEitherEndIsOutsideCurrentYear() {
        val formatter = dateRangeLabelFormatter(
            locale = Locale.US,
            today = LocalDate.of(2026, 4, 29),
            startDate = LocalDate.of(2025, 12, 31),
            endDate = LocalDate.of(2026, 1, 2),
        )

        assertEquals("Dec 31, 2025", formatter(LocalDate.of(2025, 12, 31)))
        assertEquals("Jan 2, 2026", formatter(LocalDate.of(2026, 1, 2)))
    }
}
