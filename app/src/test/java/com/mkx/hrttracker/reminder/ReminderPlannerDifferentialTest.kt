package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.occurrencesInPlanWindowFrom
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * The planner's next and latest due alerts against brute force, over generated plans (daily and
 * weekly, doses crowded into the early morning, some logged, sometimes a skip watermark) around
 * both clock changes in several zones, Samoa's skipped day included. Brute force lists every
 * alert, a dose's alerts cut at the earliest instant of any later dose, and takes the min or max
 * by instant. Where no clock change skips an hour, the cut must be the plan's next dose in wall
 * order, as it was before the spring-forward fix, so ordinary days and fall-back nights ring
 * exactly as they did. (Second review 2026-11-18, finding 3; written by its reviewer.)
 */
class ReminderPlannerDifferentialTest {
    private val medicine = testMedicine()
    private val groupUuid = UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000aa")

    private data class Window(val zone: String, val center: LocalDateTime, val hasGap: Boolean)

    private val windows = listOf(
        Window("America/New_York", LocalDateTime.of(2026, 3, 8, 3, 0), hasGap = true),
        Window("America/New_York", LocalDateTime.of(2026, 11, 1, 1, 30), hasGap = false),
        Window("America/New_York", LocalDateTime.of(2026, 6, 10, 3, 0), hasGap = false),
        Window("Europe/London", LocalDateTime.of(2026, 3, 29, 2, 0), hasGap = true),
        Window("Europe/London", LocalDateTime.of(2026, 10, 25, 1, 30), hasGap = false),
        Window("Australia/Lord_Howe", LocalDateTime.of(2026, 10, 4, 2, 15), hasGap = true),
        Window("Australia/Lord_Howe", LocalDateTime.of(2026, 4, 5, 1, 45), hasGap = false),
        // The day Samoa skipped: a 24-hour gap.
        Window("Pacific/Apia", LocalDateTime.of(2011, 12, 30, 12, 0), hasGap = true),
        Window("Asia/Tokyo", LocalDateTime.of(2026, 3, 8, 3, 0), hasGap = false),
        Window("UTC", LocalDateTime.of(2026, 3, 8, 3, 0), hasGap = false),
    )

    private val repeatsVariants = listOf(
        ReminderRepeats(count = 0),
        ReminderRepeats(),
        ReminderRepeats(count = 5, intervalMinutes = 60),
        ReminderRepeats(count = 2, intervalMinutes = 25),
    )

    @Test
    fun nextAndLatestDue_matchBruteForce_andOrdinaryNightsKeepTheWallOrderCut() {
        val random = Random(20261118)
        val nextMismatch = mutableListOf<String>()
        val latestMismatch = mutableListOf<String>()
        val cutDiffersOutsideGap = mutableListOf<String>()

        for (window in windows) {
            val zone = ZoneId.of(window.zone)
            val center = window.center.atZone(zone).toInstant()
            repeat(16) { planIndex ->
                val (group, periodDays) = plan(random, window.center.toLocalDate().minusDays(random.nextLong(0, 400)))
                val repeats = repeatsVariants[planIndex % repeatsVariants.size]
                val around = group.occurrencesInPlanWindowFrom(window.center.minusDays(3))
                    .takeWhile { dose -> dose.scheduledFor.isBefore(window.center.plusDays(3)) }
                    .map { dose -> dose.scheduledFor }.distinct().toList()
                val entries = around.filter { random.nextInt(5) == 0 }.map { doseAt ->
                    val slot = group.schedule.timeSlots.first { it.time == doseAt.toLocalTime() }
                    testMedicationLogEntry(
                        medicine = medicine,
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        sourceGroupUuid = groupUuid,
                        appliedAt = doseAt.atZone(zone).toInstant(),
                        scheduledFor = doseAt,
                        scheduleTimeUuid = slot.uuid,
                    )
                }
                val skipped = around.takeIf { it.isNotEmpty() && random.nextInt(4) == 0 }?.random(random)
                val brute = BruteForce(group, entries, repeats, skipped, zone)
                val horizon = window.center.plusDays(periodDays * 2 + 14)
                val alerts = brute.alerts(window.center.minusDays(4), horizon, wallOrderCut = false)
                if (!window.hasGap) {
                    val wallOrder = brute.alerts(window.center.minusDays(4), horizon, wallOrderCut = true)
                    if (alerts != wallOrder && cutDiffersOutsideGap.size < 10) {
                        cutDiffersOutsideGap += "${window.zone} times=${group.schedule.times} $repeats"
                    }
                }

                val instants = buildList {
                    // A minute apart for four hours each side of the centre, then coarser.
                    for (m in -240L..240L) add(center.plusSeconds(m * 60 + random.nextLong(0, 60)))
                    for (m in -2880L..2880L step 37) add(center.plusSeconds(m * 60))
                    for (m in -240L..240L step 5) add(center.plusSeconds(m * 60)) // on alert instants
                }
                for (at in instants) {
                    val next = group.nextReminderAlert(at, entries, repeats, skipped, zone)
                    val bruteNext = alerts.filter { alert -> alert.alertAt.isAfter(at) }
                        .minByOrNull { alert -> alert.alertAt }
                    if (next != bruteNext && nextMismatch.size < 10) {
                        nextMismatch += "${window.zone} $at times=${group.schedule.times} $repeats " +
                                "planner=$next brute=$bruteNext"
                    }

                    val since = at.minus(Duration.ofMinutes(listOf(0L, 5L, 16L, 50L, 61L, 180L, 1500L).random(random)))
                    val latest = group.latestDueReminderAlert(since, at, entries, repeats, skipped, zone)
                    val bruteLatest = alerts
                        .filter { alert -> !alert.alertAt.isBefore(since) && !alert.alertAt.isAfter(at) }
                        .let { due -> due.lastOrNull { alert -> alert.alertAt == due.maxOf(ReminderAlert::alertAt) } }
                        ?.takeIf { alert -> at.isBefore(alert.alertAt.plus(ALERT_CURRENT_FOR)) }
                    if (latest != bruteLatest && latestMismatch.size < 10) {
                        latestMismatch += "${window.zone} since=$since now=$at times=${group.schedule.times} " +
                                "$repeats planner=$latest brute=$bruteLatest"
                    }
                }
            }
        }
        assertEquals("next alert == brute force", emptyList<String>(), nextMismatch)
        assertEquals("latest due alert == brute force", emptyList<String>(), latestMismatch)
        assertEquals("no gap: the wall-order cut", emptyList<String>(), cutDiffersOutsideGap)
    }

