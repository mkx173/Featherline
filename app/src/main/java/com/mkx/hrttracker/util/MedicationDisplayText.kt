package com.mkx.hrttracker.util

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.formatDose
import java.util.Locale

fun medicineDisplayName(medicine: Medicine, context: Context): String {
    medicine.displayName?.takeIf(String::isNotBlank)?.let { return it }
    return when (val selection = medicine.selection) {
        is MedicineSelection.Catalog -> context.getString(selection.medicationKey.labelRes)
        is MedicineSelection.Custom -> selection.medicationName
        is MedicineSelection.PatchOff -> context.getString(R.string.medicine_patch_off_name)
    }
}

// Null `medicine` means PATCH_OFF — the entry is named by its application route.
fun medicationEntryTitle(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
    context: Context,
): String {
    return if (medicine != null) {
        medicineDisplayName(medicine, context)
    } else {
        context.getString(applicationType.labelRes)
    }
}

fun medicationRouteLabel(
    applicationType: MedicationApplicationType,
    context: Context,
): String = context.getString(applicationType.labelRes)

fun medicinePreparationSummary(medicine: Medicine, context: Context): String {
    val locale = Locale.getDefault()
    return when (val preparation = medicine.preparation) {
        is MedicinePreparation.Pill -> context.getString(
            R.string.medication_preparation_summary_pill,
            preparation.strengthMgPerTablet.formatDose(locale),
        )

        is MedicinePreparation.Capsule -> context.getString(
            R.string.medication_preparation_summary_capsule,
            preparation.strengthMgPerCapsule.formatDose(locale),
        )

        is MedicinePreparation.InjectionSingleUseVial -> context.getString(
            R.string.medication_preparation_summary_single_use_vial,
            preparation.strengthMgPerVial.formatDose(locale),
        )

        is MedicinePreparation.InjectionMultiUseVial -> context.getString(
            R.string.medication_preparation_summary_multi_use_vial,
            preparation.concentrationMgPerMl.formatDose(locale),
            preparation.vialVolumeMl.formatDose(locale),
        )

        is MedicinePreparation.GelSachet -> context.getString(
            R.string.medication_preparation_summary_gel_sachet,
            preparation.concentrationPercent.formatDose(locale),
            preparation.sachetWeightGrams.formatDose(locale),
        )

        is MedicinePreparation.GelContainer -> context.getString(
            R.string.medication_preparation_summary_gel_container,
            preparation.concentrationPercent.formatDose(locale),
            preparation.containerWeightGrams.formatDose(locale),
        )

        is MedicinePreparation.Patch -> when (val spec = preparation.specification) {
            is MedicinePreparation.PatchSpecification.TotalMg -> context.getString(
                R.string.medication_preparation_summary_patch_total,
                spec.valueMg.formatDose(locale),
            )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> context.getString(
                R.string.medication_preparation_summary_patch_release_rate,
                spec.valueMcgPerDay.formatDose(locale),
            )
        }

        is MedicinePreparation.PatchOff -> context.getString(R.string.medicine_patch_off_name)
    }
}

// Returns null for a PATCH_OFF entry (null medicine) or a Noop/empty dose.
fun doseInstructionText(
    context: Context,
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
): String? {
    if (medicine == null) {
        return null
    }
    val locale = Locale.getDefault()
    val portion = when (doseInstruction) {
        is DoseInstruction.TabletFraction -> context.getString(
            R.string.dose_instruction_summary_tablet_fraction,
            formatTabletFraction(doseInstruction),
        )
        is DoseInstruction.VolumeMl -> context.getString(
            R.string.dose_instruction_summary_volume_ml,
            doseInstruction.valueMl.formatDose(locale),
        )
        is DoseInstruction.WeightGrams -> context.getString(
            R.string.dose_instruction_summary_weight_grams,
            doseInstruction.valueGrams.formatDose(locale),
        )
        DoseInstruction.WholeUnit, DoseInstruction.Noop -> null
    }

    val active = DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(medicine, doseInstruction)
        ?.let { rate ->
            context.getString(
                R.string.dose_instruction_summary_patch_release_rate,
                rate.formatDose(locale),
            )
        }
        ?: DoseInstructionCalculator.perUnitAmountMg(medicine, doseInstruction)?.let { perUnitMg ->
            val displayUnit = if (medicine.selection is MedicineSelection.Custom) {
                medicine.displayDoseUnit
            } else {
                MedicineDisplayDoseUnit.MG
            }
            context.getString(
                R.string.dose_instruction_summary_active_amount,
                displayUnit.fromMg(perUnitMg).formatDose(locale),
                context.getString(displayUnit.shortLabelStringRes()),
            )
        }

    val parts = listOfNotNull(portion, active)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

fun medicationCountIndicatorText(
    context: Context,
    count: Int,
): String? {
    return if (count > 1) {
        context.getString(R.string.medication_count_multiplicity, count)
    } else {
        null
    }
}

@androidx.annotation.StringRes
private fun MedicineDisplayDoseUnit.shortLabelStringRes(): Int = when (this) {
    MedicineDisplayDoseUnit.MG -> R.string.unit_mg
    MedicineDisplayDoseUnit.MCG -> R.string.unit_mcg
    MedicineDisplayDoseUnit.G -> R.string.unit_grams
}

internal fun formatTabletFraction(fraction: DoseInstruction.TabletFraction): String {
    return if (fraction.denominator == 1) {
        fraction.numerator.toString()
    } else {
        "${fraction.numerator}/${fraction.denominator}"
    }
}
