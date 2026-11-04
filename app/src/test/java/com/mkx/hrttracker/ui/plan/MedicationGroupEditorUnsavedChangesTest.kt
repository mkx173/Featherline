package com.mkx.hrttracker.ui.plan

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
}
