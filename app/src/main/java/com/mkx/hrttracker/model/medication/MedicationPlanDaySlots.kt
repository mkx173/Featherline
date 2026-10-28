package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.atStoredZone
import com.mkx.hrttracker.util.medicationDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** The day ending a record follows: its plan's, or midnight for a record not from a plan. */
internal fun MedicationLogEntry.dayBoundary(groupsByUuid: Map<UUID, MedicationGroup>): MedicationDayBoundary =
    sourceGroupUuid?.let(groupsByUuid::get)?.schedule?.dayBoundary ?: MedicationDayBoundary.Midnight

internal fun planCalendarDate(
    scheduledForIso: String?,
    appliedAtEpochMillis: Long,
    appliedAtTimeZoneId: String,
    boundary: MedicationDayBoundary,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate {
    val dateTime = scheduledForIso?.let(LocalDateTime::parse) ?: atStoredZone(
        instant = Instant.ofEpochMilli(appliedAtEpochMillis),
        storedTimeZoneId = appliedAtTimeZoneId,
        deviceZone = zoneId,
    )
    return medicationDay(dateTime, boundary)
}

internal fun MedicationLogEntry.planCalendarDate(
    boundary: MedicationDayBoundary,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate {
    return planCalendarDate(
        scheduledForIso = scheduledFor?.toString(),
        appliedAtEpochMillis = appliedAt.toEpochMilli(),
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        boundary = boundary,
        zoneId = zoneId,
    )
}

internal fun List<MedicationGroup>.scheduledGroupsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationGroup> {
    return filter { group ->
        val hasScheduledSlots =
            group.scheduledSlotsInPlanWindow(date, zoneId).isNotEmpty()
        hasScheduledSlots ||
                entries.any { entry ->
                    entry.sourceGroupUuid == group.uuid &&
                            entry.scheduledFor?.let { medicationDay(it, group.schedule.dayBoundary) } == date &&
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
    val group =
        scheduledGroups.firstOrNull { scheduledGroup -> scheduledGroup.uuid == sourceGroupUuid }
            ?: return true
    if (medicationDay(scheduledFor, group.schedule.dayBoundary) != date) {
        return true
    }
    return !group.hasMedicationSignatureFor(entry)
}

internal fun MedicationGroup.scheduledSlotsInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationGroupSlotKey> {
    if (date.isBefore(schedule.since)) {
        return emptyList()
    }
    // A medication day can reach into the next civil day's early hours.
    return occurrencesBetweenInPlanWindow(date, date.plusDays(1), zoneId)
        .filter { occurrence -> medicationDay(occurrence.scheduledFor, schedule.dayBoundary) == date }
        .map { occurrence -> occurrence.toMedicationGroupSlotKey() }
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
            medicationDay(scheduledFor, schedule.dayBoundary) == date &&
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
