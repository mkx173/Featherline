package com.mkx.hrttracker.ui.components

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
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

    @Test
    fun stockSubcardHostSegmentPreservesSingleRowShape() {
        assertEquals(
            MedicationCardWithStockSegment(index = 0, count = 1),
            medicationCardWithStockHostSegment(rowIndex = 0, rowCount = 1),
        )
    }

    @Test
    fun stockSubcardHostSegmentPreservesMiddleRowShape() {
        assertEquals(
            MedicationCardWithStockSegment(index = 1, count = 3),
            medicationCardWithStockHostSegment(rowIndex = 1, rowCount = 3),
        )
    }
}
