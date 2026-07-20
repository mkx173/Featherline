package com.mkx.hrttracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class AppDateTimeFormattersTest {
    @Test
    fun localizedShortTimeFormatter_usesExplicit24HourFormat() {
        val formatter = localizedShortTimeFormatter(
            locale = Locale.US,
            uses24HourFormat = true,
        )

        assertEquals("21:05", LocalTime.of(21, 5).format(formatter))
    }

    @Test
    fun localizedShortTimeFormatter_usesExplicit12HourFormat() {
        val formatter = localizedShortTimeFormatter(
            locale = Locale.UK,
            uses24HourFormat = false,
        )

        assertEquals("9:05 pm", LocalTime.of(21, 5).format(formatter))
    }

    @Test
    fun localizedShortTimeFormatter_placesChineseDayPeriodBeforeTime() {
        val formatter = localizedShortTimeFormatter(
            locale = Locale.SIMPLIFIED_CHINESE,
            uses24HourFormat = false,
        )

        assertEquals("上午9:05", LocalTime.of(9, 5).format(formatter))
    }

    @Test
    fun dateRangeLabelFormatter_usesChineseDatePatternsForCantonese() {
        val formatter = dateRangeLabelFormatter(
            locale = Locale.forLanguageTag("yue-Hant-HK"),
            today = LocalDate.of(2026, 4, 29),
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 29),
        )

        assertEquals("4月1日", formatter(LocalDate.of(2026, 4, 1)))
    }

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
