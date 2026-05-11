package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.ui.plan.PlanScheduleTimeSlot
import com.mkx.hrttracker.ui.plan.isSlotFulfilled
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID

data class MedicationReminderSlot(
    val groupUuid: UUID,
    val scheduledAt: LocalDateTime,
    val scheduleTimeUuid: UUID?,
)

data class MedicationReminderBundleItem(
    val slot: MedicationReminderSlot,
    val groupName: String,
    val medications: List<MedicationGroupMedication>,
)

data class MedicationReminderBundle(
    val scheduledAt: LocalDateTime,
    val items: List<MedicationReminderBundleItem>,
) {
    val slots: List<MedicationReminderSlot>
        get() = items.map(MedicationReminderBundleItem::slot)

    val notificationTag: String
        get() = medicationReminderNotificationTag(slots)
}

internal fun buildMedicationReminderBundle(
    scheduledAt: LocalDateTime,
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
): MedicationReminderBundle? {
    val items = groups
        .sortedBy(MedicationGroup::createdAt)
        .asSequence()
        .filter(MedicationGroup::isActive)
        .filter { group -> group.notificationsEnabled && group.medications.isNotEmpty() }
        .flatMap { group ->
            group.occurrencesBetweenInPlanWindow(
                startDate = scheduledAt.toLocalDate(),
                endDate = scheduledAt.toLocalDate(),
            )
                .asSequence()
                .filter { occurrence -> occurrence.scheduledFor == scheduledAt }
                .map { occurrence -> group to occurrence }
        }
        .filterNot { (group, occurrence) ->
            isSlotFulfilled(
                group = group,
                slot = PlanScheduleTimeSlot(
                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                    scheduledFor = occurrence.scheduledFor,
                ),
                entries = entries,
            )
        }
        .map { (group, occurrence) ->
            MedicationReminderBundleItem(
                slot = MedicationReminderSlot(
                    groupUuid = group.uuid,
                    scheduledAt = occurrence.scheduledFor,
                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                ),
                groupName = group.name,
                medications = group.medications,
            )
        }
        .toList()

    return items
        .takeIf(List<MedicationReminderBundleItem>::isNotEmpty)
        ?.let { bundleItems ->
            MedicationReminderBundle(
                scheduledAt = scheduledAt,
                items = bundleItems,
            )
        }
}

internal fun medicationReminderNotificationTag(slots: List<MedicationReminderSlot>): String {
    val sortedSlotValues = slots.map(MedicationReminderSlot::toStorageValue).sorted()
    val joinedSlotValues = sortedSlotValues.joinToString(separator = ";")
    val bundleUuid = UUID.nameUUIDFromBytes(joinedSlotValues.toByteArray(StandardCharsets.UTF_8))
    val scheduledAt = slots.minOfOrNull(MedicationReminderSlot::scheduledAt)
        ?: LocalDateTime.MIN
    return "medication-reminder/$scheduledAt/$bundleUuid"
}

internal fun MedicationReminderSlot.toStorageValue(): String {
    return listOf(
        groupUuid.toString(),
        scheduleTimeUuid?.toString().orEmpty(),
        scheduledAt.toString(),
    ).joinToString(separator = "|")
}

internal fun medicationReminderSlotFromStorageValue(value: String): MedicationReminderSlot? {
    val parts = value.split("|", limit = 3)
    if (parts.size != 3) {
        return null
    }
    return runCatching {
        MedicationReminderSlot(
            groupUuid = UUID.fromString(parts[0]),
            scheduleTimeUuid = parts[1].takeIf(String::isNotBlank)?.let(UUID::fromString),
            scheduledAt = LocalDateTime.parse(parts[2]),
        )
    }.getOrNull()
}
