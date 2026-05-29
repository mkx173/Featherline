package com.mkx.hrttracker.ui.catalog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.medication.testPatchOffMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun slotDraftStockProjectionForDisplay_keepsFrozenProjectionForManualLog() {
        val medicine = testMedicine()
        val beforeSaveProjection = stockProjection(medicine, totalStockUnits = 4.0)
        val afterSaveProjection = stockProjection(
            medicine = medicine.copy(
                stock = medicine.stock.copy(unitsRemaining = 3.0),
            ),
            totalStockUnits = 3.0,
        )

        assertEquals(
            beforeSaveProjection,
            slotDraftStockProjectionForDisplay(
                mode = MedicineSlotDraftMode.MANUAL_LOG,
                isStockProjectionFrozen = true,
                selectedStockProjection = afterSaveProjection,
                frozenStockProjection = beforeSaveProjection,
            ),
        )
    }

    @Test
    fun slotDraftStockProjectionForDisplay_usesLiveProjectionWhenNotFrozen() {
        val medicine = testMedicine()
        val beforeSaveProjection = stockProjection(medicine, totalStockUnits = 4.0)
        val afterSaveProjection = stockProjection(
            medicine = medicine.copy(
                stock = medicine.stock.copy(unitsRemaining = 3.0),
            ),
            totalStockUnits = 3.0,
        )

        assertEquals(
            afterSaveProjection,
            slotDraftStockProjectionForDisplay(
                mode = MedicineSlotDraftMode.MANUAL_LOG,
                isStockProjectionFrozen = false,
                selectedStockProjection = afterSaveProjection,
                frozenStockProjection = beforeSaveProjection,
            ),
        )
    }
}

private fun stockProjection(
    medicine: Medicine,
    totalStockUnits: Double,
): MedicineStockProjection {
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = totalStockUnits,
        runway = RunwayProjection.NoSchedule,
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = MedicineStockState.NO_RUNWAY,
    )
}
