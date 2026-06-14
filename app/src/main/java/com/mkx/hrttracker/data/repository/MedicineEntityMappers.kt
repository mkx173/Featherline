package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import java.time.Instant
import java.util.UUID

internal fun MedicineEntity.toMedicineModel(): Medicine {
    val preparation = toMedicinePreparation()
    // PATCH_OFF preparation always maps to the sentinel selection; ignore the
    // stored selectionKind (which is CATALOG for storage convenience).
    val selection = if (preparation is MedicinePreparation.PatchOff) {
        MedicineSelection.PatchOff
    } else {
        toMedicineSelection()
    }
    validateIdentityFields(selection, preparation)
    return Medicine(
        uuid = UUID.fromString(uuid),
        selection = selection,
        category = MedicationCategory.fromStorageValue(category),
        preparation = preparation,
        displayName = displayName,
        identityKey = identityKey,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
        archivedAt = archivedAtEpochMillis?.let(Instant::ofEpochMilli),
        // Defaults to MG for any row missing/with an unknown unit, so rows
        // written before the column existed (handled by MIGRATION_1_2) and
        // any future unknown values stay safely readable.
        displayDoseUnit = MedicineDisplayDoseUnit.fromStorageValue(displayDoseUnit),
        stock = MedicineStock(
            trackingEnabled = trackingEnabled,
            unitsRemaining = stockUnitsRemaining,
            unitsLastTotal = stockUnitsLastTotal,
            openContainerAmount = openContainerAmount,
            warnAtDaysRemaining = warnAtDaysRemaining,
            generation = stockGeneration,
        ).normalizedFor(preparation),
        importedFromExternalTracker = importedFromExternalTracker,
    )
}

/**
 * Restores the eager-unseal invariant at a model-read boundary: a tracking
 * container preparation never keeps an empty open amount while sealed stock
 * remains. Shared by every decode/restore site (Room mapper, home-snapshot
 * decode, backup restore) so legacy/imported degenerate rows land canonical.
 */
internal fun MedicineStock.normalizedFor(preparation: MedicinePreparation): MedicineStock {
    if (!trackingEnabled) return this
    val capacity = when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> preparation.vialVolumeMl
        is MedicinePreparation.GelContainer -> preparation.containerWeightGrams
        else -> return this
    }
    val sealed = unitsRemaining?.takeIf { it.isFinite() } ?: 0.0
    val (normalizedOpen, normalizedSealed) = normalizeOpenContainer(
        open = openContainerAmount,
        sealed = sealed,
        capacity = capacity,
    )
    return copy(
        unitsRemaining = normalizedSealed,
        openContainerAmount = normalizedOpen,
        unitsLastTotal = decrementDenominatorOnCrack(
            lastTotal = unitsLastTotal,
            cracked = normalizedSealed != sealed,
        ),
    )
}

internal fun Medicine.toEntity(): MedicineEntity {
    val storageFields = preparation.toStorageFields()
    return MedicineEntity(
        uuid = uuid.toString(),
        selectionKind = selection.kind.name,
        // PATCH_OFF reuses CATALOG selectionKind for storage but has no
        // medicationKey/customName; the preparationType PATCH_OFF marker is
        // what reconstructs the MedicineSelection.PatchOff variant on read.
        medicationKey = when {
            preparation is MedicinePreparation.ImportedInjection -> preparation.ester.name
            preparation is MedicinePreparation.ImportedGel -> MedicationKey.ESTRADIOL.name
            selection is MedicineSelection.Catalog -> selection.medicationKey.name
            else -> null
        },
        customMedicationName = when (val currentSelection = selection) {
            is MedicineSelection.Catalog -> null
            is MedicineSelection.Custom -> currentSelection.medicationName
            is MedicineSelection.PatchOff -> null
        },
        customMedicationNameNormalized = when (val currentSelection = selection) {
            is MedicineSelection.Catalog -> null
            is MedicineSelection.Custom -> currentSelection.normalizedMedicationName
            is MedicineSelection.PatchOff -> null
        },
        category = category.name,
        preparationType = storageFields.preparationType,
        strengthMgPerTablet = storageFields.strengthMgPerTablet,
        strengthMgPerVial = storageFields.strengthMgPerVial,
        concentrationMgPerMl = storageFields.concentrationMgPerMl,
        vialVolumeMl = storageFields.vialVolumeMl,
        concentrationPercent = storageFields.concentrationPercent,
        sachetWeightGrams = storageFields.sachetWeightGrams,
        containerWeightGrams = storageFields.containerWeightGrams,
        patchTotalMg = storageFields.patchTotalMg,
        patchReleaseRateMcgPerDay = storageFields.patchReleaseRateMcgPerDay,
        displayName = displayName,
        identityKey = identityKey,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
        archivedAtEpochMillis = archivedAt?.toEpochMilli(),
        displayDoseUnit = displayDoseUnit.name,
        trackingEnabled = stock.trackingEnabled,
        stockUnitsRemaining = stock.unitsRemaining,
        stockUnitsLastTotal = stock.unitsLastTotal,
        openContainerAmount = stock.openContainerAmount,
        warnAtDaysRemaining = stock.warnAtDaysRemaining,
        stockGeneration = stock.generation,
        importedFromExternalTracker = importedFromExternalTracker,
    )
}

