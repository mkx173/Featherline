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
    fun stockSubcardCardSegmentPreservesTopOfFirstRow() {
        assertEquals(
            MedicationCardWithStockSegment(index = 0, count = 2),
            medicationCardWithStockCardSegment(rowIndex = 0),
        )
    }

    @Test
    fun stockSubcardCardSegmentUsesMiddleShapeForNonFirstRows() {
        assertEquals(
            MedicationCardWithStockSegment(index = 1, count = 3),
            medicationCardWithStockCardSegment(rowIndex = 2),
        )
    }

    @Test
    fun stockSubcardBottomSegmentPreservesBottomOfLastRow() {
        assertEquals(
            MedicationCardWithStockSegment(index = 1, count = 2),
            medicationCardWithStockSubcardSegment(rowIndex = 2, rowCount = 3),
        )
    }

    @Test
    fun stockSubcardBottomSegmentUsesMiddleShapeBeforeLastRow() {
        assertEquals(
            MedicationCardWithStockSegment(index = 1, count = 3),
            medicationCardWithStockSubcardSegment(rowIndex = 1, rowCount = 3),
        )
    }
}
