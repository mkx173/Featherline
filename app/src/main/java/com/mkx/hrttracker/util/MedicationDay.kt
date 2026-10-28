package com.mkx.hrttracker.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Which local times belong to the previous medication day. The app exposes one
 * switch: either the day ends at midnight, or it ends at 04:00.
 */
enum class MedicationDayBoundary(val startTime: LocalTime) {
    Midnight(LocalTime.MIDNIGHT),
    FourAm(LocalTime.of(4, 0));

    val endsAtFourAm: Boolean
        get() = this == FourAm

    companion object {
        fun fromSwitch(dayEndsAtFourAm: Boolean): MedicationDayBoundary =
            if (dayEndsAtFourAm) FourAm else Midnight
    }
}

/** Calendar date owning a medication timestamp, under [boundary]. */
fun medicationDay(dateTime: LocalDateTime, boundary: MedicationDayBoundary): LocalDate {
    return if (dateTime.toLocalTime().isBefore(boundary.startTime)) {
        dateTime.toLocalDate().minusDays(1)
    } else {
        dateTime.toLocalDate()
    }
}

/** Orders times from the boundary on, so with the day ending at 04:00, 01:00 sorts after 22:00. */
fun MedicationDayBoundary.timeOfDayOrder(time: LocalTime): Int =
    Math.floorMod(time.toSecondOfDay() - startTime.toSecondOfDay(), LocalTime.MAX.toSecondOfDay() + 1)

/** Whether [time] falls in the small hours that count toward the night before; never with the day ending at midnight. */
fun MedicationDayBoundary.isPastMidnight(time: LocalTime): Boolean = time.isBefore(startTime)

/** When [time] falls on medication day [date]: small-hours times are on the next calendar date. */
fun MedicationDayBoundary.dateTimeOn(date: LocalDate, time: LocalTime): LocalDateTime =
    (if (isPastMidnight(time)) date.plusDays(1) else date).atTime(time)

fun medicationDayStart(date: LocalDate, boundary: MedicationDayBoundary): LocalDateTime =
    date.atTime(boundary.startTime)
