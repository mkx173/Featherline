package com.mkx.hrttracker.reminder

data class ReminderCapabilityState(
    val hasNotificationAccess: Boolean = true,
    val hasExactAlarmAccess: Boolean = true,
)