internal fun MedicinePreparation.toStorageFields(): MedicinePreparationStorageFields {
    return when (this) {
        is MedicinePreparation.Pill -> MedicinePreparationStorageFields(
            preparationType = type.name,
            strengthMgPerTablet = strengthMgPerTablet,
        )

        is MedicinePreparation.Capsule -> MedicinePreparationStorageFields(
            preparationType = type.name,
            strengthMgPerTablet = strengthMgPerCapsule,
        )

        is MedicinePreparation.InjectionSingleUseVial -> MedicinePreparationStorageFields(
            preparationType = type.name,
            strengthMgPerVial = strengthMgPerVial,
        )

        is MedicinePreparation.InjectionMultiUseVial -> MedicinePreparationStorageFields(
            preparationType = type.name,
            concentrationMgPerMl = concentrationMgPerMl,
            vialVolumeMl = vialVolumeMl,
        )

        is MedicinePreparation.GelSachet -> MedicinePreparationStorageFields(
            preparationType = type.name,
            concentrationPercent = concentrationPercent,
            sachetWeightGrams = sachetWeightGrams,
        )

        is MedicinePreparation.GelContainer -> MedicinePreparationStorageFields(
            preparationType = type.name,
            concentrationPercent = concentrationPercent,
            containerWeightGrams = containerWeightGrams,
        )

        is MedicinePreparation.ImportedInjection -> MedicinePreparationStorageFields(
            preparationType = type.name,
            strengthMgPerVial = administeredMg,
        )

        is MedicinePreparation.ImportedGel -> MedicinePreparationStorageFields(
            preparationType = type.name,
            strengthMgPerVial = appliedEstradiolMg,
        )

        is MedicinePreparation.Patch -> when (val currentSpecification = specification) {
            is MedicinePreparation.PatchSpecification.TotalMg -> MedicinePreparationStorageFields(
                preparationType = type.name,
                patchTotalMg = currentSpecification.valueMg,
            )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> MedicinePreparationStorageFields(
                preparationType = type.name,
                patchReleaseRateMcgPerDay = currentSpecification.valueMcgPerDay,
            )
        }

        // No numeric columns; the preparationType marker is the whole payload.
        is MedicinePreparation.PatchOff -> MedicinePreparationStorageFields(
            preparationType = type.name,
        )
    }
}

// The strengthMgPerTablet column / field is dual-purpose: it stores per-tablet
// mg for PILL and per-capsule mg for CAPSULE. Reusing the column avoids a Room
// migration; encode/decode branches above route the value into the right
// MedicinePreparation variant based on preparationType.
internal data class MedicinePreparationStorageFields(
    val preparationType: String,
    val strengthMgPerTablet: Double? = null,
    val strengthMgPerVial: Double? = null,
    val concentrationMgPerMl: Double? = null,
    val vialVolumeMl: Double? = null,
    val concentrationPercent: Double? = null,
    val sachetWeightGrams: Double? = null,
    val containerWeightGrams: Double? = null,
    val patchTotalMg: Double? = null,
    val patchReleaseRateMcgPerDay: Double? = null,
)

private fun MedicineEntity.toMedicineSelection(): MedicineSelection {
    return when (MedicationSelectionKind.fromStorageValue(selectionKind)) {
        MedicationSelectionKind.CATALOG -> MedicineSelection.Catalog(
            medicationKey = checkNotNull(MedicationKey.fromStorageValue(medicationKey))
        )

        MedicationSelectionKind.CUSTOM -> MedicineSelection.Custom(
            medicationName = checkNotNull(customMedicationName) {
                "Custom medicine $uuid is missing customMedicationName."
            }
        )
    }
}

private fun MedicineEntity.validateIdentityFields(
    selection: MedicineSelection,
    preparation: MedicinePreparation,
) {
    if (importedFromExternalTracker && identityKey.startsWith("E|")) {
        return
    }

    val expectedIdentityKey = when (selection) {
        is MedicineSelection.Catalog -> {
            MedicineIdentityKey.catalog(selection.medicationKey, preparation)
        }

        is MedicineSelection.Custom -> {
            val expectedNormalizedName = selection.normalizedMedicationName
            val storedNormalizedName = checkNotNull(customMedicationNameNormalized) {
                "Custom medicine $uuid is missing customMedicationNameNormalized."
            }
            check(storedNormalizedName == expectedNormalizedName) {
                "Custom medicine $uuid normalized name does not match selection."
            }
            MedicineIdentityKey.custom(selection.medicationName, preparation)
        }

        is MedicineSelection.PatchOff -> {
            // The singleton must carry the PATCH_OFF preparation and no
            // selection-specific columns.
            check(preparation is MedicinePreparation.PatchOff) {
                "PATCH_OFF medicine $uuid must carry PatchOff preparation."
            }
            check(medicationKey == null) {
                "PATCH_OFF medicine $uuid must not carry a catalog medicationKey."
            }
            check(customMedicationName == null && customMedicationNameNormalized == null) {
                "PATCH_OFF medicine $uuid must not carry a custom medication name."
            }
            MedicineIdentityKey.patchOff()
        }
    }

    check(identityKey == expectedIdentityKey) {
        "Medicine $uuid identityKey does not match selection and preparation."
    }
}

