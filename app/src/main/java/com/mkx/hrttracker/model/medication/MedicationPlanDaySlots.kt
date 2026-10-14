package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.atStoredZone
import com.mkx.hrttracker.util.medicationDay
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
    dayStartMinutes: Int = 0,
): LocalDate {
    val dateTime = scheduledForIso?.let(LocalDateTime::parse) ?: atStoredZone(
        instant = Instant.ofEpochMilli(appliedAtEpochMillis),
        storedTimeZoneId = appliedAtTimeZoneId,
        deviceZone = zoneId,
    )
    return medicationDay(dateTime, dayStartMinutes)
}

internal fun MedicationLogEntry.planCalendarDate(zoneId: ZoneId, dayStartMinutes: Int = 0): LocalDate {
    return planCalendarDate(
        scheduledForIso = scheduledFor?.toString(),
        appliedAtEpochMillis = appliedAt.toEpochMilli(),
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        zoneId = zoneId,
        dayStartMinutes = dayStartMinutes,
    )
}

internal fun List<MedicationGroup>.scheduledGroupsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    dayStartMinutes: Int = 0,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationGroup> {
    return filter { group ->
        val hasScheduledSlots = if (dayStartMinutes == 0) {
            group.schedule.isScheduledOn(date)
        } else {
            group.scheduledSlotsInPlanWindow(date, zoneId, dayStartMinutes).isNotEmpty()
        }
        hasScheduledSlots ||
                entries.any { entry ->
                    entry.sourceGroupUuid == group.uuid &&
                            entry.scheduledFor?.let { medicationDay(it, dayStartMinutes) } == date &&
                            group.hasMedicationSignatureFor(entry)
                }
    }
}

internal fun isPlanOffPlanEntry(
    entry: MedicationLogEntry,
    scheduledGroups: List<MedicationGroup>,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    dayStartMinutes: Int = 0,
): Boolean {
    val sourceGroupUuid = entry.sourceGroupUuid ?: return true
    val scheduledFor = entry.scheduledFor ?: return true
    if (medicationDay(scheduledFor, dayStartMinutes) != date) {
        return true
    }

    val group =
        scheduledGroups.firstOrNull { scheduledGroup -> scheduledGroup.uuid == sourceGroupUuid }
            ?: return true
    return !group.hasMedicationSignatureFor(entry)
}

internal fun MedicationGroup.scheduledTimesInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    dayStartMinutes: Int = 0,
): List<LocalTime> {
    return scheduledSlotsInPlanWindow(date, zoneId, dayStartMinutes).map(MedicationGroupSlotKey::time)
}

internal fun MedicationGroup.scheduledTimesForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
    dayStartMinutes: Int = 0,
): List<LocalTime> {
    return scheduledSlotsForPlanDay(
        date = date,
        entries = entries,
        zoneId = zoneId,
        includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
        unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
        dayStartMinutes = dayStartMinutes,
    ).map(MedicationGroupSlotKey::time)
}

internal fun MedicationGroup.scheduledSlotsInPlanWindow(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    dayStartMinutes: Int = 0,
): List<MedicationGroupSlotKey> {
    val endDate = if (dayStartMinutes == 0) date else date.plusDays(1)
    return occurrencesBetweenInPlanWindow(date, endDate, zoneId)
        .filter { occurrence -> medicationDay(occurrence.scheduledFor, dayStartMinutes) == date }
        .map { occurrence -> occurrence.toMedicationGroupSlotKey() }
}

internal fun MedicationGroup.scheduledSlotsForPlanDay(
    date: LocalDate,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
    dayStartMinutes: Int = 0,
): List<MedicationGroupSlotKey> {
    val visibleSlots = scheduledSlotsInPlanWindow(date, zoneId, dayStartMinutes).filter { slot ->
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
            medicationDay(scheduledFor, dayStartMinutes) == date &&
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
