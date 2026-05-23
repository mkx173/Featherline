package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

fun testMedicine(
    uuid: UUID = UUID.randomUUID(),
    key: MedicationKey = MedicationKey.ESTRADIOL,
    preparation: MedicinePreparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
    displayName: String? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH,
    archivedAt: Instant? = null,
): Medicine {
    return Medicine(
        uuid = uuid,
        selection = MedicineSelection.Catalog(key),
        category = key.category,
        preparation = preparation,
        displayName = displayName,
        identityKey = MedicineIdentityKey.catalog(key, preparation),
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
}

fun testCustomMedicine(
    uuid: UUID = UUID.randomUUID(),
    medicationName: String = "Custom medication",
    category: MedicationCategory = MedicationCategory.CUSTOM,
    preparation: MedicinePreparation = MedicinePreparation.Pill(strengthMgPerTablet = 10.0),
    displayName: String? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH,
    archivedAt: Instant? = null,
): Medicine {
    return Medicine(
        uuid = uuid,
        selection = MedicineSelection.Custom(medicationName),
        category = category,
        preparation = preparation,
        displayName = displayName,
        identityKey = MedicineIdentityKey.custom(medicationName, preparation),
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
}

// The global PATCH_OFF singleton. Distinct UUID so it sorts deterministically
// after the catalog medicines in tests that rely on creation-time ordering.
fun testPatchOffMedicine(
    uuid: UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000000"),
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH,
    archivedAt: Instant? = null,
): Medicine {
    return Medicine(
        uuid = uuid,
        selection = MedicineSelection.PatchOff,
        category = MedicationCategory.ESTRADIOL,
        preparation = MedicinePreparation.PatchOff,
        displayName = null,
        identityKey = MedicineIdentityKey.patchOff(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
}

fun testDoseInstruction(
    numerator: Int = 1,
    denominator: Int = 1,
): DoseInstruction {
    return DoseInstruction.TabletFraction(numerator = numerator, denominator = denominator)
}

fun testMedicationGroupMedication(
    uuid: UUID = UUID.randomUUID(),
    medicine: Medicine? = testMedicine(),
    applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    doseInstruction: DoseInstruction = testDoseInstruction(),
    count: Int = 1,
): MedicationGroupMedication {
    return MedicationGroupMedication(
        uuid = uuid,
        medicine = medicine,
        applicationType = applicationType,
        doseInstruction = doseInstruction,
        count = count,
    )
}

fun testMedicationLogEntry(
    uuid: UUID = UUID.randomUUID(),
    medicine: Medicine? = testMedicine(),
    category: MedicationCategory = medicine?.category ?: MedicationCategory.ESTRADIOL,
    applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    doseInstruction: DoseInstruction = testDoseInstruction(),
    equivalentE2Mg: Double? = null,
    sourceGroupUuid: UUID?,
    appliedAt: Instant,
    appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    scheduledFor: LocalDateTime? = null,
    count: Int = 1,
    scheduleTimeUuid: UUID? = null,
): MedicationLogEntry {
    return MedicationLogEntry(
        uuid = uuid,
        medicine = medicine,
        category = category,
        applicationType = applicationType,
        doseInstruction = doseInstruction,
        equivalentE2Mg = equivalentE2Mg,
        sourceGroupUuid = sourceGroupUuid,
        appliedAt = appliedAt,
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        scheduledFor = scheduledFor,
        count = count,
        scheduleTimeUuid = scheduleTimeUuid,
    )
}

fun testInstant(dateTime: LocalDateTime): Instant {
    return dateTime.atZone(ZoneId.systemDefault()).toInstant()
}
