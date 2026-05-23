package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationCatalog
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationCatalogEntry
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import java.util.UUID

// ---------------------------------------------------------------------------
// New UI draft types (Task 6). The picker resolves a Medicine before producing
// a DoseInstruction; the two concerns are tracked by two focused draft types.
// ---------------------------------------------------------------------------

enum class PatchSpecKind { TOTAL_MG, RELEASE_RATE }

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
    val patchSpecKind: PatchSpecKind = PatchSpecKind.TOTAL_MG,
    val patchTotalMg: String = "",
    val patchReleaseRateMcgPerDay: String = "",
    val displayName: String = "",
    // The user-picked unit for raw-mass fields on a CUSTOM medicine — pill
    // strength, single-use vial strength, and patch total mg. Catalog medicines
    // ignore this field; their unit is always MG. The conversion to mg happens
    // on save (see [toMedicinePreparation]) — typing into the field doesn't
    // rescale the displayed value.
    val customDoseUnit: MedicineDisplayDoseUnit = MedicineDisplayDoseUnit.MG,
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
    // The picker's unit choice — only consulted when selectionKind == CUSTOM
    // (catalog medicines are always stored as MG).
    val displayDoseUnit: MedicineDisplayDoseUnit,
)

internal enum class TabletFractionOption(val numerator: Int, val denominator: Int) {
    QUARTER(1, 4),
    HALF(1, 2),
    WHOLE(1, 1);

    fun label(): String {
        return when (this) {
            QUARTER -> "¼"
            HALF -> "½"
            WHOLE -> "1"
        }
    }
}

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

fun MedicinePickerUiState.selectedCatalogEntry(): MedicationCatalogEntry {
    val catalog = catalog()
    return if (selectionKind == MedicationSelectionKind.CATALOG) {
        catalog.entries.firstOrNull { it.medicationKey == medicationKey } ?: catalog.entries.first()
    } else {
        catalog.entries.first()
    }
}

fun MedicinePickerUiState.activeDoseAssistPresets(): List<MedicationDoseAssistPreset> {
    if (selectionKind != MedicationSelectionKind.CATALOG) {
        return emptyList()
    }
    if (applicationType == MedicationApplicationType.PATCH_OFF) {
        return emptyList()
    }
    val preparationType = inferredOrSelectedPreparationType() ?: return emptyList()
    val presets = selectedCatalogEntry().doseAssistPresets[preparationType].orEmpty()
    return when (preparationType) {
        MedicinePreparationType.PATCH -> when (patchSpecKind) {
            PatchSpecKind.TOTAL_MG ->
                presets.filterIsInstance<MedicationDoseAssistPreset.PatchTotalMg>()

            PatchSpecKind.RELEASE_RATE ->
                presets.filterIsInstance<MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay>()
        }

        else -> presets
    }
}

