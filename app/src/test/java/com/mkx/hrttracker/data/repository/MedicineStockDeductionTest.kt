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
    fun containerDeductionCarriesOpenDregIntoCrackedSealedUnit() {
        // Exact-split: the 0.1 left in the open vial is consumed first, and only
        // the 0.15 residual is drawn from the freshly cracked unit, so the new
        // open level keeps the carried dreg (1.0 - (0.25 - 0.1) = 0.85) instead
        // of discarding it (which would leave 1.0 - 0.25 = 0.75).
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
        assertEquals(0.85, result.openContainerAmount ?: error("missing open"), 1e-9)
    }

    @Test
    fun containerDeductionConservesTotalVolumeAcrossVialBoundary() {
        // Headline case: 0.3 mL left in a 10 mL vial, a 1.0 mL dose. The dose
        // straddles the boundary; exact-split conserves volume by carrying the
        // 0.3 over, so the fresh vial reads 10 - (1.0 - 0.3) = 9.3 (no waste).
        val result = deductInsertedDoseStock(
            preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            containerCapacity = 10.0,
            fields = StockDeductionFields(
                unitsRemaining = 2.0,
                unitsLastTotal = 4.0,
                openContainerAmount = 0.3,
            ),
            requestedDose = 1.0,
        )

        assertEquals(1.0, result.unitsRemaining ?: error("missing sealed"), 0.0)
        assertEquals(9.3, result.openContainerAmount ?: error("missing open"), 1e-9)
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
