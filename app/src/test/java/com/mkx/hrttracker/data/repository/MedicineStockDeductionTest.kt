package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun containerDeductionDrainsMultipleSealedUnitsForALargeDose() {
        // A single dose larger than open + one vial spans several containers and
        // still conserves volume: total 3.5 - 2.7 = 0.8 left as a new open vial,
        // all sealed units consumed (the branchy single-crack path would have
        // lost the excess and left a stale sealed unit).
        val result = deductInsertedDoseStock(
            preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            containerCapacity = 1.0,
            fields = StockDeductionFields(
                unitsRemaining = 3.0,
                unitsLastTotal = 4.0,
                openContainerAmount = 0.5,
            ),
            requestedDose = 2.7,
        )

        assertEquals(0.0, result.unitsRemaining ?: error("missing sealed"), 1e-9)
        assertEquals(0.8, result.openContainerAmount ?: error("missing open"), 1e-9)
        assertEquals(4.0, result.unitsLastTotal ?: error("missing denominator"), 0.0)
    }

    @Test
    fun containerDeductionTreatsNonFiniteStoredOpenAmountAsEmpty() {
        // A corrupt non-finite open amount (e.g. a NaN persisted by a legacy
        // write) must not flow into the total round-trip and poison every stock
        // field. It is treated as an empty open, so the dose simply cracks a
        // sealed unit and the result stays finite (open 0 + 2*10 - 1 -> 9, one
        // sealed left), rather than writing NaN/NaN back to the database.
        val result = deductInsertedDoseStock(
            preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            containerCapacity = 10.0,
            fields = StockDeductionFields(
                unitsRemaining = 2.0,
                unitsLastTotal = 4.0,
                openContainerAmount = Double.NaN,
            ),
            requestedDose = 1.0,
        )

        val open = result.openContainerAmount ?: error("missing open")
        assertTrue("open must stay finite", open.isFinite())
        assertEquals(9.0, open, 1e-9)
        assertEquals(1.0, result.unitsRemaining ?: error("missing sealed"), 1e-9)
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
