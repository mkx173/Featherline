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
    skippedSlots: Set<MedicationReminderSlot> = emptySet(),
): List<MedicationReminderPlan> {
    return groups.asSequence()
        .filter(MedicationGroup::isActive)
        .filter { group -> group.notificationsEnabled && group.medications.isNotEmpty() }
        .mapNotNull { group ->
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
                            MedicationReminderSlot(
                                groupUuid = group.uuid,
                                scheduledAt = occurrence.scheduledFor,
                                scheduleTimeUuid = occurrence.scheduleTimeUuid,
                            ) !in skippedSlots &&
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
