package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.displayZoneOf
import com.mkx.hrttracker.util.isCrossZone
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class PlanDayScheduleEntry(
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val groupCreatedAt: Instant = Instant.EPOCH,
    val scheduleTimeUuid: UUID? = null,
    val scheduledTime: LocalTime,
    val scheduledFor: LocalDateTime = LocalDateTime.of(LocalDate.now(), scheduledTime),
    val medication: MedicationGroupMedication,
    val medicationSortOrder: Int = 0,
    val fulfillingEntryUuids: List<UUID>,
    // Actual administered-amount delta of the last fulfilling log, so fulfilled
    // schedule rows can render the actual dose rather than the planned one.
    val doseAmountDelta: Double? = null,
    val outsideScheduleWindowEntryUuids: List<UUID> = emptyList(),
    val loggedAt: LocalDateTime? = null,
    val isLastFulfillingEntryCrossZone: Boolean = false,
    val isImportedRecord: Boolean = false,
    val outsideScheduleWindowLoggedAt: LocalDateTime? = null,
    /** [scheduleOffsetMinutes] to the last fulfilling log, else the last one outside the window. */
    val loggedOffsetMinutes: Long? = null,
    val loggedCount: Int = 0,
    val isFulfilled: Boolean,
    val isDueSoon: Boolean,
    val isPastDue: Boolean,
    /** The row's own plan's day ending: its moon and day label follow it. */
    val dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
) {
    val hasOutsideScheduleWindowEntry: Boolean
        get() = outsideScheduleWindowEntryUuids.isNotEmpty()
}

data class PlanDaySchedule(
    val date: LocalDate,
    val scheduledEntries: List<PlanDayScheduleEntry>,
    val unplannedEntries: List<MedicationLogEntry>,
)

fun buildPlanDaySchedule(
    date: LocalDate,
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime = LocalDateTime.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
    /** Every plan a record can come from, archived ones too; [groups] may be filtered. */
    groupsByUuid: Map<UUID, MedicationGroup>,
    /** The clock's instant: which pass of a fall-back hour [now] is on. */
    clockNow: Instant = Instant.now(),
): PlanDaySchedule {
    // Each record sits on the day its own plan's ending gives it; one not from a plan on its calendar day.
    val dayEntries = entries.filter { entry ->
        entry.planCalendarDate(entry.dayBoundary(groupsByUuid), zoneId) == date
    }
    val scheduledGroups = groups.scheduledGroupsForPlanDay(
        date = date,
        entries = dayEntries,
        zoneId = zoneId,
    )
    val scheduledEntries = scheduledGroups
        .flatMap { group ->
            val medicationsBySignature = group.medications
                .withIndex()
                .groupBy { indexedMedication ->
                    MedicationSignature.fromGroupMedication(indexedMedication.value)
                }
            group.scheduledSlotsForPlanDay(
                date = date,
                entries = entries,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
                unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
            ).flatMap { slot ->
                val slotDateTime = slot.scheduledFor
                val matchingSlotLogs = entries.filter { entry ->
                    isEntryForPlanSlot(
                        group = group,
                        slot = slot,
                        entry = entry,
                    )
                }
                val slotLogs = matchingSlotLogs.filter { entry ->
                    isEntryFulfillingPlanSlot(
                        group = group,
                        slot = slot,
                        entry = entry,
                        zoneId = zoneId,
                    )
                }
                val logsBySignature = slotLogs.groupBy(MedicationSignature::fromLogEntry)
                val outsideWindowLogsBySignature = matchingSlotLogs
                    .filter { entry ->
                        !isEntryWithinScheduleFulfillmentWindow(
                            group = group,
                            entry = entry,
                            zoneId = zoneId,
                        )
                    }
                    .groupBy(MedicationSignature::fromLogEntry)
                val isDueSoonSlot = isDueSoon(slotDateTime, now, zoneId, clockNow)

                medicationsBySignature.map { (signature, indexedMedicationsForSignature) ->
                    val requiredCount = indexedMedicationsForSignature.sumOf { indexedMedication ->
                        indexedMedication.value.count
                    }
                    val medicationSortOrder =
                        indexedMedicationsForSignature.minOf { indexedMedication ->
                            indexedMedication.index
                        }
                    val matchingLogs = logsBySignature[signature].orEmpty()
                        .sortedBy(MedicationLogEntry::appliedAt)
                    val outsideWindowLogs = outsideWindowLogsBySignature[signature].orEmpty()
                        .sortedBy(MedicationLogEntry::appliedAt)
                    val loggedCount = matchingLogs.recordedCount()
                    val isFulfilled = loggedCount >= requiredCount
                    val lastFulfillingEntry = matchingLogs.lastOrNull()
                    val lastOutsideWindowEntry = outsideWindowLogs.lastOrNull()
                    PlanDayScheduleEntry(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        groupColorKey = group.colorKey,
                        groupCreatedAt = group.createdAt,
                        scheduleTimeUuid = slot.scheduleTimeUuid,
                        scheduledFor = slotDateTime,
                        scheduledTime = slot.time,
                        medication = indexedMedicationsForSignature.first().value.copy(count = requiredCount),
                        medicationSortOrder = medicationSortOrder,
                        fulfillingEntryUuids = matchingLogs.map { it.uuid },
                        doseAmountDelta = lastFulfillingEntry?.doseAmountDelta,
                        outsideScheduleWindowEntryUuids = outsideWindowLogs.map { it.uuid },
                        loggedAt = lastFulfillingEntry?.let {
                            appliedAtAsLocalDateTime(
                                it,
                                zoneId
                            )
                        },
                        isLastFulfillingEntryCrossZone = lastFulfillingEntry?.let {
                            isCrossZone(
                                it,
                                zoneId
                            )
                        } == true,
                        isImportedRecord = (matchingLogs + outsideWindowLogs)
                            .any { entry -> entry.importSourceApp != null },
                        outsideScheduleWindowLoggedAt = lastOutsideWindowEntry?.let {
                            appliedAtAsLocalDateTime(
                                it,
                                zoneId
                            )
                        },
                        loggedOffsetMinutes = (lastFulfillingEntry ?: lastOutsideWindowEntry)
                            ?.let { entry -> scheduleOffsetMinutes(slotDateTime, entry, zoneId) },
                        loggedCount = loggedCount,
                        isFulfilled = isFulfilled,
                        isDueSoon = !isFulfilled && isDueSoonSlot,
                        isPastDue = !isFulfilled && isPastDue(slotDateTime, now, zoneId, clockNow),
                        dayBoundary = group.schedule.dayBoundary,
                    )
                }
            }
        }
        .sortedWith(planDayScheduleEntryComparator)

    val unplannedEntries = dayEntries
        .filter { entry ->
            isPlanOffPlanEntry(
                entry = entry,
                scheduledGroups = scheduledGroups,
                date = date,
                zoneId = zoneId,
            )
        }
        .sortedBy { it.appliedAt }

    return PlanDaySchedule(
        date = date,
        scheduledEntries = scheduledEntries,
        unplannedEntries = unplannedEntries
    )
}

