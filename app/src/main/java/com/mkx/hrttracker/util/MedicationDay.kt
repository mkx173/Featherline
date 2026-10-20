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

fun medicationDayStart(date: LocalDate, boundary: MedicationDayBoundary): LocalDateTime =
    date.atTime(boundary.startTime)
