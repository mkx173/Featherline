package com.mkx.hrttracker.model.medication

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class MedicationGroupSlotOccurrence(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val scheduledFor: LocalDateTime,
)

fun MedicationGroupSchedule.isScheduledOn(date: LocalDate): Boolean {
    val normalizedInterval = interval.coerceAtLeast(1)

    return when (type) {
        MedicationGroupScheduleType.DAILY -> {
            !date.isBefore(since) &&
                ChronoUnit.DAYS.between(since, date) % normalizedInterval.toLong() == 0L
        }
        MedicationGroupScheduleType.WEEKLY -> {
            if (date.isBefore(since) ||
                weeklyDaysOfWeek.isEmpty() ||
                date.dayOfWeek !in weeklyDaysOfWeek
            ) {
                return false
            }
            val sinceWeekStart = since.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val dateWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ChronoUnit.WEEKS.between(sinceWeekStart, dateWeekStart) % normalizedInterval.toLong() == 0L
        }
    }
}

fun MedicationGroupSchedule.occurrencesBetween(
    startDate: LocalDate,
    endDate: LocalDate
): List<LocalDateTime> {
    if (startDate.isAfter(endDate) || times.isEmpty()) {
        return emptyList()
    }

    val sortedTimes = times.sorted()
    val result = mutableListOf<LocalDateTime>()
    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        if (isScheduledOn(currentDate)) {
            sortedTimes.forEach { time ->
                result += LocalDateTime.of(currentDate, time)
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    return result
}

fun MedicationGroupSchedule.nextOccurrencesFrom(
    start: LocalDateTime,
    limit: Int,
    lookaheadDays: Long = 90L
): List<LocalDateTime> {
    if (limit <= 0 || times.isEmpty()) {
        return emptyList()
    }

    val sortedTimes = times.sorted()
    val maxDate = start.toLocalDate().plusDays(lookaheadDays)
    val result = mutableListOf<LocalDateTime>()
    var currentDate = start.toLocalDate()
    while (!currentDate.isAfter(maxDate) && result.size < limit) {
        if (isScheduledOn(currentDate)) {
            for (time in sortedTimes) {
                val occurrence = LocalDateTime.of(currentDate, time)
                if (!occurrence.isBefore(start)) {
                    result += occurrence
                    if (result.size >= limit) break
                }
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
        if (schedule.isScheduledOn(currentDate)) {
            sortedTimeSlots.forEach { slot ->
                val occurrence = LocalDateTime.of(currentDate, slot.time)
                if (ownsUnloggedOccurrence(slot, occurrence, zoneId)) {
                    result += MedicationGroupSlotOccurrence(
                        groupUuid = uuid,
                        scheduleTimeUuid = slot.uuid,
                        scheduledFor = occurrence,
                    )
                }
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

fun MedicationGroup.ownsUnloggedOccurrence(
    scheduleTime: MedicationGroupScheduleTime,
    scheduledFor: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val effectiveFrom = if (includePastScheduledSlots) {
        scheduleTime.effectiveFrom
    } else {
        maxOf(scheduleTime.effectiveFrom, createdAt.atZone(zoneId).toLocalDateTime())
    }
    if (scheduledFor.isBefore(effectiveFrom)) {
        return false
    }

    val archivedAt = archivedAtLocal ?: archivedAt?.atZone(zoneId)?.toLocalDateTime() ?: return true
    return scheduledFor.isBefore(archivedAt)
}
