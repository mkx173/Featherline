package com.mkx.hrttracker.ui.components

import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockStatusIndicatorTest {

    @Test
    fun poolGaugeUsesFractionOfLastTotal() {
        val inputs = stockStatusFuelGaugeInputs(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 4.0,
                    unitsLastTotal = 10.0,
                ),
            ),
        )

        assertTrue(inputs.visible)
        assertEquals(4.0, inputs.numerator, 1e-9)
        assertEquals(10.0, inputs.denominator, 1e-9)
        assertEquals(0.4f, inputs.progress())
    }

    @Test
    fun containerGaugeUsesCurrentOpenContainerFill() {
        val inputs = stockStatusFuelGaugeInputs(
            projection(
                preparation = MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = 5.0,
                ),
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 3.0,
                    unitsLastTotal = 3.0,
                    openContainerAmount = 2.5,
                ),
            ),
        )

        assertTrue(inputs.visible)
        assertEquals(2.5, inputs.numerator, 1e-9)
        assertEquals(5.0, inputs.denominator, 1e-9)
        assertEquals(0.5f, inputs.progress())
    }

    @Test
    fun sealedOnlyContainerHidesGauge() {
        val inputs = stockStatusFuelGaugeInputs(
            projection(
                preparation = MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 1.0,
                    unitsLastTotal = 1.0,
                    openContainerAmount = null,
                ),
            ),
        )

        assertFalse(inputs.visible)
    }

    @Test
    fun untrackedMedicineWithPreservedStockHidesGauge() {
        val inputs = stockStatusFuelGaugeInputs(
            projection(
                state = MedicineStockState.UNTRACKED,
                stock = MedicineStock(
                    trackingEnabled = false,
                    unitsRemaining = 4.0,
                    unitsLastTotal = 10.0,
                ),
            ),
        )

        assertFalse(inputs.visible)
    }

    @Test
    fun noRunwayMedicineHidesGauge() {
        val inputs = stockStatusFuelGaugeInputs(
            projection(
                state = MedicineStockState.NO_RUNWAY,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 4.0,
                    unitsLastTotal = 10.0,
                ),
            ),
        )

        assertFalse(inputs.visible)
    }
}

private fun projection(
    state: MedicineStockState = MedicineStockState.HEALTHY,
    preparation: MedicinePreparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
    stock: MedicineStock,
): MedicineStockProjection {
    return MedicineStockProjection(
        medicine = testMedicine(
            preparation = preparation,
            stock = stock,
        ),
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = 4.0,
        runway = RunwayProjection.Days(
            days = 7,
            lastFulfillable = java.time.LocalDate.of(2026, 1, 1),
        ),
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}
