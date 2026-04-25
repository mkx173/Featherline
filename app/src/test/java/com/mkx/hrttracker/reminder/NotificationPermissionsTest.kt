package com.mkx.hrttracker.reminder

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionsTest {
    @Test
    fun shouldShowNotificationPermissionRecoveryToast_returns_false_before_tiramisu() {
        assertFalse(
            shouldShowNotificationPermissionRecoveryToast(
                sdkInt = Build.VERSION_CODES.S_V2,
                hasRequestedPermissionBefore = true,
                shouldShowPermissionRationale = false
            )
        )
    }

    @Test
    fun shouldShowNotificationPermissionRecoveryToast_returns_false_on_first_request() {
        assertFalse(
            shouldShowNotificationPermissionRecoveryToast(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRequestedPermissionBefore = false,
                shouldShowPermissionRationale = false
            )
        )
    }

    @Test
    fun shouldShowNotificationPermissionRecoveryToast_returns_false_when_rationale_can_be_shown() {
        assertFalse(
            shouldShowNotificationPermissionRecoveryToast(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRequestedPermissionBefore = true,
                shouldShowPermissionRationale = true
            )
        )
    }

    @Test
    fun shouldShowNotificationPermissionRecoveryToast_returns_true_when_request_is_suppressed() {
        assertTrue(
            shouldShowNotificationPermissionRecoveryToast(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasRequestedPermissionBefore = true,
                shouldShowPermissionRationale = false
            )
        )
    }
}
