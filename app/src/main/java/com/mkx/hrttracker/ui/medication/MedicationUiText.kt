package com.mkx.hrttracker.ui.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
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
            R.string.medication_dose_mg,
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
fun medicationSummary(details: MedicationDetails): String {
    val displayName = medicationDisplayName(details)
    val applicationLabel = stringResource(details.applicationType.labelRes)
    val doseText = medicationDoseText(details)
    return if (doseText == null) {
        stringResource(R.string.medication_summary_without_dose, displayName, applicationLabel)
    } else {
        stringResource(R.string.medication_summary_with_dose, displayName, doseText, applicationLabel)
    }
}

fun applicationTypeBadgeLabelRes(applicationType: MedicationApplicationType): Int {
    return when (applicationType) {
        MedicationApplicationType.ORAL -> R.string.medication_application_badge_oral
        MedicationApplicationType.SUBLINGUAL -> R.string.medication_application_badge_sublingual
        MedicationApplicationType.INJECTION -> R.string.medication_application_badge_injection
        MedicationApplicationType.GEL -> R.string.medication_application_badge_gel
        MedicationApplicationType.PATCH_ON -> R.string.medication_application_badge_patch_on
        MedicationApplicationType.PATCH_OFF -> R.string.medication_application_badge_patch_off
    }
}

@Composable
fun applicationTypeBadgeLabel(applicationType: MedicationApplicationType): String {
    return stringResource(applicationTypeBadgeLabelRes(applicationType))
}

fun medicationCountIndicatorText(count: Int): String = "${count}×"
