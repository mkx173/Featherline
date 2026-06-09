package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.RunwayProjection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MedicineStockStateResolverTest {

    @Test
    fun untracked_evenWithStock_returnsUntracked() {
        val state = resolve(trackingEnabled = false)
        assertEquals(MedicineStockState.UNTRACKED, state)
    }

    @Test
    fun trackedNoSchedule_returnsNoRunway() {
        val state = resolve(
            runway = RunwayProjection.NoSchedule,
            maxPerAdministration = 0.0,
            imminentDoseCount = 0,
        )
        assertEquals(MedicineStockState.NO_RUNWAY, state)
    }

    @Test
    fun zeroStock_returnsOutBeforeNoRunway() {
        val state = resolve(
            totalStockUnits = 0.0,
            runway = RunwayProjection.NoSchedule,
            imminentDoseCount = 0,
        )
        assertEquals(MedicineStockState.OUT, state)
    }

    @Test
    fun fewerThanTwoImminentDoses_returnsImminent() {
        val state = resolve(
            runway = RunwayProjection.Days(days = 5, lastFulfillable = LocalDate.of(2026, 1, 6)),
            imminentDoseCount = 1,
            maxPerAdministration = 1.0,
        )
        assertEquals(MedicineStockState.IMMINENT, state)
    }

    @Test
    fun runwayAtThreshold_returnsUserLow() {
        val state = resolve(
            runway = RunwayProjection.Days(days = 14, lastFulfillable = LocalDate.of(2026, 1, 15)),
            imminentDoseCount = 2,
        )
        assertEquals(MedicineStockState.USER_LOW, state)
    }

    @Test
    fun warningThresholdOffReturnsHealthyForDaysRunway() {
        val state = resolve(
            runway = RunwayProjection.Days(days = 1, lastFulfillable = LocalDate.of(2026, 1, 2)),
            warnAtDaysRemaining = 0,
            imminentDoseCount = 2,
        )
        assertEquals(MedicineStockState.HEALTHY, state)
    }

    @Test
    fun beyondHorizon_returnsHealthy() {
        val state = resolve(runway = RunwayProjection.BeyondHorizon)
        assertEquals(MedicineStockState.HEALTHY, state)
    }

    private fun resolve(
        trackingEnabled: Boolean = true,
        totalStockUnits: Double = 30.0,
        runway: RunwayProjection = RunwayProjection.Days(
            days = 30,
            lastFulfillable = LocalDate.of(2026, 1, 31),
        ),
        warnAtDaysRemaining: Int = 14,
        imminentDoseCount: Int = 2,
        maxPerAdministration: Double = 1.0,
    ): MedicineStockState {
        return MedicineStockStateResolver.resolveState(
            trackingEnabled = trackingEnabled,
            totalStockUnits = totalStockUnits,
            runway = runway,
            warnAtDaysRemaining = warnAtDaysRemaining,
            imminentDoseCount = imminentDoseCount,
            maxPerAdministration = maxPerAdministration,
        )
    }
}
