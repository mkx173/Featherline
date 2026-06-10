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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Serializes reconciles so a stacked burst of requests (e.g. several
    // pause/resume cycles around a permission dialog) runs one at a time: the
    // first applies the change, the rest observe an unchanged snapshot and
    // short-circuit. Also closes the interleaving where a stale pass could
    // cancel-all after a newer pass had already re-applied alarms.
    private val reconcileMutex = Mutex()

    // The capability snapshot the alarms were last (re)scheduled against. Null
    // until the first reconcile, so the first run of a process always applies
    // once even when capability matches the construction-time seed.
    private var lastReconciledSnapshot: ReminderCapabilityState? = null

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

    /**
     * Reconciles reminder scheduling against the current notification /
     * exact-alarm capability. Cheap when nothing changed: callers fire this on
     * every resume, but the master-disable check and the (expensive) full
     * reschedule only run when a capability actually flipped since the last
     * reconcile — or when [forceReschedule] is set for system events (boot,
     * time / time-zone change) that invalidate already-scheduled alarms
     * regardless of capability.
     *
     * Firing reminders advance themselves (the firing receiver reschedules its
     * own group), so a per-resume reschedule was only ever a self-heal net, not
     * part of the normal advance cycle — skipping it when capability is
     * unchanged loses no alarms.
     */
    suspend fun reconcile(
        reason: String = "explicit",
        forceReschedule: Boolean = false,
    ) = reconcileMutex.withLock {
        val snapshot = ReminderCapabilityState(
            hasNotificationAccess = canPostNotifications(context),
            hasExactAlarmAccess = canScheduleExactAlarms(context),
        )
        _state.value = snapshot

        val capabilityChanged = snapshot != lastReconciledSnapshot
        diagnosticsLogger.info(
            TAG,
            "reminder_capability_reconcile_start reason=$reason " +
                    "notification=${snapshot.hasNotificationAccess} " +
                    "exactAlarm=${snapshot.hasExactAlarmAccess} " +
                    "changed=$capabilityChanged force=$forceReschedule"
        )

        if (!capabilityChanged && !forceReschedule) {
            diagnosticsLogger.info(TAG, "reminder_capability_reconcile_skipped reason=$reason")
            return@withLock
        }

        var disabledReminders = false
        if (!snapshot.hasNotificationAccess) {
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
        lastReconciledSnapshot = snapshot
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
