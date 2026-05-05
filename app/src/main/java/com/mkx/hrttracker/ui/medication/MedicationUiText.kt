package com.mkx.hrttracker.ui.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.customDoseDisplayUnit
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.model.medication.formatDoseFromCanonicalMg
import com.mkx.hrttracker.util.rememberAppLocale

@Composable
fun medicationDisplayName(details: MedicationDetails): String {
    return when (val selection = details.selection) {
        is MedicationSelection.Catalog -> stringResource(selection.medicationKey.labelRes)
        is MedicationSelection.Custom -> selection.medicationName
    }
}

@Composable
fun medicationDoseText(details: MedicationDetails): String? {
    val appLocale = rememberAppLocale()
    return when (val dose = details.dose) {
        is MedicationDose.MgAsMedicine -> {
            val doseUnit = details.customDoseDisplayUnit()
            stringResource(
                R.string.medication_dose_with_unit,
                doseUnit.formatDoseFromCanonicalMg(dose.valueMg, appLocale),
                stringResource(doseUnit.shortLabelRes)
            )
        }

        is MedicationDose.GelEquivalentEstradiolMg -> stringResource(
            R.string.medication_dose_mg_e2,
            dose.valueMg.formatDose(appLocale)
        )

        is MedicationDose.GelPercentAndWeight -> stringResource(
            R.string.medication_dose_percent_and_weight,
            dose.percent.formatDose(appLocale),
            dose.weightGrams.formatDose(appLocale)
        )

        is MedicationDose.PatchTotalMg -> stringResource(
            R.string.medication_dose_mg_e2,
            dose.valueMg.formatDose(appLocale)
        )

        is MedicationDose.PatchReleaseRateMcgPerDay -> stringResource(
            R.string.medication_dose_release_rate_mcg_day,
            dose.valueMcgPerDay.formatDose(appLocale)
        )

        MedicationDose.None -> null
    }
}

@Composable
internal fun medicationSupportingText(
    details: MedicationDetails,
    medicationCount: Int,
    extraSupportingText: String? = null,
): String {
    val applicationTypeLabel = stringResource(details.applicationType.labelRes)
    return listOfNotNull(
        applicationTypeLabel,
        medicationDoseText(details),
        medicationCountIndicatorText(medicationCount).takeIf { medicationCount > 1 },
        extraSupportingText?.takeIf(String::isNotBlank)
    ).joinToString(separator = " · ")
}

@Composable
internal fun medicationDoseSupportingText(
    details: MedicationDetails,
    medicationCount: Int,
): String {
    return listOfNotNull(
        medicationDoseText(details),
        medicationCountIndicatorText(medicationCount).takeIf { medicationCount > 1 },
    ).joinToString(separator = " · ")
}

fun medicationCountIndicatorText(count: Int): String = "${count}x"
