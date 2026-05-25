package com.mkx.hrttracker.ui.components

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationCardTest {
    @Test
    fun medicationCardUsesNeutralGroupPaletteForMissingGroupOnlyWhenRequested() {
        assertFalse(
            medicationCardUsesGroupPalette(
                groupColorKey = null,
                missingGroupColorTreatment = MedicationCardMissingGroupColorTreatment.PRIMARY_CONTAINER,
            ),
        )
        assertTrue(
            medicationCardUsesGroupPalette(
                groupColorKey = null,
                missingGroupColorTreatment = MedicationCardMissingGroupColorTreatment.NEUTRAL_GROUP_PALETTE,
            ),
        )
        assertTrue(
            medicationCardUsesGroupPalette(
                groupColorKey = MedicationGroupColorKey.TEAL,
                missingGroupColorTreatment = MedicationCardMissingGroupColorTreatment.PRIMARY_CONTAINER,
            ),
        )
    }
}
