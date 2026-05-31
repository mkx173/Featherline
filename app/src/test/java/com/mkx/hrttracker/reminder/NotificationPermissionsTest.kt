package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionsTest {
    @Test
    fun canScheduleExactAlarms_belowS_returnsTrue_withoutSystemService() {
        val context = mockk<Context>()

        assertTrue(canScheduleExactAlarms(context, sdkInt = 30))

        verify(exactly = 0) { context.getSystemService(any<Class<*>>()) }
    }

    @Test
    fun canScheduleExactAlarms_belowS_returnsTrue_withoutCallingAlarmManager() {
        val alarmManager = mockk<AlarmManager>()

        assertTrue(canScheduleExactAlarms(alarmManager, sdkInt = 30))

        verify(exactly = 0) { alarmManager.canScheduleExactAlarms() }
    }

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
