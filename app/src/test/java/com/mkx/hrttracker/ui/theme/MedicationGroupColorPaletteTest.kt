package com.mkx.hrttracker.ui.theme

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupColorPaletteTest {
    @Test
    fun medicationGroupPalettes_covers_every_color_key_and_null() {
        val expected: Set<MedicationGroupColorKey?> =
            MedicationGroupColorKey.entries.toSet<MedicationGroupColorKey?>() + null
        assertEquals(expected, MedicationGroupPalettes.keys)
    }
}
