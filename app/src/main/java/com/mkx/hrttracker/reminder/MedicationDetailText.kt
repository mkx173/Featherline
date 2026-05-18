package com.mkx.hrttracker.reminder

import android.content.Context
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
            "${unit.formatDoseFromCanonicalMg(dose.valueMg, locale)} ${context.getString(unit.shortLabelRes)}"
        }
        is MedicationDose.GelEquivalentEstradiolMg ->
            "${MedicationDoseUnit.MG.formatDoseFromCanonicalMg(dose.valueMg, locale)} ${context.getString(MedicationDoseUnit.MG.shortLabelRes)}"
        is MedicationDose.PatchTotalMg ->
            "${MedicationDoseUnit.MG.formatDoseFromCanonicalMg(dose.valueMg, locale)} ${context.getString(MedicationDoseUnit.MG.shortLabelRes)}"
        is MedicationDose.PatchReleaseRateMcgPerDay ->
            "${dose.valueMcgPerDay.formatDose(locale)} ${context.getString(MedicationDoseUnit.MCG.shortLabelRes)}"
        is MedicationDose.GelPercentAndWeight ->
            "${dose.percent.formatDose(locale)}% ${dose.weightGrams.formatDose(locale)} ${context.getString(MedicationDoseUnit.G.shortLabelRes)}"
        MedicationDose.None -> null
    }
}

internal fun medicationDoseText(context: Context, medication: MedicationGroupMedication): String? =
    medicationDoseText(context, medication.details)
