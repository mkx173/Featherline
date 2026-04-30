package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.nextOccurrencesFrom
import com.mkx.hrttracker.ui.plan.isSlotFulfilled
import java.time.LocalDateTime
import java.util.UUID

data class MedicationReminderPlan(
    val groupUuid: UUID,
    val groupName: String,
    val scheduledAt: LocalDateTime,
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
            group.schedule
                .nextOccurrencesFrom(
                    start = now,
                    limit = MAX_OCCURRENCES_TO_SCAN_PER_GROUP,
                    lookaheadDays = lookaheadDays
                )
                .firstOrNull { occurrence ->
                    !isSlotFulfilled(
                        group = group,
                        date = occurrence.toLocalDate(),
                        time = occurrence.toLocalTime(),
                        entries = entries
                    )
                }
                ?.let { occurrence ->
                    MedicationReminderPlan(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        scheduledAt = occurrence
                    )
                }
        }
        .sortedBy { plan -> plan.scheduledAt }
        .toList()
}

private const val MAX_OCCURRENCES_TO_SCAN_PER_GROUP = 32
