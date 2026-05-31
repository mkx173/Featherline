package com.mkx.hrttracker.ui.onboarding

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingNotificationPermissionActionTest {
    @Test
    fun exactAlarmCard_hiddenBelowS_shownFromS_whenNotGranted() {
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = 30, exactAlarmGranted = false))
        assertTrue(shouldShowExactAlarmOnboardingCard(sdkInt = 31, exactAlarmGranted = false))
    }

    @Test
    fun exactAlarmCard_hidden_whenAlreadyGranted() {
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = 31, exactAlarmGranted = true))
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = Build.VERSION_CODES.TIRAMISU, exactAlarmGranted = true))
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, exactAlarmGranted = true))
    }

    @Test
    fun notificationOnboardingCardMode_followsGrantState() {
        assertTrue(shouldShowNotificationPermissionOnboardingCard(notificationsGranted = false))
        assertFalse(shouldShowNotificationPermissionOnboardingCard(notificationsGranted = true))
        assertFalse(shouldShowReminderMasterOnboardingCard(notificationsGranted = false))
        assertTrue(shouldShowReminderMasterOnboardingCard(notificationsGranted = true))
    }

    @Test
    fun reminderChoice_defaultsToNotificationGrantState_untilUserOverrides() {
        assertFalse(
            resolveOnboardingReminderChoice(
                notificationsGranted = false,
                reminderChoiceOverride = null,
            )
        )
        assertTrue(
            resolveOnboardingReminderChoice(
                notificationsGranted = true,
                reminderChoiceOverride = null,
            )
        )
        assertFalse(
            resolveOnboardingReminderChoice(
                notificationsGranted = true,
                reminderChoiceOverride = false,
            )
        )
        assertTrue(
            resolveOnboardingReminderChoice(
                notificationsGranted = false,
                reminderChoiceOverride = true,
            )
        )
    }

    @Test
    fun resolveOnboardingNotificationPermissionAction_shows_toast_when_runtime_dialog_is_suppressed() {
        assertEquals(
            OnboardingNotificationPermissionAction.SHOW_UNAVAILABLE_TOAST,
            resolveOnboardingNotificationPermissionAction(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRuntimePermission = false,
                areNotificationsEnabled = true,
                hasRequestedPermissionBefore = true,
                shouldShowPermissionRationale = false
            )
        )
    }

    @Test
    fun resolveOnboardingNotificationPermissionAction_requests_permission_on_first_android_13_request() {
        assertEquals(
            OnboardingNotificationPermissionAction.REQUEST_PERMISSION,
            resolveOnboardingNotificationPermissionAction(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRuntimePermission = false,
                areNotificationsEnabled = true,
                hasRequestedPermissionBefore = false,
                shouldShowPermissionRationale = false
            )
        )
    }

    @Test
    fun resolveOnboardingNotificationPermissionAction_opens_settings_before_runtime_permission() {
        assertEquals(
            OnboardingNotificationPermissionAction.OPEN_NOTIFICATION_SETTINGS,
            resolveOnboardingNotificationPermissionAction(
                sdkInt = Build.VERSION_CODES.S_V2,
                hasRuntimePermission = false,
                areNotificationsEnabled = false,
                hasRequestedPermissionBefore = true,
                shouldShowPermissionRationale = false
            )
        )
    }

    @Test
    fun resolveOnboardingNotificationPermissionAction_shows_toast_when_app_notifications_are_disabled() {
        assertEquals(
            OnboardingNotificationPermissionAction.SHOW_UNAVAILABLE_TOAST,
            resolveOnboardingNotificationPermissionAction(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRuntimePermission = true,
                areNotificationsEnabled = false,
                hasRequestedPermissionBefore = false,
                shouldShowPermissionRationale = false
            )
        )
    }
}
