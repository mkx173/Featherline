package com.mkx.hrttracker.ui.onboarding

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingDialogsTest {
    @Test
    fun onboardingReminderEnableAction_requests_notification_permission_first_on_tiramisu_plus() {
        val action = onboardingReminderEnableAction(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasRuntimeNotificationPermission = false,
            notificationsEnabled = true,
            canScheduleExactAlarms = true
        )

        assertEquals(OnboardingReminderEnableAction.REQUEST_NOTIFICATION_PERMISSION, action)
    }

    @Test
    fun onboardingReminderEnableAction_declines_when_notifications_are_unavailable() {
        val action = onboardingReminderEnableAction(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasRuntimeNotificationPermission = true,
            notificationsEnabled = false,
            canScheduleExactAlarms = true
        )

        assertEquals(OnboardingReminderEnableAction.DECLINE_NOTIFICATIONS_UNAVAILABLE, action)
    }

    @Test
    fun onboardingReminderEnableAction_requests_exact_alarm_after_notifications_are_ready() {
        val action = onboardingReminderEnableAction(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasRuntimeNotificationPermission = true,
            notificationsEnabled = true,
            canScheduleExactAlarms = false
        )

        assertEquals(OnboardingReminderEnableAction.REQUEST_EXACT_ALARM_ACCESS, action)
    }

    @Test
    fun onboardingReminderEnableAction_completes_when_all_permissions_are_ready() {
        val action = onboardingReminderEnableAction(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasRuntimeNotificationPermission = true,
            notificationsEnabled = true,
            canScheduleExactAlarms = true
        )

        assertEquals(OnboardingReminderEnableAction.COMPLETE_ENABLED, action)
    }
}
