package com.mkx.hrttracker.ui.history

import com.kizitonwose.calendar.core.DayPosition
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.util.calendarMonthTitleFormatter
import com.mkx.hrttracker.util.historyEntryGroupDateFormatter
import com.mkx.hrttracker.util.historyEntryGroupDayFormatter
import com.mkx.hrttracker.util.historyMonthLabelFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

class HistoryUiModelsTest {
    // Shared so the (medicineUuid, applicationType, doseInstruction) signature
    // matches between scheduled group slots and the log entries used to
    // fulfill them. A random per-call UUID would mean no entry ever matches
    // its slot, and the fulfillment-status assertions would all degrade to
    // MISSED.
    private val estradiolMedicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun historyTopAppBarFlipFace_switches_faces_at_halfway() {
        assertEquals(
            HistoryTopAppBarFlipFace.NORMAL,
            historyTopAppBarFlipFace(progress = 0f)
        )
        assertEquals(
            HistoryTopAppBarFlipFace.NORMAL,
            historyTopAppBarFlipFace(progress = 0.49f)
        )
        assertEquals(
            HistoryTopAppBarFlipFace.SELECTION,
            historyTopAppBarFlipFace(progress = 0.5f)
        )
        assertEquals(
            HistoryTopAppBarFlipFace.SELECTION,
            historyTopAppBarFlipFace(progress = 1f)
        )
    }

    @Test
    fun historyTopAppBarFlipRotationX_rotates_normal_out_and_selection_in() {
        assertEquals(
            0f,
            historyTopAppBarFlipRotationX(
                progress = 0f,
                face = HistoryTopAppBarFlipFace.NORMAL
            ),
            0.001f
        )
        assertEquals(
            45f,
            historyTopAppBarFlipRotationX(
                progress = 0.25f,
                face = HistoryTopAppBarFlipFace.NORMAL
            ),
            0.001f
        )
        assertEquals(
            -45f,
            historyTopAppBarFlipRotationX(
                progress = 0.75f,
                face = HistoryTopAppBarFlipFace.SELECTION
            ),
            0.001f
        )
        assertEquals(
            0f,
            historyTopAppBarFlipRotationX(
                progress = 1f,
                face = HistoryTopAppBarFlipFace.SELECTION
            ),
            0.001f
        )
    }

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
    fun historyEntryGroupDateFormatter_omits_year_for_current_year() {
        val formatter = historyEntryGroupDateFormatter(
            appLocale = Locale.US,
            today = LocalDate.of(2026, 4, 28)
        )

        assertEquals("Apr 23", formatter(LocalDate.of(2026, 4, 23)))
    }

