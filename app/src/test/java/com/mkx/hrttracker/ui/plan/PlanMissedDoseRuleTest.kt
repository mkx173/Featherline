package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.history.HistoryCalendarDayStateMonthRange
import com.mkx.hrttracker.ui.history.HistoryIndicatorColorMode
import com.mkx.hrttracker.ui.history.HistoryMonthSummary
import com.mkx.hrttracker.ui.history.buildHistoryMonthDayStates
import com.mkx.hrttracker.ui.history.buildHistoryCalendarDayUiState
import com.mkx.hrttracker.ui.history.buildHistoryMonthSummary
import com.mkx.hrttracker.ui.history.historyCalendarIndicatorStatus
import com.mkx.hrttracker.ui.history.historyIndicatorColorMode
import com.mkx.hrttracker.ui.history.withHistoryLiveDayStates
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

/** Decision 6: a dose isn't missed before it's past due, on any date, today included. */
class PlanMissedDoseRuleTest {
    private val oct28 = LocalDate.of(2026, 10, 28)
    private val oct29 = LocalDate.of(2026, 10, 29)
    private val medicine = testMedicine()

    @Test
    fun nightOwlPlansNightDoseIsPlannedThenDueThenMissed() {
        val plan = group(oct28, LocalTime.of(22, 0), LocalTime.of(2, 0), dayBoundary = MedicationDayBoundary.FourAm)
        val entries = listOf(taken(plan, oct28.atTime(22, 0)))

        // 00:30: Oct 28's 02:00 dose is still ahead.
        val atHalfPastMidnight = oct29.atTime(0, 30)
        assertEquals(SelectedDayRowState.PLANNED, nightRowState(plan, entries, atHalfPastMidnight))
        val inProgress = planDay(plan, entries, oct28, atHalfPastMidnight)
        assertEquals(PlanCalendarDayStatus.PARTIAL, inProgress.status)
        assertEquals(1, inProgress.pendingOccurrenceCount)
        // Week strip: partial, neutral (Emphasized only with a missed dose).
        assertFalse(inProgress.hasMissedDose)
        val historyInProgress = historyDay(plan, entries, oct28, atHalfPastMidnight)
        assertFalse(historyInProgress.hasMissedDose)
        assertEquals(
            PlanCalendarDayStatus.PARTIAL,
            historyCalendarIndicatorStatus(historyInProgress.status, historyInProgress.hasMissedDose, hasRecord = true),
        )
        assertEquals(HistoryMonthSummary(logged = 1), monthSummary(plan, entries, atHalfPastMidnight))

        assertEquals(SelectedDayRowState.DUE, nightRowState(plan, entries, oct29.atTime(1, 30)))

        val atHalfPastThree = oct29.atTime(3, 30)
        assertEquals(SelectedDayRowState.MISSED, nightRowState(plan, entries, atHalfPastThree))
        assertTrue(planDay(plan, entries, oct28, atHalfPastThree).hasMissedDose)
        assertTrue(historyDay(plan, entries, oct28, atHalfPastThree).hasMissedDose)
        assertEquals(HistoryMonthSummary(logged = 1, partial = 1), monthSummary(plan, entries, atHalfPastThree))
    }

    @Test
    fun midnightPlansTodayIsNotMissedBeforeItsFirstDoseIsPastDue() {
        val plan = group(oct29, LocalTime.of(9, 0), LocalTime.of(21, 0))

        val atSeven = oct29.atTime(7, 0)
        val upcoming = planDay(plan, emptyList(), oct29, atSeven)
        assertEquals(PlanCalendarDayStatus.MISSED, upcoming.status)
        // Week strip: the planned glyph (showFutureMissedIcon), neutral.
        assertFalse(upcoming.hasMissedDose)
        val historyUpcoming = historyDay(plan, emptyList(), oct29, atSeven)
        assertEquals(
            PlanCalendarDayStatus.NONE,
            historyCalendarIndicatorStatus(historyUpcoming.status, historyUpcoming.hasMissedDose, hasRecord = false),
        )
        assertEquals(HistoryMonthSummary(), monthSummary(plan, emptyList(), atSeven))

        val atHalfPastTen = oct29.atTime(10, 30)
        assertTrue(planDay(plan, emptyList(), oct29, atHalfPastTen).hasMissedDose)
        val historyMissed = historyDay(plan, emptyList(), oct29, atHalfPastTen)
        assertTrue(historyMissed.hasMissedDose)
        assertEquals(
            PlanCalendarDayStatus.MISSED,
            historyCalendarIndicatorStatus(historyMissed.status, historyMissed.hasMissedDose, hasRecord = false),
        )
        assertEquals(HistoryMonthSummary(missed = 1), monthSummary(plan, emptyList(), atHalfPastTen))
        // Today's own rows keep past due.
        assertEquals(SelectedDayRowState.PAST_DUE, rowState(plan, emptyList(), oct29, LocalTime.of(9, 0), atHalfPastTen))
    }

    @Test
    fun aDoseTakenBeforeItsTimeCountsOnceAsMatchedNeverAsPending() {
        val plan = group(oct29, LocalTime.of(9, 0), LocalTime.of(21, 0))
        val entries = listOf(taken(plan, oct29.atTime(9, 0), appliedAt = oct29.atTime(8, 30)))

        val state = planDay(plan, entries, oct29, oct29.atTime(8, 40))

        assertEquals(2, state.expectedOccurrenceCount)
        assertEquals(1, state.matchedOccurrenceCount)
        assertEquals(1, state.pendingOccurrenceCount)
        assertEquals(PlanCalendarDayStatus.PARTIAL, state.status)
        assertFalse(state.hasMissedDose)
    }

