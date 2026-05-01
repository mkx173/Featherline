package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
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
    fun append_daily_time_sorts_by_time() {
        val updated = appendDailyTime(
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(8, 0)),
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(20, 0)),
            ),
            time = LocalTime.of(12, 0)
        )

        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(20, 0)),
            updated.map(MedicationGroupScheduleTimeUiState::time)
        )
    }

    @Test
    fun updated_daily_time_sorts_by_time_and_strips_seconds() {
        val updated = dailyTimesWithUpdatedTime(
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(
                    localId = "morning",
                    time = LocalTime.of(8, 0),
                ),
                MedicationGroupScheduleTimeUiState(
                    localId = "evening",
                    time = LocalTime.of(20, 0),
                ),
            ),
            localId = "evening",
            time = LocalTime.of(7, 30, 45)
        )

        assertEquals(
            listOf("evening", "morning"),
            updated.map(MedicationGroupScheduleTimeUiState::localId)
        )
        assertEquals(
            listOf(LocalTime.of(7, 30), LocalTime.of(8, 0)),
            updated.map(MedicationGroupScheduleTimeUiState::time)
        )
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

    @Test
    fun next_default_date_time_rounds_forward_to_next_half_hour() {
        val cases = listOf(
            LocalDateTime.of(2026, 4, 25, 14, 39) to
                LocalDateTime.of(2026, 4, 25, 15, 0),
            LocalDateTime.of(2026, 4, 25, 15, 1) to
                LocalDateTime.of(2026, 4, 25, 15, 30),
            LocalDateTime.of(2026, 4, 25, 23, 31) to
                LocalDateTime.of(2026, 4, 26, 0, 0),
        )

        cases.forEach { (currentDateTime, expectedDefaultDateTime) ->
            assertEquals(
                expectedDefaultDateTime,
                nextMedicationGroupEditorDefaultDateTime(currentDateTime)
            )
        }
    }
}
