package com.mkx.hrttracker.ui.onboarding

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingNotificationPermissionActionTest {
    @Test
    fun exactAlarmCard_hiddenBelowS_regardlessOfGrant() {
        // SCHEDULE_EXACT_ALARM does not exist before API 31, so the card never applies.
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = 30, exactAlarmGrantedAtStart = false))
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = 30, exactAlarmGrantedAtStart = true))
    }

    @Test
    fun exactAlarmCard_hidden_whenGrantedAtStart() {
        // From API 31 the permission is granted by default; if the user still has
        // it when onboarding begins there is nothing to ask, so the card is skipped.
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = 31, exactAlarmGrantedAtStart = true))
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = Build.VERSION_CODES.TIRAMISU, exactAlarmGrantedAtStart = true))
        assertFalse(shouldShowExactAlarmOnboardingCard(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, exactAlarmGrantedAtStart = true))
    }

    @Test
    fun exactAlarmCard_shown_whenRevokedAtStart_andStaysVisibleAfterGranting() {
        // Edge case: user revoked exact alarms before onboarding. We surface the
        // card, and because visibility keys off the captured start state it stays
        // visible (does not vanish) once the user re-grants during onboarding.
        assertTrue(shouldShowExactAlarmOnboardingCard(sdkInt = 31, exactAlarmGrantedAtStart = false))
    }

    @Test
    fun notificationPermissionCard_keysOffStartState_andStaysStable() {
        // Missing access at start: card is shown and stays shown through an
        // in-onboarding grant (it flips to a granted state rather than vanishing).
        assertTrue(shouldShowNotificationPermissionOnboardingCard(notificationsGrantedAtStart = false))
        // Already had access at start: no permission card (the master toggle is used).
        assertFalse(shouldShowNotificationPermissionOnboardingCard(notificationsGrantedAtStart = true))
    }

    @Test
    fun reminderMasterCard_onlyShownWhenAccessPredatedOnboarding() {
        // No access at all: nothing to toggle.
        assertFalse(
            shouldShowReminderMasterOnboardingCard(
                notificationsGranted = false,
                notificationsGrantedAtStart = false,
            )
        )
        // Granted during onboarding: the grant is the opt-in, so we skip the toggle.
        assertFalse(
            shouldShowReminderMasterOnboardingCard(
                notificationsGranted = true,
                notificationsGrantedAtStart = false,
            )
        )
        // Already had access before onboarding: ask whether to enable reminders.
        assertTrue(
            shouldShowReminderMasterOnboardingCard(
                notificationsGranted = true,
                notificationsGrantedAtStart = true,
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
