package com.mkx.hrttracker.util

import android.content.Context
import com.mkx.hrttracker.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun medicationDayTimeText(
    context: Context,
    dateTime: LocalDateTime,
    dayDate: LocalDate,
    timeFormatter: DateTimeFormatter,
): String {
    val time = dateTime.format(timeFormatter)
    return when (dateTime.toLocalDate()) {
        dayDate -> time
        dayDate.plusDays(1) -> context.getString(R.string.medication_next_day_time, time)
        else -> "${dateTime.toLocalDate()} $time"
    }
}
