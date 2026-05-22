package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationCatalog
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import java.util.UUID

// ---------------------------------------------------------------------------
// New UI draft types (Task 6). The picker resolves a Medicine before producing
// a DoseInstruction; the two concerns are tracked by two focused draft types.
// ---------------------------------------------------------------------------

data class MedicinePickerUiState(
    val category: MedicationCategory = MedicationCategory.ESTRADIOL,
    val applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    val selectionKind: MedicationSelectionKind = MedicationSelectionKind.CATALOG,
    val medicationKey: MedicationKey? = MedicationKey.ESTRADIOL_VALERATE,
    val customMedicationName: String = "",
    val customCategory: MedicationCategory = MedicationCategory.CUSTOM,
    val selectedMedicineUuid: UUID? = null,
    val preparationType: MedicinePreparationType? = null,
    val pillStrengthMg: String = "",
    val singleUseVialStrengthMg: String = "",
    val concentrationMgPerMl: String = "",
    val vialVolumeMl: String = "",
    val gelConcentrationPercent: String = "",
    val sachetWeightGrams: String = "",
    val containerWeightGrams: String = "",
    val patchTotalMg: String = "",
    val patchReleaseRateMcgPerDay: String = "",
    val displayName: String = "",
)

data class DoseInstructionDraftUiState(
    val applicationType: MedicationApplicationType,
    val preparationType: MedicinePreparationType,
    val tabletFractionNumerator: Int = 1,
    val tabletFractionDenominator: Int = 1,
    val volumeMl: String = "",
    val weightGrams: String = "",
)

data class ResolvedMedicationDraft(
    val medicineUuid: UUID,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
)

data class NewMedicineRequest(
    val selectionKind: MedicationSelectionKind,
    val medicationKey: MedicationKey?,
    val customMedicationName: String?,
    val displayName: String?,
    val category: MedicationCategory,
    val preparation: MedicinePreparation,
)

// ---------------------------------------------------------------------------
// Categories / catalog helpers
// ---------------------------------------------------------------------------

fun editorMedicationCategories(): List<MedicationCategory> {
    return MedicationCategory.entries.filterNot { it == MedicationCategory.TESTOSTERONE }
}

fun MedicinePickerUiState.catalog(): MedicationApplicationCatalog {
    return MedicationCatalog.catalogFor(category, applicationType)
}

fun MedicinePickerUiState.supportsCatalogSelection(): Boolean {
    return catalog().entries.any { it.medicationKey != null }
}

fun MedicinePickerUiState.supportsCustomName(): Boolean {
    return catalog().allowCustomMedicationName
}

fun MedicinePickerUiState.requiresCustomName(): Boolean {
    return !supportsCatalogSelection() || selectionKind == MedicationSelectionKind.CUSTOM
}

fun MedicinePickerUiState.availableCatalogKeys(): List<MedicationKey> {
    return catalog().entries.mapNotNull { it.medicationKey }
}

// ---------------------------------------------------------------------------
// Default-draft construction
// ---------------------------------------------------------------------------

fun defaultMedicineDraft(
    category: MedicationCategory = MedicationCategory.ESTRADIOL,
    applicationType: MedicationApplicationType =
        MedicationCatalog.applicationTypesFor(category).first(),
): MedicinePickerUiState {
    val resolvedApplicationType = MedicationCatalog.applicationTypesFor(category)
        .firstOrNull { it == applicationType }
        ?: MedicationCatalog.applicationTypesFor(category).first()
    val catalog = MedicationCatalog.catalogFor(category, resolvedApplicationType)
    val supportsCatalog = catalog.entries.any { it.medicationKey != null }
    val selectionKind = if (supportsCatalog) {
        MedicationSelectionKind.CATALOG
    } else {
        MedicationSelectionKind.CUSTOM
    }
    return MedicinePickerUiState(
        category = category,
        applicationType = resolvedApplicationType,
        selectionKind = selectionKind,
        medicationKey = catalog.entries.firstOrNull { it.medicationKey != null }?.medicationKey,
        customCategory = if (category == MedicationCategory.CUSTOM) {
            MedicationCategory.CUSTOM
        } else {
            category
        },
        preparationType = inferredPreparationType(resolvedApplicationType),
    )
}

// ---------------------------------------------------------------------------
// Preparation-type inference
// ---------------------------------------------------------------------------

