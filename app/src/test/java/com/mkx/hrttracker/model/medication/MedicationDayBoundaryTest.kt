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
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
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
    private val midnight = MedicationDayBoundary.Midnight
    private val fourAm = MedicationDayBoundary.FourAm

    @Test
    fun boundaryUsesHalfOpenLocalDayAndRetainsMidnightDefault() {
        val nextDay = day.plusDays(1)
        assertEquals(nextDay, medicationDay(nextDay.atTime(1, 0), midnight))
        assertEquals(day, medicationDay(nextDay.atTime(3, 59, 59), fourAm))
        assertEquals(nextDay, medicationDay(nextDay.atTime(4, 0), fourAm))
        assertEquals(day.atTime(4, 0), medicationDayStart(day, fourAm))
        assertEquals(LocalDate.of(2026, 12, 31), medicationDay(LocalDateTime.of(2027, 1, 1, 0, 1), fourAm))
        assertEquals(nextDay, medicationDay(nextDay.atTime(23, 58), fourAm))
        assertEquals(nextDay, medicationDay(nextDay.atTime(23, 59), fourAm))
    }

    @Test
    fun todayOrdersDawnAfterEveningForTheSameMedicationDay() {
        val evening = group(listOf(LocalTime.of(20, 0))).copy(name = "Evening")
        val dawn = group(listOf(LocalTime.of(2, 0))).copy(name = "Dawn")
        val section = buildMainTodaySection(
            groups = listOf(dawn, evening),
            entries = emptyList(),
            now = day.plusDays(1).atTime(12, 0),
            zoneId = zone,
        )
        assertEquals(listOf("Evening", "Dawn"), section.rows.map { it.groupName })
    }

    @Test
    fun entryOwnershipKeepsPlanAssociationAndStoredWallTime() {
        val linked = log(day.plusDays(2).atTime(1, 0)).copy(scheduledFor = day.atTime(23, 0))
        assertEquals(day, linked.planCalendarDate(fourAm, zone))
        val manual = log(day.plusDays(1).atTime(0, 30)).copy(appliedAtTimeZoneId = "Asia/Tokyo")
        assertEquals(day.plusDays(1), manual.planCalendarDate(fourAm, zone))
        assertEquals(day, manual.copy(appliedAtTimeZoneId = "invalid").planCalendarDate(fourAm, zone))
    }

    @Test
    fun dailyScheduleIncludesNextDawnWithOriginalTimesAndFulfillment() {
        val group = group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)))
        val scheduledFor = day.plusDays(1).atTime(1, 0)
        val entry = log(scheduledFor).copy(sourceGroupUuid = group.uuid, scheduledFor = scheduledFor)
        val schedule = buildPlanDaySchedule(
            date = day,
            groups = listOf(group),
            entries = listOf(entry),
            now = day.atTime(22, 0),
            zoneId = zone,
            groupsByUuid = listOf(group).associateBy(MedicationGroup::uuid),
        )
        assertEquals(listOf(day.atTime(20, 0), scheduledFor), schedule.scheduledEntries.map { it.scheduledFor })
        assertFalse(schedule.scheduledEntries.first().isFulfilled)
        assertTrue(schedule.scheduledEntries.last().isFulfilled)
        assertTrue(schedule.unplannedEntries.isEmpty())
        assertEquals(scheduledFor, entry.scheduledFor)
        assertEquals(scheduledFor.toInstant(java.time.ZoneOffset.UTC), entry.appliedAt)
    }

    @Test
    fun weeklyDawnFiresInTheSelectedMedicationDaysEarlyHours() {
        val monday = LocalDate.of(2026, 11, 2)
        val group = group(listOf(LocalTime.of(1, 0))).copy(schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = monday,
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
            times = listOf(LocalTime.of(1, 0)),
            dayBoundary = fourAm,
        ))
        val schedule = buildPlanDaySchedule(
            date = monday,
            groups = listOf(group),
            entries = emptyList(),
            now = monday.atTime(20, 0),
            zoneId = zone,
            groupsByUuid = listOf(group).associateBy(MedicationGroup::uuid),
        )
        assertEquals(listOf(monday.plusDays(1).atTime(1, 0)), schedule.scheduledEntries.map { it.scheduledFor })
        assertTrue(
            buildPlanDaySchedule(
                monday.plusDays(1), listOf(group), emptyList(), zoneId = zone, groupsByUuid = mapOf(group.uuid to group),
            ).scheduledEntries.isEmpty()
        )
    }

    @Test
    fun historyRegroupsANightOwlDawnAcrossMonthButKeepsManualRecordsOnTheirCalendarDay() {
        // The plan is hidden from the calendar, but its records still follow its ending.
        val group = group(listOf(LocalTime.of(3, 0)))
        val groupsByUuid = mapOf(group.uuid to group)
        val dawn = log(day.plusDays(1).atTime(3, 59)).copy(sourceGroupUuid = group.uuid)
        val manualDawn = log(day.plusDays(1).atTime(3, 59))
        val boundary = log(day.plusDays(1).atTime(4, 0)).copy(sourceGroupUuid = group.uuid)
        val entries = listOf(dawn, manualDawn, boundary)
        assertEquals(
            listOf(dawn),
            buildHistoryVisibleEntries(entries, YearMonth.from(day), null, zone, groupsByUuid),
        )
        assertEquals(
            mapOf(day to listOf(dawn), day.plusDays(1) to listOf(manualDawn, boundary)),
            groupHistoryEntriesByDate(entries, zone, groupsByUuid),
        )
        val states = buildHistoryCalendarDayUiState(emptyList(), entries, day, day.plusDays(1), zone, groupsByUuid)
        assertEquals(PlanCalendarDayStatus.OFFPLAN, states.getValue(day).status)
        assertEquals(1, buildHistoryMonthSummary(entries, YearMonth.from(day), states, day, zone, groupsByUuid).logged)
        // A record not from a plan sits on its calendar day.
        val manualEntries = listOf(manualDawn)
        assertTrue(
            buildPlanDaySchedule(day, emptyList(), manualEntries, zoneId = zone, groupsByUuid = emptyMap())
                .unplannedEntries.isEmpty()
        )
        assertEquals(
            manualEntries,
            buildPlanDaySchedule(day.plusDays(1), emptyList(), manualEntries, zoneId = zone, groupsByUuid = emptyMap())
                .unplannedEntries,
        )
    }

    @Test
    fun midnightSupplementStaysWithItsEveningPlanInCalendar() {
        val group = group(listOf(LocalTime.of(23, 0)))
        val entry = log(day.plusDays(1).atTime(1, 0)).copy(sourceGroupUuid = group.uuid, scheduledFor = day.atTime(23, 0))
        val states = buildHistoryCalendarDayUiState(listOf(group), listOf(entry), day, day.plusDays(1), zone, mapOf(group.uuid to group))
        assertEquals(PlanCalendarDayStatus.FULFILLED, states.getValue(day).status)
        assertEquals(PlanCalendarDayStatus.MISSED, states.getValue(day.plusDays(1)).status)
    }

    @Test
    fun archivedDawnSlotRetainsLoggedRecordAndHidesFutureUnloggedSlots() {
        val archivedAt = day.plusDays(1).atTime(2, 0)
        val group = group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0))).copy(
            archivedAt = archivedAt.atZone(zone).toInstant(), archivedAtLocal = archivedAt,
        )
        val schedule = buildPlanDaySchedule(
            date = day,
            groups = listOf(group),
            entries = emptyList(),
            now = day.atTime(22, 0),
            zoneId = zone,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = day.atTime(22, 0),
            groupsByUuid = listOf(group).associateBy(MedicationGroup::uuid),
        )
        assertEquals(listOf(day.atTime(20, 0)), schedule.scheduledEntries.map { it.scheduledFor })
        val scheduledFor = day.plusDays(1).atTime(1, 0)
        val entry = log(scheduledFor).copy(sourceGroupUuid = group.uuid, scheduledFor = scheduledFor)
        val withLog = buildPlanDaySchedule(
            date = day,
            groups = listOf(group),
            entries = listOf(entry),
            now = day.atTime(22, 0),
            zoneId = zone,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = day.atTime(22, 0),
            groupsByUuid = listOf(group).associateBy(MedicationGroup::uuid),
        )
        assertTrue(withLog.scheduledEntries.last().isFulfilled)
    }

    @Test
    fun homeKeepsDawnInTodayAndKeepsPreviousMidnightInLastNight() {
        val groups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0))))
        val now = day.plusDays(1).atTime(1, 0)
        val today = buildMainTodaySection(groups, listOf(log(now)), now, zone)
        // Today's header is the calendar date; the plan's evening and dawn rows stay in it.
        assertEquals(day.plusDays(1), today.date)
        assertEquals(2, today.totalCount)
        assertEquals(1, today.manualCount)

        // Before 04:00 Today already holds this evening, so the night before last is not shown.
        assertTrue(buildMainLastNightSection(groups, emptyList(), now, zone).rows.isEmpty())
        assertTrue(buildMainComingUpSection(groups, emptyList(), day.atTime(22, 0), zone).rows.isEmpty())

        // The plan's tomorrow is not the calendar's, so Tomorrow shows no date.
        val upcoming = buildMainUpcomingSection(groups, emptyList(), now, zoneId = zone)
        assertEquals(null, upcoming.anchorDate)
        assertEquals(
            listOf(day.plusDays(1).atTime(20, 0), day.plusDays(2).atTime(1, 0)),
            upcoming.rows.map { row -> row.scheduledAt },
        )
        assertEquals(
            day.plusDays(1),
            buildMainTodaySection(groups, emptyList(), now.withHour(5), zone).date,
        )
    }

    @Test
    fun lastNightKeepsMissedDawnAfterTheBoundaryPasses() {
        val groups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0))))
        val afterBoundary = day.plusDays(1).atTime(4, 30)
        val lastNight = buildMainLastNightSection(groups, emptyList(), afterBoundary, zone)
        assertEquals(day, lastNight.date)
        assertEquals(
            listOf(day.atTime(20, 0), day.plusDays(1).atTime(1, 0)),
            lastNight.rows.map { row -> row.scheduledAt },
        )
        assertTrue(
            buildMainLastNightSection(groups, emptyList(), day.plusDays(1).atTime(12, 0), zone)
                .rows.isEmpty()
        )
    }

    @Test
    fun midnightBoundaryLastNightIsEveningOnlyUntilNoon() {
        val groups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)), midnight))
        val morning = day.plusDays(1).atTime(4, 30)
        val lastNight = buildMainLastNightSection(groups, emptyList(), morning, zone)
        assertEquals(day, lastNight.date)
        assertEquals(listOf(day.atTime(20, 0)), lastNight.rows.map { row -> row.scheduledAt })
        assertTrue(
            buildMainLastNightSection(groups, emptyList(), day.plusDays(1).atTime(12, 0), zone)
                .rows.isEmpty()
        )
    }

    @Test
    fun tonightOnlyExistsAtMidnightAndUpcomingExcludesItsRows() {
        val groups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)), midnight))
        val evening = day.atTime(20, 0)
        val tonight = buildMainComingUpSection(groups, emptyList(), evening, zone)
        assertEquals(day.plusDays(1), tonight.date)
        assertEquals(listOf(day.plusDays(1).atTime(1, 0)), tonight.rows.map { row -> row.scheduledAt })

        val upcoming = buildMainUpcomingSection(groups, emptyList(), evening, zoneId = zone)
        assertEquals(day.plusDays(1), upcoming.anchorDate)
        assertEquals(listOf(day.plusDays(1).atTime(20, 0)), upcoming.rows.map { row -> row.scheduledAt })

        val nightOwlGroups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0))))
        assertTrue(buildMainComingUpSection(nightOwlGroups, emptyList(), evening, zone).rows.isEmpty())
    }

    @Test
    fun fourAmUpcomingIncludesNextDawnInTimeOrder() {
        val fourAmUpcoming = buildMainUpcomingSection(
            groups = listOf(group(listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)))),
            entries = emptyList(),
            now = day.atTime(20, 0),
            zoneId = zone,
        )
        assertEquals(
            listOf(day.plusDays(1).atTime(20, 0), day.plusDays(2).atTime(1, 0)),
            fourAmUpcoming.rows.map { row -> row.scheduledAt },
        )
    }

    private fun log(at: LocalDateTime) = testMedicationLogEntry(
        medicine = medicine, sourceGroupUuid = null, appliedAt = at.atZone(zone).toInstant(), appliedAtTimeZoneId = zone.id,
    )

    private fun group(times: List<LocalTime>, dayBoundary: MedicationDayBoundary = fourAm) = MedicationGroup(
        uuid = UUID.randomUUID(), name = "Night doses", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            MedicationGroupScheduleType.DAILY, 1, day.minusDays(10), emptySet(), times, dayBoundary = dayBoundary,
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )
}