fun activeDoseAssistPresets(
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState,
): List<MedicationDoseAssistPreset> {
    if (medicineDraft.selectionKind != MedicationSelectionKind.CATALOG) {
        return emptyList()
    }
    if (doseInstructionDraft.applicationType == MedicationApplicationType.PATCH_OFF) {
        return emptyList()
    }
    val presets = medicineDraft.selectedCatalogEntry()
        .doseAssistPresets[doseInstructionDraft.preparationType]
        .orEmpty()
    return when (doseInstructionDraft.preparationType) {
        MedicinePreparationType.GEL_CONTAINER ->
            presets.filterIsInstance<MedicationDoseAssistPreset.GelWeightGrams>()

        MedicinePreparationType.PILL,
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH,
        MedicinePreparationType.PATCH_OFF -> emptyList()
    }
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
        // Ambiguous routes (gel, injection) still need an initial preparation
        // or the dose form has nothing to render — falls back to the first
        // ambiguous option (container for gel, single-use vial for injection).
        preparationType = inferredPreparationType(resolvedApplicationType)
            ?: ambiguousPreparationTypes(resolvedApplicationType).firstOrNull(),
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

// The application routes a medicine of this preparation can use. PILL is the
// only ambiguous one (oral vs sublingual share the same pill); the rest map to
// a single route. PATCH_OFF preparation is compatible only with the PATCH_OFF
// route — the singleton can't be applied any other way.
fun applicationTypesCompatibleWithPreparation(
    preparationType: MedicinePreparationType,
): List<MedicationApplicationType> {
    return when (preparationType) {
        MedicinePreparationType.PILL -> listOf(
            MedicationApplicationType.ORAL,
            MedicationApplicationType.SUBLINGUAL,
        )
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> listOf(
            MedicationApplicationType.INJECTION,
        )
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.GEL_CONTAINER -> listOf(
            MedicationApplicationType.GEL,
        )
        MedicinePreparationType.PATCH -> listOf(
            MedicationApplicationType.PATCH_ON,
        )
        MedicinePreparationType.PATCH_OFF -> listOf(
            MedicationApplicationType.PATCH_OFF,
        )
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
            // Container before sachet: a refillable bottle is the more common
            // dispenser; sachet is the single-dose alternative.
            MedicinePreparationType.GEL_CONTAINER,
            MedicinePreparationType.GEL_SACHET,
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
    if (applicationType == this.applicationType) return this
    // For a resolved medicine, switching between routes that share its
    // preparation (PILL: oral ↔ sublingual) preserves the medicine selection;
    // any other switch resets the draft (the resolved medicine no longer fits).
    val preparation = inferredOrSelectedPreparationType()
    if (
        selectedMedicineUuid != null &&
        preparation != null &&
        applicationType in applicationTypesCompatibleWithPreparation(preparation)
    ) {
        return copy(applicationType = applicationType)
    }
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

fun MedicinePickerUiState.applyDoseAssistPreset(
    preset: MedicationDoseAssistPreset,
): MedicinePickerUiState {
    return when (preset) {
        is MedicationDoseAssistPreset.MgAsMedicine -> when (inferredOrSelectedPreparationType()) {
            MedicinePreparationType.PILL -> copy(pillStrengthMg = preset.valueMg)
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
                copy(singleUseVialStrengthMg = preset.valueMg)

            else -> this
        }

        is MedicationDoseAssistPreset.GelPercent -> copy(
            gelConcentrationPercent = preset.percent,
        )

        is MedicationDoseAssistPreset.GelWeightGrams -> when (inferredOrSelectedPreparationType()) {
            MedicinePreparationType.GEL_SACHET -> copy(sachetWeightGrams = preset.weightGrams)
            else -> this
        }

        is MedicationDoseAssistPreset.PatchTotalMg -> copy(
            patchSpecKind = PatchSpecKind.TOTAL_MG,
            patchTotalMg = preset.valueMg,
        )

        is MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay -> copy(
            patchSpecKind = PatchSpecKind.RELEASE_RATE,
            patchReleaseRateMcgPerDay = preset.valueMcgPerDay,
        )
    }
}

fun DoseInstructionDraftUiState.applyDoseAssistPreset(
    preset: MedicationDoseAssistPreset,
): DoseInstructionDraftUiState {
    return when (preset) {
        is MedicationDoseAssistPreset.GelWeightGrams ->
            if (preparationType == MedicinePreparationType.GEL_CONTAINER) {
                copy(weightGrams = preset.weightGrams)
            } else {
                this
            }

        is MedicationDoseAssistPreset.MgAsMedicine,
        is MedicationDoseAssistPreset.GelPercent,
        is MedicationDoseAssistPreset.PatchTotalMg,
        is MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay -> this
    }
}

// ---------------------------------------------------------------------------
// Count handling — preserved from the previous draft model, keyed on route.
// ---------------------------------------------------------------------------

fun MedicationApplicationType.supportsMedicationCountEditor(): Boolean {
    return when (this) {
        MedicationApplicationType.ORAL,
        MedicationApplicationType.SUBLINGUAL,
        MedicationApplicationType.PATCH_ON,
        // Injection count expresses "N vials / N injections of the given dose"
        // (e.g. 2 single-use vials at once, or 2 separate multi-use shots);
        // DoseInstructionCalculator already multiplies the per-unit equivalent
        // by count, so PK math handles this correctly.
        MedicationApplicationType.INJECTION -> true

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
//
// The old draft compared per-dose mg (typed directly into a single field) to
// the threshold. The new draft has no per-dose mg field: the dose is computed
// from the medicine's preparation strength + the dose instruction. For
// antiandrogens the only path is PILL + TabletFraction, so we derive
// per-instruction mg as `strengthMgPerTablet × numerator / denominator`, then
// multiply by `count` per the old semantics (warning fires on the total taken
// per occurrence). Strictly-greater-than comparison is preserved.
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

// Per-instruction mg derivable from the picker draft, when the route is a
// pill + tablet fraction. Other routes/preparations are not antiandrogen paths
// and return null so the warning never fires for them.
private fun MedicinePickerUiState.pickerPillPerInstructionMgOrNull(
    doseInstructionDraft: DoseInstructionDraftUiState,
): Double? {
    if (inferredOrSelectedPreparationType() != MedicinePreparationType.PILL) {
        return null
    }
    if (doseInstructionDraft.preparationType != MedicinePreparationType.PILL) {
        return null
    }
    val numerator = doseInstructionDraft.tabletFractionNumerator
    val denominator = doseInstructionDraft.tabletFractionDenominator
    if (numerator <= 0 || denominator <= 0) {
        return null
    }
    val strength = parsePositiveDouble(pillStrengthMg) ?: return null
    return strength * numerator / denominator
}

fun MedicinePickerUiState.exceedsDoseWarningThreshold(
    doseInstructionDraft: DoseInstructionDraftUiState,
    count: Int = 1,
): Boolean {
    val thresholdMg = doseWarningThresholdMg() ?: return false
    val perInstructionMg = pickerPillPerInstructionMgOrNull(doseInstructionDraft) ?: return false
    val resolvedCount = count.coerceAtLeast(1)
    return perInstructionMg * resolvedCount > thresholdMg
}

internal enum class MedicationSummaryTrailingIndicator {
    DOSE_WARNING,
}

internal fun medicationSummaryTrailingIndicator(
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    countText: String,
): MedicationSummaryTrailingIndicator? {
    if (doseInstructionDraft == null) {
        return null
    }
    val resolvedCount = parseMedicationCountText(countText)
    return if (medicineDraft.exceedsDoseWarningThreshold(doseInstructionDraft, resolvedCount)) {
        MedicationSummaryTrailingIndicator.DOSE_WARNING
    } else {
        null
    }
}

// String resource for the unit's short label (mg / μg / g) — used as the field
// suffix and as the segment label inside the picker. Lives in the UI layer so
// the model doesn't depend on R.
@androidx.annotation.StringRes
fun MedicineDisplayDoseUnit.shortLabelRes(): Int {
    return when (this) {
        MedicineDisplayDoseUnit.MG -> R.string.unit_mg
        MedicineDisplayDoseUnit.MCG -> R.string.unit_mcg
        MedicineDisplayDoseUnit.G -> R.string.unit_grams
    }
}

// Preparation types whose primary numeric input is a mass in milligrams —
// where it makes sense to let the user pick a typing unit (mg/μg/g) instead
// of forcing them to convert. Multi-use vials carry mg/mL, gels carry % + g,
// and patch release-rate carries μg/day, so none of those toggle.
fun MedicinePreparationType.hasRawMassDoseField(patchSpecKind: PatchSpecKind): Boolean {
    return when (this) {
        MedicinePreparationType.PILL,
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> true

        MedicinePreparationType.PATCH -> patchSpecKind == PatchSpecKind.TOTAL_MG

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.GEL_CONTAINER,
        // The singleton has no numeric fields, raw-mass or otherwise.
        MedicinePreparationType.PATCH_OFF -> false
    }
}

// Whether the picker should render its mg/μg/g unit selector for the current
// draft. Catalog medicines never see it; custom medicines see it only when the
// preparation has a raw-mass field per [hasRawMassDoseField].
fun MedicinePickerUiState.showsCustomDoseUnitPicker(): Boolean {
    if (selectionKind != MedicationSelectionKind.CUSTOM) {
        return false
    }
    val preparationType = inferredOrSelectedPreparationType() ?: return false
    return preparationType.hasRawMassDoseField(patchSpecKind)
}

// Routes whose dose is fully determined by the medicine identity (whole-unit
// patches/single-use vials/gel sachets, plus PATCH_OFF's Noop) need no
// editable dose form. Routes where the dose is a per-instruction quantity
// (mg per fraction of a pill, ml per multi-use-vial draw, grams per gel
// container dose) must keep the form visible even when the user has selected
// an existing medicine — otherwise the dose-instruction draft has no UI to
// populate volumeMl/weightGrams/tablet-fraction and validation blocks the
// save.
internal fun requiresEditableDoseInstructionForm(
    preparationType: MedicinePreparationType,
): Boolean {
    return when (preparationType) {
        MedicinePreparationType.PILL,
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        MedicinePreparationType.GEL_CONTAINER -> true

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH,
        // PATCH_OFF emits a Noop dose with no editable form.
        MedicinePreparationType.PATCH_OFF -> false
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
    // Catalog medicines force MG so a stray picker value on a draft that
    // started as custom and was switched to catalog can't bleed through.
    // Also force MG when the picker wouldn't have been visible for this
    // preparation type — e.g., a custom multi-use vial doesn't expose the
    // picker, so the customDoseUnit field may still carry a stale value from
    // an earlier PILL/PATCH preparation choice.
    val resolvedDisplayDoseUnit = if (
        resolvedSelectionKind == MedicationSelectionKind.CUSTOM &&
        showsCustomDoseUnitPicker()
    ) {
        customDoseUnit
    } else {
        MedicineDisplayDoseUnit.MG
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
        preparation = toMedicinePreparation(resolvedDisplayDoseUnit),
        displayDoseUnit = resolvedDisplayDoseUnit,
    )
}

internal fun MedicinePickerUiState.toMedicinePreparation(
    displayDoseUnit: MedicineDisplayDoseUnit = customDoseUnit,
): MedicinePreparation {
    // The raw-mass fields (pill strength, single-use vial strength, patch
    // total mg) are typed in the picker's unit; convert back to mg on save.
    // Other fields carry their own units (mg/mL, mL, %, g, μg/day) so they
    // pass through unchanged.
    return when (checkNotNull(inferredOrSelectedPreparationType())) {
        MedicinePreparationType.PILL -> MedicinePreparation.Pill(
            strengthMgPerTablet = displayDoseUnit.toMg(
                checkNotNull(parsePositiveDouble(pillStrengthMg)),
            ),
        )

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = displayDoseUnit.toMg(
                    checkNotNull(parsePositiveDouble(singleUseVialStrengthMg)),
                ),
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
            specification = when (patchSpecKind) {
                PatchSpecKind.TOTAL_MG -> MedicinePreparation.PatchSpecification.TotalMg(
                    valueMg = displayDoseUnit.toMg(
                        checkNotNull(parsePositiveDouble(patchTotalMg)),
                    ),
                )

                PatchSpecKind.RELEASE_RATE ->
                    MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                        valueMcgPerDay = checkNotNull(
                            parsePositiveDouble(patchReleaseRateMcgPerDay),
                        ),
                    )
            },
        )

        // PATCH_OFF is the global singleton — it is never created via the
        // picker. The medicine manager's create flow can't produce it; the
        // singleton is auto-created on the first patch medicine creation.
        MedicinePreparationType.PATCH_OFF ->
            error("PATCH_OFF is not creatable from the medicine picker.")
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

        // The applicationType guard above already returns Noop for PATCH_OFF;
        // if we ever reach this branch the picker is in an inconsistent state.
        MedicinePreparationType.PATCH_OFF -> DoseInstruction.Noop
    }
}

internal fun DoseInstructionDraftUiState.selectedTabletFractionOption(): TabletFractionOption {
    return TabletFractionOption.entries.firstOrNull { option ->
        option.numerator == tabletFractionNumerator &&
            option.denominator == tabletFractionDenominator
    } ?: TabletFractionOption.WHOLE
}

internal fun DoseInstructionDraftUiState.selectTabletFraction(
    option: TabletFractionOption,
): DoseInstructionDraftUiState {
    return copy(
        tabletFractionNumerator = option.numerator,
        tabletFractionDenominator = option.denominator,
    )
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

        MedicinePreparationType.PATCH -> when (patchSpecKind) {
            PatchSpecKind.TOTAL_MG ->
                R.string.validation_patch_total_required
                    .takeIf { parsePositiveDouble(patchTotalMg) == null }

            PatchSpecKind.RELEASE_RATE ->
                R.string.validation_patch_release_rate_required
                    .takeIf { parsePositiveDouble(patchReleaseRateMcgPerDay) == null }
        }

        // The singleton has no editable fields, so nothing to validate.
        MedicinePreparationType.PATCH_OFF -> null
    }
}

fun MedicinePickerUiState.selectedMedicineValidationErrorRes(): Int? {
    if (applicationType == MedicationApplicationType.PATCH_OFF) {
        return null
    }
    return R.string.validation_medication_selection_required
        .takeIf { selectedMedicineUuid == null }
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
    val resolvedApplicationType = compatibleApplicationTypeForMedicine(
        medicine = medicine,
        preferredApplicationType = applicationType,
    )
    val base = defaultMedicineDraft(
        category = medicine.category,
        applicationType = resolvedApplicationType,
    )
    val isCatalog = medicine.selection is MedicineSelection.Catalog
    // Catalog medicines always render in MG. Reusing the stored value avoids
    // showing the picker for a Custom medicine whose unit was set elsewhere.
    val resolvedDoseUnit = if (isCatalog) {
        MedicineDisplayDoseUnit.MG
    } else {
        medicine.displayDoseUnit
    }
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
        customDoseUnit = resolvedDoseUnit,
    ).withPreparationFields(medicine.preparation, resolvedDoseUnit)
}

fun compatibleApplicationTypeForMedicine(
    medicine: Medicine,
    preferredApplicationType: MedicationApplicationType,
): MedicationApplicationType {
    return when (medicine.preparation.type) {
        MedicinePreparationType.PILL -> when (preferredApplicationType) {
            MedicationApplicationType.ORAL,
            MedicationApplicationType.SUBLINGUAL -> preferredApplicationType

            MedicationApplicationType.INJECTION,
            MedicationApplicationType.GEL,
            MedicationApplicationType.PATCH_ON,
            MedicationApplicationType.PATCH_OFF -> MedicationApplicationType.ORAL
        }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> MedicationApplicationType.INJECTION

        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.GEL_CONTAINER -> MedicationApplicationType.GEL

        MedicinePreparationType.PATCH -> MedicationApplicationType.PATCH_ON

        // The singleton's only valid route. The slot draft sheet forces this
        // even if the caller passed something else.
        MedicinePreparationType.PATCH_OFF -> MedicationApplicationType.PATCH_OFF
    }
}

private fun MedicinePickerUiState.withPreparationFields(
    preparation: MedicinePreparation,
    // The unit the raw-mass values should be displayed in. For catalog
    // medicines (always MG) this is the identity transform; for custom
    // medicines whose picker stored MCG/G, the displayed value is rescaled
    // out of mg storage so the editor shows what the user originally typed.
    displayDoseUnit: MedicineDisplayDoseUnit = MedicineDisplayDoseUnit.MG,
): MedicinePickerUiState {
    return when (preparation) {
        is MedicinePreparation.Pill ->
            copy(pillStrengthMg = displayDoseUnit.fromMg(preparation.strengthMgPerTablet).toInputString())

        is MedicinePreparation.InjectionSingleUseVial ->
            copy(singleUseVialStrengthMg = displayDoseUnit.fromMg(preparation.strengthMgPerVial).toInputString())

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
                copy(
                    patchSpecKind = PatchSpecKind.TOTAL_MG,
                    patchTotalMg = displayDoseUnit.fromMg(spec.valueMg).toInputString(),
                )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay ->
                copy(
                    patchSpecKind = PatchSpecKind.RELEASE_RATE,
                    patchReleaseRateMcgPerDay = spec.valueMcgPerDay.toInputString(),
                )
        }

        // No numeric fields to populate; the singleton's draft is empty.
        is MedicinePreparation.PatchOff -> this
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

        // PATCH_OFF emits a Noop dose; the early return above for the
        // PATCH_OFF application route covers the normal flow, this branch
        // exists only for exhaustiveness.
        MedicinePreparationType.PATCH_OFF -> null
    }
}
