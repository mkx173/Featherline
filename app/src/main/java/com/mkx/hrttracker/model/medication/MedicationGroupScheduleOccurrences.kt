package com.mkx.hrttracker.model.medication

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun MedicationGroupSchedule.isScheduledOn(date: LocalDate): Boolean {
    val normalizedInterval = interval.coerceAtLeast(1)

    return when (type) {
        MedicationGroupScheduleType.DAILY -> {
            !date.isBefore(since) &&
                ChronoUnit.DAYS.between(since, date) % normalizedInterval.toLong() == 0L
        }
        MedicationGroupScheduleType.WEEKLY -> {
            val scheduledDayOfWeek = weeklyDayOfWeek ?: return false
            val firstScheduledDate = since.with(TemporalAdjusters.nextOrSame(scheduledDayOfWeek))

            !date.isBefore(firstScheduledDate) &&
                date.dayOfWeek == scheduledDayOfWeek &&
                ChronoUnit.WEEKS.between(firstScheduledDate, date) % normalizedInterval.toLong() == 0L
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