internal val planDayScheduleEntryComparator: Comparator<PlanDayScheduleEntry> =
    compareBy<PlanDayScheduleEntry> { entry -> entry.scheduledFor }
        .thenBy { entry -> entry.groupCreatedAt }
        .thenBy { entry -> entry.medicationSortOrder }

// Between instants: in a fall-back hour the wall clock repeats, and a status
// counted on it would go back. [clockNow] tells which pass [now] is on.
internal fun isDueSoon(
    scheduledAt: LocalDateTime,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    clockNow: Instant = Instant.now(),
): Boolean {
    val elapsed = Duration.between(scheduledAt.atZone(zoneId).toInstant(), now.instantAsOf(clockNow, zoneId))
    return elapsed.abs() < planDueSoonGracePeriod
}

internal fun isPastDue(
    scheduledAt: LocalDateTime,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    clockNow: Instant = Instant.now(),
): Boolean {
    val pastDueAt = scheduledAt.atZone(zoneId).toInstant().plus(planDueSoonGracePeriod)
    return !now.instantAsOf(clockNow, zoneId).isBefore(pastDueAt)
}

/**
 * Real minutes from the slot at [scheduledFor] to a dose at [appliedAt], the slot placed in
 * [zoneId], the zone the dose was logged in, as fulfilment places it. Home's "N h late", the
 * Plan screen's and the log editor's offsets all count with it, so they agree across a clock change.
 */
fun scheduleOffsetMinutes(scheduledFor: LocalDateTime, appliedAt: Instant, zoneId: ZoneId): Long =
    Duration.between(scheduledFor.atZone(zoneId).toInstant(), appliedAt).toMinutes()

/** [scheduleOffsetMinutes] for a logged [entry], in the zone it was logged in. */
fun scheduleOffsetMinutes(scheduledFor: LocalDateTime, entry: MedicationLogEntry, deviceZone: ZoneId): Long =
    scheduleOffsetMinutes(scheduledFor, entry.appliedAt, displayZoneOf(entry, deviceZone))

/** This wall-clock time's instant: in a fall-back hour, on the pass [clockNow] is on. */
internal fun LocalDateTime.instantAsOf(clockNow: Instant, zoneId: ZoneId): Instant =
    ZonedDateTime.ofLocal(this, zoneId, zoneId.rules.getOffset(clockNow)).toInstant()

private val planDueSoonGracePeriod: Duration = Duration.ofHours(1)
