package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.medicationDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * The plan data a section is built from. Home and the widget pass their own
 * sources (visibility and archived-slot rules differ), but the day and
 * membership rules stay in [buildMedicationDaySections].
 */
data class MedicationDaySectionSource(
    val groups: List<MedicationGroup>,
    val entries: List<MedicationLogEntry>,
    val includeUnloggedArchivedSlots: Boolean = true,
    val unloggedArchivedSlotCutoff: LocalDateTime? = null,
    /**
     * Every plan a record can come from, archived ones too, for the day each
     * record follows. [groups] may leave plans out; this must not.
     */
    val allGroups: List<MedicationGroup> = groups,
)

data class MedicationDaySections(
    val today: LocalDate,
    val lastNight: PlanDaySchedule?,
    val todaySchedule: PlanDaySchedule,
    val comingUp: PlanDaySchedule?,
    /** The day ending [comingUp] was built under: at 04:00 it holds the next morning. */
    val comingUpDayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
)

/**
 * Home's section model shared with the widget. Each plan, with its records, is
 * placed by its own day ending; a record not from a plan follows midnight:
 * - last night: the previous medication day, only until noon, and only rows the
 *   boundary counts as last night;
 * - today: the medication day containing [now], in time order. Between 00:00 and
 *   04:00 a night-owl plan's evening is still its today, so [MedicationDaySections.today]
 *   is the calendar date and each row keeps its own day;
 * - coming up: the next medication day's first bucket, during this day's last; see
 *   isComingUpVisible.
 */
fun buildMedicationDaySections(
    source: MedicationDaySectionSource,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    comingUpSource: MedicationDaySectionSource = source,
): MedicationDaySections {
    // Built once per source: the partitions keep every plan these maps hold.
    val groupsByUuid = source.groupsByUuid()
    val comingUpGroupsByUuid = if (comingUpSource === source) groupsByUuid else comingUpSource.groupsByUuid()
    val midnight = buildSingleEndingDaySections(
        source = source.partitionedTo(MedicationDayBoundary.Midnight, groupsByUuid),
        now = now,
        boundary = MedicationDayBoundary.Midnight,
        zoneId = zoneId,
        comingUpSource = comingUpSource.partitionedTo(MedicationDayBoundary.Midnight, comingUpGroupsByUuid),
        groupsByUuid = groupsByUuid,
        comingUpGroupsByUuid = comingUpGroupsByUuid,
    )
    val nightOwlSource = source.partitionedTo(MedicationDayBoundary.FourAm, groupsByUuid)
    val nightOwlComingUpSource = comingUpSource.partitionedTo(MedicationDayBoundary.FourAm, comingUpGroupsByUuid)
    // With every plan ending at midnight this is the single-boundary result as it was.
    if (nightOwlSource.isEmpty() && nightOwlComingUpSource.isEmpty()) {
        return midnight
    }
    val nightOwl = buildSingleEndingDaySections(
        source = nightOwlSource,
        now = now,
        boundary = MedicationDayBoundary.FourAm,
        zoneId = zoneId,
        comingUpSource = nightOwlComingUpSource,
        groupsByUuid = groupsByUuid,
        comingUpGroupsByUuid = comingUpGroupsByUuid,
    )
    val today = now.toLocalDate()
    // The two Coming up windows (18:00-24:00 and 00:00-04:00) never overlap.
    val comingUp = mergedOrNull(midnight.comingUp, nightOwl.comingUp)
    return MedicationDaySections(
        today = today,
        lastNight = mergedOrNull(midnight.lastNight, nightOwl.lastNight),
        todaySchedule = merged(today, listOf(midnight.todaySchedule, nightOwl.todaySchedule)),
        comingUp = comingUp,
        comingUpDayBoundary = if (nightOwl.comingUp != null) {
            MedicationDayBoundary.FourAm
        } else {
            MedicationDayBoundary.Midnight
        },
    )
}

