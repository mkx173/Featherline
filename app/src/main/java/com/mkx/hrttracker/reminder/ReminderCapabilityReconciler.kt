package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderCapabilityReconciler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val _state = MutableStateFlow(
        ReminderCapabilityState(
            hasNotificationAccess = canPostNotifications(context),
            hasExactAlarmAccess = canScheduleExactAlarms(context),
        )
    )
    val state: StateFlow<ReminderCapabilityState> = _state.asStateFlow()

    fun requestReconcile(reason: String) {
        diagnosticsLogger.info(TAG, "reminder_capability_request_reconcile reason=$reason")
        appScope.launch {
            runCatching { reconcile(reason) }
                .onFailure { throwable ->
                    diagnosticsLogger.warning(
                        TAG,
                        "reminder_capability_reconcile_failed reason=$reason",
                        throwable,
                    )
                }
        }
    }

    suspend fun reconcile(reason: String = "explicit") {
        val hasNotificationAccess = canPostNotifications(context)
        val hasExactAlarmAccess = canScheduleExactAlarms(context)
        diagnosticsLogger.info(
            TAG,
            "reminder_capability_reconcile_start reason=$reason " +
                    "notification=$hasNotificationAccess exactAlarm=$hasExactAlarmAccess"
        )

        _state.value = ReminderCapabilityState(
            hasNotificationAccess = hasNotificationAccess,
            hasExactAlarmAccess = hasExactAlarmAccess,
        )

        var disabledReminders = false
        if (!hasNotificationAccess) {
            val currentSettings = settingsRepository.getCurrentSettings()
            if (currentSettings.remindersEnabled) {
                settingsRepository.setRemindersEnabled(false)
                disabledReminders = true
                diagnosticsLogger.info(
                    TAG,
                    "reminder_capability_reconcile_disabled_master reason=$reason"
                )
            }
        }

        if (disabledReminders) {
            medicationReminderSnoozeScheduler.clearAllSnoozes()
        }

        medicationReminderScheduler.rescheduleAll()
        medicationReminderSnoozeScheduler.rescheduleAll()
        diagnosticsLogger.info(
            TAG,
            "reminder_capability_reconcile_complete reason=$reason " +
                    "disabledMaster=$disabledReminders"
        )
    }

    private companion object {
        const val TAG = "ReminderCapabilityReconciler"
    }
}
