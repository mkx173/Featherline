package com.mkx.hrttracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupColorPaletteTest {
    @Test
    fun medicationGroupSeedColor_returns_neutral_seed_when_color_is_missing() {
        assertEquals(Color(0xFF6E7482), medicationGroupSeedColor(null))
    }

    @Test
    fun medicationGroupSeedColor_maps_each_persisted_color_key_to_a_stable_seed() {
        val expectedSeeds = mapOf(
            MedicationGroupColorKey.ROSE to Color(0xFFB14C62),
            MedicationGroupColorKey.ORCHID to Color(0xFF8F56B8),
            MedicationGroupColorKey.INDIGO to Color(0xFF4B63D2),
            MedicationGroupColorKey.TEAL to Color(0xFF006D6B),
            MedicationGroupColorKey.EMERALD to Color(0xFF3A6A2D),
            MedicationGroupColorKey.AMBER to Color(0xFF8B5E10),
            MedicationGroupColorKey.CORAL to Color(0xFFBA4F38),
            MedicationGroupColorKey.SLATE to Color(0xFF586273),
        )

        expectedSeeds.forEach { (colorKey, seedColor) ->
            assertEquals(seedColor, medicationGroupSeedColor(colorKey))
        }
    }
}
