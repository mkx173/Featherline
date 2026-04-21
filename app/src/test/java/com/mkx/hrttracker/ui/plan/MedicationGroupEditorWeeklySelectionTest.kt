package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
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
}
