package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicineStockState

object MedicineStockStateResolver {

    fun resolveState(
        trackingEnabled: Boolean,
        totalStockUnits: Double,
        runway: RunwayProjection,
        warnAtDaysRemaining: Int,
        imminentDoseCount: Int,
        maxPerAdministration: Double,
    ): MedicineStockState {
        if (!trackingEnabled) return MedicineStockState.UNTRACKED
        if (totalStockUnits == 0.0) return MedicineStockState.OUT
        if (runway is RunwayProjection.NoSchedule) return MedicineStockState.NO_RUNWAY
        if (maxPerAdministration > 0.0 && imminentDoseCount < 2) {
            return MedicineStockState.IMMINENT
        }
        if (runway is RunwayProjection.BeyondHorizon) return MedicineStockState.HEALTHY
        if (runway is RunwayProjection.Days &&
            warnAtDaysRemaining > 0 &&
            runway.days <= warnAtDaysRemaining
        ) {
            return MedicineStockState.USER_LOW
        }
        return MedicineStockState.HEALTHY
    }
}
