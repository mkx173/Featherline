package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class MaterialPickerDialogsTest {
    @Test
    fun material_picker_date_millis_to_local_date_uses_requested_zone() {
        val zoneId = ZoneId.of("UTC")
        val expectedDate = LocalDate.of(2026, 4, 22)
        val selectedDateMillis = expectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        assertEquals(
            expectedDate,
            materialPickerDateMillisToLocalDate(selectedDateMillis, zoneId)
        )
    }

    @Test
    fun datePickerSelectableDates_honorsMinimumAndMaximumDates() {
        val selectableDates = datePickerSelectableDates(
            minimumDate = LocalDate.of(2026, 4, 10),
            maximumDate = LocalDate.of(2026, 4, 20),
        )

        assertFalse(selectableDates.isSelectableDate(LocalDate.of(2026, 4, 9).toUtcPickerMillis()))
        assertTrue(selectableDates.isSelectableDate(LocalDate.of(2026, 4, 10).toUtcPickerMillis()))
        assertTrue(selectableDates.isSelectableDate(LocalDate.of(2026, 4, 20).toUtcPickerMillis()))
        assertFalse(selectableDates.isSelectableDate(LocalDate.of(2026, 4, 21).toUtcPickerMillis()))
    }

    @Test
    fun datePickerSelectableDates_honorsMinimumAndMaximumYears() {
        val selectableDates = datePickerSelectableDates(
            minimumDate = LocalDate.of(2026, 4, 10),
            maximumDate = LocalDate.of(2027, 4, 20),
        )

        assertFalse(selectableDates.isSelectableYear(2025))
        assertTrue(selectableDates.isSelectableYear(2026))
        assertTrue(selectableDates.isSelectableYear(2027))
        assertFalse(selectableDates.isSelectableYear(2028))
    }

    private fun LocalDate.toUtcPickerMillis(): Long {
        return atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
