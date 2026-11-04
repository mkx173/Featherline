package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MedicationGroupEditorUnsavedChangesTest {

    @Test
    fun editedDraft_ignoresIdsAndSurroundingWhitespace() {
        val loaded = MedicationGroupEditorUiState(groupName = "Morning")
        val sameAfterReload = loaded.copy(
            groupName = " Morning ",
            dailyTimes = loaded.dailyTimes.map { time -> time.copy(localId = "reloaded") },
            weeklyTimeLocalId = "reloaded",
        )
        assertEquals(loaded.toEditedDraft(), sameAfterReload.toEditedDraft())
    }

    @Test
    fun editedDraft_changesWithAnEdit() {
        val loaded = MedicationGroupEditorUiState(groupName = "Morning")
        assertNotEquals(loaded.toEditedDraft(), loaded.copy(groupName = "Evening").toEditedDraft())
        assertNotEquals(loaded.toEditedDraft(), loaded.copy(dailyIntervalDays = "2").toEditedDraft())
    }

    @Test
    fun editedDraft_ignoresTheClockMovingAnUntouchedNewPlanOn() {
        // A plan starting now, left open past the day's end, starts the next day untouched.
        val opened = MedicationGroupEditorUiState(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            scheduleStart = ScheduleStart.Now,
            sinceDate = LocalDate.of(2026, 4, 25),
            weeklyDaysOfWeek = setOf(DayOfWeek.SATURDAY),
        )
        val pastMidnight = opened.withStartsNowOn(LocalDateTime.of(2026, 4, 26, 0, 1))
        assertEquals(LocalDate.of(2026, 4, 26), pastMidnight.sinceDate)
        assertEquals(opened.toEditedDraft(), pastMidnight.toEditedDraft())
    }

    @Test
    fun editedDraft_keepsAPickedStartDateAndWeekdays() {
        val loaded = MedicationGroupEditorUiState(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            scheduleStart = ScheduleStart.WholeDay,
            sinceDate = LocalDate.of(2026, 4, 25),
        )
        assertNotEquals(
            loaded.toEditedDraft(),
            loaded.copy(sinceDate = LocalDate.of(2026, 5, 1)).toEditedDraft(),
        )
        assertNotEquals(
            loaded.toEditedDraft(),
            loaded.copy(
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
                hasUserCustomizedWeeklyDays = true,
            ).toEditedDraft(),
        )
    }
}
