package com.mkx.hrttracker.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalibrationEditorUnsavedChangesTest {

    private fun snapshot(
        notes: String = "",
        drafts: List<CalibrationResultDraftSnapshot> = emptyList(),
    ) = CalibrationEditorDraftSnapshot(
        collectedDateEpochDay = 20_000,
        collectedTimeSecondOfDay = 9 * 3600,
        collectedZoneId = "UTC",
        notes = notes,
        drafts = drafts,
    )

    @Test
    fun blankRows_andTheirDefaultUnits_areNotEdits() {
        val loaded = snapshot(drafts = listOf(CalibrationResultDraftSnapshot(analyteKey = "e2", unit = "pg_ml")))
        val unitsFollowedSettings = snapshot(
            drafts = listOf(
                CalibrationResultDraftSnapshot(analyteKey = "e2", unit = "pmol_l"),
                CalibrationResultDraftSnapshot(analyteKey = "t"),
            ),
        )
        assertEquals(loaded.enteredContent(), unitsFollowedSettings.enteredContent())
    }

    @Test
    fun enteredValue_orNote_isAnEdit() {
        val loaded = snapshot(drafts = listOf(CalibrationResultDraftSnapshot(analyteKey = "e2")))
        assertNotEquals(
            loaded.enteredContent(),
            snapshot(drafts = listOf(CalibrationResultDraftSnapshot(analyteKey = "e2", valueText = "120")))
                .enteredContent(),
        )
        assertNotEquals(loaded.enteredContent(), snapshot(notes = "fasting").enteredContent())
        assertEquals(loaded.enteredContent(), snapshot(notes = "  ").enteredContent())
    }
}
