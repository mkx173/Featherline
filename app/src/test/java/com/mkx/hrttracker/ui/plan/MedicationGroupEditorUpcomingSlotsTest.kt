package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationGroupEditorUpcomingSlotsTest {
    @Test
    fun buildMedicationGroupEditorUpcomingOccurrences_dailyUsesTimeCountPlusOne() {
        val uiState = MedicationGroupEditorUiState(
            scheduleType = MedicationGroupScheduleType.DAILY,
            sinceDate = LocalDate.of(2026, 4, 1),
            dailyIntervalDays = "1",
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(8, 0)),
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(20, 0))
            )
        )

        val occurrences = buildMedicationGroupEditorUpcomingOccurrences(
            uiState = uiState,
            start = LocalDateTime.of(2026, 4, 18, 12, 0)
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 20, 0),
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0)
            ),
            occurrences
        )
    }

    @Test
    fun buildMedicationGroupEditorUpcomingOccurrences_weeklyUsesSelectedWeekdayCountPlusOne() {
        val uiState = MedicationGroupEditorUiState(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            sinceDate = LocalDate.of(2026, 4, 1),
            weeklyIntervalWeeks = "1",
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            weeklyTime = LocalTime.of(9, 0)
        )

        val occurrences = buildMedicationGroupEditorUpcomingOccurrences(
            uiState = uiState,
            start = LocalDateTime.of(2026, 4, 18, 12, 0)
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 23, 9, 0),
                LocalDateTime.of(2026, 4, 27, 9, 0)
            ),
            occurrences
        )
    }
}
