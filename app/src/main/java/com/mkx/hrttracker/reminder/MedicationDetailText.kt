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
    val doseText = doseInstructionText(
        context = context,
        medicine = medication.medicine,
        doseInstruction = medication.doseInstruction,
        count = medication.count,
    )

    // For PATCH_OFF the title falls back to the route name; drop the duplicate.
    return listOfNotNull(groupName, name, appType.takeIf { it != name }, doseText)
        .joinToString(separator = " · ")
}

internal data class ExpandedDetailLines(
    val visibleLines: List<String>,
    val hiddenCount: Int,
)

// Cap on visible medication lines in the expanded notification body. Beyond
// this we append a localized "...and N more" suffix instead of letting the
// system silently truncate at MAX_CHARSEQUENCE_LENGTH (1024 chars in AOSP).
internal const val REMINDER_DETAIL_MAX_LINES = 3

internal fun buildExpandedDetailLines(
    items: List<MedicationReminderBundleItem>,
    maxLines: Int = REMINDER_DETAIL_MAX_LINES,
    renderLine: (groupName: String, medication: MedicationGroupMedication) -> String,
): ExpandedDetailLines {
    val allLines = items.flatMap { item ->
        item.medications.map { medication -> renderLine(item.groupName, medication) }
    }
    return if (allLines.size <= maxLines) {
        ExpandedDetailLines(visibleLines = allLines, hiddenCount = 0)
    } else {
        ExpandedDetailLines(
            visibleLines = allLines.take(maxLines),
            hiddenCount = allLines.size - maxLines,
        )
    }
}