// PILL and PATCH are inferred straight from the application route; INJECTION
// and GEL are ambiguous (single/multi vial, sachet/container) so the user must
// pick. PATCH_OFF carries no medicine, so it has no preparation.
internal fun inferredPreparationType(
    applicationType: MedicationApplicationType,
): MedicinePreparationType? {
    return when (applicationType) {
        MedicationApplicationType.ORAL,
        MedicationApplicationType.SUBLINGUAL -> MedicinePreparationType.PILL

        MedicationApplicationType.PATCH_ON -> MedicinePreparationType.PATCH

        MedicationApplicationType.INJECTION,
        MedicationApplicationType.GEL,
        MedicationApplicationType.PATCH_OFF -> null
    }
}

internal fun ambiguousPreparationTypes(
    applicationType: MedicationApplicationType,
): List<MedicinePreparationType> {
    return when (applicationType) {
        MedicationApplicationType.INJECTION -> listOf(
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        )

        MedicationApplicationType.GEL -> listOf(
            MedicinePreparationType.GEL_SACHET,
            MedicinePreparationType.GEL_CONTAINER,
        )

        else -> emptyList()
    }
}

fun MedicinePickerUiState.requiresPreparationTypeSelection(): Boolean {
    return inferredPreparationType(applicationType) == null &&
        ambiguousPreparationTypes(applicationType).isNotEmpty()
}

fun MedicinePickerUiState.inferredOrSelectedPreparationType(): MedicinePreparationType? {
    return inferredPreparationType(applicationType) ?: preparationType
}

// ---------------------------------------------------------------------------
// Draft transforms
// ---------------------------------------------------------------------------

fun MedicinePickerUiState.changeCategory(category: MedicationCategory): MedicinePickerUiState {
    return defaultMedicineDraft(
        category = category,
        applicationType = MedicationCatalog.applicationTypesFor(category).first(),
    ).copy(customMedicationName = customMedicationName)
}

fun MedicinePickerUiState.changeApplicationType(
    applicationType: MedicationApplicationType,
): MedicinePickerUiState {
    return defaultMedicineDraft(
        category = category,
        applicationType = applicationType,
    ).copy(customMedicationName = customMedicationName)
}

fun MedicinePickerUiState.changeMedicationKey(
    medicationKey: MedicationKey,
): MedicinePickerUiState {
    if (availableCatalogKeys().none { it == medicationKey }) {
        return this
    }
    return copy(
        selectionKind = MedicationSelectionKind.CATALOG,
        medicationKey = medicationKey,
        selectedMedicineUuid = null,
    )
}

fun MedicinePickerUiState.changePreparationType(
    preparationType: MedicinePreparationType,
): MedicinePickerUiState {
    if (preparationType !in ambiguousPreparationTypes(applicationType)) {
        return this
    }
    return copy(preparationType = preparationType)
}

// ---------------------------------------------------------------------------
// Count handling — preserved from the previous draft model, keyed on route.
// ---------------------------------------------------------------------------

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

fun MedicinePickerUiState.showsMedicationCountEditor(): Boolean {
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
        count = countStepBase(countText) + delta,
    )
}

