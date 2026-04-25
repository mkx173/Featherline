package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationCatalog
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCatalogEntry
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import java.math.BigDecimal

data class MedicationDraftUiState(
    val category: MedicationCategory = MedicationCategory.ESTRADIOL,
    val applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    val selectionKind: MedicationSelectionKind = MedicationSelectionKind.CATALOG,
    val medicationKey: MedicationKey? = MedicationKey.ESTRADIOL_VALERATE,
    val customMedicationName: String = "",
    val doseKind: MedicationDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
    val doseMg: String = "",
    val gelPercent: String = "",
    val gelWeightGrams: String = "",
    val patchReleaseRateMcgPerDay: String = "",
)

fun editorMedicationCategories(): List<MedicationCategory> {
    return MedicationCategory.entries.filterNot { it == MedicationCategory.TESTOSTERONE }
}

fun defaultMedicationDraft(
    category: MedicationCategory = MedicationCategory.ESTRADIOL,
    applicationType: MedicationApplicationType = MedicationCatalog.applicationTypesFor(category).first(),
): MedicationDraftUiState {
    return buildMedicationDraft(category, applicationType) { entry ->
        entry.defaultDoseKind
    }
}

private fun firstAvailableMedicationDraft(
    category: MedicationCategory,
    applicationType: MedicationApplicationType,
): MedicationDraftUiState {
    return buildMedicationDraft(category, applicationType) { entry ->
        entry.doseKinds.first()
    }
}

private fun buildMedicationDraft(
    category: MedicationCategory,
    applicationType: MedicationApplicationType,
    doseKindResolver: (MedicationCatalogEntry) -> MedicationDoseKind,
): MedicationDraftUiState {
    val resolvedApplicationType = MedicationCatalog.applicationTypesFor(category)
        .firstOrNull { it == applicationType }
        ?: MedicationCatalog.applicationTypesFor(category).first()
    val catalog = MedicationCatalog.catalogFor(category, resolvedApplicationType)
    val defaultEntry = catalog.entries.first()
    val selectionKind = if (catalog.entries.any { it.medicationKey != null }) {
        MedicationSelectionKind.CATALOG
    } else {
        MedicationSelectionKind.CUSTOM
    }

    return MedicationDraftUiState(
        category = category,
        applicationType = resolvedApplicationType,
        selectionKind = selectionKind,
        medicationKey = defaultEntry.medicationKey,
        doseKind = doseKindResolver(defaultEntry),
    )
}

fun MedicationDraftUiState.catalog(): MedicationApplicationCatalog {
    return MedicationCatalog.catalogFor(category, applicationType)
}

fun MedicationDraftUiState.supportsCatalogSelection(): Boolean {
    return catalog().entries.any { it.medicationKey != null }
}

fun MedicationDraftUiState.supportsCustomName(): Boolean {
    return catalog().allowCustomMedicationName
}

fun MedicationDraftUiState.requiresCustomName(): Boolean {
    return !supportsCatalogSelection() || selectionKind == MedicationSelectionKind.CUSTOM
}

fun MedicationDraftUiState.selectedCatalogEntry(): MedicationCatalogEntry {
    val catalog = catalog()
    return if (selectionKind == MedicationSelectionKind.CATALOG) {
        catalog.entries.firstOrNull { it.medicationKey == medicationKey } ?: catalog.entries.first()
    } else {
        catalog.entries.first()
    }
}

fun MedicationDraftUiState.availableDoseKinds(): List<MedicationDoseKind> {
    return selectedCatalogEntry().doseKinds.toList()
}

fun MedicationDraftUiState.activeDoseAssistPresets(): List<MedicationDoseAssistPreset> {
    return selectedCatalogEntry().doseAssistPresets[doseKind].orEmpty()
}

fun MedicationDraftUiState.changeCategory(category: MedicationCategory): MedicationDraftUiState {
    return firstAvailableMedicationDraft(
        category = category,
        applicationType = MedicationCatalog.applicationTypesFor(category).first(),
    ).copy(
        customMedicationName = customMedicationName
    )
}

fun MedicationDraftUiState.changeApplicationType(
    applicationType: MedicationApplicationType,
): MedicationDraftUiState {
    return firstAvailableMedicationDraft(
        category = category,
        applicationType = applicationType
    ).copy(
        customMedicationName = customMedicationName
    )
}

fun MedicationDraftUiState.changeSelectionKind(
    selectionKind: MedicationSelectionKind,
): MedicationDraftUiState {
    val base = defaultMedicationDraft(
        category = category,
        applicationType = applicationType
    )
    return when (selectionKind) {
        MedicationSelectionKind.CATALOG -> base.copy(
            selectionKind = MedicationSelectionKind.CATALOG,
            customMedicationName = customMedicationName
        )

        MedicationSelectionKind.CUSTOM -> base.copy(
            selectionKind = MedicationSelectionKind.CUSTOM,
            customMedicationName = customMedicationName,
            medicationKey = null
        )
    }
}

fun MedicationDraftUiState.changeMedicationKey(
    medicationKey: MedicationKey,
): MedicationDraftUiState {
    val entry = catalog().entries.firstOrNull { it.medicationKey == medicationKey }
        ?: return this
    return copy(
        selectionKind = MedicationSelectionKind.CATALOG,
        medicationKey = medicationKey,
        doseKind = if (doseKind in entry.doseKinds) doseKind else entry.defaultDoseKind
    )
}

