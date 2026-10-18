package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.nextOccurrencesInPlanWindowFrom
import java.time.LocalDateTime
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
    lookaheadDays: Long = 90L,
    /** Each group's armed reminder time, from the schedule store. */
    armed: Map<UUID, LocalDateTime> = emptyMap(),
): List<MedicationReminderPlan> {
    return groups.asSequence()
        .filter(MedicationGroup::isActive)
        .filter { group -> group.notificationsEnabled && group.medications.isNotEmpty() }
        .mapNotNull { group ->
            // Armed and past its time: the alarm never reached the receiver
            // (it would have replaced this entry with the next time), as when
            // the device was off at the time. Re-arm it, which fires at once,
            // and the receiver names the group's latest due dose.
            // ponytail: an alarm in flight when this runs is delivered twice;
            // the second replaces the same notification.
            armed[group.uuid]
                ?.takeUnless { scheduledAt -> scheduledAt.isAfter(now) }
                ?.let { scheduledAt ->
                    return@mapNotNull MedicationReminderPlan(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        scheduledAt = scheduledAt,
                        scheduleTimeUuid = null,
                    )
                }
            group
                .nextOccurrencesInPlanWindowFrom(
                    start = now,
                    limit = Int.MAX_VALUE,
                    lookaheadDays = lookaheadDays
                )
                .firstOrNull { occurrence ->
                    // Re-arm FUTURE alarms only. An occurrence at exactly `now`
                    // has already fired its alarm, and the startup `now` is
                    // truncated to the minute, so re-arming it would set an
                    // exact alarm in the past (up to 59s ago) that AlarmManager
                    // delivers immediately -> a just-tapped reminder re-fires on
                    // app reopen within the same minute.
                    occurrence.scheduledFor.isAfter(now) &&
                            !isSlotFulfilled(
                                group = group,
                                slot = MedicationGroupSlotKey(
                                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                                    scheduledFor = occurrence.scheduledFor,
                                ),
                                entries = entries
                            )
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
