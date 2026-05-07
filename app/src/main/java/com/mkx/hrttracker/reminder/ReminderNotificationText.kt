package com.mkx.hrttracker.reminder

import androidx.annotation.StringRes
import com.mkx.hrttracker.R

internal data class ReminderNotificationText(
    @param:StringRes val titleRes: Int,
    val body: ReminderNotificationBody,
)

internal sealed interface ReminderNotificationBody {
    data class GroupName(
        val groupName: String,
    ) : ReminderNotificationBody

    data class MoreGroups(
        val firstGroupName: String,
        val additionalGroupCount: Int,
    ) : ReminderNotificationBody
}

internal fun buildReminderNotificationText(
    bundle: MedicationReminderBundle,
): ReminderNotificationText {
    val firstGroupName = bundle.items.first().groupName
    return if (bundle.items.size > 1) {
        ReminderNotificationText(
            titleRes = R.string.reminder_notification_merged_title,
            body = ReminderNotificationBody.MoreGroups(
                firstGroupName = firstGroupName,
                additionalGroupCount = bundle.items.size - 1,
            ),
        )
    } else {
        ReminderNotificationText(
            titleRes = R.string.reminder_notification_single_title,
            body = ReminderNotificationBody.GroupName(firstGroupName),
        )
    }
}
