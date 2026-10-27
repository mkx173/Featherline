package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.medicationDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
)

data class MedicationDaySections(
    val today: LocalDate,
    val lastNight: PlanDaySchedule?,
    val todaySchedule: PlanDaySchedule,
    val comingUp: PlanDaySchedule?,
)

/**
 * Home's section model shared with the widget:
 * - last night: the previous medication day, only until noon, and only rows the
 *   boundary counts as last night;
 * - today: the medication day containing [now], in time order;
 * - coming up: the next medication day's Midnight rows, only when the boundary
 *   shows a separate Tonight block.
 */
fun buildMedicationDaySections(
    source: MedicationDaySectionSource,
    now: LocalDateTime,
    boundary: MedicationDayBoundary,
    zoneId: ZoneId = ZoneId.systemDefault(),
    comingUpSource: MedicationDaySectionSource = source,
): MedicationDaySections {
    val today = medicationDay(now, boundary)
    val todaySchedule = source.scheduleFor(date = today, now = now, boundary = boundary, zoneId = zoneId)
    val lastNight = if (boundary.isLastNightVisible(now)) {
        source.scheduleFor(
            date = boundary.lastNightMedicationDay(now),
            now = now,
            boundary = boundary,
            zoneId = zoneId,
        ).filteredToLastNight(boundary = boundary, zoneId = zoneId)
    } else {
        null
    }
    val comingUp = if (boundary.isTonightVisible(now)) {
        comingUpSource.scheduleFor(
            date = boundary.tonightMedicationDay(now),
            now = now,
            boundary = boundary,
            zoneId = zoneId,
        ).filteredToMidnight(zoneId = zoneId)
    } else {
        null
    }

    return MedicationDaySections(
        today = today,
        lastNight = lastNight,
        todaySchedule = todaySchedule,
        comingUp = comingUp,
    )
}

private fun MedicationDaySectionSource.scheduleFor(
    date: LocalDate,
    now: LocalDateTime,
    boundary: MedicationDayBoundary,
    zoneId: ZoneId,
): PlanDaySchedule = buildPlanDaySchedule(
    date = date,
    boundary = boundary,
    groups = groups,
    entries = entries,
    now = now,
    zoneId = zoneId,
    includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
    unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
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

private fun PlanDaySchedule.filteredToMidnight(zoneId: ZoneId): PlanDaySchedule = PlanDaySchedule(
    date = date,
    scheduledEntries = scheduledEntries.filter { entry ->
        entry.scheduledFor.isMidnightRange()
    },
    unplannedEntries = unplannedEntries.filter { entry ->
        appliedAtAsLocalDateTime(entry, zoneId).isMidnightRange()
    },
)

private fun LocalDateTime.isMidnightRange(): Boolean =
    MedicationDayTimeRange.of(toLocalTime()) == MedicationDayTimeRange.MIDNIGHT
