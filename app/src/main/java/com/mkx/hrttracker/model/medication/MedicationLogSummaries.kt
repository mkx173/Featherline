package com.mkx.hrttracker.model.medication

import java.time.Instant

internal fun findLastEstradiolEntry(
    entries: List<MedicationLogEntry>,
    onOrBefore: Instant? = null,
): MedicationLogEntry? {
    return entries.asSequence()
        .filter { entry ->
            entry.category == MedicationCategory.ESTRADIOL &&
                    (onOrBefore == null || !entry.appliedAt.isAfter(onOrBefore))
        }
        .maxByOrNull(MedicationLogEntry::appliedAt)
}

internal fun timeSinceEntryMillis(
    target: Instant,
    entry: MedicationLogEntry?,
): Long? {
    return entry?.let {
        (target.toEpochMilli() - it.appliedAt.toEpochMilli()).coerceAtLeast(0L)
    }
}
