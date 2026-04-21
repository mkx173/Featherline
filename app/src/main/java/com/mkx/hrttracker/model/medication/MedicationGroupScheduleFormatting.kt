package com.mkx.hrttracker.model.medication

import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun MedicationGroupSchedule.formatSummary(
    locale: Locale,
    timeFormatter: DateTimeFormatter,
    dailyLabel: String,
    weeklyLabel: String
): String {
    val formattedTimes = times.sorted().joinToString(separator = ", ") { time ->
        time.format(timeFormatter)
    }

    return when (type) {
        MedicationGroupScheduleType.WEEKLY -> {
            val dayLabel = weeklyDaysOfWeek
                .sortedBy { it.value }
                .joinToString(separator = ", ") { dayOfWeek ->
                    dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                }
            "$weeklyLabel $interval • $dayLabel • $formattedTimes"
        }
        MedicationGroupScheduleType.DAILY -> {
            "$dailyLabel $interval • $formattedTimes"
        }
    }
}
