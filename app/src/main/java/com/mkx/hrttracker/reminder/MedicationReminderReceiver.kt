package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MedicationReminderReceiver : BroadcastReceiver() {
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var medicationGroupRepository: MedicationGroupRepository

    @Inject
    lateinit var medicationLogRepository: MedicationLogRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var medicationReminderScheduler: MedicationReminderScheduler

    @Inject
    lateinit var reminderNotificationManager: ReminderNotificationManager

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val groupUuid = intent.getStringExtra(EXTRA_GROUP_UUID)
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            ?: return pendingResult.finish().also {
                diagnosticsLogger.info(
                    TAG,
                    "reminder_receiver_ignored reason=missing_or_invalid_group_uuid action=${intent.action}"
                )
            }
        val scheduledAt = intent.getStringExtra(EXTRA_SCHEDULED_AT)
            ?.let(LocalDateTime::parse)
            ?: LocalDateTime.now()

        diagnosticsLogger.info(
            TAG,
            "reminder_receiver_received groupUuid=$groupUuid scheduledAt=$scheduledAt action=${intent.action}"
        )
        appScope.launch {
            runCatching {
                val settings = settingsRepository.getCurrentSettings()
                if (!settings.remindersEnabled) {
                    diagnosticsLogger.info(
                        TAG,
                        "reminder_receiver_master_disabled groupUuid=$groupUuid scheduledAt=$scheduledAt"
                    )
                    medicationReminderScheduler.cancelReminder(groupUuid)
                    return@runCatching
                }

                val groups = medicationGroupRepository.getGroups()
                val entries = medicationLogRepository.getScheduledGroupEntriesSince(scheduledAt)
                val bundle = buildMedicationReminderBundle(
                    scheduledAt = scheduledAt,
                    groups = groups,
                    entries = entries,
                )
                if (bundle != null) {
                    reminderNotificationManager.showDoseReminderNotification(
                        bundle,
                        hideMedicationDetails = settings.hideMedicationDetails,
                    )
                    diagnosticsLogger.info(
                        TAG,
                        "reminder_receiver_notification_shown groupUuid=$groupUuid " +
                            "scheduledAt=$scheduledAt groups=${groups.size} entries=${entries.size} " +
                            "items=${bundle.items.size}"
                    )
                } else {
                    diagnosticsLogger.info(
                        TAG,
                        "reminder_receiver_notification_skipped reason=no_bundle groupUuid=$groupUuid " +
                            "scheduledAt=$scheduledAt groups=${groups.size} entries=${entries.size}"
                    )
                }

                medicationReminderScheduler.rescheduleGroup(
                    groupUuid = groupUuid,
                    after = scheduledAt.plusSeconds(1)
                )
                diagnosticsLogger.info(
                    TAG,
                    "reminder_receiver_reschedule_requested groupUuid=$groupUuid after=${scheduledAt.plusSeconds(1)}"
                )
            }.onFailure { throwable ->
                diagnosticsLogger.warning(
                    TAG,
                    "reminder_receiver_failed groupUuid=$groupUuid scheduledAt=$scheduledAt",
                    throwable,
                )
            }.also {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MedicationReminderReceiver"
    }
}
