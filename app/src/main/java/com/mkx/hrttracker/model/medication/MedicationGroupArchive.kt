package com.mkx.hrttracker.model.medication

import java.util.UUID

fun MedicationGroup.isArchived(): Boolean = archivedAt != null

fun MedicationGroup.isActive(): Boolean = archivedAt == null

fun visibleMedicationEntries(
    entries: List<MedicationLogEntry>,
    groups: List<MedicationGroup>,
    showArchivedGroupRecords: Boolean,
): List<MedicationLogEntry> {
    if (showArchivedGroupRecords) {
        return entries
    }

    val archivedGroupUuids = groups
        .filter(MedicationGroup::isArchived)
        .mapTo(mutableSetOf<UUID>()) { group -> group.uuid }
    if (archivedGroupUuids.isEmpty()) {
        return entries
    }

    return entries.filter { entry ->
        entry.sourceGroupUuid !in archivedGroupUuids
    }
}
