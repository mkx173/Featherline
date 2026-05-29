package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineDetailScreenStockWarningTest {

    @Test
    fun warnAtBelowIntervalShowsWarningOnlyWhenEnabledAndShorterThanInterval() {
        assertTrue(showWarnAtBelowIntervalWarning(warnAtDays = 7, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 14, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 21, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 0, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 7, intervalDays = null))
    }

    @Test
    fun adjustSheetStockProjectionForDisplayKeepsFrozenProjection() {
        val medicine = testMedicine()
        val beforeSubmitProjection = stockProjection(
            medicine = medicine,
            totalStockUnits = 4.0,
        )
        val afterSubmitProjection = stockProjection(
            medicine = medicine.copy(
                stock = medicine.stock.copy(unitsRemaining = 3.0),
            ),
            totalStockUnits = 3.0,
        )

        assertEquals(
            beforeSubmitProjection,
            adjustSheetStockProjectionForDisplay(
                isStockProjectionFrozen = true,
                stockProjection = afterSubmitProjection,
                frozenStockProjection = beforeSubmitProjection,
            ),
        )
    }

    @Test
    fun adjustSheetStockProjectionForDisplayUsesLiveProjectionWhenNotFrozen() {
        val medicine = testMedicine()
        val beforeSubmitProjection = stockProjection(
            medicine = medicine,
            totalStockUnits = 4.0,
        )
        val afterSubmitProjection = stockProjection(
            medicine = medicine.copy(
                stock = medicine.stock.copy(unitsRemaining = 3.0),
            ),
            totalStockUnits = 3.0,
        )

        assertEquals(
            afterSubmitProjection,
            adjustSheetStockProjectionForDisplay(
                isStockProjectionFrozen = false,
                stockProjection = afterSubmitProjection,
                frozenStockProjection = beforeSubmitProjection,
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
        dosesPerDayMagnitude = 0.0,
        totalStockUnits = totalStockUnits,
        runway = RunwayProjection.NoSchedule,
        intervalDays = null,
        maxPerAdministration = 0.0,
        state = MedicineStockState.NO_RUNWAY,
    )
}
