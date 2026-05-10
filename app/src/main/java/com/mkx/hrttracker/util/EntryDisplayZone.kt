package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import java.time.ZoneId

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
