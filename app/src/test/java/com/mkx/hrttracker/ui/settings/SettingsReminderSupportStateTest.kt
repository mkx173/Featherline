package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReminderSupportStateTest {
    @Test
    fun notificationOffTakesPriorityOverExactAlarmOff() {
        assertEquals(
            SettingsReminderSupportState.NOTIFICATION_OFF,
            resolveSettingsReminderSupportState(
                hasNotificationAccess = false,
                hasExactAlarmAccess = false,
                remindersEnabled = true,
            ),
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
