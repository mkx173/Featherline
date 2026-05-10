package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationCatalog
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCatalogEntry
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.fromCanonicalMg
import com.mkx.hrttracker.model.medication.toCanonicalMg
import java.math.BigDecimal

data class MedicationDraftUiState(
    val category: MedicationCategory = MedicationCategory.ESTRADIOL,
    val applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    val selectionKind: MedicationSelectionKind = MedicationSelectionKind.CATALOG,
    val medicationKey: MedicationKey? = MedicationKey.ESTRADIOL_VALERATE,
    val customMedicationName: String = "",
    val doseKind: MedicationDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
    val doseMg: String = "",
    val customDoseUnit: MedicationDoseUnit = MedicationDoseUnit.MG,
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

fun MedicationDraftUiState.showsCustomDoseUnitSelector(): Boolean {
    return selectionKind == MedicationSelectionKind.CUSTOM &&
        doseKind == MedicationDoseKind.MG_AS_MEDICINE
}

fun MedicationApplicationType.supportsMedicationCountEditor(): Boolean {
    return when (this) {
        MedicationApplicationType.ORAL,
        MedicationApplicationType.SUBLINGUAL,
        MedicationApplicationType.PATCH_ON -> true

        MedicationApplicationType.INJECTION,
        MedicationApplicationType.GEL,
        MedicationApplicationType.PATCH_OFF -> false
    }
}

fun MedicationDraftUiState.showsMedicationCountEditor(): Boolean {
    return applicationType.supportsMedicationCountEditor()
}

fun normalizeMedicationCount(
    applicationType: MedicationApplicationType,
    count: Int,
): Int {
    return if (applicationType.supportsMedicationCountEditor()) {
        count.coerceAtLeast(1)
    } else {
        1
    }
}

fun parseMedicationCountText(countText: String): Int {
    return countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
}

fun parsePositiveMedicationCountOrNull(countText: String): Int? {
    return countText.toIntOrNull()?.takeIf { it > 0 }
}

fun countStepBase(countText: String): Int {
    return countText.toIntOrNull()?.coerceAtLeast(0) ?: 0
}

fun sanitizeMedicationCountText(input: String): String {
    return input.filter(Char::isDigit)
}

fun stepMedicationCount(
    applicationType: MedicationApplicationType,
    countText: String,
    delta: Int,
): Int {
    return normalizeMedicationCount(
        applicationType = applicationType,
        count = countStepBase(countText) + delta
    )
}

fun resolveMedicationCountTextAfterDraftChange(
    previousDraft: MedicationDraftUiState,
    updatedDraft: MedicationDraftUiState,
    currentCountText: String,
): String {
    return if (
        previousDraft.category != updatedDraft.category ||
        previousDraft.applicationType != updatedDraft.applicationType
    ) {
        "1"
    } else if (updatedDraft.applicationType.supportsMedicationCountEditor()) {
        currentCountText
    } else {
        "1"
    }
}

fun resolveMedicationCountAfterDraftChange(
    previousDraft: MedicationDraftUiState,
    updatedDraft: MedicationDraftUiState,
    currentCount: Int,
): Int {
    val nextCount = if (
        previousDraft.category != updatedDraft.category ||
        previousDraft.applicationType != updatedDraft.applicationType
    ) {
        1
    } else {
        currentCount
    }
    return normalizeMedicationCount(updatedDraft.applicationType, nextCount)
}

fun medicationCountValidationErrorRes(
    applicationType: MedicationApplicationType,
    countText: String,
): Int? {
    if (!applicationType.supportsMedicationCountEditor()) {
        return null
    }

    return if (parsePositiveMedicationCountOrNull(countText) == null) {
        R.string.validation_count_required
    } else {
        null
    }
}

fun resolvedMedicationCountForSave(
    applicationType: MedicationApplicationType,
    countText: String,
): Int {
    return if (applicationType.supportsMedicationCountEditor()) {
        checkNotNull(parsePositiveMedicationCountOrNull(countText))
    } else {
        1
    }
}

fun MedicationDraftUiState.displayDoseUnit(): MedicationDoseUnit {
    return if (showsCustomDoseUnitSelector()) {
        customDoseUnit
    } else {
        MedicationDoseUnit.MG
    }
}

fun MedicationDraftUiState.doseWarningThresholdMg(): Double? {
    if (doseKind != MedicationDoseKind.MG_AS_MEDICINE ||
        selectionKind != MedicationSelectionKind.CATALOG
    ) {
        return null
    }

    return when (selectedCatalogEntry().medicationKey) {
        MedicationKey.SPIRONOLACTONE -> 200.0
        MedicationKey.CYPROTERONE_ACETATE -> 12.5
        MedicationKey.BICALUTAMIDE -> 50.0
        else -> null
    }
}

fun MedicationDraftUiState.exceedsDoseWarningThreshold(count: Int = 1): Boolean {
    val thresholdMg = doseWarningThresholdMg() ?: return false
    val resolvedCount = count.coerceAtLeast(1)
    return doseMg.toDoubleOrNull()
        ?.let { valueMg -> (valueMg * resolvedCount) > thresholdMg } == true
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

fun MedicationDraftUiState.changeMedicationKey(
    medicationKey: MedicationKey,
): MedicationDraftUiState {
    val entry = catalog().entries.firstOrNull { it.medicationKey == medicationKey }
        ?: return this
    return copy(
        selectionKind = MedicationSelectionKind.CATALOG,
        medicationKey = medicationKey,
        doseKind = if (doseKind in entry.doseKinds) doseKind else entry.defaultDoseKind,
        customDoseUnit = MedicationDoseUnit.MG,
    )
}

fun MedicationDraftUiState.changeDoseKind(
    doseKind: MedicationDoseKind,
): MedicationDraftUiState {
    if (this.doseKind == doseKind) {
        return this
    }
    // Clear every dose-value field on a real switch so leftover values from the
    // previous kind don't reappear if the user toggles back. Without this, e.g.
    // typing 100 in MG_AS_MEDICINE, switching to PATCH_RELEASE_RATE_MCG_DAY, then
    // back, would resurrect "100" instead of starting fresh.
    val next = copy(
        doseKind = doseKind,
        doseMg = "",
        gelPercent = "",
        gelWeightGrams = "",
        patchReleaseRateMcgPerDay = "",
    )
    return if (next.showsCustomDoseUnitSelector()) {
        next
    } else {
        next.copy(customDoseUnit = MedicationDoseUnit.MG)
    }
}

fun MedicationDraftUiState.changeCustomDoseUnit(
    customDoseUnit: MedicationDoseUnit,
): MedicationDraftUiState {
    if (!showsCustomDoseUnitSelector() || this.customDoseUnit == customDoseUnit) {
        return this
    }

    return copy(
        customDoseUnit = customDoseUnit,
    )
}

fun MedicationDraftUiState.applyDoseAssistPreset(
    preset: MedicationDoseAssistPreset,
): MedicationDraftUiState {
    return when (preset) {
        is MedicationDoseAssistPreset.MgAsMedicine -> copy(
            doseKind = MedicationDoseKind.MG_AS_MEDICINE,
            doseMg = preset.valueMg
        )

        is MedicationDoseAssistPreset.GelEquivalentEstradiolMg -> copy(
            doseKind = MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
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
    val resolvedCustomDoseUnit = if (
        selection is MedicationSelection.Custom &&
        doseKind == MedicationDoseKind.MG_AS_MEDICINE
    ) {
        customDoseUnit
    } else {
        MedicationDoseUnit.MG
    }

    val dose = when (doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE -> MedicationDose.MgAsMedicine(
            if (selection is MedicationSelection.Custom) {
                resolvedCustomDoseUnit.toCanonicalMg(doseMg.toDouble())
            } else {
                doseMg.toDouble()
            }
        )
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
        dose = dose,
        customDoseUnit = resolvedCustomDoseUnit,
    )
}

fun medicationDraftFromDetails(
    details: MedicationDetails,
): MedicationDraftUiState {
    val base = defaultMedicationDraft(
        category = details.category,
        applicationType = details.applicationType
    )
    val resolvedCustomDoseUnit = if (
        details.selection is MedicationSelection.Custom &&
        details.dose is MedicationDose.MgAsMedicine
    ) {
        details.customDoseUnit
    } else {
        MedicationDoseUnit.MG
    }
    return base.copy(
        selectionKind = when (details.selection) {
            is MedicationSelection.Catalog -> MedicationSelectionKind.CATALOG
            is MedicationSelection.Custom -> MedicationSelectionKind.CUSTOM
        },
        medicationKey = (details.selection as? MedicationSelection.Catalog)?.medicationKey
            ?: base.medicationKey,
        customMedicationName = (details.selection as? MedicationSelection.Custom)?.medicationName.orEmpty(),
        doseKind = details.dose.kind,
        customDoseUnit = resolvedCustomDoseUnit,
        doseMg = when (val dose = details.dose) {
            is MedicationDose.MgAsMedicine -> resolvedCustomDoseUnit.fromCanonicalMg(
                dose.valueMg
            ).toInputString()
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
