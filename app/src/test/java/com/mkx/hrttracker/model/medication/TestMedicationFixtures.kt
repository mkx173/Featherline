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
    customDoseUnit: MedicationDoseUnit = MedicationDoseUnit.MG,
): MedicationDetails {
    return MedicationDetails(
        category = category,
        applicationType = applicationType,
        selection = MedicationSelection.Custom(medicationName),
        dose = dose,
        customDoseUnit = customDoseUnit,
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
    sourceGroupUuid: UUID?,
    appliedAt: Instant,
    appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    scheduledFor: LocalDateTime? = null,
    count: Int = 1,
): MedicationLogEntry {
    return MedicationLogEntry(
        uuid = uuid,
        details = details,
        dosageMgAsEstradiol = dosageMgAsEstradiol,
        sourceGroupUuid = sourceGroupUuid,
        appliedAt = appliedAt,
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        scheduledFor = scheduledFor,
        count = count
    )
}

fun testInstant(dateTime: LocalDateTime): Instant {
    return dateTime.atZone(ZoneId.systemDefault()).toInstant()
}
