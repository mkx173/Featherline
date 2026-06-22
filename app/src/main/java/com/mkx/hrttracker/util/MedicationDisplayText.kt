package com.mkx.hrttracker.util

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.model.medication.formatPortion
import com.mkx.hrttracker.model.medication.isWholeOne
import com.mkx.hrttracker.model.medication.reduceTabletPortion
import java.util.Locale

fun medicineDisplayName(medicine: Medicine, context: Context): String {
    importedExternalMedicineDisplayKey(medicine)?.let { medicationKey ->
        return context.getString(medicationKey.labelRes)
    }
    medicine.displayName?.takeIf(String::isNotBlank)?.let { return it }
    return when (val selection = medicine.selection) {
        is MedicineSelection.Catalog -> context.getString(selection.medicationKey.labelRes)
        is MedicineSelection.Custom -> selection.medicationName
        is MedicineSelection.PatchOff -> context.getString(R.string.medicine_patch_off_name)
    }
}

fun importedExternalMedicineDisplayKey(medicine: Medicine): MedicationKey? {
    if (!medicine.importedFromExternalTracker) {
        return null
    }
    return when (val preparation = medicine.preparation) {
        is MedicinePreparation.ImportedInjection -> preparation.ester
        is MedicinePreparation.ImportedGel -> MedicationKey.ESTRADIOL
        else -> (medicine.selection as? MedicineSelection.Catalog)?.medicationKey
    }
}

// Null `medicine` means PATCH_OFF — titled by the removal medicine string, NOT the
// route label, which is the shortened "Patch" now shared with PATCH_ON.
fun medicationEntryTitle(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
    context: Context,
): String {
    return when {
        medicine != null -> medicineDisplayName(medicine, context)
        applicationType == MedicationApplicationType.PATCH_OFF ->
            context.getString(R.string.medicine_patch_off_name)

        else -> context.getString(applicationType.labelRes)
    }
}

fun medicationRouteLabel(
    applicationType: MedicationApplicationType,
    context: Context,
): String = context.getString(applicationType.labelRes)

fun medicinePreparationSummary(medicine: Medicine, context: Context): String {
    // Read the locale from the caller's context (the widget passes a settings-derived
    // localized context; the reminder passes the app context). currentAppLocale() ties
    // number formatting to the same context that resolves the strings — unlike
    // appLanguageLocale(), which is unreadable in a freshly-spawned widget process below
    // API 33 and would format numbers in the device locale while strings stay localized.
    val locale = context.currentAppLocale()
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

        is MedicinePreparation.ImportedInjection -> context.getString(
            R.string.medication_preparation_summary_imported_injection,
            preparation.administeredMg.formatDose(locale),
        )

        is MedicinePreparation.ImportedGel -> context.getString(
            R.string.medication_preparation_summary_imported_gel,
            preparation.appliedEstradiolMg.formatDose(locale),
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

        // PatchOff has no preparation to summarize; show the route label ("Patch")
        // so the card reads "Remove patch" (name) with "Patch" beneath, not the name twice.
        is MedicinePreparation.PatchOff -> context.getString(R.string.medication_application_patch_off)
    }
}