fun MedicationDraftUiState.changeDoseKind(
    doseKind: MedicationDoseKind,
): MedicationDraftUiState {
    return copy(doseKind = doseKind)
}

fun MedicationDraftUiState.applyDoseAssistPreset(
    preset: MedicationDoseAssistPreset,
): MedicationDraftUiState {
    return when (preset) {
        is MedicationDoseAssistPreset.MgAsMedicine -> copy(
            doseKind = MedicationDoseKind.MG_AS_MEDICINE,
            doseMg = preset.valueMg
        )

        is MedicationDoseAssistPreset.GelPercent -> copy(
            doseKind = MedicationDoseKind.GEL_PERCENT_AND_WEIGHT,
            gelPercent = preset.percent
        )

        is MedicationDoseAssistPreset.GelWeightGrams -> copy(
            doseKind = MedicationDoseKind.GEL_PERCENT_AND_WEIGHT,
            gelWeightGrams = preset.weightGrams
        )

        is MedicationDoseAssistPreset.PatchTotalMg -> copy(
            doseKind = MedicationDoseKind.PATCH_TOTAL_MG,
            doseMg = preset.valueMg
        )

        is MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay -> copy(
            doseKind = MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
            patchReleaseRateMcgPerDay = preset.valueMcgPerDay
        )
    }
}

fun MedicationDraftUiState.validationErrorRes(): Int? {
    return validationErrors().firstOrNull()
}

internal fun MedicationDraftUiState.validationErrors(): List<Int> {
    val errors = mutableListOf<Int>()

    if (requiresCustomName() && customMedicationName.trim().isEmpty()) {
        errors += R.string.validation_name_required
    }

    if (selectionKind == MedicationSelectionKind.CATALOG &&
        supportsCatalogSelection() &&
        selectedCatalogEntry().medicationKey == null
    ) {
        errors += R.string.validation_medication_selection_required
    }

    when (doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE,
        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
        MedicationDoseKind.PATCH_TOTAL_MG -> {
            if (doseMg.toDoubleOrNull()?.let { it > 0.0 } != true) {
                errors += R.string.validation_dose_required
            }
        }

        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> {
            if (gelPercent.toDoubleOrNull()?.let { it > 0.0 } != true) {
                errors += R.string.validation_gel_percent_required
            }
            if (gelWeightGrams.toDoubleOrNull()?.let { it > 0.0 } != true) {
                errors += R.string.validation_gel_weight_required
            }
        }

        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> {
            if (patchReleaseRateMcgPerDay.toDoubleOrNull()?.let { it > 0.0 } != true) {
                errors += R.string.validation_patch_release_rate_required
            }
        }

        MedicationDoseKind.NONE -> Unit
    }

    return errors
}

fun MedicationDraftUiState.toMedicationDetails(): MedicationDetails {
    val selection = if (requiresCustomName()) {
        MedicationSelection.Custom(customMedicationName.trim())
    } else {
        MedicationSelection.Catalog(checkNotNull(selectedCatalogEntry().medicationKey))
    }

    val dose = when (doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE -> MedicationDose.MgAsMedicine(doseMg.toDouble())
        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> MedicationDose.GelEquivalentEstradiolMg(
            doseMg.toDouble()
        )

        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> MedicationDose.GelPercentAndWeight(
            percent = gelPercent.toDouble(),
            weightGrams = gelWeightGrams.toDouble()
        )

        MedicationDoseKind.PATCH_TOTAL_MG -> MedicationDose.PatchTotalMg(doseMg.toDouble())
        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> MedicationDose.PatchReleaseRateMcgPerDay(
            patchReleaseRateMcgPerDay.toDouble()
        )

        MedicationDoseKind.NONE -> MedicationDose.None
    }

    return MedicationDetails(
        category = category,
        applicationType = applicationType,
        selection = selection,
        dose = dose
    )
}

fun medicationDraftFromDetails(
    details: MedicationDetails,
): MedicationDraftUiState {
    val base = defaultMedicationDraft(
        category = details.category,
        applicationType = details.applicationType
    )
    return base.copy(
        selectionKind = when (details.selection) {
            is MedicationSelection.Catalog -> MedicationSelectionKind.CATALOG
            is MedicationSelection.Custom -> MedicationSelectionKind.CUSTOM
        },
        medicationKey = (details.selection as? MedicationSelection.Catalog)?.medicationKey
            ?: base.medicationKey,
        customMedicationName = (details.selection as? MedicationSelection.Custom)?.medicationName.orEmpty(),
        doseKind = details.dose.kind,
        doseMg = when (val dose = details.dose) {
            is MedicationDose.MgAsMedicine -> dose.valueMg.toInputString()
            is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg.toInputString()
            is MedicationDose.PatchTotalMg -> dose.valueMg.toInputString()
            else -> ""
        },
        gelPercent = when (val dose = details.dose) {
            is MedicationDose.GelPercentAndWeight -> dose.percent.toInputString()
            else -> ""
        },
        gelWeightGrams = when (val dose = details.dose) {
            is MedicationDose.GelPercentAndWeight -> dose.weightGrams.toInputString()
            else -> ""
        },
        patchReleaseRateMcgPerDay = when (val dose = details.dose) {
            is MedicationDose.PatchReleaseRateMcgPerDay -> dose.valueMcgPerDay.toInputString()
            else -> ""
        },
    )
}

private fun Double.toInputString(): String {
    return BigDecimal.valueOf(this)
        .stripTrailingZeros()
        .toPlainString()
}