    @Test
    fun midnightPlansLastDoseBeforeMidnightIsMissedOnlyOncePastDue() {
        val plan = group(oct28, LocalTime.of(23, 30))

        val atTenPast = oct29.atTime(0, 10)
        assertEquals(SelectedDayRowState.DUE, rowState(plan, emptyList(), oct28, LocalTime.of(23, 30), atTenPast))
        assertFalse(planDay(plan, emptyList(), oct28, atTenPast).hasMissedDose)

        val atThirtyOnePast = oct29.atTime(0, 31)
        assertEquals(SelectedDayRowState.MISSED, rowState(plan, emptyList(), oct28, LocalTime.of(23, 30), atThirtyOnePast))
        assertTrue(planDay(plan, emptyList(), oct28, atThirtyOnePast).hasMissedDose)
    }

    @Test
    fun historyHeaderAndCalendarColoursAreEmphasisedOnlyWithAMissedDose() {
        assertEquals(HistoryIndicatorColorMode.Emphasized, historyIndicatorColorMode(hasMissedDose = true))
        assertEquals(HistoryIndicatorColorMode.Neutral, historyIndicatorColorMode(hasMissedDose = false))
    }

    @Test
    fun historyLiveDayOverlayMatchesAFullRebuildThroughTheDay() {
        val nightOwl = group(
            LocalDate.of(2026, 10, 1), LocalTime.of(22, 0), LocalTime.of(2, 0),
            dayBoundary = MedicationDayBoundary.FourAm,
        )
        val midnight = group(LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), LocalTime.of(21, 0), LocalTime.of(23, 30))
        val groups = listOf(nightOwl, midnight)
        val groupsByUuid = groups.associateBy(MedicationGroup::uuid)
        val entries = listOf(
            taken(nightOwl, oct28.atTime(22, 0)),
            taken(midnight, oct28.atTime(9, 0)),
            taken(midnight, oct29.atTime(9, 0), appliedAt = oct29.atTime(8, 30)),
        )
        val ranges = listOf(HistoryCalendarDayStateMonthRange(YearMonth.of(2026, 8), YearMonth.of(2026, 12)))
        // The settled map is built once per day, at its first minute.
        val settled = buildHistoryMonthDayStates(ranges, groups, entries, oct29.atStartOfDay(), groupsByUuid)

        for (time in listOf(LocalTime.of(0, 30), LocalTime.of(3, 30), LocalTime.of(4, 59), LocalTime.of(10, 0))) {
            val now = oct29.atTime(time)
            assertEquals(
                "at $now",
                buildHistoryMonthDayStates(ranges, groups, entries, now, groupsByUuid),
                withHistoryLiveDayStates(settled, groups, entries, now, groupsByUuid),
            )
        }
    }

    private fun nightRowState(plan: MedicationGroup, entries: List<MedicationLogEntry>, now: LocalDateTime) =
        rowState(plan, entries, oct28, LocalTime.of(2, 0), now)

    private fun rowState(
        plan: MedicationGroup,
        entries: List<MedicationLogEntry>,
        date: LocalDate,
        time: LocalTime,
        now: LocalDateTime,
    ): SelectedDayRowState {
        val entry = buildPlanDaySchedule(
            date = date,
            groups = listOf(plan),
            entries = entries,
            now = now,
            groupsByUuid = mapOf(plan.uuid to plan),
        ).scheduledEntries.single { it.scheduledTime == time }
        return selectedDayScheduledRowState(date = date, today = now.toLocalDate(), entry = entry)
    }

    private fun planDay(
        plan: MedicationGroup,
        entries: List<MedicationLogEntry>,
        date: LocalDate,
        now: LocalDateTime,
    ) = buildPlanCalendarDayUiState(
        groups = listOf(plan),
        entries = entries,
        startDate = date,
        endDate = date,
        now = now,
        groupsByUuid = mapOf(plan.uuid to plan),
    ).getValue(date)

    private fun historyDay(
        plan: MedicationGroup,
        entries: List<MedicationLogEntry>,
        date: LocalDate,
        now: LocalDateTime,
    ) = buildHistoryCalendarDayUiState(
        groups = listOf(plan),
        entries = entries,
        startDate = date,
        endDate = date,
        now = now,
        groupsByUuid = mapOf(plan.uuid to plan),
    ).getValue(date)

    private fun monthSummary(
        plan: MedicationGroup,
        entries: List<MedicationLogEntry>,
        now: LocalDateTime,
    ): HistoryMonthSummary {
        val month = YearMonth.of(2026, 10)
        val groupsByUuid = mapOf(plan.uuid to plan)
        return buildHistoryMonthSummary(
            entries = entries,
            displayedMonth = month,
            dayStates = buildHistoryCalendarDayUiState(
                groups = listOf(plan),
                entries = entries,
                startDate = month.atDay(1),
                endDate = month.atEndOfMonth(),
                now = now,
                groupsByUuid = groupsByUuid,
            ),
            today = now.toLocalDate(),
            groupsByUuid = groupsByUuid,
        )
    }

    private fun taken(
        plan: MedicationGroup,
        scheduledFor: LocalDateTime,
        appliedAt: LocalDateTime = scheduledFor,
    ) = testMedicationLogEntry(
        medicine = medicine,
        sourceGroupUuid = plan.uuid,
        scheduleTimeUuid = plan.schedule.timeSlots.single { it.time == scheduledFor.toLocalTime() }.uuid,
        appliedAt = testInstant(appliedAt),
        scheduledFor = scheduledFor,
    )

    private fun group(
        since: LocalDate,
        vararg times: LocalTime,
        dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
    ) = MedicationGroup(
        uuid = UUID.randomUUID(),
        name = "Plan",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = since,
            weeklyDaysOfWeek = emptySet(),
            times = times.toList(),
            dayBoundary = dayBoundary,
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )
}
