package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.displayZoneOf
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Query key for slot-level fulfillment lookups. Nullable [scheduleTimeUuid]
 * because callers asking about ad-hoc logged slots (entries that don't match
 * any current schedule entry) pass `null` — the predicate falls back to
 * exact-time matching in that case.
 *
 * Distinct from [MedicationGroupSlotOccurrence], which is what the schedule
 * generator emits (always has a uuid).
 */
internal data class MedicationGroupSlotKey(
    val scheduleTimeUuid: UUID?,
    val scheduledFor: LocalDateTime,
)

internal fun slotRecords(
    group: MedicationGroup,
    slot: MedicationGroupSlotKey,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLogEntry> {
    return entries.filter { entry ->
        isEntryFulfillingPlanSlot(
            group = group,
            slot = slot,
            entry = entry,
            zoneId = zoneId,
        )
    }
}

internal fun isEntryFulfillingPlanSlot(
    group: MedicationGroup,
    slot: MedicationGroupSlotKey,
    entry: MedicationLogEntry,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    return isEntryForPlanSlot(
        group = group,
        slot = slot,
        entry = entry,
    ) && isEntryWithinScheduleFulfillmentWindow(
        group = group,
        entry = entry,
        zoneId = zoneId
    )
}

internal fun isEntryForPlanSlot(
    group: MedicationGroup,
    slot: MedicationGroupSlotKey,
    entry: MedicationLogEntry,
): Boolean {
    val scheduledFor = entry.scheduledFor ?: return false
    if (entry.sourceGroupUuid != group.uuid) {
        return false
    }
    return if (slot.scheduleTimeUuid != null && entry.scheduleTimeUuid != null) {
        entry.scheduleTimeUuid == slot.scheduleTimeUuid &&
            scheduledFor.toLocalDate() == slot.scheduledFor.toLocalDate()
    } else {
        scheduledFor == slot.scheduledFor
    }
}

internal fun isEntryWithinScheduleFulfillmentWindow(
    group: MedicationGroup,
    entry: MedicationLogEntry,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val scheduledFor = entry.scheduledFor ?: return false
    val appliedAtZoneId = displayZoneOf(entry.appliedAtTimeZoneId, zoneId)
    val appliedAt = entry.appliedAt.atZone(appliedAtZoneId).toLocalDateTime()
    return isWithinScheduleFulfillmentWindow(
        scheduledFor = scheduledFor,
        appliedAt = appliedAt,
        previousScheduledFor = group.previousScheduledForBefore(scheduledFor, zoneId = appliedAtZoneId),
        nextScheduledFor = group.nextScheduledForAfter(scheduledFor, zoneId = appliedAtZoneId)
    )
}