fun resolveMedicationCountTextAfterDraftChange(
    previousDraft: MedicinePickerUiState,
    updatedDraft: MedicinePickerUiState,
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

// ---------------------------------------------------------------------------
// Dose-warning threshold (antiandrogen safety) — keyed on the total per-dose mg.
// ---------------------------------------------------------------------------

fun MedicinePickerUiState.doseWarningThresholdMg(): Double? {
    if (selectionKind != MedicationSelectionKind.CATALOG) {
        return null
    }
    return when (medicationKey) {
        MedicationKey.SPIRONOLACTONE -> 200.0
        MedicationKey.CYPROTERONE_ACETATE -> 12.5
        MedicationKey.BICALUTAMIDE -> 50.0
        else -> null
    }
}

// ---------------------------------------------------------------------------
// Conversion helpers
// ---------------------------------------------------------------------------

private fun parsePositiveDouble(text: String): Double? {
    return text.trim().toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
}

fun MedicinePickerUiState.toNewMedicineRequest(): NewMedicineRequest {
    val resolvedSelectionKind = if (requiresCustomName()) {
        MedicationSelectionKind.CUSTOM
    } else {
        MedicationSelectionKind.CATALOG
    }
    val resolvedCategory = if (resolvedSelectionKind == MedicationSelectionKind.CUSTOM &&
        category == MedicationCategory.CUSTOM
    ) {
        customCategory
    } else {
        category
    }
    return NewMedicineRequest(
        selectionKind = resolvedSelectionKind,
        medicationKey = if (resolvedSelectionKind == MedicationSelectionKind.CATALOG) {
            medicationKey
        } else {
            null
        },
        customMedicationName = if (resolvedSelectionKind == MedicationSelectionKind.CUSTOM) {
            customMedicationName.trim()
        } else {
            null
        },
        displayName = displayName.trim().takeIf(String::isNotBlank),
        category = resolvedCategory,
        preparation = toMedicinePreparation(),
    )
}

internal fun MedicinePickerUiState.toMedicinePreparation(): MedicinePreparation {
    return when (checkNotNull(inferredOrSelectedPreparationType())) {
        MedicinePreparationType.PILL -> MedicinePreparation.Pill(
            strengthMgPerTablet = checkNotNull(parsePositiveDouble(pillStrengthMg)),
        )

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = checkNotNull(parsePositiveDouble(singleUseVialStrengthMg)),
            )

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL ->
            MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = checkNotNull(parsePositiveDouble(concentrationMgPerMl)),
                vialVolumeMl = checkNotNull(parsePositiveDouble(vialVolumeMl)),
            )

        MedicinePreparationType.GEL_SACHET -> MedicinePreparation.GelSachet(
            concentrationPercent = checkNotNull(parsePositiveDouble(gelConcentrationPercent)),
            sachetWeightGrams = checkNotNull(parsePositiveDouble(sachetWeightGrams)),
        )

        MedicinePreparationType.GEL_CONTAINER -> MedicinePreparation.GelContainer(
            concentrationPercent = checkNotNull(parsePositiveDouble(gelConcentrationPercent)),
            containerWeightGrams = checkNotNull(parsePositiveDouble(containerWeightGrams)),
        )

        MedicinePreparationType.PATCH -> MedicinePreparation.Patch(
            specification = parsePositiveDouble(patchTotalMg)
                ?.let(MedicinePreparation.PatchSpecification::TotalMg)
                ?: MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    valueMcgPerDay = checkNotNull(
                        parsePositiveDouble(patchReleaseRateMcgPerDay),
                    ),
                ),
        )
    }
}

// The dose-instruction draft is seeded from the picker; its shape follows the
// resolved preparation type. PATCH_OFF carries a Noop dose with a placeholder
// preparation type (never read, since PATCH_OFF emits Noop unconditionally).
fun MedicinePickerUiState.toDoseInstructionDraft(): DoseInstructionDraftUiState {
    return DoseInstructionDraftUiState(
        applicationType = applicationType,
        preparationType = inferredOrSelectedPreparationType() ?: MedicinePreparationType.PILL,
    )
}

fun DoseInstructionDraftUiState.toDoseInstruction(): DoseInstruction {
    if (applicationType == MedicationApplicationType.PATCH_OFF) {
        return DoseInstruction.Noop
    }
    return when (preparationType) {
        MedicinePreparationType.PILL -> DoseInstruction.TabletFraction(
            numerator = tabletFractionNumerator,
            denominator = tabletFractionDenominator,
        )

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH -> DoseInstruction.WholeUnit

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> DoseInstruction.VolumeMl(
            valueMl = checkNotNull(parsePositiveDouble(volumeMl)),
        )

        MedicinePreparationType.GEL_CONTAINER -> DoseInstruction.WeightGrams(
            valueGrams = checkNotNull(parsePositiveDouble(weightGrams)),
        )
    }
}

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

