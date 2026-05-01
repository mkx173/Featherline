package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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
    fun is_selectable_date_from_minimum_rejects_earlier_dates() {
        val minimumDate = LocalDate.of(2026, 4, 25)

        assertEquals(
            false,
            isSelectableDateFromMinimum(LocalDate.of(2026, 4, 24), minimumDate)
        )
        assertEquals(
            true,
            isSelectableDateFromMinimum(LocalDate.of(2026, 4, 25), minimumDate)
        )
        assertEquals(
            true,
            isSelectableDateFromMinimum(LocalDate.of(2026, 4, 26), minimumDate)
        )
    }
}
