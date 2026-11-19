package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.isPastMidnight
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationDayStart
import com.mkx.hrttracker.util.timeOfDayOrder
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

fun MedicationGroupSchedule.isScheduledOn(date: LocalDate): Boolean =
    isScheduledAt(medicationDayStart(date, dayBoundary))

/**
 * Whether an occurrence at [dateTime] fires under this plan's [dayBoundary]. Both
 * cadences count medication days: with the day ending at 04:00, a Monday 01:00 weekly slot fires
 * at 01:00 on calendar Tuesday, the early hours of medication day Monday.
 *
 * [allowBeforeSince] backs batch-add's backfill before the plan's start; the regular
 * plan path requires the occurrence's medication day to be on or after `since`.
 */
fun MedicationGroupSchedule.isScheduledAt(
    dateTime: LocalDateTime,
    allowBeforeSince: Boolean = false,
): Boolean {
    val normalizedInterval = interval.coerceAtLeast(1)
    val day = medicationDay(dateTime, dayBoundary)
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
            if (isScheduledAt(occurrence)) {
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
            if (!occurrence.isBefore(start) && isScheduledAt(occurrence)) {
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
            if (schedule.isScheduledAt(occurrence) &&
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
): List<MedicationGroupSlotOccurrence> {
    if (limit <= 0 || schedule.timeSlots.isEmpty()) {
        return emptyList()
    }

    return occurrencesBetweenInPlanWindow(
        startDate = start.toLocalDate(),
        endDate = start.toLocalDate().plusDays(lookaheadDays),
        zoneId = zoneId,
    )
        .asSequence()
        .filter { occurrence -> !occurrence.scheduledFor.isBefore(start) }
        .take(limit)
        .toList()
}

/**
 * Every occurrence from [start] on, in order, with no horizon: it goes from one dosing
 * medication day straight to the next, however far apart, as for a 13-week depot or a plan
 * that starts next year. It ends at the archive cutoff, so an active plan's never ends.
 */
fun MedicationGroup.occurrencesInPlanWindowFrom(
    start: LocalDateTime,
): Sequence<MedicationGroupSlotOccurrence> {
    val boundary = schedule.dayBoundary
    // In the medication day's own order: with the day ending at 04:00, 01:00 is its last.
    val slots = schedule.timeSlots.sortedBy { slot -> boundary.timeOfDayOrder(slot.time) }
    if (slots.isEmpty()) {
        return emptySequence()
    }
    val firstDay = maxOf(
        medicationDay(start, boundary),
        medicationDay(slots.minOf(MedicationGroupScheduleTime::effectiveFrom), boundary),
    )
    val archivedAt = archivedAtLocal
    return schedule.dosingDaysFrom(firstDay)
        .flatMap { day ->
            slots.asSequence().map { slot ->
                val date = if (boundary.isPastMidnight(slot.time)) day.plusDays(1) else day
                slot to LocalDateTime.of(date, slot.time)
            }
        }
        .takeWhile { (_, scheduledFor) -> archivedAt == null || scheduledFor.isBefore(archivedAt) }
        .filter { (slot, scheduledFor) ->
            !scheduledFor.isBefore(start) && ownsUnloggedOccurrence(slot, scheduledFor)
        }
        .map { (slot, scheduledFor) ->
            MedicationGroupSlotOccurrence(
                groupUuid = uuid,
                scheduleTimeUuid = slot.uuid,
                scheduledFor = scheduledFor,
            )
        }
}

/** The medication days from [date] on that [isScheduledOn] holds, stepped to by the cadence. */
private fun MedicationGroupSchedule.dosingDaysFrom(date: LocalDate): Sequence<LocalDate> {
    val from = maxOf(date, since)
    val period = interval.coerceAtLeast(1).toLong()
    val elapsed = ChronoUnit.DAYS.between(since, from)
    return when (type) {
        MedicationGroupScheduleType.DAILY ->
            generateSequence(Math.floorDiv(elapsed + period - 1, period)) { cycle -> cycle + 1 }
                .map { cycle -> since.plusDays(cycle * period) }

        // Each dosing week holds every weekday, so a plan with any day finds one in each.
        MedicationGroupScheduleType.WEEKLY -> if (weeklyDaysOfWeek.isEmpty()) {
            emptySequence()
        } else {
            generateSequence(Math.floorDiv(elapsed, 7 * period)) { cycle -> cycle + 1 }
                .flatMap { cycle ->
                    (0L until 7L).asSequence().map { offset -> since.plusDays(cycle * 7 * period + offset) }
                }
                .filter { day -> !day.isBefore(from) && day.dayOfWeek in weeklyDaysOfWeek }
        }
    }
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
