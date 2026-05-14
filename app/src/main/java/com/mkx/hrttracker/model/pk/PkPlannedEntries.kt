package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotOccurrence
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

// Synthesizes virtual estradiol log entries for unfulfilled future schedule
// slots so the PK simulator can project levels assuming the user takes their
// medication as planned.
//
// A future slot is considered fulfilled by a real entry iff that entry shares
// the same (sourceGroupUuid, scheduleTimeUuid, scheduledFor) triple — the
// real entry then replaces the virtual one (with its real appliedAt and dose).
// The returned `planned` list flows through the simulator separately so the
// resulting dose markers can be rendered with the "planned" UI treatment;
// once a real entry occupies a slot, that marker shifts back to "logged".
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

    val occupied = realEntries.asSequence()
        .mapNotNull { entry ->
            val groupUuid = entry.sourceGroupUuid ?: return@mapNotNull null
            val scheduleTimeUuid = entry.scheduleTimeUuid ?: return@mapNotNull null
            val scheduledFor = entry.scheduledFor ?: return@mapNotNull null
            SlotKey(groupUuid, scheduleTimeUuid, scheduledFor)
        }
        .toSet()

    val planned = activeGroups.asSequence()
        .flatMap { group ->
            group.occurrencesBetweenInPlanWindow(
                startDate = now.toLocalDate(),
                endDate = horizon.toLocalDate(),
                zoneId = zoneId,
            ).asSequence()
                .filter { occurrence -> occurrence.scheduledFor.isAfter(now) }
                .filter { occurrence -> occurrence.scheduledFor.isBefore(horizon) }
                .filter { occurrence ->
                    SlotKey(group.uuid, occurrence.scheduleTimeUuid, occurrence.scheduledFor) !in occupied
                }
                .flatMap { occurrence ->
                    group.toVirtualEstradiolEntries(occurrence, zoneId).asSequence()
                }
        }
        .toList()

    return EstradiolPkSimulationEntries(real = realEntries, planned = planned)
}

internal data class EstradiolPkSimulationEntries(
    val real: List<MedicationLogEntry>,
    val planned: List<MedicationLogEntry>,
)

private data class SlotKey(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val scheduledFor: LocalDateTime,
)

private fun MedicationGroup.toVirtualEstradiolEntries(
    occurrence: MedicationGroupSlotOccurrence,
    zoneId: ZoneId,
): List<MedicationLogEntry> {
    val appliedAt = occurrence.scheduledFor.atZone(zoneId).toInstant()
    return medications
        .filter { medication -> medication.category == MedicationCategory.ESTRADIOL }
        .map { medication ->
            MedicationLogEntry(
                uuid = virtualEntryUuid(
                    groupUuid = uuid,
                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                    scheduledFor = occurrence.scheduledFor,
                    medicationUuid = medication.uuid,
                ),
                details = medication.details,
                dosageMgAsEstradiol = null,
                sourceGroupUuid = uuid,
                appliedAt = appliedAt,
                appliedAtTimeZoneId = zoneId.id,
                scheduledFor = occurrence.scheduledFor,
                count = medication.count,
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
