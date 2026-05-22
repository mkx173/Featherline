package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.util.doseInstructionText
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
