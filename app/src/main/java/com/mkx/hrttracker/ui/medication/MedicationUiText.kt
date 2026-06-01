package com.mkx.hrttracker.ui.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
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
        is MedicinePreparation.PatchOff -> stringResource(R.string.medicine_patch_off_name)
    }
}

@Composable
fun doseInstructionSummary(
    medicine: Medicine,
    instruction: DoseInstruction,
    doseAmountDelta: Double? = null,
): String? {
    val appLocale = rememberAppLocale()
    // Render the actual administered amount (scheduled + delta) for measured
    // forms; ampules keep WholeUnit and carry the delta on the mg line below.
    val effectiveInstruction = DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
        preparation = medicine.preparation,
        doseInstruction = instruction,
        doseAmountDelta = doseAmountDelta,
    )
    val portion = when (effectiveInstruction) {
        // A single whole tablet is implied by the active mg line; skip "1 tablet".
        is DoseInstruction.TabletFraction -> if (effectiveInstruction.numerator == 1 && effectiveInstruction.denominator == 1) {
            null
        } else {
            stringResource(
                R.string.dose_instruction_summary_tablet_fraction,
                formatTabletFraction(effectiveInstruction),
            )
        }
        is DoseInstruction.VolumeMl -> stringResource(
            R.string.dose_instruction_summary_volume_ml,
            effectiveInstruction.valueMl.formatDose(appLocale),
        )
        is DoseInstruction.WeightGrams -> stringResource(
            R.string.dose_instruction_summary_weight_grams,
            effectiveInstruction.valueGrams.formatDose(appLocale),
        )
        // Gel sachets dose one whole packet at a time but the packet's gram weight
        // is still useful context.
        DoseInstruction.WholeUnit -> (medicine.preparation as? MedicinePreparation.GelSachet)?.let {
            stringResource(
                R.string.dose_instruction_summary_weight_grams,
                it.sachetWeightGrams.formatDose(appLocale),
            )
        }
        DoseInstruction.Noop -> null
    }

    val activeAmount = DoseInstructionCalculator.perUnitAmountMg(medicine, instruction, doseAmountDelta)
        ?.let { perUnitMg ->
            val displayUnit = if (medicine.selection is MedicineSelection.Custom) {
                medicine.displayDoseUnit
            } else {
                MedicineDisplayDoseUnit.MG
            }
            stringResource(
                R.string.dose_instruction_summary_active_amount,
                displayUnit.fromMg(perUnitMg).formatDose(appLocale),
                stringResource(displayUnit.shortLabelRes()),
            )
        }

    // Concentration-bearing preparations (multi-use vial, gel) show
    // "concentration · portion" so the row identifies which preparation
    // a log/slot refers to when one medicine has several preparations. The
    // active mass is not surfaced for these forms; the portion already reflects
    // the actual administered amount.
    val concentration = concentrationSummary(medicine.preparation, appLocale)
    if (concentration != null) {
        return listOfNotNull(concentration, portion).joinToString(separator = " · ")
    }

    val active = DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(medicine, instruction)?.let { rate ->
        stringResource(
            R.string.dose_instruction_summary_patch_release_rate,
            rate.formatDose(appLocale),
        )
    } ?: activeAmount

    val parts = listOfNotNull(portion, active)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

@Composable
private fun concentrationSummary(
    preparation: MedicinePreparation,
    locale: java.util.Locale,
): String? = when (preparation) {
    is MedicinePreparation.InjectionMultiUseVial -> stringResource(
        R.string.dose_instruction_summary_concentration_mg_per_ml,
        preparation.concentrationMgPerMl.formatDose(locale),
    )
    is MedicinePreparation.GelSachet -> stringResource(
        R.string.dose_instruction_summary_concentration_percent,
        preparation.concentrationPercent.formatDose(locale),
    )
    is MedicinePreparation.GelContainer -> stringResource(
        R.string.dose_instruction_summary_concentration_percent,
        preparation.concentrationPercent.formatDose(locale),
    )
    else -> null
}

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
        .takeIf {
            shouldIncludeApplicationTypeInSupportingText(
                hasMedicine = medicine != null,
                applicationType = applicationType,
            )
        }
    val doseText = medicine?.let { doseInstructionSummary(it, doseInstruction) }
    return listOfNotNull(
        applicationTypeLabel,
        medicationCountIndicatorText(count),
        doseText,
        extraSupportingText?.takeIf(String::isNotBlank),
    ).joinToString(separator = " · ")
}

@Composable
fun medicationCountIndicatorText(count: Int): String? {
    return if (count > 1) {
        stringResource(R.string.medication_count_multiplicity, count)
    } else {
        null
    }
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
