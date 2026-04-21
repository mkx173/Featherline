package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupColorKeyTest {
    @Test
    fun fromStorageValue_falls_back_to_rose_for_unknown_values() {
        assertEquals(MedicationGroupColorKey.ROSE, MedicationGroupColorKey.fromStorageValue(null))
        assertEquals(MedicationGroupColorKey.ROSE, MedicationGroupColorKey.fromStorageValue("UNKNOWN"))
    }

    @Test
    fun nextAvailableMedicationGroupColor_returns_first_unused_color_in_assignment_order() {
        val colorKey = nextAvailableMedicationGroupColor(
            usedColors = setOf(
                MedicationGroupColorKey.ROSE,
                MedicationGroupColorKey.ORCHID,
                MedicationGroupColorKey.INDIGO
            ),
            seed = 42
        )

        assertEquals(MedicationGroupColorKey.TEAL, colorKey)
    }

    @Test
    fun nextAvailableMedicationGroupColor_cycles_deterministically_when_palette_is_exhausted() {
        val colorKey = nextAvailableMedicationGroupColor(
            usedColors = MedicationGroupColorKey.assignmentOrder.toSet(),
            seed = 10
        )

        assertEquals(MedicationGroupColorKey.INDIGO, colorKey)
    }
}
