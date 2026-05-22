package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import java.time.Instant
import java.util.UUID

internal fun MedicineEntity.toMedicineModel(): Medicine {
    val selection = toMedicineSelection()
    val preparation = toMedicinePreparation()
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
    )
}

internal fun Medicine.toEntity(): MedicineEntity {
    val storageFields = preparation.toStorageFields()
    return MedicineEntity(
        uuid = uuid.toString(),
        selectionKind = selection.kind.name,
        medicationKey = when (val currentSelection = selection) {
            is MedicineSelection.Catalog -> currentSelection.medicationKey.name
            is MedicineSelection.Custom -> null
        },
        customMedicationName = when (val currentSelection = selection) {
            is MedicineSelection.Catalog -> null
            is MedicineSelection.Custom -> currentSelection.medicationName
        },
        customMedicationNameNormalized = when (val currentSelection = selection) {
            is MedicineSelection.Catalog -> null
            is MedicineSelection.Custom -> currentSelection.normalizedMedicationName
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
    )
}

internal fun MedicinePreparation.toStorageFields(): MedicinePreparationStorageFields {
    return when (this) {
        is MedicinePreparation.Pill -> MedicinePreparationStorageFields(
            preparationType = type.name,
            strengthMgPerTablet = strengthMgPerTablet,
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
    }
}

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

        MedicinePreparationType.PATCH -> {
            requirePatchPreparationFields()
            MedicinePreparation.Patch(
                specification = patchTotalMg?.let(MedicinePreparation.PatchSpecification::TotalMg)
                    ?: MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                        valueMcgPerDay = checkNotNull(patchReleaseRateMcgPerDay)
                    )
            )
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