    @Test
    fun historyEntryGroupDateFormatter_shows_year_for_different_year() {
        val formatter = historyEntryGroupDateFormatter(
            appLocale = Locale.US,
            today = LocalDate.of(2026, 4, 28)
        )

        assertEquals("Dec 31, 2025", formatter(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun historyEntryGroupDateFormatter_shows_year_for_different_year_in_chinese() {
        val formatter = historyEntryGroupDateFormatter(
            appLocale = Locale.SIMPLIFIED_CHINESE,
            today = LocalDate.of(2026, 4, 28)
        )

        assertEquals("2025年12月31日", formatter(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun historyMonthLabelFormatter_uses_compact_chinese_month_format() {
        assertEquals(
            "4月",
            LocalDate.of(2026, 4, 23).format(historyMonthLabelFormatter(Locale.SIMPLIFIED_CHINESE))
        )
    }

    @Test
    fun calendarMonthTitleFormatter_omits_year_for_current_year() {
        val formatter = calendarMonthTitleFormatter(
            locale = Locale.US,
            currentYear = 2026
        )

        assertEquals("April", formatter(LocalDate.of(2026, 4, 23)))
    }

    @Test
    fun calendarMonthTitleFormatter_shows_year_for_different_year() {
        val formatter = calendarMonthTitleFormatter(
            locale = Locale.US,
            currentYear = 2026
        )

        assertEquals("December 2025", formatter(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun calendarMonthTitleFormatter_omits_year_for_current_year_in_chinese() {
        val formatter = calendarMonthTitleFormatter(
            locale = Locale.SIMPLIFIED_CHINESE,
            currentYear = 2026
        )

        assertEquals(
            "4月",
            formatter(LocalDate.of(2026, 4, 23))
        )
    }

    @Test
    fun calendarMonthTitleFormatter_shows_year_for_different_year_in_chinese() {
        val formatter = calendarMonthTitleFormatter(
            locale = Locale.SIMPLIFIED_CHINESE,
            currentYear = 2026
        )

        assertEquals(
            "2025年12月",
            formatter(LocalDate.of(2025, 12, 31))
        )
    }

    @Test
    fun historyCalendarDayStateMonthRanges_tracksVisibleMonthWithoutLoadingGap() {
        assertEquals(
            listOf(
                HistoryCalendarDayStateMonthRange(
                    startMonth = YearMonth.of(2026, 1),
                    endMonth = YearMonth.of(2026, 5),
                ),
                HistoryCalendarDayStateMonthRange(
                    startMonth = YearMonth.of(2026, 7),
                    endMonth = YearMonth.of(2026, 11),
                ),
            ),
            historyCalendarDayStateMonthRanges(
                displayedMonth = YearMonth.of(2026, 3),
                visibleMonths = setOf(YearMonth.of(2026, 9)),
                calendarStartMonth = YearMonth.of(2026, 1),
                calendarEndMonth = YearMonth.of(2026, 12),
            )
        )
    }

    @Test
    fun historyCalendarDayStateMonthRanges_mergesOverlappingWindows() {
        assertEquals(
            listOf(
                HistoryCalendarDayStateMonthRange(
                    startMonth = YearMonth.of(2026, 1),
                    endMonth = YearMonth.of(2026, 7),
                ),
            ),
            historyCalendarDayStateMonthRanges(
                displayedMonth = YearMonth.of(2026, 3),
                visibleMonths = setOf(YearMonth.of(2026, 5)),
                calendarStartMonth = YearMonth.of(2026, 1),
                calendarEndMonth = YearMonth.of(2026, 12),
            )
        )
    }

    @Test
    fun historyEntrySupportingText_renders_count_before_primary_text() {
        assertEquals(
            "2x \u00B7 1mg \u00B7 Nightly estradiol",
            historyEntrySupportingText(
                primaryText = "1mg",
                countText = "2x",
                groupName = "Nightly estradiol"
            )
        )
    }

    @Test
    fun historyEntrySupportingText_omits_count_when_null() {
        assertEquals(
            "1mg \u00B7 Nightly estradiol",
            historyEntrySupportingText(
                primaryText = "1mg",
                countText = null,
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
    fun resolveHistoryEffectiveSelectedDate_keeps_selection_until_reset_target_month() {
        assertEquals(
            LocalDate.of(2026, 3, 31),
            resolveHistoryEffectiveSelectedDate(
                displayedMonth = YearMonth.of(2026, 2),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            null,
            resolveHistoryEffectiveSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
    }

    @Test
    fun resolveHistoryEffectiveSelectedDate_clears_out_of_month_selection_without_pending_transition() {
        assertEquals(
            null,
            resolveHistoryEffectiveSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
    }

    @Test
    fun resolveHistoryCalendarSelectedDate_uses_pending_date_immediately() {
        assertEquals(
            LocalDate.of(2026, 4, 2),
            resolveHistoryCalendarSelectedDate(
                displayedMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = LocalDate.of(2026, 4, 2),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            LocalDate.of(2026, 4, 2),
            resolveHistoryCalendarSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 2),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            resolveHistoryCalendarSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
    }

    @Test
    fun resolveHistoryCalendarSelectedDate_clears_highlight_in_reset_target_month() {
        assertEquals(
            LocalDate.of(2026, 3, 31),
            resolveHistoryCalendarSelectedDate(
                displayedMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            null,
            resolveHistoryCalendarSelectedDate(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 4, 2),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
    }

    @Test
    fun shouldCommitPendingHistorySelection_waits_for_settled_target_month() {
        assertEquals(
            false,
            shouldCommitPendingHistorySelection(
                settledDisplayedMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = LocalDate.of(2026, 4, 1),
                selectedDate = null
            )
        )
        assertEquals(
            true,
            shouldCommitPendingHistorySelection(
                settledDisplayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 1),
                selectedDate = null
            )
        )
        assertEquals(
            false,
            shouldCommitPendingHistorySelection(
                settledDisplayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 1),
                selectedDate = LocalDate.of(2026, 4, 1)
            )
        )
    }

    @Test
    fun shouldClearPendingHistorySelection_clears_after_navigation_settles_elsewhere() {
        assertEquals(
            false,
            shouldClearPendingHistorySelection(
                settledDisplayedMonth = YearMonth.of(2026, 3),
                navigationMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 1)
            )
        )
        assertEquals(
            false,
            shouldClearPendingHistorySelection(
                settledDisplayedMonth = YearMonth.of(2026, 4),
                navigationMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 1)
            )
        )
        assertEquals(
            true,
            shouldClearPendingHistorySelection(
                settledDisplayedMonth = YearMonth.of(2026, 3),
                navigationMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = LocalDate.of(2026, 4, 1)
            )
        )
    }

    @Test
    fun shouldClearHistorySelectionOnMonthChange_preserves_selection_for_pending_or_selected_date_in_target_month() {
        assertEquals(
            false,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 4, 2),
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
        assertEquals(
            true,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = LocalDate.of(2026, 5, 1),
                selectedDate = LocalDate.of(2026, 4, 2)
            )
        )
        assertEquals(
            false,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 4, 2)
            )
        )
        assertEquals(
            true,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31)
            )
        )
    }

    @Test
    fun shouldClearHistorySelectionOnMonthChange_delays_reset_until_target_month() {
        assertEquals(
            false,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 3),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            false,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 2),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            true,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = YearMonth.of(2026, 4),
                pendingSelectedDate = null,
                selectedDate = LocalDate.of(2026, 3, 31),
                pendingSelectionResetTargetMonth = YearMonth.of(2026, 4)
            )
        )
    }

    @Test
    fun canResetHistoryCalendar_usesImmediateNavigationMonthAndResettableSelection() {
        assertEquals(
            true,
            canResetHistoryCalendar(
                navigationMonth = YearMonth.of(2026, 3),
                currentMonth = YearMonth.of(2026, 4),
                hasResettableSelection = false
            )
        )
        assertEquals(
            true,
            canResetHistoryCalendar(
                navigationMonth = YearMonth.of(2026, 4),
                currentMonth = YearMonth.of(2026, 4),
                hasResettableSelection = true
            )
        )
        assertEquals(
            false,
            canResetHistoryCalendar(
                navigationMonth = YearMonth.of(2026, 4),
                currentMonth = YearMonth.of(2026, 4),
                hasResettableSelection = false
            )
        )
    }

    @Test
    fun shouldAnimateHistoryCalendarReset_uses_six_month_threshold() {
        assertEquals(
            true,
            shouldAnimateHistoryCalendarReset(
                navigationMonth = YearMonth.of(2025, 10),
                currentMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            false,
            shouldAnimateHistoryCalendarReset(
                navigationMonth = YearMonth.of(2025, 9),
                currentMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            false,
            shouldAnimateHistoryCalendarReset(
                navigationMonth = YearMonth.of(2025, 8),
                currentMonth = YearMonth.of(2026, 4)
            )
        )
    }

    @Test
    fun historyMonthPickerYearOptions_uses_calendar_range_years() {
        assertEquals(
            listOf(2024, 2025, 2026),
            historyMonthPickerYearOptions(
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
    }

    @Test
    fun historyMonthPickerMonthOptions_filters_boundary_years() {
        assertEquals(
            listOf(11, 12),
            historyMonthPickerMonthOptions(
                selectedYear = 2024,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
        assertEquals(
            (1..12).toList(),
            historyMonthPickerMonthOptions(
                selectedYear = 2025,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
        assertEquals(
            listOf(1, 2),
            historyMonthPickerMonthOptions(
                selectedYear = 2026,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
    }

    @Test
    fun coerceHistoryMonthPickerSelection_keeps_selection_in_calendar_range() {
        assertEquals(
            YearMonth.of(2024, 11),
            coerceHistoryMonthPickerSelection(
                selectedYear = 2024,
                selectedMonthValue = 1,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
        assertEquals(
            YearMonth.of(2026, 2),
            coerceHistoryMonthPickerSelection(
                selectedYear = 2027,
                selectedMonthValue = 12,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
        assertEquals(
            YearMonth.of(2025, 6),
            coerceHistoryMonthPickerSelection(
                selectedYear = 2025,
                selectedMonthValue = 6,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
    }

    @Test
    fun historyMonthPickerWheelState_uses_filtered_options_and_indexes() {
        val state = historyMonthPickerWheelState(
            selectedMonth = YearMonth.of(2024, 11),
            calendarStartMonth = YearMonth.of(2024, 11),
            calendarEndMonth = YearMonth.of(2026, 2)
        )

        assertEquals(YearMonth.of(2024, 11), state.selectedMonth)
        assertEquals(listOf(2024, 2025, 2026), state.yearOptions)
        assertEquals(0, state.selectedYearIndex)
        assertEquals(listOf(11, 12), state.monthOptions)
        assertEquals(0, state.selectedMonthIndex)
    }

    @Test
    fun historyMonthPickerSelectionForYearIndex_coerces_month_into_range() {
        assertEquals(
            YearMonth.of(2026, 2),
            historyMonthPickerSelectionForYearIndex(
                selectedYearIndex = 2,
                currentMonthValue = 12,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
            )
        )
    }

    @Test
    fun historyMonthPickerSelectionForMonthIndex_maps_index_within_filtered_months() {
        assertEquals(
            YearMonth.of(2024, 12),
            historyMonthPickerSelectionForMonthIndex(
                selectedYear = 2024,
                selectedMonthIndex = 1,
                calendarStartMonth = YearMonth.of(2024, 11),
                calendarEndMonth = YearMonth.of(2026, 2)
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
    fun buildHistoryMonthSummary_counts_off_plan_days_even_when_primary_status_is_fulfilled() {
        val summary = buildHistoryMonthSummary(
            entries = listOf(entryAt(LocalDateTime.of(2026, 4, 10, 8, 0))),
            displayedMonth = YearMonth.of(2026, 4),
            dayStates = mapOf(
                LocalDate.of(2026, 4, 10) to stateFor(
                    status = PlanCalendarDayStatus.FULFILLED,
                    hasOffPlanRecord = true
                )
            ),
            today = LocalDate.of(2026, 4, 20)
        )

        assertEquals(1, summary.onTrack)
        assertEquals(1, summary.offPlan)
    }

    @Test
    fun buildHistoryCalendarDayUiState_keeps_primary_status_and_tracks_same_day_off_plan_records() {
        val group = MedicationGroup(
            uuid = UUID.fromString("9420e8eb-379e-4e28-9ed9-d4e01a845744"),
            name = "Estradiol",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("88ceb348-79c3-42e8-b7ca-d7db366d51d8"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            updatedAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0))
        )

        val dayStates = buildHistoryCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("9c6b8810-0f6a-49ec-b4c1-d6777f5f3d91"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 10, 8, 0)),
                    scheduledFor = LocalDateTime.of(2026, 4, 10, 8, 0)
                ),
                entryAt(LocalDateTime.of(2026, 4, 10, 20, 0))
            ),
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 10)
        )

        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.FULFILLED,
                hasOffPlanRecord = true
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 10))
        )
    }

    @Test
    fun buildHistoryCalendarDayUiState_matches_planned_record_by_scheduled_date_when_applied_after_midnight() {
        val scheduledDate = LocalDate.of(2026, 4, 16)
        val group = MedicationGroup(
            uuid = UUID.fromString("c8ec1367-665a-47cc-bab2-24b3f1e66374"),
            name = "Night estradiol",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = setOf(scheduledDate.dayOfWeek),
                times = listOf(LocalTime.of(23, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("9bd71b0c-a8f6-4660-a67e-ec0b7d082be4"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            updatedAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0))
        )

        val dayStates = buildHistoryCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("50a94865-4686-4f35-9291-42f4f8ed9d16"),
                    medicine = group.medications.single().medicine,
                    applicationType = group.medications.single().applicationType,
                    doseInstruction = group.medications.single().doseInstruction,
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 17, 0, 15)),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 23, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 17)
        )

        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.FULFILLED,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 16))
        )
        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.NONE,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 17))
        )
    }

    @Test
    fun buildHistoryCalendarDayUiState_marks_outside_window_linked_record_off_plan_on_scheduled_date() {
        val scheduledDate = LocalDate.of(2026, 5, 2)
        val appliedDate = LocalDate.of(2026, 5, 1)
        val group = MedicationGroup(
            uuid = UUID.fromString("4d9f2fd3-dd09-4d82-953e-6558d1293f20"),
            name = "Weekly estradiol",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = setOf(scheduledDate.dayOfWeek),
                times = listOf(LocalTime.of(17, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("44ad043e-d440-40ef-b20d-2d265cb08d58"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            updatedAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0))
        )

        val dayStates = buildHistoryCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("0e0d2d8c-3cdd-4747-8bcc-cc1de4f00809"),
                    medicine = group.medications.single().medicine,
                    applicationType = group.medications.single().applicationType,
                    doseInstruction = group.medications.single().doseInstruction,
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(LocalDateTime.of(2026, 5, 1, 12, 0)),
                    scheduledFor = LocalDateTime.of(2026, 5, 2, 17, 0)
                )
            ),
            startDate = appliedDate,
            endDate = scheduledDate
        )

        // With planCalendarDate, scheduledFor wins for bucketing: entry lands on scheduledDate.
        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.NONE,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(appliedDate)
        )
        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.MISSED,
                hasOffPlanRecord = true
            ),
            dayStates.getValue(scheduledDate)
        )
    }

    @Test
    fun buildHistoryCalendarDayUiState_countsArchivedLinkedRecordWithoutOffPlan() {
        val archivedGroup = MedicationGroup(
            uuid = UUID.fromString("b5dfc5d9-bd18-47c2-bd0f-62f5d36c3723"),
            name = "Archived estradiol",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("9fc1bb97-6f3e-4e91-bf30-c757e43de714"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            updatedAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            archivedAt = testInstant(LocalDateTime.of(2026, 4, 10, 10, 0)),
        )

        val dayStates = buildHistoryCalendarDayUiState(
            groups = listOf(archivedGroup),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("cc49561d-efb0-476e-8499-f6f11c3bc3ee"),
                    medicine = archivedGroup.medications.single().medicine,
                    applicationType = archivedGroup.medications.single().applicationType,
                    doseInstruction = archivedGroup.medications.single().doseInstruction,
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = archivedGroup.uuid,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 10, 8, 3)),
                    scheduledFor = LocalDateTime.of(2026, 4, 10, 8, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 11)
        )

        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.FULFILLED,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 10))
        )
        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.NONE,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 11))
        )
    }

    @Test
    fun buildHistoryCalendarDayUiState_keepsArchiveDayLinkedRecordPlannedAfterArchiveTime() {
        val archivedGroup = MedicationGroup(
            uuid = UUID.fromString("d97acf5c-cc7f-4e29-8124-51f53a9d29f0"),
            name = "Archived evening estradiol",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("d2f9a642-05d7-4935-94e2-92ec5f55fc28"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            updatedAt = testInstant(LocalDateTime.of(2026, 4, 1, 0, 0)),
            archivedAt = testInstant(LocalDateTime.of(2026, 4, 10, 10, 0)),
        )

        val dayStates = buildHistoryCalendarDayUiState(
            groups = listOf(archivedGroup),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("9dfc0850-a576-459c-ace3-e189b71105d8"),
                    medicine = archivedGroup.medications.single().medicine,
                    applicationType = archivedGroup.medications.single().applicationType,
                    doseInstruction = archivedGroup.medications.single().doseInstruction,
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = archivedGroup.uuid,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 10, 20, 5)),
                    scheduledFor = LocalDateTime.of(2026, 4, 10, 20, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 11)
        )

        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.FULFILLED,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 10))
        )
        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.NONE,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 11))
        )
    }

    @Test
    fun buildHistoryCalendarDayUiState_countsLoggedSlotBeforeGroupCreationWithoutOffPlan() {
        val group = MedicationGroup(
            uuid = UUID.fromString("69e8c8cf-8c16-4473-8b08-c87774db79bf"),
            name = "Backfilled estradiol",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("50edcf50-76aa-4ced-aab1-1fa19ff52860"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = testInstant(LocalDateTime.of(2026, 4, 18, 10, 0)),
            updatedAt = testInstant(LocalDateTime.of(2026, 4, 18, 10, 0)),
        )

        val dayStates = buildHistoryCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("eaa0a6ba-577a-4f65-839b-927dbd2d1179"),
                    medicine = group.medications.single().medicine,
                    applicationType = group.medications.single().applicationType,
                    doseInstruction = group.medications.single().doseInstruction,
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 17, 8, 3)),
                    scheduledFor = LocalDateTime.of(2026, 4, 17, 8, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 17),
            endDate = LocalDate.of(2026, 4, 17)
        )

        assertEquals(
            HistoryCalendarDayUiState(
                status = PlanCalendarDayStatus.FULFILLED,
                hasOffPlanRecord = false
            ),
            dayStates.getValue(LocalDate.of(2026, 4, 17))
        )
    }

    @Test
    fun buildHistoryMonthSummary_counts_persisted_counted_rows_as_one_logged_entry() {
        val summary = buildHistoryMonthSummary(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    equivalentE2Mg = 2.0,
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
    fun selectAllHistoryEntries_adds_all_target_entry_ids_without_dropping_existing_selection() {
        val visibleEntryId = UUID.fromString("88fd619f-528b-4510-b41e-6fef01f20d24")
        val alreadySelectedHiddenEntryId = UUID.fromString("7e96ac74-7177-4ef0-b2cc-81230f89f78d")

        val selection = selectAllHistoryEntries(
            currentSelection = setOf(alreadySelectedHiddenEntryId),
            entryIds = setOf(visibleEntryId),
        )

        assertEquals(
            setOf(alreadySelectedHiddenEntryId, visibleEntryId),
            selection,
        )
    }

    @Test
    fun reverseHistoryEntrySelection_inverts_only_target_entries_and_preserves_others() {
        val selectedVisibleEntryId = UUID.fromString("88fd619f-528b-4510-b41e-6fef01f20d24")
        val unselectedVisibleEntryId = UUID.fromString("44cd9d4d-4b8e-4d7c-9218-df44a20a3f36")
        val hiddenSelectedEntryId = UUID.fromString("7e96ac74-7177-4ef0-b2cc-81230f89f78d")

        val selection = reverseHistoryEntrySelection(
            currentSelection = setOf(selectedVisibleEntryId, hiddenSelectedEntryId),
            entryIds = setOf(selectedVisibleEntryId, unselectedVisibleEntryId),
        )

        assertEquals(
            setOf(unselectedVisibleEntryId, hiddenSelectedEntryId),
            selection,
        )
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
    fun historyCalendarDayAlpha_dims_today_when_rendered_as_adjacent_month_day() {
        assertEquals(
            0.56f,
            historyCalendarDayAlpha(
                position = DayPosition.OutDate,
                isFuture = false,
                isSelected = false,
                isToday = true
            ),
            0f
        )
        assertEquals(
            0.56f,
            historyCalendarDayAlpha(
                position = DayPosition.InDate,
                isFuture = false,
                isSelected = false,
                isToday = true
            ),
            0f
        )
        assertEquals(
            1f,
            historyCalendarDayAlpha(
                position = DayPosition.MonthDate,
                isFuture = false,
                isSelected = false,
                isToday = true
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
    fun canSelectHistoryCalendarDate_disallows_adjacent_month_outside_calendar_range() {
        val today = LocalDate.of(2026, 4, 20)

        assertEquals(
            true,
            canSelectHistoryCalendarDate(
                date = LocalDate.of(2026, 3, 31),
                today = today,
                targetMonth = YearMonth.of(2026, 3),
                calendarStartMonth = YearMonth.of(2026, 3),
                calendarEndMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            false,
            canSelectHistoryCalendarDate(
                date = LocalDate.of(2026, 3, 31),
                today = today,
                targetMonth = YearMonth.of(2026, 3),
                calendarStartMonth = YearMonth.of(2026, 4),
                calendarEndMonth = YearMonth.of(2026, 4)
            )
        )
        assertEquals(
            false,
            canSelectHistoryCalendarDate(
                date = LocalDate.of(2026, 5, 1),
                today = today,
                targetMonth = YearMonth.of(2026, 5),
                calendarStartMonth = YearMonth.of(2026, 4),
                calendarEndMonth = YearMonth.of(2026, 5)
            )
        )
        assertEquals(
            true,
            canSelectHistoryCalendarDate(
                date = LocalDate.of(2026, 4, 20),
                today = today,
                targetMonth = null,
                calendarStartMonth = YearMonth.of(2026, 4),
                calendarEndMonth = YearMonth.of(2026, 4)
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

    private fun stateFor(
        status: PlanCalendarDayStatus,
        hasOffPlanRecord: Boolean = status == PlanCalendarDayStatus.OFFPLAN
    ): HistoryCalendarDayUiState {
        return when (status) {
            PlanCalendarDayStatus.NONE -> HistoryCalendarDayUiState(
                status = status,
                hasOffPlanRecord = hasOffPlanRecord
            )
            PlanCalendarDayStatus.OFFPLAN -> HistoryCalendarDayUiState(
                status = status,
                hasOffPlanRecord = hasOffPlanRecord
            )
            PlanCalendarDayStatus.MISSED -> HistoryCalendarDayUiState(
                status = status,
                hasOffPlanRecord = hasOffPlanRecord
            )
            PlanCalendarDayStatus.PARTIAL -> HistoryCalendarDayUiState(
                status = status,
                hasOffPlanRecord = hasOffPlanRecord
            )
            PlanCalendarDayStatus.FULFILLED -> HistoryCalendarDayUiState(
                status = status,
                hasOffPlanRecord = hasOffPlanRecord
            )
        }
    }

    private fun entryAt(dateTime: LocalDateTime): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = testMedicine(
                uuid = estradiolMedicineUuid,
                key = MedicationKey.ESTRADIOL,
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(dateTime)
        )
    }
}
