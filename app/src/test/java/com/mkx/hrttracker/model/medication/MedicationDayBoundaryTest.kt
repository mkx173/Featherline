package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.ui.history.buildHistoryCalendarDayUiState
import com.mkx.hrttracker.ui.history.buildHistoryMonthSummary
import com.mkx.hrttracker.ui.history.buildHistoryVisibleEntries
import com.mkx.hrttracker.ui.history.groupHistoryEntriesByDate
import com.mkx.hrttracker.ui.main.buildMainComingUpSection
import com.mkx.hrttracker.ui.main.buildMainLastNightSection
import com.mkx.hrttracker.ui.main.buildMainTodaySection
import com.mkx.hrttracker.ui.main.buildMainUpcomingSection
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationDayEnd
import com.mkx.hrttracker.util.medicationDayStart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDayBoundaryTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 31)
    private val medicine = testMedicine()

    @Test
    fun boundaryUsesHalfOpenLocalDayAndRetainsMidnightDefault() {
        val nextDay = day.plusDays(1)
        assertEquals(nextDay, medicationDay(nextDay.atTime(1, 0)))
        assertEquals(day, medicationDay(nextDay.atTime(4, 29, 59), 270))
        assertEquals(nextDay, medicationDay(nextDay.atTime(4, 30), 270))
        assertEquals(day.atTime(4, 30), medicationDayStart(day, 270))
        assertEquals(nextDay.atTime(4, 30), medicationDayEnd(day, 270))
        assertEquals(LocalDate.of(2026, 12, 31), medicationDay(LocalDateTime.of(2027, 1, 1, 0, 1), 300))
        assertEquals(day, medicationDay(nextDay.atTime(23, 58), 1439))
        assertEquals(nextDay, medicationDay(nextDay.atTime(23, 59), 1439))
    }

    @Test
    fun entryOwnershipKeepsPlanAssociationAndStoredWallTime() {
        val linked = log(day.plusDays(2).atTime(1, 0)).copy(scheduledFor = day.atTime(23, 0))
        assertEquals(day, linked.planCalendarDate(zone, 300))
        val manual = log(day.plusDays(1).atTime(0, 30)).copy(appliedAtTimeZoneId = "Asia/Tokyo")
        assertEquals(day.plusDays(1), manual.planCalendarDate(zone, 300))
        assertEquals(day, manual.copy(appliedAtTimeZoneId = "invalid").planCalendarDate(zone, 300))
    }

    @Test
    fun dailyScheduleIncludesNextDawnWithOriginalTimesAndFulfillment() {
        val group = group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)))
        val scheduledFor = day.plusDays(1).atTime(1, 0)
        val entry = log(scheduledFor).copy(sourceGroupUuid = group.uuid, scheduledFor = scheduledFor)
        val schedule = buildPlanDaySchedule(day, listOf(group), listOf(entry), day.atTime(22, 0), zone, dayStartMinutes = 300)
        assertEquals(listOf(day.atTime(20, 0), scheduledFor), schedule.scheduledEntries.map { it.scheduledFor })
        assertFalse(schedule.scheduledEntries.first().isFulfilled)
        assertTrue(schedule.scheduledEntries.last().isFulfilled)
        assertTrue(schedule.unplannedEntries.isEmpty())
        assertEquals(scheduledFor, entry.scheduledFor)
        assertEquals(scheduledFor.toInstant(java.time.ZoneOffset.UTC), entry.appliedAt)
    }

    @Test
    fun weeklyDawnBelongsToPreviousDayWithoutMovingItsOccurrence() {
        val monday = LocalDate.of(2026, 11, 2)
        val group = group(listOf(LocalTime.of(1, 0))).copy(schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = monday,
            weeklyDaysOfWeek = setOf(DayOfWeek.TUESDAY),
            times = listOf(LocalTime.of(1, 0)),
        ))
        val schedule = buildPlanDaySchedule(monday, listOf(group), emptyList(), monday.atTime(20, 0), zone, dayStartMinutes = 300)
        assertEquals(listOf(monday.plusDays(1).atTime(1, 0)), schedule.scheduledEntries.map { it.scheduledFor })
        assertTrue(buildPlanDaySchedule(monday.plusDays(1), listOf(group), emptyList(), zoneId = zone, dayStartMinutes = 300).scheduledEntries.isEmpty())
    }

    @Test
    fun manualHistoryRegroupsAcrossMonthWithMatchingSummaryAndExactBoundary() {
        val dawn = log(day.plusDays(1).atTime(4, 29))
        val boundary = log(day.plusDays(1).atTime(4, 30))
        val entries = listOf(dawn, boundary)
        assertEquals(listOf(dawn), buildHistoryVisibleEntries(entries, YearMonth.from(day), null, zone, 270))
        assertEquals(setOf(day, day.plusDays(1)), groupHistoryEntriesByDate(entries, zone, 270).keys)
        val states = buildHistoryCalendarDayUiState(emptyList(), entries, day, day.plusDays(1), zone, 270)
        assertEquals(PlanCalendarDayStatus.OFFPLAN, states.getValue(day).status)
        assertEquals(1, buildHistoryMonthSummary(entries, YearMonth.from(day), states, day, zone, 270).logged)
        assertEquals(listOf(dawn), buildPlanDaySchedule(day, emptyList(), entries, zoneId = zone, dayStartMinutes = 270).unplannedEntries)
    }

    @Test
    fun midnightSupplementStaysWithItsEveningPlanInCalendar() {
        val group = group(listOf(LocalTime.of(23, 0)))
        val entry = log(day.plusDays(1).atTime(1, 0)).copy(sourceGroupUuid = group.uuid, scheduledFor = day.atTime(23, 0))
        val states = buildHistoryCalendarDayUiState(listOf(group), listOf(entry), day, day.plusDays(1), zone, 300)
        assertEquals(PlanCalendarDayStatus.FULFILLED, states.getValue(day).status)
        assertEquals(PlanCalendarDayStatus.MISSED, states.getValue(day.plusDays(1)).status)
    }

    @Test
    fun archivedDawnSlotRetainsLoggedRecordAndHidesFutureUnloggedSlots() {
        val archivedAt = day.plusDays(1).atTime(2, 0)
        val group = group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0))).copy(
            archivedAt = archivedAt.atZone(zone).toInstant(), archivedAtLocal = archivedAt,
        )
        val schedule = buildPlanDaySchedule(day, listOf(group), emptyList(), day.atTime(22, 0), zone,
            includeUnloggedArchivedSlots = false, unloggedArchivedSlotCutoff = day.atTime(22, 0), dayStartMinutes = 300)
        assertEquals(listOf(day.atTime(20, 0)), schedule.scheduledEntries.map { it.scheduledFor })
        val scheduledFor = day.plusDays(1).atTime(1, 0)
        val entry = log(scheduledFor).copy(sourceGroupUuid = group.uuid, scheduledFor = scheduledFor)
        val withLog = buildPlanDaySchedule(day, listOf(group), listOf(entry), day.atTime(22, 0), zone,
            includeUnloggedArchivedSlots = false, unloggedArchivedSlotCutoff = day.atTime(22, 0), dayStartMinutes = 300)
        assertTrue(withLog.scheduledEntries.last().isFulfilled)
    }

    @Test
    fun homeKeepsDawnInTodayAndRemovesContextDuplicates() {
        val groups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0))))
        val now = day.plusDays(1).atTime(1, 0)
        val today = buildMainTodaySection(groups, listOf(log(now)), now, zone, dayStartMinutes = 300)
        assertEquals(day, today.date)
        assertEquals(2, today.totalCount)
        assertEquals(1, today.manualCount)
        assertTrue(buildMainLastNightSection(groups, emptyList(), now, zone, dayStartMinutes = 300).rows.isEmpty())
        assertTrue(buildMainComingUpSection(groups, emptyList(), day.atTime(22, 0), zone, dayStartMinutes = 300).rows.isEmpty())
        val upcoming = buildMainUpcomingSection(groups, emptyList(), now, zoneId = zone, dayStartMinutes = 300)
        assertEquals(day.plusDays(1), upcoming.anchorDate)
        assertEquals(listOf(day.plusDays(1).atTime(20, 0), day.plusDays(2).atTime(1, 0)), upcoming.rows.map { it.scheduledAt })
        assertEquals(day.plusDays(1), buildMainTodaySection(groups, emptyList(), now.withHour(5), zone, dayStartMinutes = 300).date)
    }

    private fun log(at: LocalDateTime) = testMedicationLogEntry(
        medicine = medicine, sourceGroupUuid = null, appliedAt = at.atZone(zone).toInstant(), appliedAtTimeZoneId = zone.id,
    )

    private fun group(times: List<LocalTime>) = MedicationGroup(
        uuid = UUID.randomUUID(), name = "Night doses", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(MedicationGroupScheduleType.DAILY, 1, day.minusDays(10), emptySet(), times),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )
}
