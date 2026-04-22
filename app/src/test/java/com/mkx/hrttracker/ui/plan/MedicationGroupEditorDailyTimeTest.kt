package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MedicationGroupEditorDailyTimeTest {
    @Test
    fun append_daily_time_uses_selected_time_and_strips_seconds() {
        val updated = appendDailyTime(
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(8, 0))
            ),
            time = LocalTime.of(12, 34, 56, 123_000_000)
        )

        assertEquals(2, updated.size)
        assertEquals(LocalTime.of(12, 34), updated.last().time)
    }

    @Test
    fun has_duplicate_daily_time_matches_existing_time() {
        assertTrue(
            hasDuplicateDailyTime(
                dailyTimes = listOf(
                    MedicationGroupScheduleTimeUiState(
                        localId = "existing",
                        time = LocalTime.of(12, 34)
                    )
                ),
                time = LocalTime.of(12, 34, 59)
            )
        )
    }

    @Test
    fun has_duplicate_daily_time_ignores_current_row_when_editing() {
        assertFalse(
            hasDuplicateDailyTime(
                dailyTimes = listOf(
                    MedicationGroupScheduleTimeUiState(
                        localId = "existing",
                        time = LocalTime.of(12, 34)
                    )
                ),
                time = LocalTime.of(12, 34),
                excludingLocalId = "existing"
            )
        )
    }
}
