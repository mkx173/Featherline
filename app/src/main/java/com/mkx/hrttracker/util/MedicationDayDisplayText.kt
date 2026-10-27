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
 * Formats [dateTime]'s time and, when its medication day is not [dayDate], appends
 * the signed day offset: `09:00 (+1)`. With the day ending at 04:00, a time between
 * midnight and 04:00 belongs to the night before, so it gets no offset there.
 */
fun medicationDayTimeText(
    context: Context,
    dateTime: LocalDateTime,
    dayDate: LocalDate,
    boundary: MedicationDayBoundary,
    timeFormatter: DateTimeFormatter,
): String {
    val time = dateTime.format(timeFormatter)
    val dayOffset = ChronoUnit.DAYS.between(dayDate, medicationDay(dateTime, boundary))
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
