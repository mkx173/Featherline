package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationGroupSlotOccurrence
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.occurrencesInPlanWindowFrom
import com.mkx.hrttracker.model.settings.ReminderRepeats
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class MedicationReminderPlan(
    val groupUuid: UUID,
    val groupName: String,
    /** When the alarm goes off: the instant its delivery is identified by. */
    val alertAt: Instant,
    /** The dose it rings for; null for an armed alert armed again as it was. */
    val doseAt: LocalDateTime?,
)

/**
 * One alert of a plan's dose: the dose's wall time, and the alert's instant, the dose's instant
 * plus k × the interval, in real time.
 */
internal data class ReminderAlert(
    val doseAt: LocalDateTime,
    val alertAt: Instant,
)

internal fun buildNextMedicationReminderPlans(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    /** The clock: alerts after it are ahead, an armed alert at or before it has passed. */
    now: Instant,
    /** Each group's armed alert, from the schedule store. */
    armed: Map<UUID, Instant> = emptyMap(),
    repeats: ReminderRepeats = ReminderRepeats(),
    /** Each group's skip watermark: its doses at or before it don't ring. */
    skippedThrough: Map<UUID, LocalDateTime> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<MedicationReminderPlan> =
    groups.asSequence()
        .filter(MedicationGroup::isActive)
        .filter { group -> group.notificationsEnabled && group.medications.isNotEmpty() }
        .mapNotNull { group ->
            // Armed and past its instant: the alarm hasn't been delivered (the receiver drops
            // this entry first), as when the device was off at the time or an inexact alarm
            // runs late. Re-arm it, which fires at once, and the receiver names the group's
            // latest due alert. An alarm in flight when this runs comes twice, and only the
            // first posts.
            armed[group.uuid]
                ?.takeUnless { alertAt -> alertAt.isAfter(now) }
                ?.let { alertAt ->
                    return@mapNotNull MedicationReminderPlan(group.uuid, group.name, alertAt, doseAt = null)
                }
            // Strictly after now: an alert at exactly now has rung.
            group.nextReminderAlert(after = now, entries, repeats, skippedThrough[group.uuid], zone)
                ?.let { alert -> MedicationReminderPlan(group.uuid, group.name, alert.alertAt, alert.doseAt) }
        }
        .sortedBy { plan -> plan.alertAt }
        .toList()

/** The plan's first alert strictly after [after]. However far off: some dose ahead isn't logged. */
internal fun MedicationGroup.nextReminderAlert(
    after: Instant,
    entries: List<MedicationLogEntry>,
    repeats: ReminderRepeats,
    skippedThrough: LocalDateTime?,
    zone: ZoneId = ZoneId.systemDefault(),
): ReminderAlert? =
    reminderAlerts(reminderDosesFrom(after, repeats, zone), entries, repeats, skippedThrough, zone)
        .firstOrNull { alert -> alert.alertAt.isAfter(after) }

/**
 * The plan's latest alert from [since] up to [now]: what a delivery armed for [since] rings for.
 * Late, as after the phone was off, it is the latest one, not each one in between, and only while
 * it is current: less than [ALERT_CURRENT_FOR] old. An older one rings nothing, so a delivery
 * after a clock change, a boot or the phone being off posts no stale reminder per plan.
 */
internal fun MedicationGroup.latestDueReminderAlert(
    since: Instant,
    now: Instant,
    entries: List<MedicationLogEntry>,
    repeats: ReminderRepeats,
    skippedThrough: LocalDateTime?,
    zone: ZoneId = ZoneId.systemDefault(),
): ReminderAlert? =
    reminderAlerts(reminderDosesFrom(since, repeats, zone), entries, repeats, skippedThrough, zone)
        .filter { alert -> !alert.alertAt.isBefore(since) }
        .takeWhile { alert -> !alert.alertAt.isAfter(now) }
        .lastOrNull()
        ?.takeIf { alert -> now.isBefore(alert.alertAt.plus(ALERT_CURRENT_FOR)) }

// ponytail: one fixed hour, so an alarm battery saver or Doze defers past it posts nothing. Widen
// it, or tie it to the restrictions in force, if such reminders go missing.
/**
 * How long a due alert stays current: an inexact alarm (no exact alarm access) comes within an
 * hour of its time on Android 12 and later, unless battery saver or Doze defers it.
 */
internal val ALERT_CURRENT_FOR: Duration = Duration.ofHours(1)

/**
 * The wall time from which a plan's doses can still have an alert at or after [instant]: one
 * repeat span back, and a day more for a dose a clock change moved. The entries a pass reads
 * start here too, so a dose logged since it was due counts as logged.
 */
internal fun reminderDosesFrom(instant: Instant, repeats: ReminderRepeats, zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(instant.minus(Duration.ofMinutes(repeats.spanMinutes)), zone).minusDays(1)

/**
 * Every alert of the plan's doses from [dosesFrom] on, in instant order. A dose rings while it
 * misses some medication and is after the plan's skip watermark: at its own instant (its wall time
 * in [zone], so a time the clocks skip rings once they have, and one they repeat rings on its
 * first pass), then [ReminderRepeats.count] times more, [ReminderRepeats.intervalMinutes] apart in
 * real time, and never at or after the instant of the plan's next dose: the earliest instant of
 * any later dose. That is the next dose's own but on a spring-forward night, where a later dose
 * after the gap comes before one in it: 02:10 (03:10) rings nothing when 03:05 follows, as the
 * 03:05 reminder covers it (decision V7). Nothing is stored per alert.
 */
internal fun MedicationGroup.reminderAlerts(
    dosesFrom: LocalDateTime,
    entries: List<MedicationLogEntry>,
    repeats: ReminderRepeats,
    skippedThrough: LocalDateTime?,
    zone: ZoneId = ZoneId.systemDefault(),
): Sequence<ReminderAlert> =
    occurrencesInPlanWindowFrom(start = dosesFrom).withNextDoseAt(zone).flatMap { (dose, nextDose) ->
        val doseAt = dose.scheduledFor
        val rings = (skippedThrough == null || doseAt.isAfter(skippedThrough)) &&
                // A dose logged early or late, even outside its window, is taken.
                missingSlotCounts(
                    group = this,
                    slot = MedicationGroupSlotKey(scheduleTimeUuid = dose.scheduleTimeUuid, scheduledFor = doseAt),
                    entries = entries,
                ).isNotEmpty()
        if (!rings) {
            emptySequence()
        } else {
            val first = doseAt.atZone(zone).toInstant()
            (0..repeats.count).asSequence()
                .map { k -> ReminderAlert(doseAt, first.plus(Duration.ofMinutes(k.toLong() * repeats.intervalMinutes))) }
                .takeWhile { alert -> nextDose == null || alert.alertAt.isBefore(nextDose) }
        }
    }

/**
 * Each occurrence with the earliest instant of the plan's doses after it in wall time, or null
 * past its last.
 */
private fun Sequence<MedicationGroupSlotOccurrence>.withNextDoseAt(zone: ZoneId):
        Sequence<Pair<MedicationGroupSlotOccurrence, Instant?>> = sequence {
    val source = iterator()
    val ahead = ArrayDeque<MedicationGroupSlotOccurrence>()
    fun at(index: Int): MedicationGroupSlotOccurrence? =
        ahead.getOrNull(index) ?: if (source.hasNext()) source.next().also(ahead::addLast) else null
    while (true) {
        val dose = at(0) ?: break
        ahead.removeFirst()
        var nextDose: Instant? = null
        var index = 0
        while (true) {
            val later = at(index++) ?: break
            if (!later.scheduledFor.isAfter(dose.scheduledFor)) continue
            // Wall times map to instants in order but across a gap, which moves a dose later, so a
            // dose past the earliest one's instant read as a wall time is after it.
            if (nextDose != null && later.scheduledFor.isAfter(LocalDateTime.ofInstant(nextDose, zone))) break
            val laterAt = later.scheduledFor.atZone(zone).toInstant()
            if (nextDose == null || laterAt.isBefore(nextDose)) nextDose = laterAt
        }
        yield(dose to nextDose)
    }
}
