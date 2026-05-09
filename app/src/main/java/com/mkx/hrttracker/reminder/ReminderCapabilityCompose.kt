package com.mkx.hrttracker.reminder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderCapabilityReconcilerEntryPoint {
    fun reminderCapabilityReconciler(): ReminderCapabilityReconciler
}

@Composable
fun rememberReminderCapabilityReconciler(): ReminderCapabilityReconciler {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ReminderCapabilityReconcilerEntryPoint::class.java,
        ).reminderCapabilityReconciler()
    }
}
