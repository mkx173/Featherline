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
