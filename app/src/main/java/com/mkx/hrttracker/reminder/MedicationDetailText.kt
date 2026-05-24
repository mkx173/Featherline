package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.medicationCountIndicatorText
import com.mkx.hrttracker.util.medicationEntryTitle
import com.mkx.hrttracker.util.medicationRouteLabel

fun medicationDetailLine(
    context: Context,
    groupName: String,
    medication: MedicationGroupMedication,
): String {
    // A PATCH_OFF slot has no medicine; medicine == null suppresses the dose line.
    val name = medicationEntryTitle(medication.medicine, medication.applicationType, context)
    val appType = medicationRouteLabel(medication.applicationType, context)
    val doseText = doseInstructionText(context, medication.medicine, medication.doseInstruction)
    val countText = medicationCountIndicatorText(context = context, count = medication.count)

    // For PATCH_OFF the title falls back to the route name; drop the duplicate.
    return listOfNotNull(groupName, name, appType.takeIf { it != name }, countText, doseText)
        .joinToString(separator = " · ")
}
