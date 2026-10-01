package com.mkx.hrttracker.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Calendar date owning a medication timestamp, with a configurable local day boundary. */
fun medicationDay(dateTime: LocalDateTime, dayStartMinutes: Int = 0): LocalDate {
    return if (dateTime.toLocalTime().isBefore(medicationDayStartTime(dayStartMinutes))) {
        dateTime.toLocalDate().minusDays(1)
    } else {
        dateTime.toLocalDate()
    }
}

fun medicationDayStartTime(dayStartMinutes: Int): LocalTime {
    require(dayStartMinutes in 0..1439)
    return LocalTime.of(dayStartMinutes / 60, dayStartMinutes % 60)
}

fun medicationDayStart(date: LocalDate, dayStartMinutes: Int = 0): LocalDateTime =
    date.atTime(medicationDayStartTime(dayStartMinutes))

fun medicationDayEnd(date: LocalDate, dayStartMinutes: Int = 0): LocalDateTime =
    medicationDayStart(date.plusDays(1), dayStartMinutes)