    /**
     * The finding's shape: daily plans with 2 to 6 dose times packed from 75 minutes before to 70
     * minutes after the end of a spring-forward gap, some logged, some skipped. Many of them have
     * a dose in the gap that a later dose comes before, which the wall-order cut let ring.
     */
    @Test
    fun plansPackedAroundTheGap_matchBruteForce() {
        val random = Random(77)
        val mismatches = mutableListOf<String>()
        var plansTheCutChanges = 0
        for (window in windows.filter { it.hasGap && it.zone != "Pacific/Apia" }) {
            val zone = ZoneId.of(window.zone)
            val center = window.center.atZone(zone).toInstant()
            repeat(100) { planIndex ->
                val times = List(random.nextInt(2, 7)) {
                    window.center.toLocalTime().minusMinutes(75).plusMinutes(random.nextLong(0, 30) * 5)
                }.distinct()
                val group = plan(
                    times = times,
                    since = window.center.toLocalDate().minusDays(30),
                    dayBoundary = if (random.nextBoolean()) MedicationDayBoundary.FourAm else MedicationDayBoundary.Midnight,
                )
                val repeats = repeatsVariants[planIndex % repeatsVariants.size]
                val around = group.occurrencesInPlanWindowFrom(window.center.minusDays(1))
                    .takeWhile { dose -> dose.scheduledFor.isBefore(window.center.plusDays(1)) }
                    .toList()
                val entries = around.filter { random.nextInt(6) == 0 }.map { dose ->
                    testMedicationLogEntry(
                        medicine = medicine,
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        sourceGroupUuid = groupUuid,
                        appliedAt = dose.scheduledFor.atZone(zone).toInstant(),
                        scheduledFor = dose.scheduledFor,
                        scheduleTimeUuid = dose.scheduleTimeUuid,
                    )
                }
                val skipped = around.takeIf { random.nextInt(5) == 0 }?.random(random)?.scheduledFor
                val brute = BruteForce(group, entries, repeats, skipped, zone)
                val alerts = brute.alerts(window.center.minusDays(3), window.center.plusDays(5), wallOrderCut = false)
                if (alerts != brute.alerts(window.center.minusDays(3), window.center.plusDays(5), wallOrderCut = true)) {
                    plansTheCutChanges++
                }
                for (m in -200L..300L) {
                    val at = center.plusSeconds(m * 60 + random.nextLong(0, 60))
                    val next = group.nextReminderAlert(at, entries, repeats, skipped, zone)
                    val since = at.minus(Duration.ofMinutes(listOf(0L, 5L, 16L, 50L, 61L, 180L).random(random)))
                    val latest = group.latestDueReminderAlert(since, at, entries, repeats, skipped, zone)
                    val bruteNext = alerts.filter { alert -> alert.alertAt.isAfter(at) }.minByOrNull { alert -> alert.alertAt }
                    val bruteLatest = alerts
                        .filter { alert -> !alert.alertAt.isBefore(since) && !alert.alertAt.isAfter(at) }
                        .let { due -> due.lastOrNull { alert -> alert.alertAt == due.maxOf(ReminderAlert::alertAt) } }
                        ?.takeIf { alert -> at.isBefore(alert.alertAt.plus(ALERT_CURRENT_FOR)) }
                    if ((next != bruteNext || latest != bruteLatest) && mismatches.size < 10) {
                        mismatches += "${window.zone} times=${times.sorted()} $repeats skipped=$skipped at=$at " +
                                "next=$next/$bruteNext latest=$latest/$bruteLatest"
                    }
                }
            }
        }
        assertEquals("planner == brute force", emptyList<String>(), mismatches)
        assertTrue("the generator makes the finding's shape: $plansTheCutChanges plans", plansTheCutChanges >= 30)
    }

