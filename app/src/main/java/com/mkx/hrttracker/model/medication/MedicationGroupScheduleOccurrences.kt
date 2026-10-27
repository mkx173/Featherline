package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationDayStart
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class MedicationGroupSlotOccurrence(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val scheduledFor: LocalDateTime,
)

fun MedicationGroupSchedule.isScheduledOn(
    date: LocalDate,
    boundary: MedicationDayBoundary,
): Boolean = isScheduledAt(medicationDayStart(date, boundary), boundary)

/**
 * Whether the 04:00 switch moves this plan's doses: its times before 04:00 belong to
 * the night before, so a plan that skips days fires them on another calendar day.
 */
val MedicationGroupSchedule.movesWithMedicationDayBoundary: Boolean
    get() = (type == MedicationGroupScheduleType.WEEKLY || interval > 1) &&
        times.any { time -> time.isBefore(MedicationDayBoundary.FourAm.startTime) }

/**
 * Whether an occurrence at [dateTime] fires under [boundary]. Both cadences count
 * medication days: with the day ending at 04:00, a Monday 01:00 weekly slot fires
 * at 01:00 on calendar Tuesday, the early hours of medication day Monday.
 *
 * [allowBeforeSince] backs batch-add's backfill before the plan's start; the regular
 * plan path requires the occurrence's medication day to be on or after `since`.
 */
fun MedicationGroupSchedule.isScheduledAt(
    dateTime: LocalDateTime,
    boundary: MedicationDayBoundary,
    allowBeforeSince: Boolean = false,
): Boolean {
    val normalizedInterval = interval.coerceAtLeast(1)
    val day = medicationDay(dateTime, boundary)
    if (!allowBeforeSince && day.isBefore(since)) {
        return false
    }

    return when (type) {
        MedicationGroupScheduleType.DAILY -> Math.floorMod(
            ChronoUnit.DAYS.between(since, day),
            normalizedInterval.toLong(),
        ) == 0L

        MedicationGroupScheduleType.WEEKLY -> {
            if (day.dayOfWeek !in weeklyDaysOfWeek) {
                return false
            }
            // floorDiv (not `/`) keeps parity around `since` for backfilled dates:
            // JVM `/` truncates toward zero, which would lump the 6 days before
            // `since` into week 0.
            val weekIndex = Math.floorDiv(ChronoUnit.DAYS.between(since, day), 7L)
            weekIndex % normalizedInterval.toLong() == 0L
        }
    }
}

fun MedicationGroupSchedule.occurrencesBetween(
    startDate: LocalDate,
    endDate: LocalDate,
    boundary: MedicationDayBoundary,
): List<LocalDateTime> {
    if (startDate.isAfter(endDate) || times.isEmpty()) {
        return emptyList()
    }

    val sortedTimes = times.sorted()
    val result = mutableListOf<LocalDateTime>()
    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        sortedTimes.forEach { time ->
            val occurrence = LocalDateTime.of(currentDate, time)
            if (isScheduledAt(occurrence, boundary)) {
                result += occurrence
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    return result
}

fun MedicationGroupSchedule.nextOccurrencesFrom(
    start: LocalDateTime,
    limit: Int,
    lookaheadDays: Long = 90L,
    boundary: MedicationDayBoundary,
): List<LocalDateTime> {
    if (limit <= 0 || times.isEmpty()) {
        return emptyList()
    }

    val sortedTimes = times.sorted()
    val maxDate = start.toLocalDate().plusDays(lookaheadDays)
    val result = mutableListOf<LocalDateTime>()
    var currentDate = start.toLocalDate()
    while (!currentDate.isAfter(maxDate) && result.size < limit) {
        for (time in sortedTimes) {
            val occurrence = LocalDateTime.of(currentDate, time)
            if (!occurrence.isBefore(start) && isScheduledAt(occurrence, boundary)) {
                result += occurrence
                if (result.size >= limit) break
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    return result
}

fun MedicationGroup.occurrencesBetweenInPlanWindow(
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    boundary: MedicationDayBoundary,
): List<MedicationGroupSlotOccurrence> {
    if (startDate.isAfter(endDate) || schedule.timeSlots.isEmpty()) {
        return emptyList()
    }

    val sortedTimeSlots = schedule.timeSlots.sortedBy { slot -> slot.time }
    val result = mutableListOf<MedicationGroupSlotOccurrence>()
    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        sortedTimeSlots.forEach { slot ->
            val occurrence = LocalDateTime.of(currentDate, slot.time)
            if (schedule.isScheduledAt(occurrence, boundary) &&
                ownsUnloggedOccurrence(slot, occurrence)
            ) {
                result += MedicationGroupSlotOccurrence(
                    groupUuid = uuid,
                    scheduleTimeUuid = slot.uuid,
                    scheduledFor = occurrence,
                )
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    return result
}

fun MedicationGroup.nextOccurrencesInPlanWindowFrom(
    start: LocalDateTime,
    limit: Int,
    lookaheadDays: Long = 90L,
    zoneId: ZoneId = ZoneId.systemDefault(),
    boundary: MedicationDayBoundary,
): List<MedicationGroupSlotOccurrence> {
    if (limit <= 0 || schedule.timeSlots.isEmpty()) {
        return emptyList()
    }

    return occurrencesBetweenInPlanWindow(
        startDate = start.toLocalDate(),
        endDate = start.toLocalDate().plusDays(lookaheadDays),
        zoneId = zoneId,
        boundary = boundary,
    )
        .asSequence()
        .filter { occurrence -> !occurrence.scheduledFor.isBefore(start) }
        .take(limit)
        .toList()
}

fun MedicationGroup.ownsUnloggedOccurrence(
    scheduleTime: MedicationGroupScheduleTime,
    scheduledFor: LocalDateTime,
): Boolean {
    if (scheduledFor.isBefore(scheduleTime.effectiveFrom)) {
        return false
    }

    val archivedAt = archivedAtLocal ?: return true
    return scheduledFor.isBefore(archivedAt)
}
