package com.mkx.hrttracker.model.medication

import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun MedicationGroupSchedule.formatSummary(
    locale: Locale,
    timeFormatter: DateTimeFormatter,
    dailyIntervalLabel: String,
    weeklyIntervalLabel: String
): String {
    val formattedTimes = times.map { time ->
        time.format(timeFormatter)
    }

    return when (type) {
        MedicationGroupScheduleType.WEEKLY -> {
            val dayLabel = weeklyDaysOfWeek
                .sortedBy { it.value }
                .joinToString(separator = "/") { dayOfWeek ->
                    dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                }
            buildList {
                add(weeklyIntervalLabel)
                if (dayLabel.isNotBlank()) {
                    add(dayLabel)
                }
                addAll(formattedTimes)
            }.joinToString(" · ")
        }
        MedicationGroupScheduleType.DAILY -> {
            val timesString = formattedTimes.joinToString("/")
            "$dailyIntervalLabel · $timesString"
        }
    }
}
