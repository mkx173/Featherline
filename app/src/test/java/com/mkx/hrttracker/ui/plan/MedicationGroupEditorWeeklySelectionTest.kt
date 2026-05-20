package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MedicationGroupEditorWeeklySelectionTest {
    @Test
    fun toggleWeeklyDaySelection_adds_unselected_day() {
        val updated = toggleWeeklyDaySelection(
            selectedDays = setOf(DayOfWeek.MONDAY),
            dayOfWeek = DayOfWeek.THURSDAY
        )

        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            updated
        )
    }

    @Test
    fun toggleWeeklyDaySelection_removes_selected_day() {
        val updated = toggleWeeklyDaySelection(
            selectedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dayOfWeek = DayOfWeek.MONDAY
        )

        assertEquals(setOf(DayOfWeek.THURSDAY), updated)
    }

    @Test
    fun toggleWeeklyDaySelection_removes_last_selected_day() {
        val updated = toggleWeeklyDaySelection(
            selectedDays = setOf(DayOfWeek.MONDAY),
            dayOfWeek = DayOfWeek.MONDAY
        )

        assertEquals(emptySet<DayOfWeek>(), updated)
    }

    @Test
    fun editorStateWithUpdatedSinceDate_resets_weekly_selection_to_new_start_date_weekday() {
        val updated = editorStateWithUpdatedSinceDate(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                sinceDate = LocalDate.of(2026, 4, 20),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
            date = LocalDate.of(2026, 4, 22)
        )

        assertEquals(LocalDate.of(2026, 4, 22), updated.sinceDate)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), updated.weeklyDaysOfWeek)
    }

    @Test
    fun editorStateWithUpdatedSinceDate_keeps_weekly_selection_when_start_date_is_unchanged() {
        val updated = editorStateWithUpdatedSinceDate(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                sinceDate = LocalDate.of(2026, 4, 20),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
            date = LocalDate.of(2026, 4, 20)
        )

        assertEquals(LocalDate.of(2026, 4, 20), updated.sinceDate)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), updated.weeklyDaysOfWeek)
    }

    @Test
    fun editorStateWithUpdatedScheduleType_sets_weekly_days_to_start_date_weekday_when_switching_to_weekly() {
        val updated = editorStateWithUpdatedScheduleType(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.DAILY,
                sinceDate = LocalDate.of(2026, 4, 22),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
                hasUserCustomizedWeeklyDays = false,
            ),
            scheduleType = MedicationGroupScheduleType.WEEKLY,
        )

        assertEquals(MedicationGroupScheduleType.WEEKLY, updated.scheduleType)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), updated.weeklyDaysOfWeek)
    }

    @Test
    fun editorStateWithUpdatedScheduleType_preserves_customized_weekly_days_when_switching_back_to_weekly() {
        val updated = editorStateWithUpdatedScheduleType(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.DAILY,
                sinceDate = LocalDate.of(2026, 4, 22),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                hasUserCustomizedWeeklyDays = true,
            ),
            scheduleType = MedicationGroupScheduleType.WEEKLY,
        )

        assertEquals(MedicationGroupScheduleType.WEEKLY, updated.scheduleType)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            updated.weeklyDaysOfWeek
        )
        assertTrue(updated.hasUserCustomizedWeeklyDays)
    }

    @Test
    fun editorStateWithResetWeeklyDaysOfWeek_clears_customization_flag() {
        val updated = editorStateWithResetWeeklyDaysOfWeek(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                sinceDate = LocalDate.of(2026, 4, 22),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                hasUserCustomizedWeeklyDays = true,
            )
        )

        assertEquals(setOf(DayOfWeek.WEDNESDAY), updated.weeklyDaysOfWeek)
        assertFalse(updated.hasUserCustomizedWeeklyDays)
    }

    @Test
    fun editorStateWithUpdatedScheduleType_preserves_weekly_days_when_already_weekly() {
        val updated = editorStateWithUpdatedScheduleType(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                sinceDate = LocalDate.of(2026, 4, 22),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
            scheduleType = MedicationGroupScheduleType.WEEKLY,
        )

        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            updated.weeklyDaysOfWeek
        )
    }

    @Test
    fun editorStateWithUpdatedScheduleType_leaves_weekly_days_unchanged_when_switching_to_daily() {
        val updated = editorStateWithUpdatedScheduleType(
            uiState = MedicationGroupEditorUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                sinceDate = LocalDate.of(2026, 4, 22),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
            scheduleType = MedicationGroupScheduleType.DAILY,
        )

        assertEquals(MedicationGroupScheduleType.DAILY, updated.scheduleType)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            updated.weeklyDaysOfWeek
        )
    }

    @Test
    fun hasSaveableMedicationGroupContent_requires_weekly_days_for_weekly_schedule() {
        val result = hasSaveableMedicationGroupContent(
            MedicationGroupEditorUiState(
                groupName = "Morning meds",
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                weeklyDaysOfWeek = emptySet(),
                medications = listOf(validMedication())
            )
        )

        assertFalse(result)
    }

    @Test
    fun hasSaveableMedicationGroupContent_allows_daily_schedule_without_weekly_days() {
        val result = hasSaveableMedicationGroupContent(
            MedicationGroupEditorUiState(
                groupName = "Morning meds",
                scheduleType = MedicationGroupScheduleType.DAILY,
                weeklyDaysOfWeek = emptySet(),
                medications = listOf(validMedication())
            )
        )

        assertTrue(result)
    }

    private fun validMedication(): MedicationGroupMedicationItemUiState {
        return MedicationGroupMedicationItemUiState(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            )
        )
    }
}
