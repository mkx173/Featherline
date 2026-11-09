package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.occurrencesInPlanWindowFrom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class MedicationReminderPlan(
    val groupUuid: UUID,
    val groupName: String,
    val scheduledAt: LocalDateTime,
    val scheduleTimeUuid: UUID?,
)

internal fun buildNextMedicationReminderPlans(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    /** Each group's armed reminder time, from the schedule store. */
    armed: Map<UUID, LocalDateTime> = emptyMap(),
    /** The clock's instant, for [passedAsOf]. */
    clockNow: Instant = Instant.now(),
): List<MedicationReminderPlan> {
    val passed = now.passedAsOf(clockNow)
    return groups.asSequence()
        .filter(MedicationGroup::isActive)
        .filter { group -> group.notificationsEnabled && group.medications.isNotEmpty() }
        .mapNotNull { group ->
            // Armed and past its time: the alarm hasn't been delivered (the
            // receiver drops this entry first), as when the device was off at
            // the time or an inexact alarm runs late. Re-arm it, which fires at
            // once, and the receiver names the group's latest due dose. An alarm
            // in flight when this runs comes twice, and only the first posts.
            armed[group.uuid]
                ?.takeUnless { scheduledAt -> scheduledAt.isAfter(passed) }
                ?.let { scheduledAt ->
                    return@mapNotNull MedicationReminderPlan(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        scheduledAt = scheduledAt,
                        scheduleTimeUuid = null,
                    )
                }
            // However far off: some dose ahead isn't logged, so this ends.
            group
                .occurrencesInPlanWindowFrom(start = passed)
                .firstOrNull { occurrence ->
                    // Re-arm FUTURE alarms only. An occurrence at exactly `now`
                    // has already fired its alarm, and the startup `now` is
                    // truncated to the minute, so re-arming it would set an
                    // exact alarm in the past (up to 59s ago) that AlarmManager
                    // delivers immediately -> a just-tapped reminder re-fires on
                    // app reopen within the same minute. A dose logged early, even
                    // outside its window, is taken and isn't reminded.
                    occurrence.scheduledFor.isAfter(passed) &&
                            missingSlotCounts(
                                group = group,
                                slot = MedicationGroupSlotKey(
                                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                                    scheduledFor = occurrence.scheduledFor,
                                ),
                                entries = entries,
                            ).isNotEmpty()
                }
                ?.let { occurrence ->
                    MedicationReminderPlan(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        scheduledAt = occurrence.scheduledFor,
                        scheduleTimeUuid = occurrence.scheduleTimeUuid,
                    )
                }
        }
        .sortedBy { plan -> plan.scheduledAt }
        .toList()
}

/**
 * This wall time as far as it has passed: on the second pass of the hour the clocks go back,
 * that hour's end. The scheduler arms a time in it at its first pass, so every alarm in the hour
 * rang then, and a dose in it is neither ahead nor the latest due one any more. [clockNow] tells
 * which pass the clock is on.
 */
internal fun LocalDateTime.passedAsOf(clockNow: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
    val transition = zone.rules.getTransition(this)
    return if (transition != null && transition.isOverlap && zone.rules.getOffset(clockNow) == transition.offsetAfter) {
        transition.dateTimeBefore.minusNanos(1)
    } else {
        this
    }
}
