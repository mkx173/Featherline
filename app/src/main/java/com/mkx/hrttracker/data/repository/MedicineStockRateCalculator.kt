package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import java.time.DayOfWeek

object MedicineStockRateCalculator {

    /**
     * Computes scheduled occurrences per day for a group.
     *
     * DAILY: every N days, T times per fire-day -> T / N.
     * WEEKLY: every N weeks, on D weekdays, T times -> (D * T) / (7 * N).
     */
    fun occurrencesPerDay(
        scheduleType: MedicationGroupScheduleType,
        interval: Int,
        weeklyDaysOfWeek: Set<DayOfWeek>,
        scheduleTimesCount: Int,
    ): Double {
        val n = interval.coerceAtLeast(1).toDouble()
        val t = scheduleTimesCount.toDouble()
        return when (scheduleType) {
            MedicationGroupScheduleType.DAILY -> t / n
            MedicationGroupScheduleType.WEEKLY -> (weeklyDaysOfWeek.size.toDouble() * t) / (7.0 * n)
        }
    }
}
