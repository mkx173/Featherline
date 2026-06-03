package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.atStoredZone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal fun planCalendarDate(
    scheduledForIso: String?,
    appliedAtEpochMillis: Long,
    appliedAtTimeZoneId: String,
    zoneId: ZoneId,
): LocalDate {
    return scheduledForIso
        ?.let(LocalDateTime::parse)
        ?.toLocalDate()
        ?: atStoredZone(
            instant = Instant.ofEpochMilli(appliedAtEpochMillis),
            storedTimeZoneId = appliedAtTimeZoneId,
            deviceZone = zoneId,
        ).toLocalDate()
}

internal fun MedicationLogEntry.planCalendarDate(zoneId: ZoneId): LocalDate {
    return planCalendarDate(
        scheduledForIso = scheduledFor?.toString(),
        appliedAtEpochMillis = appliedAt.toEpochMilli(),
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        zoneId = zoneId,
    )
}

internal fun List<MedicationGroup>.scheduledGroupsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
): List<MedicationGroup> {
    return filter { group ->
        group.schedule.isScheduledOn(date) ||
            entries.any { entry ->
                entry.sourceGroupUuid == group.uuid &&
                    entry.scheduledFor?.toLocalDate() == date &&
                    group.hasMedicationSignatureFor(entry)
            }
    }
}

internal fun isPlanOffPlanEntry(
    entry: MedicationLogEntry,
    scheduledGroups: List<MedicationGroup>,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val sourceGroupUuid = entry.sourceGroupUuid ?: return true
    val scheduledFor = entry.scheduledFor ?: return true
    if (scheduledFor.toLocalDate() != date) {
        return true
    }

    val group = scheduledGroups.firstOrNull { scheduledGroup -> scheduledGroup.uuid == sourceGroupUuid }
        ?: return true
    return !group.hasMedicationSignatureFor(entry)
}

internal fun MedicationGroup.scheduledTimesInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalTime> {
    return scheduledSlotsInPlanWindow(date, zoneId).map(MedicationGroupSlotKey::time)
}

internal fun MedicationGroup.scheduledTimesForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): List<LocalTime> {
    return scheduledSlotsForPlanDay(
        date = date,
        entries = entries,
        zoneId = zoneId,
        includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
        unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
    ).map(MedicationGroupSlotKey::time)
}

internal fun MedicationGroup.scheduledSlotsInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationGroupSlotKey> {
    return occurrencesBetweenInPlanWindow(date, date, zoneId).map { occurrence ->
        occurrence.toMedicationGroupSlotKey()
    }
}

internal fun MedicationGroup.scheduledSlotsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): List<MedicationGroupSlotKey> {
    val visibleSlots = scheduledSlotsInPlanWindow(date, zoneId).filter { slot ->
        isArchivedUnloggedPlanSlotVisible(
            slotDateTime = slot.scheduledFor,
            includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
            unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
        )
    }
    val loggedSlots = entries.mapNotNull { entry ->
        val scheduledFor = entry.scheduledFor ?: return@mapNotNull null
        if (
            entry.sourceGroupUuid == uuid &&
            scheduledFor.toLocalDate() == date &&
            hasMedicationSignatureFor(entry)
        ) {
            if (
                entry.scheduleTimeUuid != null &&
                visibleSlots.any { slot ->
                    slot.scheduleTimeUuid == entry.scheduleTimeUuid &&
                        slot.scheduledFor.toLocalDate() == scheduledFor.toLocalDate()
                }
            ) {
                return@mapNotNull null
            }
            MedicationGroupSlotKey(
                scheduleTimeUuid = entry.scheduleTimeUuid,
                scheduledFor = scheduledFor,
            )
        } else {
            null
        }
    }

    return (visibleSlots + loggedSlots)
        .distinctBy(MedicationGroupSlotKey::scheduledFor)
        .sortedBy { slot -> slot.scheduledFor }
}

private fun MedicationGroup.isArchivedUnloggedPlanSlotVisible(
    slotDateTime: LocalDateTime,
    includeUnloggedArchivedSlots: Boolean,
    unloggedArchivedSlotCutoff: LocalDateTime?,
): Boolean {
    return archivedAtLocal == null ||
        includeUnloggedArchivedSlots ||
        unloggedArchivedSlotCutoff?.let { cutoff -> slotDateTime.isBefore(cutoff) } == true
}

private fun MedicationGroup.hasMedicationSignatureFor(entry: MedicationLogEntry): Boolean {
    val requiredSignatures = medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .keys
    return MedicationSignature.fromLogEntry(entry) in requiredSignatures
}

private fun MedicationGroupSlotOccurrence.toMedicationGroupSlotKey(): MedicationGroupSlotKey {
    return MedicationGroupSlotKey(
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledFor = scheduledFor,
    )
}
