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
