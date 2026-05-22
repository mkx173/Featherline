package com.mkx.hrttracker.ui.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale

@Composable
fun medicineDisplayName(medicine: Medicine): String {
    medicine.displayName?.takeIf(String::isNotBlank)?.let { return it }
    return when (val selection = medicine.selection) {
        is MedicineSelection.Catalog -> stringResource(selection.medicationKey.labelRes)
        is MedicineSelection.Custom -> selection.medicationName
    }
}

@Composable
fun medicinePreparationSummary(medicine: Medicine): String {
    val appLocale = rememberAppLocale()
    return when (val preparation = medicine.preparation) {
        is MedicinePreparation.Pill -> stringResource(
            R.string.medication_preparation_summary_pill,
            preparation.strengthMgPerTablet.formatDose(appLocale),
        )

        is MedicinePreparation.InjectionSingleUseVial -> stringResource(
            R.string.medication_preparation_summary_single_use_vial,
            preparation.strengthMgPerVial.formatDose(appLocale),
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

        is MedicinePreparation.Patch -> when (val spec = preparation.specification) {
            is MedicinePreparation.PatchSpecification.TotalMg -> stringResource(
                R.string.medication_preparation_summary_patch_total,
                spec.valueMg.formatDose(appLocale),
            )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> stringResource(
                R.string.medication_preparation_summary_patch_release_rate,
                spec.valueMcgPerDay.formatDose(appLocale),
            )
        }
    }
}

@Composable
fun doseInstructionSummary(instruction: DoseInstruction): String? {
    val appLocale = rememberAppLocale()
    return when (instruction) {
        is DoseInstruction.TabletFraction -> stringResource(
            R.string.dose_instruction_summary_tablet_fraction,
            formatTabletFraction(instruction, appLocale),
        )

        DoseInstruction.WholeUnit -> stringResource(R.string.dose_instruction_summary_whole_unit)

        is DoseInstruction.VolumeMl -> stringResource(
            R.string.dose_instruction_summary_volume_ml,
            instruction.valueMl.formatDose(appLocale),
        )

        is DoseInstruction.WeightGrams -> stringResource(
            R.string.dose_instruction_summary_weight_grams,
            instruction.valueGrams.formatDose(appLocale),
        )

        DoseInstruction.Noop -> null
    }
}

private fun formatTabletFraction(
    fraction: DoseInstruction.TabletFraction,
    appLocale: java.util.Locale,
): String {
    return if (fraction.denominator == 1) {
        fraction.numerator.toString()
    } else {
        (fraction.numerator.toDouble() / fraction.denominator.toDouble()).formatDose(appLocale)
    }
}

// Nullable-aware composers. A null `medicine` means PATCH_OFF — no medicine,
// no dose line; the entry is identified by application type alone.

@Composable
fun medicationEntryTitle(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
): String {
    return if (medicine != null) {
        medicineDisplayName(medicine)
    } else {
        stringResource(applicationType.labelRes)
    }
}

@Composable
fun medicationEntrySupportingText(
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
    applicationType: MedicationApplicationType,
    count: Int,
    extraSupportingText: String? = null,
): String {
    val applicationTypeLabel = stringResource(applicationType.labelRes)
    val doseText = if (medicine != null) {
        doseInstructionSummary(doseInstruction)
    } else {
        null
    }
    return listOfNotNull(
        applicationTypeLabel,
        doseText,
        medicationCountIndicatorText(count).takeIf { count > 1 },
        extraSupportingText?.takeIf(String::isNotBlank),
    ).joinToString(separator = " · ")
}

fun medicationCountIndicatorText(count: Int): String = "${count}x"
