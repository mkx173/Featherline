package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
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
    fun applyReminderSettingsToEditorState_preserves_group_notifications_choice_when_master_turns_off() {
        val updated = applyReminderSettingsToEditorState(
            currentState = MedicationGroupEditorUiState(
                editingGroupId = "group-id",
                remindersEnabled = true,
                notificationsEnabled = true,
                hasResolvedNotificationDefault = true
            ),
            remindersEnabled = false
        )

        assertFalse(updated.remindersEnabled)
        assertTrue(updated.notificationsEnabled)
    }

    @Test
    fun resolveNotificationSupportState_prioritizes_access_off() {
        assertEquals(
            NotificationSupportState.ACCESS_OFF,
            resolveNotificationSupportState(
                hasNotificationAccess = false,
                remindersEnabled = true,
                showInexactReminderWarning = true
            )
        )
    }

    @Test
    fun resolveNotificationSupportState_returns_master_off_when_access_available_and_master_off() {
        assertEquals(
            NotificationSupportState.MASTER_OFF,
            resolveNotificationSupportState(
                hasNotificationAccess = true,
                remindersEnabled = false,
                showInexactReminderWarning = false
            )
        )
    }

    @Test
    fun resolveNotificationSupportState_returns_inexact_when_only_exact_alarm_access_is_missing() {
        assertEquals(
            NotificationSupportState.INEXACT,
            resolveNotificationSupportState(
                hasNotificationAccess = true,
                remindersEnabled = true,
                showInexactReminderWarning = true
            )
        )
    }

    @Test
    fun resolveNotificationSupportState_returns_none_when_no_support_message_is_needed() {
        assertEquals(
            NotificationSupportState.NONE,
            resolveNotificationSupportState(
                hasNotificationAccess = true,
                remindersEnabled = true,
                showInexactReminderWarning = false
            )
        )
    }

    @Test
    fun resolveNotificationSupportState_prioritizes_master_off_over_inexact() {
        assertEquals(
            NotificationSupportState.MASTER_OFF,
            resolveNotificationSupportState(
                hasNotificationAccess = true,
                remindersEnabled = false,
                showInexactReminderWarning = true
            )
        )
    }

    @Test
    fun resolveInexactReminderWarningVisibility_returns_true_when_exact_alarm_access_is_missing() {
        assertTrue(
            resolveInexactReminderWarningVisibility(
                hasNotificationAccess = true,
                canScheduleExactAlarms = false
            )
        )
    }

    @Test
    fun resolveExactAlarmUiStateAfterEnablingReminders_warns_without_dialog_when_exact_alarm_access_missing() {
        val state = resolveExactAlarmUiStateAfterEnablingReminders(
            canScheduleExactAlarms = false
        )

        assertTrue(state.showInexactReminderWarning)
        assertFalse(state.showExactAlarmDialog)
    }

    @Test
    fun resolveExactAlarmUiStateAfterEnablingReminders_clears_warning_when_exact_alarm_access_available() {
        val state = resolveExactAlarmUiStateAfterEnablingReminders(
            canScheduleExactAlarms = true
        )

        assertFalse(state.showInexactReminderWarning)
        assertFalse(state.showExactAlarmDialog)
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
