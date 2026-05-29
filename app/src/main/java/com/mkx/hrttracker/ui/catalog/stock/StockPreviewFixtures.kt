package com.mkx.hrttracker.ui.catalog.stock

import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// Fixtures backing @Preview composables in this package. Catalog-only so we
// don't have to invent a custom-medicine identity for visual scaffolding.
internal fun previewMedicine(
    preparation: MedicinePreparation,
    stock: MedicineStock,
    medicationKey: MedicationKey = MedicationKey.ESTRADIOL,
    category: MedicationCategory = MedicationCategory.ESTRADIOL,
    uuid: UUID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001"),
): Medicine {
    return Medicine(
        uuid = uuid,
        selection = MedicineSelection.Catalog(medicationKey),
        category = category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
        displayDoseUnit = MedicineDisplayDoseUnit.MG,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = stock,
    )
}

internal fun previewProjection(
    medicine: Medicine,
    dosesPerDayMagnitude: Double,
    totalStockUnits: Double,
    runwayDays: Double?,
    state: MedicineStockState,
): MedicineStockProjection {
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = dosesPerDayMagnitude,
        totalStockUnits = totalStockUnits,
        runway = if (runwayDays == null) {
            RunwayProjection.NoSchedule
        } else {
            RunwayProjection.Days(
                days = runwayDays.toInt(),
                lastFulfillable = LocalDate.of(2026, 1, 1).plusDays(runwayDays.toLong()),
            )
        },
        intervalDays = null,
        maxPerAdministration = dosesPerDayMagnitude,
        state = state,
    )
}
