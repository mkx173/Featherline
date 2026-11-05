package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MedicationGroupEditorUnsavedChangesTest {

    // As the editor compares them: the draft against the baseline's weekday mode.
    private fun MedicationGroupEditorUiState.draftAgainst(baseline: MedicationGroupEditorUiState) =
        toEditedDraft(weekdaysFollowStart = baseline.toEditedDraft().weekdaysFollowStart)

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
            weeklyDaysOfWeek = setOf(DayOfWeek.SATURDAY),
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

    @Test
    fun editedDraft_comparesThePlanNotHowTheFormGotThere() {
        val newPlan = MedicationGroupEditorUiState(
            scheduleStart = ScheduleStart.Now,
            sinceDate = LocalDate.of(2026, 4, 25),
            weeklyDaysOfWeek = setOf(DayOfWeek.SATURDAY),
        )
        // Weekly with a weekday picked, then daily again: the weekly fields aren't saved.
        val backToDaily = newPlan.copy(
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
            hasUserCustomizedWeeklyDays = true,
            weeklyTime = LocalTime.of(20, 0),
        )
        assertEquals(newPlan.toEditedDraft(), backToDaily.draftAgainst(newPlan))
        // Weekly, an extra weekday turned on and back off: the flag stays set.
        val weekly = newPlan.copy(scheduleType = MedicationGroupScheduleType.WEEKLY)
        assertEquals(
            weekly.toEditedDraft(),
            weekly.copy(hasUserCustomizedWeeklyDays = true).draftAgainst(weekly),
        )
        // A saved weekly plan's start date picked and picked back resets the flag.
        val saved = MedicationGroupEditorUiState(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            scheduleStart = ScheduleStart.WholeDay,
            sinceDate = LocalDate.of(2026, 4, 25),
            weeklyDaysOfWeek = setOf(DayOfWeek.SATURDAY),
            hasUserCustomizedWeeklyDays = true,
        )
        assertEquals(
            saved.toEditedDraft(),
            saved.copy(hasUserCustomizedWeeklyDays = false).draftAgainst(saved),
        )
    }

    @Test
    fun editedDraft_keepsACopiedPlansWeekdaysAsTheClockMovesOn() {
        // A Saturday-only plan copied on Saturday keeps Saturday past midnight; Sunday picked
        // instead is a different plan, though Sunday is then the start date's weekday.
        val copied = MedicationGroupEditorUiState(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            scheduleStart = ScheduleStart.Now,
            sinceDate = LocalDate.of(2026, 4, 25),
            weeklyDaysOfWeek = setOf(DayOfWeek.SATURDAY),
            hasUserCustomizedWeeklyDays = true,
        )
        val midnight = LocalDateTime.of(2026, 4, 26, 0, 1)
        val untouched = copied.withStartsNowOn(midnight)
        assertEquals(copied.toEditedDraft(), untouched.draftAgainst(copied))
        val sunday = copied.copy(weeklyDaysOfWeek = setOf(DayOfWeek.SUNDAY)).withStartsNowOn(midnight)
        assertNotEquals(copied.toEditedDraft(), sunday.draftAgainst(copied))
    }
}
