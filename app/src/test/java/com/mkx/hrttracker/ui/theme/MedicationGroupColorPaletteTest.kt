package com.mkx.hrttracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.AMBER
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.CITRON
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.CORAL
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.INDIGO
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.MAUVE
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
            ROSE to Color(0xFF8D4959),
            CORAL to Color(0xFFB6584A),
            AMBER to Color(0xFF9B7228),
            CITRON to Color(0xFF7E7C2A),
            SAGE to Color(0xFF4F7A55),
            TEAL to Color(0xFF2E6E72),
            SKY to Color(0xFF3F6FA8),
            INDIGO to Color(0xFF5557A8),
            VIOLET to Color(0xFF7C4FA0),
            PLUM to Color(0xFF8E3F7A),
            MAUVE to Color(0xFF75565C)
        )

        expectedSeeds.forEach { (colorKey, seedColor) ->
            assertEquals(seedColor, medicationGroupSeedColor(colorKey))
        }
    }

    @Test
    fun medicationGroupLightenRatioDelta_returns_correct_delta_for_each_key() {
        val expectedDeltas = mapOf(
            ROSE to 0.2f,
            CITRON to 0.1f,
            PLUM to 0.25f,
            CORAL to -0.05f,
            AMBER to -0.05f,
            SAGE to 0f,
            TEAL to 0f,
            SKY to 0f,
            INDIGO to 0f,
            VIOLET to 0f,
            MAUVE to 0f,
            null to 0f
        )

        expectedDeltas.forEach { (colorKey, delta) ->
            assertEquals(delta, medicationGroupLightenRatioDelta(colorKey))
        }
    }
}
