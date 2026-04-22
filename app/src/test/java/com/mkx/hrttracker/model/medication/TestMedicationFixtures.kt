package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

fun testCatalogMedicationDetails(
    key: MedicationKey,
    applicationType: MedicationApplicationType,
    dose: MedicationDose,
): MedicationDetails {
    return MedicationDetails(
        category = key.category,
        applicationType = applicationType,
        selection = MedicationSelection.Catalog(key),
        dose = dose
    )
}

fun testCustomMedicationDetails(
    medicationName: String,
    dose: MedicationDose,
    applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    category: MedicationCategory = MedicationCategory.CUSTOM,
): MedicationDetails {
    return MedicationDetails(
        category = category,
        applicationType = applicationType,
        selection = MedicationSelection.Custom(medicationName),
        dose = dose
    )
}

fun testMedicationGroupMedication(
    uuid: UUID = UUID.randomUUID(),
    details: MedicationDetails,
    count: Int = 1,
): MedicationGroupMedication {
    return MedicationGroupMedication(
        uuid = uuid,
        details = details,
        count = count
    )
}

fun testMedicationLogEntry(
    uuid: UUID = UUID.randomUUID(),
    details: MedicationDetails,
    dosageMgAsEstradiol: Double? = null,
    sourceType: MedicationLogEntrySourceType,
    sourceGroupUuid: UUID?,
    appliedAt: Instant,
    scheduledFor: LocalDateTime? = null,
): MedicationLogEntry {
    return MedicationLogEntry(
        uuid = uuid,
        details = details,
        dosageMgAsEstradiol = dosageMgAsEstradiol,
        sourceType = sourceType,
        sourceGroupUuid = sourceGroupUuid,
        appliedAt = appliedAt,
        scheduledFor = scheduledFor
    )
}

fun testInstant(dateTime: LocalDateTime): Instant {
    return dateTime.atZone(ZoneId.systemDefault()).toInstant()
}
