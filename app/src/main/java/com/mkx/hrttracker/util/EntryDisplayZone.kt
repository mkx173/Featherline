package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun displayZoneOf(
    storedTimeZoneId: String,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): ZoneId {
    return runCatching { ZoneId.of(storedTimeZoneId) }.getOrDefault(deviceZone)
}

fun displayZoneOf(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): ZoneId = displayZoneOf(entry.appliedAtTimeZoneId, deviceZone)

fun isCrossZone(
    instant: Instant,
    storedTimeZoneId: String,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val displayZone = displayZoneOf(storedTimeZoneId, deviceZone)
    val displayOffset = displayZone.rules.getOffset(instant)
    val deviceOffset = deviceZone.rules.getOffset(instant)
    return displayOffset != deviceOffset
}

fun isCrossZone(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): Boolean = isCrossZone(entry.appliedAt, entry.appliedAtTimeZoneId, deviceZone)

fun atStoredZone(
    instant: Instant,
    storedTimeZoneId: String,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): LocalDateTime {
    return instant.atZone(displayZoneOf(storedTimeZoneId, deviceZone)).toLocalDateTime()
}

fun appliedAtAsLocalDateTime(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): LocalDateTime = atStoredZone(entry.appliedAt, entry.appliedAtTimeZoneId, deviceZone)

fun formatEntryWallTime(
    entry: MedicationLogEntry,
    formatter: DateTimeFormatter,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): String {
    return appliedAtAsLocalDateTime(entry, deviceZone).format(formatter)
}

fun zoneDisplayName(
    zoneId: ZoneId,
    locale: Locale = Locale.getDefault(),
): String {
    val longName = zoneId.getDisplayName(TextStyle.FULL, locale)
    return if (longName != zoneId.id) longName else zoneId.id
}

fun formatEditorZoneLabel(
    appliedZoneId: ZoneId,
    appliedAtInstant: Instant,
    deviceZone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    val pickerOffset = appliedZoneId.rules.getOffset(appliedAtInstant)
    val deviceOffset = deviceZone.rules.getOffset(appliedAtInstant)
    if (pickerOffset == deviceOffset) return null
    return "${zoneDisplayName(appliedZoneId, locale)} · ${formatUtcOffset(pickerOffset)}"
}

private fun formatUtcOffset(offset: ZoneOffset): String {
    val total = offset.totalSeconds
    val sign = if (total >= 0) "+" else "-"
    val abs = kotlin.math.abs(total)
    val hours = abs / 3600
    val minutes = (abs % 3600) / 60
    return if (minutes == 0) {
        "UTC$sign$hours"
    } else {
        "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
    }
}
