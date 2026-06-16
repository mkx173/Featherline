package com.mkx.hrttracker.model.journal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Signed whole-day distance from [date] to [today]. Past dates count up
 * (`magnitude` days since, `isFuture = false`); future dates count down
 * (`magnitude` days until, `isFuture = true`). `LocalDate` math already floors
 * to whole days against the caller's local calendar, so callers pass the
 * device-local "today" - never a hard-coded date (spec section 3.1).
 */
data class DayCount(val magnitude: Long, val isFuture: Boolean)

fun dayCount(date: LocalDate, today: LocalDate): DayCount {
    val signedDaysSince = ChronoUnit.DAYS.between(date, today)
    return DayCount(
        magnitude = kotlin.math.abs(signedDaysSince),
        isFuture = signedDaysSince < 0L,
    )
}
