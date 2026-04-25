package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

fun canPostNotifications(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return false
    }

    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

fun canScheduleExactAlarms(context: Context): Boolean {
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
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