/** The sections for plans and records that all share [boundary]. */
private fun buildSingleEndingDaySections(
    source: MedicationDaySectionSource,
    now: LocalDateTime,
    boundary: MedicationDayBoundary,
    zoneId: ZoneId,
    comingUpSource: MedicationDaySectionSource,
    groupsByUuid: Map<UUID, MedicationGroup>,
    comingUpGroupsByUuid: Map<UUID, MedicationGroup>,
): MedicationDaySections {
    val today = medicationDay(now, boundary)
    val todaySchedule = source.scheduleFor(date = today, now = now, zoneId = zoneId, groupsByUuid = groupsByUuid)
    val lastNight = if (boundary.isLastNightVisible(now)) {
        source.scheduleFor(
            date = boundary.lastNightMedicationDay(now),
            now = now,
            zoneId = zoneId,
            groupsByUuid = groupsByUuid,
        ).filteredToLastNight(boundary = boundary, zoneId = zoneId)
    } else {
        null
    }
    val comingUp = if (boundary.isComingUpVisible(now)) {
        comingUpSource.scheduleFor(
            date = boundary.comingUpMedicationDay(now),
            now = now,
            zoneId = zoneId,
            groupsByUuid = comingUpGroupsByUuid,
        ).filteredTo(range = boundary.comingUpTimeRange, zoneId = zoneId)
    } else {
        null
    }

    return MedicationDaySections(
        today = today,
        lastNight = lastNight,
        todaySchedule = todaySchedule,
        comingUp = comingUp,
        comingUpDayBoundary = boundary,
    )
}

/** The plans ending at [boundary], with their records; records not from a plan go with midnight. */
private fun MedicationDaySectionSource.partitionedTo(
    boundary: MedicationDayBoundary,
    groupsByUuid: Map<UUID, MedicationGroup>,
): MedicationDaySectionSource {
    return copy(
        groups = groups.filter { group -> group.schedule.dayBoundary == boundary },
        entries = entries.filter { entry -> entry.dayBoundary(groupsByUuid) == boundary },
    )
}

// With [groups] too, a scheduled plan is always found even if a caller's allGroups left it
// out, so partitioning and dating a record agree on its plan's day ending.
private fun MedicationDaySectionSource.groupsByUuid(): Map<UUID, MedicationGroup> =
    (allGroups + groups).associateBy(MedicationGroup::uuid)

private fun MedicationDaySectionSource.isEmpty(): Boolean = groups.isEmpty() && entries.isEmpty()

private fun mergedOrNull(vararg schedules: PlanDaySchedule?): PlanDaySchedule? {
    val present = schedules.filterNotNull()
    return present.firstOrNull()
        ?.let { first -> merged(first.date, present) }
        ?.takeIf { schedule -> schedule.scheduledEntries.isNotEmpty() || schedule.unplannedEntries.isNotEmpty() }
}

private fun merged(date: LocalDate, schedules: List<PlanDaySchedule>): PlanDaySchedule = PlanDaySchedule(
    date = date,
    scheduledEntries = schedules.flatMap(PlanDaySchedule::scheduledEntries)
        .sortedWith(planDayScheduleEntryComparator),
    unplannedEntries = schedules.flatMap(PlanDaySchedule::unplannedEntries)
        .sortedBy(MedicationLogEntry::appliedAt),
)

private fun MedicationDaySectionSource.scheduleFor(
    date: LocalDate,
    now: LocalDateTime,
    zoneId: ZoneId,
    groupsByUuid: Map<UUID, MedicationGroup>,
): PlanDaySchedule = buildPlanDaySchedule(
    date = date,
    groups = groups,
    entries = entries,
    now = now,
    zoneId = zoneId,
    includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
    unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
    groupsByUuid = groupsByUuid,
)

private fun PlanDaySchedule.filteredToLastNight(
    boundary: MedicationDayBoundary,
    zoneId: ZoneId,
): PlanDaySchedule = PlanDaySchedule(
    date = date,
    scheduledEntries = scheduledEntries.filter { entry ->
        boundary.includesInLastNight(entry.scheduledFor)
    },
    unplannedEntries = unplannedEntries.filter { entry ->
        boundary.includesInLastNight(appliedAtAsLocalDateTime(entry, zoneId))
    },
)

private fun PlanDaySchedule.filteredTo(range: MedicationDayTimeRange, zoneId: ZoneId): PlanDaySchedule = PlanDaySchedule(
    date = date,
    scheduledEntries = scheduledEntries.filter { entry ->
        MedicationDayTimeRange.of(entry.scheduledFor.toLocalTime()) == range
    },
    unplannedEntries = unplannedEntries.filter { entry ->
        MedicationDayTimeRange.of(appliedAtAsLocalDateTime(entry, zoneId).toLocalTime()) == range
    },
)
