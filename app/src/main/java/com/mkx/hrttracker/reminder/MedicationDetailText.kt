package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.util.medicationDisplayName
import com.mkx.hrttracker.util.medicationDoseText
import com.mkx.hrttracker.util.medicationRouteLabel

fun medicationDetailLine(
    context: Context,
    groupName: String,
    medication: MedicationGroupMedication,
): String {
    val name = medicationDisplayName(medication.details, context)
    val appType = medicationRouteLabel(medication.details, context)
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
