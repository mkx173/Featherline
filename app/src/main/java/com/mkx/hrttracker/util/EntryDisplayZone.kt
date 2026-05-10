package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone

fun displayZoneOf(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): ZoneId {
    return runCatching { ZoneId.of(entry.appliedAtTimeZoneId) }.getOrDefault(deviceZone)
}

fun isCrossZone(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val displayZone = displayZoneOf(entry, deviceZone)
    val displayOffset = displayZone.rules.getOffset(entry.appliedAt)
    val deviceOffset = deviceZone.rules.getOffset(entry.appliedAt)
    return displayOffset != deviceOffset
}

fun appliedAtAsLocalDateTime(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): LocalDateTime {
    return entry.appliedAt.atZone(displayZoneOf(entry, deviceZone)).toLocalDateTime()
}

fun formatEntryWallTime(
    entry: MedicationLogEntry,
    formatter: DateTimeFormatter,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): String {
    return appliedAtAsLocalDateTime(entry, deviceZone).format(formatter)
}

fun formatZoneLabel(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    if (!isCrossZone(entry, deviceZone)) return null
    val zone = displayZoneOf(entry, deviceZone)
    val offset = zone.rules.getOffset(entry.appliedAt)
    val tz = TimeZone.getTimeZone(zone.id)
    val abbrev = tz.getDisplayName(false, TimeZone.SHORT, locale)
    return listOf(zone.id, abbrev, offset.toString())
        .filter(String::isNotEmpty)
        .joinToString(separator = " · ")
}
