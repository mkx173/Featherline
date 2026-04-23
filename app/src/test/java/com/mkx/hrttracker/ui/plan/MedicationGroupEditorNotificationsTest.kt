package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationGroupEditorNotificationsTest {
    @Test
    fun applyReminderSettingsToEditorState_sets_notifications_on_for_new_group_when_master_on() {
        val updated = applyReminderSettingsToEditorState(
            currentState = MedicationGroupEditorUiState(
                remindersEnabled = false,
                notificationsEnabled = false,
                hasResolvedNotificationDefault = false
            ),
            remindersEnabled = true
        )

        assertTrue(updated.remindersEnabled)
        assertTrue(updated.notificationsEnabled)
        assertTrue(updated.hasResolvedNotificationDefault)
    }

    @Test
    fun applyReminderSettingsToEditorState_does_not_override_existing_group_notifications() {
        val updated = applyReminderSettingsToEditorState(
            currentState = MedicationGroupEditorUiState(
                editingGroupId = "group-id",
                remindersEnabled = false,
                notificationsEnabled = false,
                hasResolvedNotificationDefault = true
            ),
            remindersEnabled = true
        )

        assertTrue(updated.remindersEnabled)
        assertFalse(updated.notificationsEnabled)
    }

    @Test
    fun applyReminderSettingsToEditorState_does_not_override_new_group_after_default_resolved() {
        val updated = applyReminderSettingsToEditorState(
            currentState = MedicationGroupEditorUiState(
                remindersEnabled = true,
                notificationsEnabled = false,
                hasResolvedNotificationDefault = true
            ),
            remindersEnabled = true
        )

        assertFalse(updated.notificationsEnabled)
    }

    @Test
    fun shouldOfferMasterReminderRecovery_returns_true_when_master_switch_is_off() {
        assertTrue(
            shouldOfferMasterReminderRecovery(
                remindersEnabled = false,
                notificationsToggleEnabled = false
            )
        )
    }

    @Test
    fun shouldOfferMasterReminderRecovery_returns_false_when_toggle_is_available() {
        assertFalse(
            shouldOfferMasterReminderRecovery(
                remindersEnabled = true,
                notificationsToggleEnabled = true
            )
        )
    }

    @Test
    fun shouldOfferMasterReminderRecovery_returns_false_when_disabled_for_non_master_reason() {
        assertFalse(
            shouldOfferMasterReminderRecovery(
                remindersEnabled = true,
                notificationsToggleEnabled = false
            )
        )
    }

    @Test
    fun shouldEnableMasterReminders_returns_true_only_for_master_and_group_request() {
        assertTrue(shouldEnableMasterReminders(MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST))
        assertFalse(shouldEnableMasterReminders(GROUP_ONLY_NOTIFICATION_ENABLE_REQUEST))
    }

    @Test
    fun shouldEnableGroupNotifications_returns_true_for_group_only_and_master_and_group() {
        assertTrue(shouldEnableGroupNotifications(GROUP_ONLY_NOTIFICATION_ENABLE_REQUEST))
        assertTrue(shouldEnableGroupNotifications(MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST))
        assertFalse(shouldEnableGroupNotifications(null))
    }
}
