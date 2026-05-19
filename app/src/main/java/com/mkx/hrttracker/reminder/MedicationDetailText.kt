package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.customDoseDisplayUnit
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.model.medication.formatDoseFromCanonicalMg
import com.mkx.hrttracker.ui.medication.labelRes
import com.mkx.hrttracker.ui.medication.shortLabelRes
import java.util.Locale

fun medicationDetailLine(
    context: Context,
    groupName: String,
    medication: MedicationGroupMedication,
): String {
    val name = when (val sel = medication.details.selection) {
        is MedicationSelection.Catalog -> context.getString(sel.medicationKey.labelRes)
        is MedicationSelection.Custom -> sel.medicationName
    }
    val appType = context.getString(medication.details.applicationType.labelRes)
    val doseText = medicationDoseText(context, medication)
    val countText = if (medication.count > 1) " · ${medication.count}x" else ""

    return buildString {
        append(groupName)
        append(" · ")
        append(name)
        append(" · ")
        append(appType)
        if (doseText != null) {
            append(" · ")
            append(doseText)
        }
        append(countText)
    }
}

fun medicationDisplayName(details: MedicationDetails, context: Context): String =
    when (val sel = details.selection) {
        is MedicationSelection.Catalog -> context.getString(sel.medicationKey.labelRes)
        is MedicationSelection.Custom -> sel.medicationName
    }

fun medicationRouteLabel(details: MedicationDetails, context: Context): String =
    context.getString(details.applicationType.labelRes)

internal fun medicationDoseText(context: Context, details: MedicationDetails): String? {
    val locale = Locale.getDefault()
    return when (val dose = details.dose) {
        is MedicationDose.MgAsMedicine -> {
            val unit = details.customDoseDisplayUnit()
            context.getString(R.string.medication_dose_with_unit,
                unit.formatDoseFromCanonicalMg(dose.valueMg, locale),
                context.getString(unit.shortLabelRes))
        }
        is MedicationDose.GelEquivalentEstradiolMg ->
            context.getString(R.string.medication_dose_mg_e2,
                MedicationDoseUnit.MG.formatDoseFromCanonicalMg(dose.valueMg, locale))
        is MedicationDose.PatchTotalMg ->
            context.getString(R.string.medication_dose_mg_e2,
                MedicationDoseUnit.MG.formatDoseFromCanonicalMg(dose.valueMg, locale))
        is MedicationDose.PatchReleaseRateMcgPerDay ->
            context.getString(R.string.medication_dose_release_rate_mcg_day,
                dose.valueMcgPerDay.formatDose(locale))
        is MedicationDose.GelPercentAndWeight ->
            context.getString(R.string.medication_dose_percent_and_weight,
                dose.percent.formatDose(locale),
                dose.weightGrams.formatDose(locale))
        MedicationDose.None -> null
    }
}

internal fun medicationDoseText(context: Context, medication: MedicationGroupMedication): String? =
    medicationDoseText(context, medication.details)
