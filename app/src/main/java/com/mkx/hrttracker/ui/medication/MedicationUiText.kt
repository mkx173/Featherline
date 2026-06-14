package com.mkx.hrttracker.ui.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale

@Composable
fun medicineDisplayName(medicine: Medicine): String {
    medicine.displayName?.takeIf(String::isNotBlank)?.let { return it }
    return when (val selection = medicine.selection) {
        is MedicineSelection.Catalog -> stringResource(selection.medicationKey.labelRes)
        is MedicineSelection.Custom -> selection.medicationName
        // Localized name for the global singleton row.
        is MedicineSelection.PatchOff -> stringResource(R.string.medicine_patch_off_name)
    }
}

@Composable
fun medicinePreparationSummary(medicine: Medicine): String {
    val appLocale = rememberAppLocale()
    // Catalog medicines always display in MG; custom medicines respect the
    // unit the user picked when creating them so the summary reads the way
    // they typed it.
    val displayUnit = if (medicine.selection is MedicineSelection.Custom) {
        medicine.displayDoseUnit
    } else {
        MedicineDisplayDoseUnit.MG
    }
    val unitLabel = stringResource(displayUnit.shortLabelRes())
    return when (val preparation = medicine.preparation) {
        is MedicinePreparation.Pill -> stringResource(
            R.string.medication_preparation_summary_pill_with_unit,
            displayUnit.fromMg(preparation.strengthMgPerTablet).formatDose(appLocale),
            unitLabel,
        )

        is MedicinePreparation.Capsule -> stringResource(
            R.string.medication_preparation_summary_capsule_with_unit,
            displayUnit.fromMg(preparation.strengthMgPerCapsule).formatDose(appLocale),
            unitLabel,
        )

        is MedicinePreparation.InjectionSingleUseVial -> stringResource(
            R.string.medication_preparation_summary_single_use_vial_with_unit,
            displayUnit.fromMg(preparation.strengthMgPerVial).formatDose(appLocale),
            unitLabel,
        )

        is MedicinePreparation.InjectionMultiUseVial -> stringResource(
            R.string.medication_preparation_summary_multi_use_vial,
            preparation.concentrationMgPerMl.formatDose(appLocale),
            preparation.vialVolumeMl.formatDose(appLocale),
        )

        is MedicinePreparation.GelSachet -> stringResource(
            R.string.medication_preparation_summary_gel_sachet,
            preparation.concentrationPercent.formatDose(appLocale),
            preparation.sachetWeightGrams.formatDose(appLocale),
        )

        is MedicinePreparation.GelContainer -> stringResource(
            R.string.medication_preparation_summary_gel_container,
            preparation.concentrationPercent.formatDose(appLocale),
            preparation.containerWeightGrams.formatDose(appLocale),
        )

        is MedicinePreparation.ImportedInjection -> stringResource(
            R.string.medication_preparation_summary_single_use_vial_with_unit,
            displayUnit.fromMg(preparation.administeredMg).formatDose(appLocale),
            unitLabel,
        )

        is MedicinePreparation.ImportedGel -> stringResource(
            R.string.medication_preparation_summary_imported_gel,
            preparation.appliedEstradiolMg.formatDose(appLocale),
        )

        is MedicinePreparation.Patch -> when (val spec = preparation.specification) {
            is MedicinePreparation.PatchSpecification.TotalMg -> stringResource(
                R.string.medication_preparation_summary_patch_total_with_unit,
                displayUnit.fromMg(spec.valueMg).formatDose(appLocale),
                unitLabel,
            )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> stringResource(
                R.string.medication_preparation_summary_patch_release_rate,
                spec.valueMcgPerDay.formatDose(appLocale),
            )
        }

        // Singleton has no strength; the summary just names the action.
        // PatchOff has no preparation to summarize; show the route label ("Patch")
        // so the card reads "Remove patch" (name) with "Patch" beneath, not the name twice.
        is MedicinePreparation.PatchOff -> stringResource(R.string.medication_application_patch_off)
    }
}

// Compose wrapper over the single source of truth in MedicationDisplayText's
// doseInstructionText. Both the in-app cards (here) and the widget/reminder (the
// Context-based callers) must render identical dose summaries, so the branch
// logic lives in one place; this only supplies the Composition's Context.
@Composable
fun doseInstructionSummary(
    medicine: Medicine,
    instruction: DoseInstruction,
    count: Int = 1,
    doseAmountDelta: Double? = null,
): String? = doseInstructionText(
    context = LocalContext.current,
    medicine = medicine,
    doseInstruction = instruction,
    count = count,
    doseAmountDelta = doseAmountDelta,
)

internal fun formatTabletFraction(fraction: DoseInstruction.TabletFraction): String {
    return if (fraction.denominator == 1) {
        fraction.numerator.toString()
    } else {
        "${fraction.numerator}/${fraction.denominator}"
    }
}

// Nullable-aware composers. A null `medicine` means PATCH_OFF — no medicine,
// no dose line; the entry is identified by application type alone.

@Composable
fun medicationEntryTitle(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
): String {
    return when {
        medicine != null -> medicineDisplayName(medicine)
        // PATCH_OFF is titled by the removal string, not the route label (shortened
        // "Patch", shared with PATCH_ON), so the "Remove" cue survives.
        applicationType == MedicationApplicationType.PATCH_OFF ->
            stringResource(R.string.medicine_patch_off_name)

        else -> stringResource(applicationType.labelRes)
    }
}

@Composable
fun medicationEntrySupportingText(
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
    applicationType: MedicationApplicationType,
    count: Int,
    extraSupportingText: String? = null,
    doseAmountDelta: Double? = null,
): String {
    val applicationTypeLabel = stringResource(applicationType.labelRes)
        .takeIf {
            shouldIncludeApplicationTypeInSupportingText(
                hasMedicine = medicine != null,
                applicationType = applicationType,
            )
        }
    val doseText = medicine?.let {
        doseInstructionSummary(
            medicine = it,
            instruction = doseInstruction,
            count = count,
            doseAmountDelta = doseAmountDelta,
        )
    }
    return listOfNotNull(
        applicationTypeLabel,
        doseText,
        extraSupportingText?.takeIf(String::isNotBlank),
    ).joinToString(separator = " · ")
}

internal fun shouldUseApplicationTypeAsMedicationEntryTitle(
    hasMedicine: Boolean,
    applicationType: MedicationApplicationType,
): Boolean {
    return !hasMedicine
}

internal fun shouldIncludeApplicationTypeInSupportingText(
    hasMedicine: Boolean,
    applicationType: MedicationApplicationType,
): Boolean {
    return hasMedicine || applicationType == MedicationApplicationType.PATCH_OFF
}