// Returns null for a PATCH_OFF entry (null medicine) or a Noop/empty dose.
fun doseInstructionText(
    context: Context,
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
    count: Int = 1,
    doseAmountDelta: Double? = null,
): String? {
    if (medicine == null) {
        return null
    }
    // Locale comes from the caller's context (see medicinePreparationSummary) so dose
    // numbers match the locale that context resolves strings in — correct for the
    // widget's settings-derived localized context even in a spawned process below API 33.
    val locale = context.currentAppLocale()
    if (count > 1) {
        return aggregateDoseInstructionText(
            context = context,
            medicine = medicine,
            doseInstruction = doseInstruction,
            count = count,
            doseAmountDelta = doseAmountDelta,
            locale = locale,
        )
    }

    // Render the actual administered amount (scheduled + delta) for measured
    // forms; ampules keep WholeUnit and carry the delta on the mg line below.
    val effectiveInstruction = DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
        preparation = medicine.preparation,
        doseInstruction = doseInstruction,
        doseAmountDelta = doseAmountDelta,
    )
    val portion = when (effectiveInstruction) {
        // A single whole tablet is implied by the active mg line; skip "1 tablet".
        is DoseInstruction.TabletFraction -> if (effectiveInstruction.numerator == 1 && effectiveInstruction.denominator == 1) {
            null
        } else {
            context.getString(
                R.string.dose_instruction_summary_tablet_fraction,
                formatTabletFraction(effectiveInstruction),
            )
        }

        is DoseInstruction.VolumeMl -> context.getString(
            R.string.dose_instruction_summary_volume_ml,
            effectiveInstruction.valueMl.formatDose(locale),
        )

        is DoseInstruction.WeightGrams -> context.getString(
            R.string.dose_instruction_summary_weight_grams,
            effectiveInstruction.valueGrams.formatDose(locale),
        )
        // Gel sachets dose one whole packet at a time but the packet's gram weight
        // is still useful context.
        DoseInstruction.WholeUnit -> (medicine.preparation as? MedicinePreparation.GelSachet)?.let {
            context.getString(
                R.string.dose_instruction_summary_weight_grams,
                it.sachetWeightGrams.formatDose(locale),
            )
        }

        DoseInstruction.Noop -> null
    }

    val activeAmount =
        DoseInstructionCalculator.perUnitAmountMg(medicine, doseInstruction, doseAmountDelta)
            ?.let { perUnitMg ->
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

    // Concentration-bearing preparations (multi-use vial, gel) show
    // "concentration · portion" so the row identifies which preparation
    // a log/slot refers to when one medicine has several preparations. The
    // active mass is not surfaced for these forms; the portion already reflects
    // the actual administered amount.
    concentrationSummary(context, locale, medicine.preparation)?.let { concentration ->
        return listOfNotNull(concentration, portion).joinToString(separator = " · ")
    }

    val active = DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(medicine, doseInstruction)
        ?.let { rate ->
            context.getString(
                R.string.dose_instruction_summary_patch_release_rate,
                rate.formatDose(locale),
            )
        }
        ?: activeAmount

    val parts = listOfNotNull(portion, active)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

private fun aggregateDoseInstructionText(
    context: Context,
    medicine: Medicine,
    doseInstruction: DoseInstruction,
    count: Int,
    doseAmountDelta: Double?,
    locale: Locale,
): String? {
    val effectiveInstruction = DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
        preparation = medicine.preparation,
        doseInstruction = doseInstruction,
        doseAmountDelta = doseAmountDelta,
    )
    concentrationSummary(context, locale, medicine.preparation)?.let { concentration ->
        return aggregateConcentrationDoseInstructionText(
            context = context,
            preparation = medicine.preparation,
            effectiveInstruction = effectiveInstruction,
            count = count,
            concentration = concentration,
            locale = locale,
        )
    }

    val portion = aggregateDosePortionText(
        context = context,
        preparation = medicine.preparation,
        effectiveInstruction = effectiveInstruction,
        count = count,
        locale = locale,
    )
    val active = DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(medicine, doseInstruction)
        ?.let { rate ->
            context.getString(
                R.string.dose_instruction_summary_patch_release_rate,
                (rate * count).formatDose(locale),
            )
        }
        ?: aggregateActiveAmountText(
            context = context,
            medicine = medicine,
            doseInstruction = doseInstruction,
            count = count,
            doseAmountDelta = doseAmountDelta,
            locale = locale,
        )

    val parts = listOfNotNull(portion, active)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

private fun aggregateConcentrationDoseInstructionText(
    context: Context,
    preparation: MedicinePreparation,
    effectiveInstruction: DoseInstruction,
    count: Int,
    concentration: String,
    locale: Locale,
): String? = when {
    preparation is MedicinePreparation.GelSachet && effectiveInstruction == DoseInstruction.WholeUnit -> {
        val sachets = doseCountNoun(context, R.plurals.stock_count_sachets, count)
        val totalWeight = context.getString(
            R.string.dose_instruction_summary_weight_grams,
            (preparation.sachetWeightGrams * count).formatDose(locale),
        )
        listOf(sachets, concentration, totalWeight).joinToString(separator = " · ")
    }

    effectiveInstruction is DoseInstruction.VolumeMl -> {
        val portion = context.getString(
            R.string.dose_instruction_summary_volume_ml,
            (effectiveInstruction.valueMl * count).formatDose(locale),
        )
        listOf(concentration, portion).joinToString(separator = " · ")
    }

    effectiveInstruction is DoseInstruction.WeightGrams -> {
        val portion = context.getString(
            R.string.dose_instruction_summary_weight_grams,
            (effectiveInstruction.valueGrams * count).formatDose(locale),
        )
        listOf(concentration, portion).joinToString(separator = " · ")
    }

    effectiveInstruction == DoseInstruction.Noop -> null
    else -> concentration
}

private fun aggregateDosePortionText(
    context: Context,
    preparation: MedicinePreparation,
    effectiveInstruction: DoseInstruction,
    count: Int,
    locale: Locale,
): String? = when {
    preparation is MedicinePreparation.Pill && effectiveInstruction is DoseInstruction.TabletFraction -> {
        val foldedPortion = reduceTabletPortion(
            numerator = effectiveInstruction.numerator,
            denominator = effectiveInstruction.denominator,
            count = count,
        )
        if (foldedPortion.isWholeOne()) {
            null
        } else {
            context.getString(
                R.string.dose_instruction_summary_tablet_fraction,
                foldedPortion.formatPortion(locale),
            )
        }
    }

    preparation is MedicinePreparation.Capsule && effectiveInstruction == DoseInstruction.WholeUnit ->
        doseCountNoun(context, R.plurals.stock_count_capsules, count)

    preparation is MedicinePreparation.InjectionSingleUseVial && effectiveInstruction == DoseInstruction.WholeUnit ->
        doseCountNoun(context, R.plurals.stock_count_vials, count)

    preparation is MedicinePreparation.Patch && effectiveInstruction == DoseInstruction.WholeUnit ->
        doseCountNoun(context, R.plurals.stock_count_patches, count)

    else -> null
}

private fun aggregateActiveAmountText(
    context: Context,
    medicine: Medicine,
    doseInstruction: DoseInstruction,
    count: Int,
    doseAmountDelta: Double?,
    locale: Locale,
): String? {
    val totalMg = DoseInstructionCalculator.totalAmountMg(
        perUnitAmountMg = DoseInstructionCalculator.perUnitAmountMg(
            medicine,
            doseInstruction,
            doseAmountDelta
        ),
        count = count,
    ) ?: return null
    val displayUnit = if (medicine.selection is MedicineSelection.Custom) {
        medicine.displayDoseUnit
    } else {
        MedicineDisplayDoseUnit.MG
    }
    return context.getString(
        R.string.dose_instruction_summary_active_amount,
        displayUnit.fromMg(totalMg).formatDose(locale),
        context.getString(displayUnit.shortLabelStringRes()),
    )
}

private fun doseCountNoun(
    context: Context,
    pluralResId: Int,
    count: Int,
): String {
    // Dose summaries intentionally reuse stock noun plurals; the localized nouns are identical.
    val noun = context.resources.getQuantityString(pluralResId, count)
    return "$count $noun"
}

private fun concentrationSummary(
    context: Context,
    locale: Locale,
    preparation: MedicinePreparation,
): String? = when (preparation) {
    is MedicinePreparation.InjectionMultiUseVial -> context.getString(
        R.string.dose_instruction_summary_concentration_mg_per_ml,
        preparation.concentrationMgPerMl.formatDose(locale),
    )

    is MedicinePreparation.GelSachet -> context.getString(
        R.string.dose_instruction_summary_concentration_percent,
        preparation.concentrationPercent.formatDose(locale),
    )

    is MedicinePreparation.GelContainer -> context.getString(
        R.string.dose_instruction_summary_concentration_percent,
        preparation.concentrationPercent.formatDose(locale),
    )

    else -> null
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
