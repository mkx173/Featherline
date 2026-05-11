package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class ReminderNotificationTextTest {
    @Test
    fun buildReminderNotificationText_uses_friendly_single_group_copy() {
        val text = buildReminderNotificationText(
            bundle = reminderBundle(groupNames = listOf("Estradiol"))
        )

        assertEquals(R.string.reminder_notification_single_title, text.titleRes)
        assertEquals(
            ReminderNotificationBody.GroupName("Estradiol"),
            text.body
        )
    }

    @Test
    fun buildReminderNotificationText_lists_both_groups_when_exactly_two() {
        val text = buildReminderNotificationText(
            bundle = reminderBundle(groupNames = listOf("Estradiol", "Spiro"))
        )

        assertEquals(R.string.reminder_notification_merged_title, text.titleRes)
        assertEquals(
            ReminderNotificationBody.TwoGroups(
                firstGroupName = "Estradiol",
                secondGroupName = "Spiro",
            ),
            text.body
        )
    }

    @Test
    fun buildReminderNotificationText_summarizes_merged_groups_with_two_names_and_more_count() {
        val text = buildReminderNotificationText(
            bundle = reminderBundle(
                groupNames = listOf("Estradiol", "Spiro", "Progesterone", "Finasteride")
            )
        )

        assertEquals(R.string.reminder_notification_merged_title, text.titleRes)
        assertEquals(
            ReminderNotificationBody.MoreGroups(
                firstGroupName = "Estradiol",
                secondGroupName = "Spiro",
                additionalGroupCount = 2,
            ),
            text.body
        )
    }

    private fun reminderBundle(
        groupNames: List<String>,
    ): MedicationReminderBundle {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        return MedicationReminderBundle(
            scheduledAt = scheduledAt,
            items = groupNames.mapIndexed { index, groupName ->
                MedicationReminderBundleItem(
                    slot = MedicationReminderSlot(
                        groupUuid = UUID.nameUUIDFromBytes(groupName.toByteArray()),
                        scheduledAt = scheduledAt,
                        scheduleTimeUuid = UUID.nameUUIDFromBytes("slot-$index".toByteArray()),
                    ),
                    groupName = groupName,
                    medications = emptyList(),
                )
            },
        )
    }
}
