package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.ui.log.buildEditingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID
import kotlin.random.Random

/**
 * New York 2026-03-08: 02:00 EST becomes 03:00 EDT, so a 02:10 dose lands at 03:10 EDT, after
 * the plan's 03:05 dose. A gap dose whose moment comes at or after a later dose's never rings:
 * that dose stands for it (docs/reminders.md), and Home shows it as missed. So that dose's window
 * ignores it: its previous dose skips a gap dose it silences. A gap dose that does ring still
 * bounds its neighbours. Neighbours stay on the wall clock (by instant, Log now three minutes
 * after the 03:05 reminder would count as outside). A gap dose's own window is as before.
 */
class SpringForwardWindowNeighborTest {
    private val newYork = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 3, 8)
    private val medicine = testMedicine()

    private fun plan(
        vararg times: LocalTime,
        type: MedicationGroupScheduleType = MedicationGroupScheduleType.DAILY,
        interval: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
    ) = MedicationGroup(
        uuid = UUID.randomUUID(),
        name = "Night",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = type,
            interval = interval,
            since = LocalDate.of(2010, 1, 1),
            weeklyDaysOfWeek = weekdays,
            times = times.toList(),
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )

    /** The predicate every status reader uses (Home rows, Plan, History). */
    private fun inWindow(plan: MedicationGroup, slot: LocalTime, loggedAt: LocalTime): Boolean =
        isEntryWithinScheduleFulfillmentWindow(
            group = plan,
            entry = testMedicationLogEntry(
                medicine = medicine,
                sourceGroupUuid = plan.uuid,
                appliedAt = day.atTime(loggedAt).atZone(newYork).toInstant(),
                appliedAtTimeZoneId = newYork.id,
                scheduledFor = day.atTime(slot),
            ),
            zoneId = newYork,
        )

    @Test
    fun theReviewsPlan_0210And0305() {
        val plan = plan(LocalTime.of(2, 10), LocalTime.of(3, 5))
        val slot0305 = day.atTime(3, 5)

        // 03:05's neighbours skip the 02:10 gap dose: the 7th's 03:05 before, the 9th's 02:10 after.
        assertEquals(day.minusDays(1).atTime(3, 5), plan.previousScheduledForBefore(slot0305, zoneId = newYork))
        assertEquals(day.plusDays(1).atTime(2, 10), plan.nextScheduledForAfter(slot0305, zoneId = newYork))
        // The gap dose keeps its neighbours.
        assertEquals(day.minusDays(1).atTime(3, 5), plan.previousScheduledForBefore(day.atTime(2, 10), zoneId = newYork))
        assertEquals(slot0305, plan.nextScheduledForAfter(day.atTime(2, 10), zoneId = newYork))

        // The 03:05 dose logged three minutes early, and three minutes after its reminder: inside.
        assertEquals(true, inWindow(plan, LocalTime.of(3, 5), LocalTime.of(3, 2)))
        assertEquals(true, inWindow(plan, LocalTime.of(3, 5), LocalTime.of(3, 8)))

        // The gap dose's own window, as before.
        assertEquals(true, inWindow(plan, LocalTime.of(2, 10), LocalTime.of(3, 6)))
        assertEquals(false, inWindow(plan, LocalTime.of(2, 10), LocalTime.of(3, 13)))
    }

    // A record on the 03:05 slot logged at 03:02 in New York, opened to edit on a phone now on
    // Tokyo time: the sheet picks the neighbours in the record's zone, as Home and History do.
    @Test
    fun theEditSheet_picksNeighboursInTheRecordsZone() {
        val plan = plan(LocalTime.of(2, 10), LocalTime.of(3, 5))
        val record = testMedicationLogEntry(
            medicine = medicine,
            sourceGroupUuid = plan.uuid,
            scheduleTimeUuid = plan.schedule.timeSlots.single { it.time == LocalTime.of(3, 5) }.uuid,
            appliedAt = day.atTime(3, 2).atZone(newYork).toInstant(),
            appliedAtTimeZoneId = newYork.id,
            scheduledFor = day.atTime(3, 5),
        )
        assertEquals(true, isEntryWithinScheduleFulfillmentWindow(plan, record, ZoneId.of("Asia/Tokyo")))
        val deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            val sheet = requireNotNull(buildEditingUiState(listOf(record), sourceGroup = plan))
            assertEquals(false, sheet.shouldWarnScheduleWillNotBeFulfilled())
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }

    // The user's own example for the reminder decision: 02:10, 02:50, 03:05; only 03:05 rings.
    @Test
    fun theUsersPlan_0210_0250_0305() {
        val plan = plan(LocalTime.of(2, 10), LocalTime.of(2, 50), LocalTime.of(3, 5))

        // 03:05 ten minutes after its reminder, and 35 real minutes early (01:30 EST): inside.
        assertEquals(true, inWindow(plan, LocalTime.of(3, 5), LocalTime.of(3, 15)))
        assertEquals(true, inWindow(plan, LocalTime.of(3, 5), LocalTime.of(1, 30)))

        // 02:50 (03:50 EDT) logged at 04:20: its window is as before, bounded by 03:05.
        assertEquals(false, inWindow(plan, LocalTime.of(2, 50), LocalTime.of(4, 20)))
    }

    // A gap dose that rings (02:10 at 03:10 EDT, before 03:30) bounds 03:30 as before.
    @Test
    fun aGapDoseThatRings_stillBoundsItsNeighbours() {
        val plan = plan(LocalTime.of(2, 10), LocalTime.of(3, 30))

        assertEquals(day.atTime(2, 10), plan.previousScheduledForBefore(day.atTime(3, 30), zoneId = newYork))
        // 03:30 logged at 03:15: ten real minutes are allowed (half of 03:10..03:30), so outside.
        assertEquals(false, inWindow(plan, LocalTime.of(3, 30), LocalTime.of(3, 15)))
        assertEquals(day.atTime(2, 10), plan.nextScheduledForAfter(day.minusDays(1).atTime(3, 30), zoneId = newYork))
    }

    // Two gap doses: the later one's previous is the earlier one (neither silences the other).
    @Test
    fun twoGapDoses_theLatersPreviousIsTheEarlier() {
        val plan = plan(LocalTime.of(2, 10), LocalTime.of(2, 50))

        assertEquals(day.atTime(2, 10), plan.previousScheduledForBefore(day.atTime(2, 50), zoneId = newYork))
    }

    // A once-daily dose in the skipped hour rings at 03:30 EDT: the days either side are as before.
    @Test
    fun aSingleDailyDoseInTheGap_leavesTheDaysEitherSideAsBefore() {
        val plan = plan(LocalTime.of(2, 30))

        assertEquals(day.atTime(2, 30), plan.nextScheduledForAfter(day.minusDays(1).atTime(2, 30), zoneId = newYork))
        assertEquals(day.atTime(2, 30), plan.previousScheduledForBefore(day.plusDays(1).atTime(2, 30), zoneId = newYork))
    }

    /**
     * Differential, within eight days of every 2026 clock change in seven zones (30-minute and
     * hour gaps, both hemispheres) and Apia's skipped day in 2011: the next dose is always as
     * before, and the previous dose is as before unless it was a gap dose this dose silences,
     * which it then skips. Gap neighbours and the fall-back nights are covered on purpose.
     */
    @Test
    fun aroundEveryClockChange_onlyASilencedPreviousDoseChanges() {
        val zones = listOf(
            "America/New_York" to 2026, "Europe/London" to 2026, "Australia/Sydney" to 2026,
            "Australia/Lord_Howe" to 2026, "America/Santiago" to 2026, "Asia/Tokyo" to 2026,
            "UTC" to 2026, "Pacific/Apia" to 2011,
        )
        val random = Random(20261118)
        var cases = 0
        var silenced = 0
        var gapNeighbours = 0
        var fallBackDays = 0
        for ((zoneName, year) in zones) {
            val zone = ZoneId.of(zoneName)
            val start = Instant.parse("$year-01-01T00:00:00Z")
            val transitions = generateSequence(zone.rules.nextTransition(start)) { zone.rules.nextTransition(it.instant) }
                .takeWhile { it.instant.isBefore(Instant.parse("${year + 1}-01-01T00:00:00Z")) }
                .toList()
            val days = transitions.map { it.dateTimeBefore.toLocalDate() }.ifEmpty { listOf(LocalDate.of(year, 3, 8)) }
            for ((index, transitionDay) in days.withIndex()) {
                val transition = transitions.getOrNull(index)
                repeat(150) {
                    // Times inside the changed hours, just past them, and anywhere.
                    val base = transition?.dateTimeBefore?.toLocalTime() ?: LocalTime.of(2, 0)
                    val span = transition?.duration?.abs()?.toMinutes()?.coerceIn(1, 1439) ?: 60
                    val times = (
                        List(random.nextInt(0, 4)) { base.plusMinutes(random.nextLong(0, span)) } +
                            List(random.nextInt(0, 3)) { base.plusMinutes(span + random.nextLong(0, 90)) } +
                            List(random.nextInt(0, 3)) { LocalTime.of(random.nextInt(0, 24), random.nextInt(0, 60)) }
                        ).distinct()
                    if (times.isEmpty()) return@repeat
                    val plan = if (random.nextBoolean()) {
                        plan(*times.toTypedArray(), interval = random.nextInt(1, 4))
                    } else {
                        plan(
                            *times.toTypedArray(),
                            type = MedicationGroupScheduleType.WEEKLY,
                            weekdays = DayOfWeek.entries.shuffled(random).take(random.nextInt(1, 8)).toSet(),
                        )
                    }
                    for (occurrence in plan.occurrencesBetweenInPlanWindow(transitionDay.minusDays(8), transitionDay.plusDays(8), zone)) {
                        val slot = occurrence.scheduledFor
                        val before = wallClockPrevious(plan, slot, zone)
                        val after = wallClockNext(plan, slot, zone)
                        cases++
                        if (listOfNotNull(slot, before, after).any { isInGap(it, zone) }) gapNeighbours++
                        if (transition?.isOverlap == true && slot.toLocalDate() == transitionDay) fallBackDays++
                        assertEquals("$zone $slot next", after, plan.nextScheduledForAfter(slot, zoneId = zone))
                        val expectedPrevious = if (before != null && silences(slot, before, zone)) {
                            silenced++
                            plan.occurrencesBetweenInPlanWindow(slot.toLocalDate().minusDays(90), slot.toLocalDate(), zone)
                                .map(MedicationGroupSlotOccurrence::scheduledFor)
                                .filter { it.isBefore(slot) && !silences(slot, it, zone) }
                                .maxOrNull()
                        } else {
                            before
                        }
                        assertEquals("$zone $slot previous", expectedPrevious, plan.previousScheduledForBefore(slot, zoneId = zone))
                    }
                }
            }
        }
        assertTrue("cases $cases", cases > 20_000)
        assertTrue("silenced $silenced", silenced > 100)
        assertTrue("gap neighbours $gapNeighbours", gapNeighbours > 1_000)
        assertTrue("fall-back days $fallBackDays", fallBackDays > 1_000)
    }

    /** [slot] silences [other]: a gap dose before it on the clock whose moment is at or after its own. */
    private fun silences(slot: LocalDateTime, other: LocalDateTime, zone: ZoneId) =
        isInGap(other, zone) && !other.atZone(zone).toInstant().isBefore(slot.atZone(zone).toInstant())

    /** The rule before this change: the doses either side on the wall clock. */
    private fun wallClockPrevious(plan: MedicationGroup, slot: LocalDateTime, zone: ZoneId): LocalDateTime? =
        plan.occurrencesBetweenInPlanWindow(slot.toLocalDate().minusDays(90), slot.toLocalDate(), zone)
            .map(MedicationGroupSlotOccurrence::scheduledFor)
            .filter { it.isBefore(slot) }
            .maxOrNull()

    private fun wallClockNext(plan: MedicationGroup, slot: LocalDateTime, zone: ZoneId): LocalDateTime? =
        plan.nextOccurrencesInPlanWindowFrom(slot.plusNanos(1), limit = 1, zoneId = zone).firstOrNull()?.scheduledFor

    private fun isInGap(dateTime: LocalDateTime, zone: ZoneId) = zone.rules.getValidOffsets(dateTime).isEmpty()
}