fun MedicinePickerUiState.validationErrorRes(): Int? {
    // An existing medicine is fully resolved — nothing to validate in the form.
    if (selectedMedicineUuid != null) {
        return null
    }
    if (applicationType == MedicationApplicationType.PATCH_OFF) {
        return null
    }
    if (requiresCustomName() && customMedicationName.trim().isEmpty()) {
        return R.string.validation_name_required
    }
    if (!requiresCustomName() && medicationKey == null) {
        return R.string.validation_medication_selection_required
    }
    val resolvedPreparationType = inferredOrSelectedPreparationType()
        ?: return R.string.validation_preparation_type_required

    return when (resolvedPreparationType) {
        MedicinePreparationType.PILL ->
            R.string.validation_pill_strength_required
                .takeIf { parsePositiveDouble(pillStrengthMg) == null }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            R.string.validation_vial_strength_required
                .takeIf { parsePositiveDouble(singleUseVialStrengthMg) == null }

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> when {
            parsePositiveDouble(concentrationMgPerMl) == null ->
                R.string.validation_concentration_required

            parsePositiveDouble(vialVolumeMl) == null ->
                R.string.validation_vial_volume_required

            else -> null
        }

        MedicinePreparationType.GEL_SACHET -> when {
            parsePositiveDouble(gelConcentrationPercent) == null ->
                R.string.validation_gel_concentration_required

            parsePositiveDouble(sachetWeightGrams) == null ->
                R.string.validation_sachet_weight_required

            else -> null
        }

        MedicinePreparationType.GEL_CONTAINER -> when {
            parsePositiveDouble(gelConcentrationPercent) == null ->
                R.string.validation_gel_concentration_required

            parsePositiveDouble(containerWeightGrams) == null ->
                R.string.validation_container_weight_required

            else -> null
        }

        MedicinePreparationType.PATCH -> {
            val hasTotal = parsePositiveDouble(patchTotalMg) != null
            val hasReleaseRate = parsePositiveDouble(patchReleaseRateMcgPerDay) != null
            if (!hasTotal && !hasReleaseRate) {
                R.string.validation_patch_total_required
            } else {
                null
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Draft reconstruction from resolved domain values (group/log editing).
// ---------------------------------------------------------------------------

private fun Double.toInputString(): String {
    return java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
}

fun medicineDraftFromMedicine(
    medicine: Medicine,
    applicationType: MedicationApplicationType,
): MedicinePickerUiState {
    val base = defaultMedicineDraft(
        category = medicine.category,
        applicationType = applicationType,
    )
    val isCatalog = medicine.selection is MedicineSelection.Catalog
    return base.copy(
        selectionKind = if (isCatalog) {
            MedicationSelectionKind.CATALOG
        } else {
            MedicationSelectionKind.CUSTOM
        },
        medicationKey = (medicine.selection as? MedicineSelection.Catalog)?.medicationKey
            ?: base.medicationKey,
        customMedicationName = (medicine.selection as? MedicineSelection.Custom)
            ?.medicationName.orEmpty(),
        customCategory = medicine.category,
        selectedMedicineUuid = medicine.uuid,
        preparationType = medicine.preparation.type,
        displayName = medicine.displayName.orEmpty(),
    ).withPreparationFields(medicine.preparation)
}

private fun MedicinePickerUiState.withPreparationFields(
    preparation: MedicinePreparation,
): MedicinePickerUiState {
    return when (preparation) {
        is MedicinePreparation.Pill ->
            copy(pillStrengthMg = preparation.strengthMgPerTablet.toInputString())

        is MedicinePreparation.InjectionSingleUseVial ->
            copy(singleUseVialStrengthMg = preparation.strengthMgPerVial.toInputString())

        is MedicinePreparation.InjectionMultiUseVial -> copy(
            concentrationMgPerMl = preparation.concentrationMgPerMl.toInputString(),
            vialVolumeMl = preparation.vialVolumeMl.toInputString(),
        )

        is MedicinePreparation.GelSachet -> copy(
            gelConcentrationPercent = preparation.concentrationPercent.toInputString(),
            sachetWeightGrams = preparation.sachetWeightGrams.toInputString(),
        )

        is MedicinePreparation.GelContainer -> copy(
            gelConcentrationPercent = preparation.concentrationPercent.toInputString(),
            containerWeightGrams = preparation.containerWeightGrams.toInputString(),
        )

        is MedicinePreparation.Patch -> when (val spec = preparation.specification) {
            is MedicinePreparation.PatchSpecification.TotalMg ->
                copy(patchTotalMg = spec.valueMg.toInputString())

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay ->
                copy(patchReleaseRateMcgPerDay = spec.valueMcgPerDay.toInputString())
        }
    }
}

fun doseInstructionDraftFromInstruction(
    applicationType: MedicationApplicationType,
    preparationType: MedicinePreparationType,
    doseInstruction: DoseInstruction,
): DoseInstructionDraftUiState {
    return DoseInstructionDraftUiState(
        applicationType = applicationType,
        preparationType = preparationType,
        tabletFractionNumerator = (doseInstruction as? DoseInstruction.TabletFraction)
            ?.numerator ?: 1,
        tabletFractionDenominator = (doseInstruction as? DoseInstruction.TabletFraction)
            ?.denominator ?: 1,
        volumeMl = (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl?.toInputString()
            .orEmpty(),
        weightGrams = (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams
            ?.toInputString().orEmpty(),
    )
}

fun DoseInstructionDraftUiState.validationErrorRes(): Int? {
    if (applicationType == MedicationApplicationType.PATCH_OFF) {
        return null
    }
    return when (preparationType) {
        MedicinePreparationType.PILL ->
            R.string.validation_dose_tablet_fraction_required.takeIf {
                tabletFractionNumerator <= 0 || tabletFractionDenominator <= 0
            }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH -> null

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL ->
            R.string.validation_dose_volume_required
                .takeIf { parsePositiveDouble(volumeMl) == null }

        MedicinePreparationType.GEL_CONTAINER ->
            R.string.validation_dose_weight_required
                .takeIf { parsePositiveDouble(weightGrams) == null }
    }
}
