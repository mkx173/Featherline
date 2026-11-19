package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReminderSupportStateTest {
    @Test
    fun notificationOffShowsOnlyOnceTurningRemindersOnWasDenied_andTakesPriorityOverExactAlarmOff() {
        // No message up front, as the Health Connect switch.
        assertEquals(
            SettingsReminderSupportState.NONE,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = false,
                hasExactAlarmAccess = false,
                remindersEnabled = true,
            ),
        )
        assertEquals(
            SettingsReminderSupportState.NOTIFICATION_OFF,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = false,
                hasExactAlarmAccess = false,
                remindersEnabled = false,
                isNotificationAccessDenied = true,
            ),
        )
        // Allowed in Android's settings since: the message goes.
        assertEquals(
            SettingsReminderSupportState.NONE,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = true,
                hasExactAlarmAccess = true,
                remindersEnabled = false,
                isNotificationAccessDenied = true,
            ),
        )
    }

    @Test
    fun turningRemindersOn_asksForThePermissionOnApi33_elsePointsToSettingsWhenBlocked() {
        val tiramisu = android.os.Build.VERSION_CODES.TIRAMISU
        assertEquals(
            ReminderEnableStep.REQUEST_PERMISSION,
            reminderEnableStep(tiramisu, hasPermission = false, areNotificationsEnabled = false),
        )
        // Granted, but notifications blocked for the app in Android's settings.
        assertEquals(
            ReminderEnableStep.ASK_TO_ALLOW_IN_SETTINGS,
            reminderEnableStep(tiramisu, hasPermission = true, areNotificationsEnabled = false),
        )
        // Below API 33 there is no runtime permission to ask for.
        assertEquals(
            ReminderEnableStep.ASK_TO_ALLOW_IN_SETTINGS,
            reminderEnableStep(tiramisu - 1, hasPermission = true, areNotificationsEnabled = false),
        )
        assertEquals(
            ReminderEnableStep.ENABLE,
            reminderEnableStep(tiramisu - 1, hasPermission = true, areNotificationsEnabled = true),
        )
        assertEquals(
            ReminderEnableStep.ENABLE,
            reminderEnableStep(tiramisu, hasPermission = true, areNotificationsEnabled = true),
        )
    }

    @Test
    fun exactAlarmOffShowsImmediatelyWhenNotificationAccessReturns() {
        assertEquals(
            SettingsReminderSupportState.EXACT_ALARM_OFF,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = true,
                hasExactAlarmAccess = false,
                remindersEnabled = true,
            ),
        )
    }

    @Test
    fun exactAlarmOffShowsWhileReminderEnableIsPending() {
        assertEquals(
            SettingsReminderSupportState.EXACT_ALARM_OFF,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = true,
                hasExactAlarmAccess = false,
                remindersEnabled = false,
                isReminderEnablePending = true,
            ),
        )
    }

    @Test
    fun exactAlarmOffDoesNotShowWhenRemindersAreDisabled() {
        assertEquals(
            SettingsReminderSupportState.NONE,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = true,
                hasExactAlarmAccess = false,
                remindersEnabled = false,
                isReminderEnablePending = false,
            ),
        )
    }

    @Test
    fun noSupportMessageWhenAllReminderCapabilitiesAreAvailable() {
        assertEquals(
            SettingsReminderSupportState.NONE,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = true,
                hasExactAlarmAccess = true,
                remindersEnabled = true,
            ),
        )
    }
}
