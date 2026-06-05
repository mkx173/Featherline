package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineStockDeductionTest {

    @Test
    fun nonContainerDeductionClampsAtZeroAndPreservesBaseline() {
        val result = deductInsertedDoseStock(
            preparationType = MedicinePreparationType.PILL,
            containerCapacity = null,
            fields = StockDeductionFields(
                unitsRemaining = 1.5,
                unitsLastTotal = 4.0,
                openContainerAmount = null,
            ),
            requestedDose = 2.0,
        )

        assertEquals(0.0, result.unitsRemaining ?: error("missing units"), 0.0)
        assertEquals(4.0, result.unitsLastTotal ?: error("missing denominator"), 0.0)
        assertEquals(null, result.openContainerAmount)
    }

    @Test
    fun containerDeductionDepletesOpenThenCracksSealedUnit() {
        val result = deductInsertedDoseStock(
            preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            containerCapacity = 1.0,
            fields = StockDeductionFields(
                unitsRemaining = 2.0,
                unitsLastTotal = 3.0,
                openContainerAmount = 0.1,
            ),
            requestedDose = 0.25,
        )

        assertEquals(1.0, result.unitsRemaining ?: error("missing sealed"), 0.0)
        assertEquals(3.0, result.unitsLastTotal ?: error("missing denominator"), 0.0)
        assertEquals(0.75, result.openContainerAmount ?: error("missing open"), 0.0)
    }

    @Test
    fun containerDeductionNormalizesEmptyOpenWhenSealedRemains() {
        val result = deductInsertedDoseStock(
            preparationType = MedicinePreparationType.GEL_CONTAINER,
            containerCapacity = 1.0,
            fields = StockDeductionFields(
                unitsRemaining = 2.0,
                unitsLastTotal = 2.0,
                openContainerAmount = 0.25,
            ),
            requestedDose = 0.25,
        )

        assertEquals(1.0, result.unitsRemaining ?: error("missing sealed"), 0.0)
        assertEquals(2.0, result.unitsLastTotal ?: error("missing denominator"), 0.0)
        assertEquals(1.0, result.openContainerAmount ?: error("missing open"), 0.0)
    }

    @Test
    fun modelOverloadPreservesTrackingWarningAndGeneration() {
        val result = deductInsertedDoseStock(
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 3.0,
                unitsLastTotal = 5.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 9,
                generation = 42L,
            ),
            requestedDose = 1.25,
        )

        assertEquals(true, result.trackingEnabled)
        assertEquals(1.75, result.unitsRemaining ?: error("missing units"), 0.0)
        assertEquals(5.0, result.unitsLastTotal ?: error("missing denominator"), 0.0)
        assertEquals(9, result.warnAtDaysRemaining)
        assertEquals(42L, result.generation)
    }
}
