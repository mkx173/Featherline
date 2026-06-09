package com.mkx.hrttracker.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun canPostNotifications(context: Context): Boolean {
    try {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
    } catch (_: Throwable) {
        // Notification service is not available in some environments like Android Studio preview.
        return true
    }

    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

fun canScheduleExactAlarms(
    context: Context,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean {
    if (sdkInt < Build.VERSION_CODES.S) return true
    return try {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        canScheduleExactAlarms(alarmManager, sdkInt)
    } catch (_: Throwable) {
        // AlarmManager unavailable (preview/test). Default to false so the scheduler
        // takes the inexact path; an optimistic true would push the caller down the
        // setExactAndAllowWhileIdle path and risk SecurityException on real devices.
        false
    }
}

internal fun canScheduleExactAlarms(
    alarmManager: AlarmManager,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean {
    if (sdkInt < Build.VERSION_CODES.S) return true
    return canScheduleExactAlarmsOnSOrAbove(alarmManager)
}

@SuppressLint("NewApi")
private fun canScheduleExactAlarmsOnSOrAbove(alarmManager: AlarmManager): Boolean {
    return try {
        alarmManager.canScheduleExactAlarms()
    } catch (_: Throwable) {
        false
    }
}

internal fun shouldShowNotificationPermissionRecoveryToast(
    sdkInt: Int,
    hasRequestedPermissionBefore: Boolean,
    shouldShowPermissionRationale: Boolean
): Boolean {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        return false
    }

    return hasRequestedPermissionBefore && !shouldShowPermissionRationale
}
