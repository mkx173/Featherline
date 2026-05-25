package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicineStockState

object MedicineStockStateResolver {

    fun resolveState(
        trackingEnabled: Boolean,
        totalStockUnits: Double,
        dosesPerDayMagnitude: Double,
        runwayDays: Double?,
        warnAtDaysRemaining: Int,
    ): MedicineStockState {
        if (!trackingEnabled) return MedicineStockState.UNTRACKED
        if (dosesPerDayMagnitude == 0.0) return MedicineStockState.NO_RUNWAY
        if (totalStockUnits == 0.0) return MedicineStockState.OUT
        val runway = runwayDays ?: return MedicineStockState.NO_RUNWAY
        return if (runway <= warnAtDaysRemaining.toDouble()) {
            MedicineStockState.LOW
        } else {
            MedicineStockState.HEALTHY
        }
    }
}
