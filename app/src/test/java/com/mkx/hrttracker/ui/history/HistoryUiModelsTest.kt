package com.mkx.hrttracker.ui.history

import com.kizitonwose.calendar.core.DayPosition
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.testCustomMedicationDetails
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testInstant
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
    fun buildHistoryVisibleEntries_filters_to_selected_out_of_month_day_when_present() {
        val entries = listOf(
            entryAt(LocalDateTime.of(2026, 3, 30, 8, 0)),
            entryAt(LocalDateTime.of(2026, 4, 11, 8, 0))
        )

        val visibleEntries = buildHistoryVisibleEntries(
            entries = entries,
            displayedMonth = YearMonth.of(2026, 4),
            selectedDate = LocalDate.of(2026, 3, 30)
        )

        assertEquals(1, visibleEntries.size)
        assertEquals(
            LocalDate.of(2026, 3, 30),
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

    @Test
    fun collapseHistoryEntries_collapses_exact_duplicate_logs_into_one_counted_entry() {
        val firstId = UUID.fromString("b33e87e1-7a60-461b-8fc3-0ba5afb0b147")
        val secondId = UUID.fromString("640010dc-63e5-40f4-a836-36664d4d4a68")
        val sharedAppliedAt = LocalDateTime.of(2026, 4, 10, 8, 0)
        val collapsedEntries = collapseHistoryEntries(
            listOf(
                testMedicationLogEntry(
                    uuid = firstId,
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    ),
                    dosageMgAsEstradiol = 2.0,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = UUID.fromString("d6378318-4ee8-46b3-8430-3131af61efdf"),
                    appliedAt = testInstant(sharedAppliedAt),
                    scheduledFor = sharedAppliedAt
                ),
                testMedicationLogEntry(
                    uuid = secondId,
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    ),
                    dosageMgAsEstradiol = 2.0,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = UUID.fromString("d6378318-4ee8-46b3-8430-3131af61efdf"),
                    appliedAt = testInstant(sharedAppliedAt),
                    scheduledFor = sharedAppliedAt
                ),
                testMedicationLogEntry(
                    uuid = UUID.fromString("cd74c798-022a-49e0-a8cd-67d9f92d2c59"),
                    details = testCustomMedicationDetails(
                        medicationName = "Progesterone",
                        dose = MedicationDose.MgAsMedicine(100.0)
                    ),
                    dosageMgAsEstradiol = null,
                    sourceType = MedicationLogEntrySourceType.MANUAL,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 10, 22, 0))
                )
            )
        )

        val duplicateEntry = collapsedEntries.first { entry -> entry.count == 2 }
        val singleEntry = collapsedEntries.first { entry -> entry.count == 1 }

        assertEquals(2, collapsedEntries.size)
        assertEquals(setOf(firstId, secondId), duplicateEntry.entryIds)
        assertEquals(1, singleEntry.count)
    }

    @Test
    fun toggleHistoryEntrySelection_adds_or_removes_the_full_collapsed_entry_set() {
        val entryIds = setOf(
            UUID.fromString("88fd619f-528b-4510-b41e-6fef01f20d24"),
            UUID.fromString("fd27ff9d-3991-4159-a117-225bad74532b")
        )

        val selected = toggleHistoryEntrySelection(
            currentSelection = emptySet(),
            entryIds = entryIds
        )
        val deselected = toggleHistoryEntrySelection(
            currentSelection = selected,
            entryIds = entryIds
        )

        assertEquals(entryIds, selected)
        assertEquals(emptySet<UUID>(), deselected)
    }

    @Test
    fun historyEntryTapAction_opens_editor_only_when_selection_mode_is_inactive() {
        assertEquals(
            HistoryEntryTapAction.OPEN_EDITOR,
            historyEntryTapAction(selectedEntryIds = emptySet())
        )
        assertEquals(
            HistoryEntryTapAction.TOGGLE_SELECTION,
            historyEntryTapAction(
                selectedEntryIds = setOf(
                    UUID.fromString("88fd619f-528b-4510-b41e-6fef01f20d24")
                )
            )
        )
    }

    @Test
    fun historyCalendarDayAlpha_keeps_adjacent_month_days_visible() {
        assertEquals(
            0.56f,
            historyCalendarDayAlpha(
                position = DayPosition.InDate,
                isFuture = false,
                isSelected = false,
                isToday = false
            ),
            0f
        )
        assertEquals(
            0.56f,
            historyCalendarDayAlpha(
                position = DayPosition.OutDate,
                isFuture = false,
                isSelected = false,
                isToday = false
            ),
            0f
        )
        assertEquals(
            1f,
            historyCalendarDayAlpha(
                position = DayPosition.OutDate,
                isFuture = false,
                isSelected = true,
                isToday = false
            ),
            0f
        )
    }

    @Test
    fun historyCalendarDayAlpha_uses_same_alpha_for_future_and_out_of_month_days() {
        assertEquals(
            0.56f,
            historyCalendarDayAlpha(
                position = DayPosition.MonthDate,
                isFuture = true,
                isSelected = false,
                isToday = false
            ),
            0f
        )
        assertEquals(
            0.56f,
            historyCalendarDayAlpha(
                position = DayPosition.OutDate,
                isFuture = true,
                isSelected = false,
                isToday = false
            ),
            0f
        )
        assertEquals(
            1f,
            historyCalendarDayAlpha(
                position = DayPosition.MonthDate,
                isFuture = false,
                isSelected = false,
                isToday = false
            ),
            0f
        )
    }

    @Test
    fun canSelectHistoryCalendarDate_disallows_future_dates() {
        val today = LocalDate.of(2026, 4, 20)

        assertEquals(
            true,
            canSelectHistoryCalendarDate(
                date = today.minusDays(1),
                today = today
            )
        )
        assertEquals(
            true,
            canSelectHistoryCalendarDate(
                date = today,
                today = today
            )
        )
        assertEquals(
            false,
            canSelectHistoryCalendarDate(
                date = today.plusDays(1),
                today = today
            )
        )
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
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceType = MedicationLogEntrySourceType.MANUAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(dateTime)
        )
    }
}
