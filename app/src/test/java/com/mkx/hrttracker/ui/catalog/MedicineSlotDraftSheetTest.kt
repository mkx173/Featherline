package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.medication.testPatchOffMedicine
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class MedicineSlotDraftSheetTest {
    @Test
    fun initialApplicationTypeForSlotDraft_usesPatchOffForPatchOffSingleton() {
        assertEquals(
            MedicationApplicationType.PATCH_OFF,
            initialApplicationTypeForSlotDraft(testPatchOffMedicine()),
        )
    }

    @Test
    fun initialApplicationTypeForSlotDraft_keepsDefaultOralForRegularMedicine() {
        assertEquals(
            MedicationApplicationType.ORAL,
            initialApplicationTypeForSlotDraft(testMedicine()),
        )
    }

    @Test
    fun canHideManualSlotSheet_allowsCompletionHideWhileLocked() {
        assertTrue(
            canHideManualSlotSheet(
                value = SheetValue.Hidden,
                isManualSlotLocked = true,
                allowManualSlotCompletionHide = true,
            )
        )
    }

    @Test
    fun canHideManualSlotSheet_blocksUserHideWhileLocked() {
        assertFalse(
            canHideManualSlotSheet(
                value = SheetValue.Hidden,
                isManualSlotLocked = true,
                allowManualSlotCompletionHide = false,
            )
        )
    }

    @Test
    fun canHideNewMedicineSlotSheet_allowsCompletionHideWhileLocked() {
        assertTrue(
            canHideNewMedicineSlotSheet(
                value = SheetValue.Hidden,
                isSlotLocked = true,
                allowCompletionHide = true,
            )
        )
    }

    @Test
    fun canHideNewMedicineSlotSheet_blocksUserHideWhileLocked() {
        assertFalse(
            canHideNewMedicineSlotSheet(
                value = SheetValue.Hidden,
                isSlotLocked = true,
                allowCompletionHide = false,
            )
        )
    }

    @Test
    fun canHideNewMedicineSlotSheet_allowsUserHideWhileUnlocked() {
        assertTrue(
            canHideNewMedicineSlotSheet(
                value = SheetValue.Hidden,
                isSlotLocked = false,
                allowCompletionHide = false,
            )
        )
    }

    @Test
    fun canHideNewMedicineSlotSheet_allowsNonHiddenTransitionsWhileLocked() {
        assertTrue(
            canHideNewMedicineSlotSheet(
                value = SheetValue.Expanded,
                isSlotLocked = true,
                allowCompletionHide = false,
            )
        )
    }
}
