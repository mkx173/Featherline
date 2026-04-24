package com.mkx.hrttracker.ui.history

import com.kizitonwose.calendar.core.DayPosition
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
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
import java.util.Locale
import java.util.UUID

class HistoryUiModelsTest {
    @Test
    fun historyEntryGroupDayFormatter_uses_compact_chinese_month_day_format() {
        assertEquals(
            "4月23日",
            LocalDate.of(2026, 4, 23).format(historyEntryGroupDayFormatter(Locale.SIMPLIFIED_CHINESE))
        )
    }

    @Test
    fun historyEntryGroupDayFormatter_keeps_default_non_chinese_pattern() {
        assertEquals(
            "Apr 23",
            LocalDate.of(2026, 4, 23).format(historyEntryGroupDayFormatter(Locale.US))
        )
    }

    @Test
    fun historyMonthLabelFormatter_uses_compact_chinese_month_format() {
        assertEquals(
            "4月",
            LocalDate.of(2026, 4, 23).format(historyMonthLabelFormatter(Locale.SIMPLIFIED_CHINESE))
        )
    }

    @Test
    fun historyCalendarMonthTitleFormatter_uses_chinese_year_month_format() {
        assertEquals(
            "2026年4月",
            LocalDate.of(2026, 4, 23).format(
                historyCalendarMonthTitleFormatter(Locale.SIMPLIFIED_CHINESE)
            )
        )
    }

    @Test
    fun historyEntrySupportingText_includes_plain_count_text_between_primary_text_and_group_name() {
        assertEquals(
            "1mg \u00B7 2x \u00B7 Nightly estradiol",
            historyEntrySupportingText(
                primaryText = "1mg",
                count = 2,
                groupName = "Nightly estradiol"
            )
        )
    }

