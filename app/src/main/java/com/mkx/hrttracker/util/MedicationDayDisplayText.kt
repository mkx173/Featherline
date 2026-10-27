package com.mkx.hrttracker.util

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.mkx.hrttracker.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/** The 04:00 switch for display code, provided from settings by MainActivity. */
val LocalMedicationDayBoundary = staticCompositionLocalOf { MedicationDayBoundary.Midnight }

/**
 * Formats [dateTime]'s time and, when it falls on another date than [dayDate],
 * appends the existing signed day offset: `02:00 (+1)`.
 */
fun medicationDayTimeText(
    context: Context,
    dateTime: LocalDateTime,
    dayDate: LocalDate,
    timeFormatter: DateTimeFormatter,
): String {
    val time = dateTime.format(timeFormatter)
    val dayOffset = ChronoUnit.DAYS.between(dayDate, dateTime.toLocalDate())
    if (dayOffset == 0L) return time
    val offsetText = context.getString(
        if (dayOffset > 0) {
            R.string.plan_schedule_entry_logged_day_offset_later
        } else {
            R.string.plan_schedule_entry_logged_day_offset_earlier
        },
        dayOffset.absoluteValue.toInt(),
    )
    return "$time ($offsetText)"
}

/**
 * A dose time shown with a date: the date is the medication day that owns it and the
 * time carries the offset, so with the day ending at 04:00, Oct 20 01:00 reads
 * `Oct 19` + `01:00 (+1)`.
 */
fun medicationDayDateAndTimeText(
    context: Context,
    dateTime: LocalDateTime,
    boundary: MedicationDayBoundary,
    timeFormatter: DateTimeFormatter,
): Pair<LocalDate, String> {
    val day = medicationDay(dateTime, boundary)
    return day to medicationDayTimeText(context, dateTime, day, timeFormatter)
}
