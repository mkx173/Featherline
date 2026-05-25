package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class MedicineStockRateCalculatorTest {

    @Test
    fun dailyEveryDay_twoTimes_yields2PerDay() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.DAILY,
            interval = 1,
            weeklyDaysOfWeek = emptySet(),
            scheduleTimesCount = 2,
        )
        assertEquals(2.0, r, 1e-9)
    }

    @Test
    fun dailyEvery3Days_oneTime_yields1Over3PerDay() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.DAILY,
            interval = 3,
            weeklyDaysOfWeek = emptySet(),
            scheduleTimesCount = 1,
        )
        assertEquals(1.0 / 3.0, r, 1e-9)
    }

    @Test
    fun weeklyEveryWeek_threeDays_oneTime_yields3Over7PerDay() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            scheduleTimesCount = 1,
        )
        assertEquals(3.0 / 7.0, r, 1e-9)
    }

    @Test
    fun weeklyEvery2Weeks_twoDays_twoTimes_yields4Over14() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            interval = 2,
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            scheduleTimesCount = 2,
        )
        assertEquals(4.0 / 14.0, r, 1e-9)
    }

    @Test
    fun weekly_emptyDays_yieldsZero() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            weeklyDaysOfWeek = emptySet(),
            scheduleTimesCount = 1,
        )
        assertEquals(0.0, r, 0.0)
    }

    @Test
    fun zeroScheduleTimes_yieldsZero() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.DAILY,
            interval = 1,
            weeklyDaysOfWeek = emptySet(),
            scheduleTimesCount = 0,
        )
        assertEquals(0.0, r, 0.0)
    }

    @Test
    fun zeroIntervalCoercedToOne() {
        val r = MedicineStockRateCalculator.occurrencesPerDay(
            scheduleType = MedicationGroupScheduleType.DAILY,
            interval = 0,
            weeklyDaysOfWeek = emptySet(),
            scheduleTimesCount = 2,
        )
        assertEquals(2.0, r, 1e-9)
    }
}
