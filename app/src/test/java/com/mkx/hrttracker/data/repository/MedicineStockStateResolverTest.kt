package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicineStockState
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineStockStateResolverTest {

    @Test
    fun untracked_evenWithStock_returnsUntracked() {
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = false,
            totalStockUnits = 30.0,
            dosesPerDayMagnitude = 1.0,
            runwayDays = 30.0,
            warnAtDaysRemaining = 14,
        )
        assertEquals(MedicineStockState.UNTRACKED, state)
    }

    @Test
    fun trackedNoGroups_returnsNoRunway() {
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = true,
            totalStockUnits = 30.0,
            dosesPerDayMagnitude = 0.0,
            runwayDays = null,
            warnAtDaysRemaining = 14,
        )
        assertEquals(MedicineStockState.NO_RUNWAY, state)
    }

    @Test
    fun zeroStockButTrackedWithRate_returnsOut() {
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = true,
            totalStockUnits = 0.0,
            dosesPerDayMagnitude = 1.0,
            runwayDays = 0.0,
            warnAtDaysRemaining = 14,
        )
        assertEquals(MedicineStockState.OUT, state)
    }

    @Test
    fun runwayBelowThreshold_returnsLow() {
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = true,
            totalStockUnits = 5.0,
            dosesPerDayMagnitude = 1.0,
            runwayDays = 5.0,
            warnAtDaysRemaining = 14,
        )
        assertEquals(MedicineStockState.LOW, state)
    }

    @Test
    fun runwayAtThreshold_returnsLow() {
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = true,
            totalStockUnits = 14.0,
            dosesPerDayMagnitude = 1.0,
            runwayDays = 14.0,
            warnAtDaysRemaining = 14,
        )
        assertEquals(MedicineStockState.LOW, state)
    }

    @Test
    fun runwayAboveThreshold_returnsHealthy() {
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = true,
            totalStockUnits = 30.0,
            dosesPerDayMagnitude = 1.0,
            runwayDays = 30.0,
            warnAtDaysRemaining = 14,
        )
        assertEquals(MedicineStockState.HEALTHY, state)
    }
}
