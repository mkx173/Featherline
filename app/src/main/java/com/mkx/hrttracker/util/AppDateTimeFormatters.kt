package com.mkx.hrttracker.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

typealias LocalDateFormatter = (LocalDate) -> String

fun localizedShortTimeFormatter(locale: Locale): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
}

fun dateLabelFormatter(
    locale: Locale,
    today: LocalDate
): LocalDateFormatter {
    val currentYearFormatter = currentYearDateFormatter(locale)
    val otherYearFormatter = otherYearDateFormatter(locale)

    return { date ->
        date.format(
            if (date.year == today.year) {
                currentYearFormatter
            } else {
                otherYearFormatter
            }
        )
    }
}

fun dateRangeLabelFormatter(
    locale: Locale,
    today: LocalDate,
    startDate: LocalDate,
    endDate: LocalDate,
): LocalDateFormatter {
    val currentYearFormatter = currentYearDateFormatter(locale)
    val otherYearFormatter = otherYearDateFormatter(locale)
    val shouldShowYear = startDate.year != today.year || endDate.year != today.year

    return { date ->
        date.format(if (shouldShowYear) otherYearFormatter else currentYearFormatter)
    }
}

fun medicationGroupScheduleDateFormatter(
    locale: Locale,
    today: LocalDate
): LocalDateFormatter {
    val dateFormatter = dateLabelFormatter(locale, today)

    return { date ->
        "${dateFormatter(date)} ${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}"
    }
}

fun historyEntryGroupDayFormatter(locale: Locale): DateTimeFormatter {
    return currentYearDateFormatter(locale)
}

fun historyEntryGroupDateFormatter(
    appLocale: Locale,
    today: LocalDate
): LocalDateFormatter {
    return dateLabelFormatter(appLocale, today)
}

fun historyMonthLabelFormatter(locale: Locale): DateTimeFormatter {
    return if (locale.isChineseLanguage()) {
        DateTimeFormatter.ofPattern("M月", locale)
    } else {
        DateTimeFormatter.ofPattern("LLLL", locale)
    }
}

fun calendarMonthTitleFormatter(
    locale: Locale,
    currentYear: Int
): LocalDateFormatter {
    val currentYearFormatter = historyMonthLabelFormatter(locale)
    val otherYearFormatter = if (locale.isChineseLanguage()) {
        DateTimeFormatter.ofPattern("yyyy年M月", locale)
    } else {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }

    return { date ->
        date.format(
            if (date.year == currentYear) {
                currentYearFormatter
            } else {
                otherYearFormatter
            }
        )
    }
}

fun planUpcomingDateFormatter(
    locale: Locale,
    today: LocalDate
): LocalDateFormatter {
    return dateLabelFormatter(locale, today)
}

fun monthHeaderFormatter(
    locale: Locale,
    currentYear: Int
): LocalDateFormatter {
    val currentYearFormatter = historyMonthLabelFormatter(locale)
    val otherYearFormatter = if (locale.isChineseLanguage()) {
        DateTimeFormatter.ofPattern("yyyy年M月", locale)
    } else {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }

    return { date ->
        date.format(
            if (date.year == currentYear) {
                currentYearFormatter
            } else {
                otherYearFormatter
            }
        )
    }
}

fun calibrationMonthHeaderFormatter(
    locale: Locale,
    currentYear: Int
): LocalDateFormatter {
    return monthHeaderFormatter(locale, currentYear)
}

data class CalibrationPanelDateTimeFormatters(
    val monthFormatter: DateTimeFormatter,
    val dayFormatter: DateTimeFormatter,
    val timeFormatter: DateTimeFormatter,
)

data class CalibrationPanelDateTimeLabels(
    val monthLabel: String,
    val dayLabel: String,
    val timeLabel: String,
)

fun calibrationPanelDateTimeFormatters(locale: Locale): CalibrationPanelDateTimeFormatters {
    val monthPattern = if (locale.isChineseLanguage()) {
        "M月"
    } else {
        "MMM"
    }
    return CalibrationPanelDateTimeFormatters(
        monthFormatter = DateTimeFormatter.ofPattern(monthPattern, locale),
        dayFormatter = DateTimeFormatter.ofPattern("d", locale),
        timeFormatter = localizedShortTimeFormatter(locale),
    )
}

fun formatCalibrationPanelDateTimeLabels(
    collectedAt: Instant,
    dateTimeFormatters: CalibrationPanelDateTimeFormatters,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CalibrationPanelDateTimeLabels {
    val collectedAtDateTime = collectedAt.atZone(zoneId)
    return CalibrationPanelDateTimeLabels(
        monthLabel = collectedAtDateTime.format(dateTimeFormatters.monthFormatter),
        dayLabel = collectedAtDateTime.format(dateTimeFormatters.dayFormatter),
        timeLabel = collectedAtDateTime.format(dateTimeFormatters.timeFormatter),
    )
}

fun backupFileNameTimestampFormatter(): DateTimeFormatter {
    return BackupFileNameTimestampFormatter
}

fun Context.uses24HourTimeFormat(): Boolean {
    return DateFormat.is24HourFormat(this)
}

private fun Locale.isChineseLanguage(): Boolean {
    return language == Locale.CHINESE.language
}

private fun currentYearDateFormatter(locale: Locale): DateTimeFormatter {
    return if (locale.isChineseLanguage()) {
        DateTimeFormatter.ofPattern("M月d日", locale)
    } else {
        DateTimeFormatter.ofPattern("MMM d", locale)
    }
}

private fun otherYearDateFormatter(locale: Locale): DateTimeFormatter {
    return if (locale.isChineseLanguage()) {
        DateTimeFormatter.ofPattern("yyyy年M月d日", locale)
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    }
}

private val BackupFileNameTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
