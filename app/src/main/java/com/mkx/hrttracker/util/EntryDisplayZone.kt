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

fun formatEditorZoneLabel(
    appliedZoneId: ZoneId,
    appliedAtInstant: Instant,
    deviceZone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    val pickerOffset = appliedZoneId.rules.getOffset(appliedAtInstant)
    val deviceOffset = deviceZone.rules.getOffset(appliedAtInstant)
    if (pickerOffset == deviceOffset) return null
    val offsetLabel = formatUtcOffset(pickerOffset)
    val longName = appliedZoneId.getDisplayName(TextStyle.FULL, locale)
    val zoneText = if (longName != appliedZoneId.id) longName else appliedZoneId.id
    return "$zoneText · $offsetLabel"
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
