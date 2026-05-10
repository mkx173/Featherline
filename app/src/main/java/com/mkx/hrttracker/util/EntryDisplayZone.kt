package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.MedicationLogEntry
import java.time.ZoneId

fun displayZoneOf(
    entry: MedicationLogEntry,
    deviceZone: ZoneId = ZoneId.systemDefault(),
): ZoneId {
    return runCatching { ZoneId.of(entry.appliedAtTimeZoneId) }.getOrDefault(deviceZone)
}
