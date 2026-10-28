package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.isPastMidnight
import com.mkx.hrttracker.util.timeOfDayOrder
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
    boundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
    /** Put before each time that counts toward the night before, for the caller to draw as the moon. */
    pastMidnightMark: Char? = null,
): String {
    // With the day ending at 04:00, dawn times read last, after the evening ones.
    val orderedTimes = if (boundary.endsAtFourAm) times.sortedBy(boundary::timeOfDayOrder) else times
    val formattedTimes = orderedTimes.map { time ->
        val text = time.format(timeFormatter)
        if (pastMidnightMark != null && boundary.isPastMidnight(time)) "$pastMidnightMark $text" else text
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
