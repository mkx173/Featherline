package com.mkx.hrttracker.widget

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

// The since/planned-for line's degradation ladder (spec section 3): longest -> shortest,
// duplicates collapsed (en-US SHORT is already 2-digit, so rungs 3/4 merge there).
internal fun sinceLineCandidates(
    prefixTemplate: String,
    date: LocalDate,
    locale: Locale,
): List<String> {
    val medium = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val shortPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale,
    )
    val short = date.format(DateTimeFormatter.ofPattern(shortPattern, locale))
    val shortTwoDigitYear = date.format(
        DateTimeFormatter.ofPattern(shortPattern.replace(Regex("y+"), "yy"), locale),
    )
    return listOf(
        prefixTemplate.format(medium),
        medium,
        short,
        shortTwoDigitYear,
    ).distinct()
}

// Longest candidate that fits; the last (shortest) rung is the floor.
internal fun fitSinceLine(
    candidates: List<String>,
    maxWidthPx: Float,
    measure: (String) -> Float,
): String = candidates.firstOrNull { measure(it) <= maxWidthPx } ?: candidates.last()