    @Test
    fun historyEntrySupportingText_omits_count_when_single_entry() {
        assertEquals(
            "1mg \u00B7 Nightly estradiol",
            historyEntrySupportingText(
                primaryText = "1mg",
                count = 1,
                groupName = "Nightly estradiol"
            )
        )
    }

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
    fun resolveHistoryEffectiveSelectedDate_prefers_pending_date_only_when_month_matches() {
        assertEquals(
            LocalDate.of(2026, 4, 2),
            resolveHistoryEffectiveSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 2),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            resolveHistoryEffectiveSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 5, 1),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
    }

    @Test
    fun resolveHistoryDisplayedSelectedDate_clears_old_highlight_during_cross_month_handoff() {
        assertEquals(
            null,
            resolveHistoryDisplayedSelectedDate(
                displayedMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = LocalDate.of(2026, 4, 2),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            LocalDate.of(2026, 4, 2),
            resolveHistoryDisplayedSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 2),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            resolveHistoryDisplayedSelectedDate(
                displayedMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            null,
            resolveHistoryDisplayedSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
    }

    @Test
    fun shouldClearHistorySelectionOnMonthChange_preserves_selection_only_for_pending_date_in_target_month() {
        assertEquals(
            false,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 2)
            )
        )
        assertEquals(
            true,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 5, 1)
            )
        )
        assertEquals(
            true,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null
            )
        )
    }

    @Test
    fun buildHistoryMonthSummary_counts_logged_on_track_partial_missed_and_off_plan_days() {
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
                LocalDate.of(2026, 4, 12) to stateFor(PlanCalendarDayStatus.MISSED),
                LocalDate.of(2026, 4, 13) to stateFor(PlanCalendarDayStatus.OFFPLAN),
                LocalDate.of(2026, 4, 25) to stateFor(PlanCalendarDayStatus.MISSED)
            ),
            today = LocalDate.of(2026, 4, 20)
        )

        assertEquals(3, summary.logged)
        assertEquals(1, summary.onTrack)
        assertEquals(1, summary.partial)
        assertEquals(1, summary.missed)
        assertEquals(1, summary.offPlan)
    }

    @Test
    fun buildHistoryMonthSummary_counts_persisted_counted_rows_as_one_logged_entry() {
        val summary = buildHistoryMonthSummary(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    ),
                    dosageMgAsEstradiol = 2.0,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 10, 8, 0)),
                    scheduledFor = LocalDateTime.of(2026, 4, 10, 8, 0),
                    count = 2
                ),
                entryAt(LocalDateTime.of(2026, 4, 11, 8, 0))
            ),
            displayedMonth = YearMonth.of(2026, 4),
            dayStates = emptyMap(),
            today = LocalDate.of(2026, 4, 20)
        )

        assertEquals(2, summary.logged)
        assertEquals(0, summary.offPlan)
    }

    @Test
    fun groupHistoryEntriesByDate_keeps_days_in_existing_order_and_sorts_entries_within_day_ascending() {
        val groupedEntries = groupHistoryEntriesByDate(
            listOf(
                entryAt(LocalDateTime.of(2026, 4, 11, 22, 0)),
                entryAt(LocalDateTime.of(2026, 4, 11, 8, 0)),
                entryAt(LocalDateTime.of(2026, 4, 10, 20, 0)),
            )
        )

        assertEquals(
            listOf(LocalDate.of(2026, 4, 11), LocalDate.of(2026, 4, 10)),
            groupedEntries.keys.toList()
        )
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 11, 8, 0),
                LocalDateTime.of(2026, 4, 11, 22, 0)
            ),
            groupedEntries.getValue(LocalDate.of(2026, 4, 11)).map { entry ->
                entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDateTime()
            }
        )
    }

    @Test
    fun toggleHistoryEntrySelection_adds_or_removes_single_entry_id() {
        val entryId = UUID.fromString("88fd619f-528b-4510-b41e-6fef01f20d24")

        val selected = toggleHistoryEntrySelection(
            currentSelection = emptySet(),
            entryId = entryId
        )
        val deselected = toggleHistoryEntrySelection(
            currentSelection = selected,
            entryId = entryId
        )

        assertEquals(setOf(entryId), selected)
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

    @Test
    fun historyCalendarIndicatorStatus_uses_none_for_future_dates() {
        val today = LocalDate.of(2026, 4, 20)

        assertEquals(
            PlanCalendarDayStatus.NONE,
            historyCalendarIndicatorStatus(
                date = today.plusDays(1),
                today = today,
                dayStatus = PlanCalendarDayStatus.MISSED
            )
        )
        assertEquals(
            PlanCalendarDayStatus.PARTIAL,
            historyCalendarIndicatorStatus(
                date = today.minusDays(1),
                today = today,
                dayStatus = PlanCalendarDayStatus.PARTIAL
            )
        )
    }

    @Test
    fun historyCalendarDayClickTargetMonth_returns_null_for_current_month_days() {
        assertEquals(
            null,
            historyCalendarDayClickTargetMonth(
                position = DayPosition.MonthDate,
                date = LocalDate.of(2026, 4, 18)
            )
        )
    }

    @Test
    fun historyCalendarDayClickTargetMonth_returns_adjacent_month_for_in_and_out_dates() {
        assertEquals(
            YearMonth.of(2026, 3),
            historyCalendarDayClickTargetMonth(
                position = DayPosition.InDate,
                date = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            YearMonth.of(2026, 5),
            historyCalendarDayClickTargetMonth(
                position = DayPosition.OutDate,
                date = LocalDate.of(2026, 5, 1)
            )
        )
    }

    private fun stateFor(status: PlanCalendarDayStatus): PlanCalendarDayUiState {
        return when (status) {
            PlanCalendarDayStatus.NONE -> PlanCalendarDayUiState()
            PlanCalendarDayStatus.OFFPLAN -> PlanCalendarDayUiState(hasOffPlanRecord = true)
            PlanCalendarDayStatus.MISSED -> PlanCalendarDayUiState(expectedOccurrenceCount = 1)
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
