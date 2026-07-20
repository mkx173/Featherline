package com.mkx.hrttracker.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

typealias LocalDateFormatter = (LocalDate) -> String

fun localizedShortTimeFormatter(
    locale: Locale,
    uses24HourFormat: Boolean,
): DateTimeFormatter {
    val pattern = if (uses24HourFormat) {
        "HH:mm"
    } else if (locale.isChineseLanguage()) {
        "ah:mm"
    } else {
        "h:mm a"
    }
    return DateTimeFormatter.ofPattern(pattern, locale)
}

@Composable
fun rememberLocalizedShortTimeFormatter(locale: Locale): DateTimeFormatter {
    val uses24HourFormat = rememberUses24HourTimeFormat()
    return remember(locale, uses24HourFormat) {
        localizedShortTimeFormatter(locale, uses24HourFormat)
    }
}

@Composable
fun rememberUses24HourTimeFormat(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Settings.System.TIME_12_24 is not part of Configuration, so a plain
    // LocalConfiguration-keyed remember would never invalidate when the user
    // toggles the system 24-hour preference. Observe the setting URI for
    // foreground changes, and additionally re-read on ON_RESUME because
    // ContentObserver delivery is not guaranteed while the host is stopped.
    var value by remember(context) { mutableStateOf(context.uses24HourTimeFormat()) }
    DisposableEffect(context, lifecycleOwner) {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = appContext.uses24HourTimeFormat()
            }
        }
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            observer,
        )
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                value = appContext.uses24HourTimeFormat()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return value
}

fun Context.observeUses24HourTimeFormat(): Flow<Boolean> = callbackFlow {
    val appContext = applicationContext
    trySend(appContext.uses24HourTimeFormat())
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(appContext.uses24HourTimeFormat())
        }
    }
    appContext.contentResolver.registerContentObserver(
        Settings.System.getUriFor(Settings.System.TIME_12_24),
        false,
        observer,
    )
    awaitClose { appContext.contentResolver.unregisterContentObserver(observer) }
}.distinctUntilChanged()

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

fun calibrationPanelDateTimeFormatters(
    locale: Locale,
    uses24HourFormat: Boolean,
): CalibrationPanelDateTimeFormatters {
    val monthPattern = if (locale.isChineseLanguage()) {
        "M月"
    } else {
        "MMM"
    }
    return CalibrationPanelDateTimeFormatters(
        monthFormatter = DateTimeFormatter.ofPattern(monthPattern, locale),
        dayFormatter = DateTimeFormatter.ofPattern("d", locale),
        timeFormatter = localizedShortTimeFormatter(locale, uses24HourFormat),
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
    return DateFormat.is24HourFormat(systemLocaleContext())
}

private fun Context.systemLocaleContext(): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocales(Resources.getSystem().configuration.locales)
    }
    return createConfigurationContext(configuration)
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