private fun MedicineEntity.toMedicinePreparation(): MedicinePreparation {
    return when (MedicinePreparationType.fromStorageValue(preparationType)) {
        MedicinePreparationType.PILL -> {
            requireOnlyPreparationFields("strengthMgPerTablet")
            MedicinePreparation.Pill(
                strengthMgPerTablet = checkNotNull(strengthMgPerTablet)
            )
        }

        MedicinePreparationType.CAPSULE -> {
            requireOnlyPreparationFields("strengthMgPerTablet")
            MedicinePreparation.Capsule(
                strengthMgPerCapsule = checkNotNull(strengthMgPerTablet)
            )
        }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> {
            requireOnlyPreparationFields("strengthMgPerVial")
            MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = checkNotNull(strengthMgPerVial)
            )
        }

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
            requireOnlyPreparationFields("concentrationMgPerMl", "vialVolumeMl")
            MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = checkNotNull(concentrationMgPerMl),
                vialVolumeMl = checkNotNull(vialVolumeMl),
            )
        }

        MedicinePreparationType.GEL_SACHET -> {
            requireOnlyPreparationFields("concentrationPercent", "sachetWeightGrams")
            MedicinePreparation.GelSachet(
                concentrationPercent = checkNotNull(concentrationPercent),
                sachetWeightGrams = checkNotNull(sachetWeightGrams),
            )
        }

        MedicinePreparationType.GEL_CONTAINER -> {
            requireOnlyPreparationFields("concentrationPercent", "containerWeightGrams")
            MedicinePreparation.GelContainer(
                concentrationPercent = checkNotNull(concentrationPercent),
                containerWeightGrams = checkNotNull(containerWeightGrams),
            )
        }

        MedicinePreparationType.IMPORTED_INJECTION -> {
            requireOnlyPreparationFields("strengthMgPerVial")
            val ester = checkNotNull(MedicationKey.fromStorageValue(medicationKey)) {
                "Imported injection medicine $uuid is missing ester medicationKey."
            }
            MedicinePreparation.ImportedInjection(
                administeredMg = checkNotNull(strengthMgPerVial),
                ester = ester,
            )
        }

        MedicinePreparationType.IMPORTED_GEL -> {
            requireOnlyPreparationFields("strengthMgPerVial")
            require(medicationKey == MedicationKey.ESTRADIOL.name) {
                "Imported gel medicine $uuid must carry ESTRADIOL medicationKey."
            }
            MedicinePreparation.ImportedGel(
                appliedEstradiolMg = checkNotNull(strengthMgPerVial),
            )
        }

        MedicinePreparationType.PATCH -> {
            requirePatchPreparationFields()
            MedicinePreparation.Patch(
                specification = patchTotalMg?.let(MedicinePreparation.PatchSpecification::TotalMg)
                    ?: MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                        valueMcgPerDay = checkNotNull(patchReleaseRateMcgPerDay)
                    )
            )
        }

        MedicinePreparationType.PATCH_OFF -> {
            requireOnlyPreparationFields()
            MedicinePreparation.PatchOff
        }
    }
}

private fun MedicineEntity.requireOnlyPreparationFields(vararg requiredFieldNames: String) {
    val requiredFields = requiredFieldNames.toSet()
    preparationFieldValues().forEach { (fieldName, value) ->
        if (fieldName in requiredFields) {
            check(value != null) {
                "Medicine $uuid $preparationType is missing $fieldName."
            }
        } else {
            check(value == null) {
                "Medicine $uuid $preparationType has unexpected $fieldName."
            }
        }
    }
}

private fun MedicineEntity.requirePatchPreparationFields() {
    preparationFieldValues()
        .filterNot { (fieldName, _) ->
            fieldName == "patchTotalMg" || fieldName == "patchReleaseRateMcgPerDay"
        }
        .forEach { (fieldName, value) ->
            check(value == null) {
                "Medicine $uuid $preparationType has unexpected $fieldName."
            }
        }

    check(listOfNotNull(patchTotalMg, patchReleaseRateMcgPerDay).size == 1) {
        "Medicine $uuid PATCH must have exactly one patch specification."
    }
}

private fun MedicineEntity.preparationFieldValues(): List<Pair<String, Double?>> {
    return listOf(
        "strengthMgPerTablet" to strengthMgPerTablet,
        "strengthMgPerVial" to strengthMgPerVial,
        "concentrationMgPerMl" to concentrationMgPerMl,
        "vialVolumeMl" to vialVolumeMl,
        "concentrationPercent" to concentrationPercent,
        "sachetWeightGrams" to sachetWeightGrams,
        "containerWeightGrams" to containerWeightGrams,
        "patchTotalMg" to patchTotalMg,
        "patchReleaseRateMcgPerDay" to patchReleaseRateMcgPerDay,
    )
}
