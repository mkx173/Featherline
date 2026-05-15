package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.nextOccurrencesInPlanWindowFrom
import com.mkx.hrttracker.ui.plan.PlanScheduleTimeSlot
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
    lookaheadDays: Long = 90L
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
                    !isSlotFulfilled(
                        group = group,
                        slot = PlanScheduleTimeSlot(
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
