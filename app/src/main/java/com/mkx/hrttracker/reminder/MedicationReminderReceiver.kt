package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
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

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val groupUuid = intent.getStringExtra(EXTRA_GROUP_UUID)
            ?.let(UUID::fromString)
            ?: return pendingResult.finish()
        val scheduledAt = intent.getStringExtra(EXTRA_SCHEDULED_AT)
            ?.let(LocalDateTime::parse)
            ?: LocalDateTime.now()

        appScope.launch {
            runCatching {
                if (!settingsRepository.getCurrentSettings().remindersEnabled) {
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
                    reminderNotificationManager.showDoseReminderNotification(bundle)
                }

                medicationReminderScheduler.rescheduleGroup(
                    groupUuid = groupUuid,
                    after = scheduledAt.plusSeconds(1)
                )
            }.also {
                pendingResult.finish()
            }
        }
    }
}
