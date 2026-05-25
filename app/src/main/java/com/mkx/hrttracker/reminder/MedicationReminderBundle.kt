package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.isSlotFulfilledForMedication
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.toStorageValue
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

/**
 * One unit-of-work for the "Log all" notification action: a slot plus the
 * signature of one medication that was displayed at notification time.
 * Shipped in PendingIntent extras so the action handler logs exactly what
 * the user saw — never something added (or deleted) between the notification
 * appearing and the tap.
 */
data class MedicationReminderLogTarget(
    val slot: MedicationReminderSlot,
    val signature: MedicationSignature,
)

data class MedicationReminderBundle(
    val scheduledAt: LocalDateTime,
    val items: List<MedicationReminderBundleItem>,
) {
    val slots: List<MedicationReminderSlot>
        get() = items.map(MedicationReminderBundleItem::slot)

    val logTargets: List<MedicationReminderLogTarget>
        get() = items.flatMap { item ->
            item.medications.map { medication ->
                MedicationReminderLogTarget(
                    slot = item.slot,
                    signature = MedicationSignature.fromGroupMedication(medication),
                )
            }
        }

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
                slot = MedicationGroupSlotKey(
                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                    scheduledFor = occurrence.scheduledFor,
                ),
                entries = entries,
            )
        }
        .mapNotNull { (group, occurrence) ->
            val slotKey = MedicationGroupSlotKey(
                scheduleTimeUuid = occurrence.scheduleTimeUuid,
                scheduledFor = occurrence.scheduledFor,
            )
            val displayedMedications = group.medications.filterNot { medication ->
                isSlotFulfilledForMedication(
                    group = group,
                    slot = slotKey,
                    medication = medication,
                    entries = entries,
                )
            }
            // The slot-level filter above already established that at least
            // one signature is unfulfilled, so this list is normally non-empty.
            // Guard anyway: with duplicate-signature meds in one group the two
            // predicates can disagree, and silently rendering an empty body
            // would be worse than dropping the row.
            if (displayedMedications.isEmpty()) {
                return@mapNotNull null
            }
            MedicationReminderBundleItem(
                slot = MedicationReminderSlot(
                    groupUuid = group.uuid,
                    scheduledAt = occurrence.scheduledFor,
                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                ),
                groupName = group.name,
                medications = displayedMedications,
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

// Pair encoding: `<slotStorageValue>;<signatureStorageValue>`. Both halves
// use `|` internally; `;` is unused by either, so the split is unambiguous.
internal fun MedicationReminderLogTarget.toStorageValue(): String {
    return "${slot.toStorageValue()};${signature.toStorageValue()}"
}

internal fun medicationReminderLogTargetFromStorageValue(value: String): MedicationReminderLogTarget? {
    val parts = value.split(";", limit = 2)
    if (parts.size != 2) {
        return null
    }
    val slot = medicationReminderSlotFromStorageValue(parts[0]) ?: return null
    val signature = MedicationSignature.fromStorageValue(parts[1]) ?: return null
    return MedicationReminderLogTarget(slot = slot, signature = signature)
}