    /** Every alert, worked out the long way from the plan's dose list. */
    private class BruteForce(
        val group: MedicationGroup,
        val entries: List<MedicationLogEntry>,
        val repeats: ReminderRepeats,
        val skipped: LocalDateTime?,
        val zone: ZoneId,
    ) {
        fun alerts(from: LocalDateTime, until: LocalDateTime, wallOrderCut: Boolean): List<ReminderAlert> {
            val doses = group.occurrencesInPlanWindowFrom(from)
                .takeWhile { dose -> !dose.scheduledFor.isAfter(until) }
                .toList()
            return doses.flatMap { dose ->
                val doseAt = dose.scheduledFor
                val later = doses.filter { other -> other.scheduledFor.isAfter(doseAt) }
                val cut = if (wallOrderCut) {
                    later.firstOrNull()?.scheduledFor?.atZone(zone)?.toInstant()
                } else {
                    later.minOfOrNull { other -> other.scheduledFor.atZone(zone).toInstant() }
                }
                val rings = (skipped == null || doseAt.isAfter(skipped)) && missingSlotCounts(
                    group = group,
                    slot = MedicationGroupSlotKey(scheduleTimeUuid = dose.scheduleTimeUuid, scheduledFor = doseAt),
                    entries = entries,
                ).isNotEmpty()
                if (!rings) return@flatMap emptyList()
                val first = doseAt.atZone(zone).toInstant()
                (0..repeats.count)
                    .map { k -> ReminderAlert(doseAt, first.plus(Duration.ofMinutes(k.toLong() * repeats.intervalMinutes))) }
                    .filter { alert -> cut == null || alert.alertAt.isBefore(cut) }
            }
        }
    }

    private fun plan(random: Random, since: LocalDate): Pair<MedicationGroup, Long> {
        val weekly = random.nextInt(4) == 0
        val interval = if (weekly) random.nextInt(1, 4) else listOf(1, 1, 1, 1, 2, 3, 7, 14).random(random)
        val timeCount = random.nextInt(1, 6)
        // Crowd the early morning, where the clocks change.
        val times = List(timeCount) {
            if (random.nextInt(3) > 0) LocalTime.of(random.nextInt(0, 5), random.nextInt(0, 12) * 5)
            else LocalTime.of(random.nextInt(0, 24), random.nextInt(0, 12) * 5)
        }.distinct()
        val days = if (weekly) DayOfWeek.values().toList().shuffled(random).take(random.nextInt(1, 4)).toSet() else emptySet()
        val group = plan(
            times = times,
            since = since,
            dayBoundary = if (random.nextInt(3) == 0) MedicationDayBoundary.FourAm else MedicationDayBoundary.Midnight,
            type = if (weekly) MedicationGroupScheduleType.WEEKLY else MedicationGroupScheduleType.DAILY,
            interval = interval,
            days = days,
        )
        val periodDays = if (weekly) 7L * interval else interval.toLong()
        return group to periodDays
    }

    private fun plan(
        times: List<LocalTime>,
        since: LocalDate,
        dayBoundary: MedicationDayBoundary,
        type: MedicationGroupScheduleType = MedicationGroupScheduleType.DAILY,
        interval: Int = 1,
        days: Set<DayOfWeek> = emptySet(),
    ) = MedicationGroup(
        uuid = groupUuid,
        name = "P",
        schedule = MedicationGroupSchedule(
            type = type,
            interval = interval,
            since = since,
            weeklyDaysOfWeek = days,
            times = times,
            dayBoundary = dayBoundary,
        ),
        medications = listOf(
            testMedicationGroupMedication(
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2000-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2000-01-01T00:00:00Z"),
    )
}
