package com.mkx.hrttracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.AMBER
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.CITRON
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.CORAL
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.INDIGO
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.PLUM
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.ROSE
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.SAGE
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.SKY
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.TEAL
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.VIOLET
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupColorPaletteTest {
    @Test
    fun medicationGroupSeedColor_returns_neutral_seed_when_color_is_missing() {
        assertEquals(Color(0xFF5A6470), medicationGroupSeedColor(null))
    }

    @Test
    fun medicationGroupSeedColor_maps_each_persisted_color_key_to_a_stable_seed() {
        val expectedSeeds = mapOf(
            null to Color(0xFF5A6470),
            ROSE to Color(0xFF95414A),
            CORAL to Color(0xFFB66B43),
            AMBER to Color(0xFFC18B14),
            CITRON to Color(0xFF7E7C2A),
            SAGE to Color(0xFF4F7A55),
            TEAL to Color(0xFF2E6E72),
            SKY to Color(0xFF2D80B0),
            INDIGO to Color(0xFF4D55AC),
            VIOLET to Color(0xFF8C4AA8),
            PLUM to Color(0xFF9A3D78)
        )

        expectedSeeds.forEach { (colorKey, seedColor) ->
            assertEquals(seedColor, medicationGroupSeedColor(colorKey))
        }
    }

    @Test
    fun medicationGroupLightenRatioDelta_returns_correct_delta_for_each_key_in_light_mode() {
        val expectedDeltas = mapOf(
            ROSE to 1.2f,
            CORAL to -0.6f,
            AMBER to -1.25f,
            CITRON to 0.15f,
            SAGE to 0f,
            TEAL to 0.2f,
            SKY to -0.6f,
            VIOLET to 0f,
            INDIGO to 0.5f,
            PLUM to 0.3f,
            null to 0f
        )

        expectedDeltas.forEach { (colorKey, delta) ->
            assertEquals(
                delta,
                medicationGroupLightenRatioDelta(colorKey, darkTheme = false)
            )
        }
    }

    @Test
    fun medicationGroupLightenRatioDelta_returns_correct_delta_for_each_key_in_dark_mode() {
        // Most keys share the same delta across modes; CITRON is the exception
        // (light = 0.2, dark = 0.0) because light-mode CITRON needs an extra
        // brightness bump that would oversaturate in dark mode.
        val expectedDeltas = mapOf(
            ROSE to 1.2f,
            CORAL to -0.6f,
            AMBER to -1.25f,
            CITRON to 0f,
            SAGE to 0f,
            TEAL to 0.2f,
            SKY to -0.6f,
            VIOLET to 0f,
            INDIGO to 0.5f,
            PLUM to 0.3f,
            null to 0f
        )

        expectedDeltas.forEach { (colorKey, delta) ->
            assertEquals(
                delta,
                medicationGroupLightenRatioDelta(colorKey, darkTheme = true)
            )
        }
    }
}
