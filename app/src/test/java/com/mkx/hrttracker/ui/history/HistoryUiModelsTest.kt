package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.PlanCalendarDayUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class HistoryUiModelsTest {
    @Test
    fun buildHistoryVisibleEntries_filters_to_selected_day_when_present() {
        val entries = listOf(
            entryAt(LocalDateTime.of(2026, 4, 10, 8, 0)),
            entryAt(LocalDateTime.of(2026, 4, 11, 8, 0))
        )

        val visibleEntries = buildHistoryVisibleEntries(
            entries = entries,
            displayedMonth = YearMonth.of(2026, 4),
            selectedDate = LocalDate.of(2026, 4, 11)
        )

        assertEquals(1, visibleEntries.size)
        assertEquals(
            LocalDate.of(2026, 4, 11),
            visibleEntries.single().appliedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        )
    }

    @Test
    fun buildHistoryMonthSummary_counts_logged_on_track_partial_and_missed_days() {
        val summary = buildHistoryMonthSummary(
            entries = listOf(
                entryAt(LocalDateTime.of(2026, 4, 10, 8, 0)),
                entryAt(LocalDateTime.of(2026, 4, 10, 20, 0)),
                entryAt(LocalDateTime.of(2026, 4, 11, 8, 0))
            ),
            displayedMonth = YearMonth.of(2026, 4),
            dayStates = mapOf(
                LocalDate.of(2026, 4, 10) to stateFor(PlanCalendarDayStatus.FULFILLED),
                LocalDate.of(2026, 4, 11) to stateFor(PlanCalendarDayStatus.PARTIAL),
                LocalDate.of(2026, 4, 12) to stateFor(PlanCalendarDayStatus.SCHEDULED),
                LocalDate.of(2026, 4, 13) to stateFor(PlanCalendarDayStatus.UNPLANNED),
                LocalDate.of(2026, 4, 25) to stateFor(PlanCalendarDayStatus.SCHEDULED)
            ),
            today = LocalDate.of(2026, 4, 20)
        )

        assertEquals(3, summary.logged)
        assertEquals(1, summary.onTrack)
        assertEquals(1, summary.partial)
        assertEquals(1, summary.missed)
    }

    private fun stateFor(status: PlanCalendarDayStatus): PlanCalendarDayUiState {
        return when (status) {
            PlanCalendarDayStatus.NONE -> PlanCalendarDayUiState()
            PlanCalendarDayStatus.UNPLANNED -> PlanCalendarDayUiState(hasUnplannedRecord = true)
            PlanCalendarDayStatus.SCHEDULED -> PlanCalendarDayUiState(expectedOccurrenceCount = 1)
            PlanCalendarDayStatus.PARTIAL -> PlanCalendarDayUiState(expectedOccurrenceCount = 2, matchedOccurrenceCount = 1)
            PlanCalendarDayStatus.FULFILLED -> PlanCalendarDayUiState(expectedOccurrenceCount = 1, matchedOccurrenceCount = 1)
        }
    }

    private fun entryAt(dateTime: LocalDateTime): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            routeOfAdministration = RouteOfAdministration.ORAL,
            medicineName = "Estradiol",
            dosageMgAsMedicine = 2.0,
            dosageMgAsEstradiol = 2.0,
            sourceType = MedicationLogEntrySourceType.MANUAL,
            sourceGroupUuid = null,
            appliedAt = dateTime.atZone(ZoneId.systemDefault()).toInstant()
        )
    }
}
