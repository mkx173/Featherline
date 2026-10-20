package com.mkx.hrttracker.util

import android.content.Context
import com.mkx.hrttracker.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

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
    if (dayOffset > 0) {
        val offsetText = context.getString(
            R.string.plan_schedule_entry_logged_day_offset_later,
            dayOffset.absoluteValue.toInt(),
        )
        return "$time ($offsetText)"
    }
    return dateTime.toString()
}
