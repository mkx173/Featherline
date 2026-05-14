package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSlotOccurrence
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

// Synthesizes virtual estradiol log entries for unfulfilled future schedule
// slots so the PK simulator can project levels assuming the user takes their
// medication as planned.
//
// Occupancy is keyed by (sourceGroupUuid, scheduleTimeUuid, scheduledFor,
// MedicationSignature) — the same per-signature granularity that
// PlanCalendarDayUiState uses for "did this slot get fulfilled?". A group
// with two different estradiol medications in the same slot therefore tracks
// each medication's fulfillment independently: logging one does not suppress
// the planned dose for the other.
internal fun buildEstradiolPkSimulationEntries(
    realEntries: List<MedicationLogEntry>,
    activeGroups: List<MedicationGroup>,
    now: LocalDateTime,
    horizon: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): EstradiolPkSimulationEntries {
    if (!horizon.isAfter(now)) {
        return EstradiolPkSimulationEntries(real = realEntries, planned = emptyList())
    }

    // Multiset of logged units per (slot, signature) across real entries that
    // reference a future slot. We only count entries that point at a slot
    // (sourceGroupUuid + scheduleTimeUuid + scheduledFor all present); manual
    // entries don't fulfill scheduled slots.
    val loggedCount: Map<SlotMedKey, Int> = realEntries.asSequence()
        .filter { it.category == MedicationCategory.ESTRADIOL }
        .mapNotNull { entry ->
            val groupUuid = entry.sourceGroupUuid ?: return@mapNotNull null
            val scheduleTimeUuid = entry.scheduleTimeUuid ?: return@mapNotNull null
            val scheduledFor = entry.scheduledFor ?: return@mapNotNull null
            val key = SlotMedKey(
                groupUuid = groupUuid,
                scheduleTimeUuid = scheduleTimeUuid,
                scheduledFor = scheduledFor,
                signature = MedicationSignature.fromLogEntry(entry),
            )
            key to entry.count
        }
        .groupingBy { (key, _) -> key }
        .fold(0) { acc, (_, count) -> acc + count }

    val planned = activeGroups.asSequence()
        .flatMap { group ->
            group.occurrencesBetweenInPlanWindow(
                startDate = now.toLocalDate(),
                endDate = horizon.toLocalDate(),
                zoneId = zoneId,
            ).asSequence()
                .filter { occurrence -> occurrence.scheduledFor.isAfter(now) }
                .filter { occurrence -> !occurrence.scheduledFor.isAfter(horizon) }
                .flatMap { occurrence ->
                    group.virtualEntriesForOccurrence(occurrence, loggedCount, zoneId)
                        .asSequence()
                }
        }
        .toList()

    return EstradiolPkSimulationEntries(real = realEntries, planned = planned)
}

internal data class EstradiolPkSimulationEntries(
    val real: List<MedicationLogEntry>,
    val planned: List<MedicationLogEntry>,
)

private data class SlotMedKey(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val scheduledFor: LocalDateTime,
    val signature: MedicationSignature,
)

private fun MedicationGroup.virtualEntriesForOccurrence(
    occurrence: MedicationGroupSlotOccurrence,
    loggedCount: Map<SlotMedKey, Int>,
    zoneId: ZoneId,
): List<MedicationLogEntry> {
    val appliedAt = occurrence.scheduledFor.atZone(zoneId).toInstant()
    // Group medications by signature so two MedicationGroupMedication
    // instances with identical signatures (rare but possible) merge into a
    // single virtual entry whose count == their summed planned count.
    val plannedBySignature = medications
        .filter { it.category == MedicationCategory.ESTRADIOL }
        .groupBy(MedicationSignature::fromGroupMedication)

    return plannedBySignature.mapNotNull { (signature, contributingMeds) ->
        val plannedQty = contributingMeds.sumOf(MedicationGroupMedication::count)
        val key = SlotMedKey(
            groupUuid = uuid,
            scheduleTimeUuid = occurrence.scheduleTimeUuid,
            scheduledFor = occurrence.scheduledFor,
            signature = signature,
        )
        val remaining = (plannedQty - (loggedCount[key] ?: 0)).coerceAtLeast(0)
        if (remaining == 0) return@mapNotNull null

        val templateMedication = contributingMeds.first()
        MedicationLogEntry(
            uuid = virtualEntryUuid(
                groupUuid = uuid,
                scheduleTimeUuid = occurrence.scheduleTimeUuid,
                scheduledFor = occurrence.scheduledFor,
                medicationUuid = templateMedication.uuid,
            ),
            details = templateMedication.details,
            dosageMgAsEstradiol = null,
            sourceGroupUuid = uuid,
            appliedAt = appliedAt,
            appliedAtTimeZoneId = zoneId.id,
            scheduledFor = occurrence.scheduledFor,
            count = remaining,
            scheduleTimeUuid = occurrence.scheduleTimeUuid,
        )
    }
}

private fun virtualEntryUuid(
    groupUuid: UUID,
    scheduleTimeUuid: UUID,
    scheduledFor: LocalDateTime,
    medicationUuid: UUID,
): UUID {
    return UUID.nameUUIDFromBytes(
        "pk-virtual:$groupUuid:$scheduleTimeUuid:$scheduledFor:$medicationUuid"
            .toByteArray(StandardCharsets.UTF_8)
    )
}
