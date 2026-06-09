package com.mkx.hrttracker.model.medication

import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun MedicationGroupSchedule.formatSummary(
    locale: Locale,
    timeFormatter: DateTimeFormatter,
    dailyIntervalLabel: String,
    weeklyIntervalLabel: String,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): String {
    val formattedTimes = times.map { time ->
        time.format(timeFormatter)
    }

    return when (type) {
        MedicationGroupScheduleType.WEEKLY -> {
            val dayLabel = weeklyDaysOfWeek
                .sortedBy { (it.value - firstDayOfWeek.value + DayOfWeek.entries.size) % DayOfWeek.entries.size }
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
