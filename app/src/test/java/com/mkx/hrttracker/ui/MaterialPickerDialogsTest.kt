package com.mkx.hrttracker.ui

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
}
