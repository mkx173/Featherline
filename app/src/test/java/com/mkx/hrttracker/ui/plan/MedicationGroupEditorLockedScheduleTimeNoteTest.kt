package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class MedicationGroupEditorLockedScheduleTimeNoteTest {
    @Test
    fun should_show_locked_schedule_time_note_is_false_when_group_is_not_locked() {
        val uiState = MedicationGroupEditorUiState(
            editingGroupId = "group-id",
            plannedEntryCount = 0,
            scheduleType = MedicationGroupScheduleType.DAILY,
            originalScheduleType = MedicationGroupScheduleType.DAILY,
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(
                    time = LocalTime.of(10, 0),
                    originalTime = LocalTime.of(8, 0),
                )
            ),
            originalDailyTimes = listOf(LocalTime.of(8, 0)),
        )

        assertFalse(shouldShowLockedScheduleTimeNote(uiState))
    }

    @Test
    fun should_show_locked_schedule_time_note_is_false_when_locked_time_slots_match_originals() {
        val uiState = MedicationGroupEditorUiState(
            editingGroupId = "group-id",
            plannedEntryCount = 2,
            scheduleType = MedicationGroupScheduleType.DAILY,
            originalScheduleType = MedicationGroupScheduleType.DAILY,
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(
                    time = LocalTime.of(8, 0),
                    originalTime = LocalTime.of(8, 0),
                ),
                MedicationGroupScheduleTimeUiState(
                    time = LocalTime.of(20, 0),
                    originalTime = LocalTime.of(20, 0),
                ),
            ),
            originalDailyTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        )

        assertFalse(shouldShowLockedScheduleTimeNote(uiState))
    }

    @Test
    fun should_show_locked_schedule_time_note_is_true_when_locked_daily_time_changes() {
        val uiState = MedicationGroupEditorUiState(
            editingGroupId = "group-id",
            plannedEntryCount = 2,
            scheduleType = MedicationGroupScheduleType.DAILY,
            originalScheduleType = MedicationGroupScheduleType.DAILY,
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(
                    time = LocalTime.of(8, 0),
                    originalTime = LocalTime.of(8, 0),
                ),
                MedicationGroupScheduleTimeUiState(
                    time = LocalTime.of(21, 0),
                    originalTime = LocalTime.of(20, 0),
                ),
            ),
            originalDailyTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        )

        assertTrue(shouldShowLockedScheduleTimeNote(uiState))
    }

    @Test
    fun should_show_locked_schedule_time_note_is_true_when_locked_weekly_time_changes() {
        val uiState = MedicationGroupEditorUiState(
            editingGroupId = "group-id",
            plannedEntryCount = 2,
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            originalScheduleType = MedicationGroupScheduleType.WEEKLY,
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
            originalWeeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
            weeklyTime = LocalTime.of(10, 0),
            originalWeeklyTime = LocalTime.of(8, 0),
        )

        assertTrue(shouldShowLockedScheduleTimeNote(uiState))
    }
}
