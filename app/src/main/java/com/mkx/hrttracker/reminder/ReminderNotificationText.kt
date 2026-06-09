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

    data class TwoGroups(
        val firstGroupName: String,
        val secondGroupName: String,
    ) : ReminderNotificationBody

    data class MoreGroups(
        val firstGroupName: String,
        val secondGroupName: String,
        val additionalGroupCount: Int,
    ) : ReminderNotificationBody
}

internal fun buildReminderNotificationText(
    bundle: MedicationReminderBundle,
): ReminderNotificationText {
    val items = bundle.items
    val firstGroupName = items.first().groupName
    return when {
        items.size == 1 -> ReminderNotificationText(
            titleRes = R.string.reminder_notification_single_title,
            body = ReminderNotificationBody.GroupName(firstGroupName),
        )

        items.size == 2 -> ReminderNotificationText(
            titleRes = R.string.reminder_notification_merged_title,
            body = ReminderNotificationBody.TwoGroups(
                firstGroupName = firstGroupName,
                secondGroupName = items[1].groupName,
            ),
        )

        else -> ReminderNotificationText(
            titleRes = R.string.reminder_notification_merged_title,
            body = ReminderNotificationBody.MoreGroups(
                firstGroupName = firstGroupName,
                secondGroupName = items[1].groupName,
                additionalGroupCount = items.size - 2,
            ),
        )
    }
}
