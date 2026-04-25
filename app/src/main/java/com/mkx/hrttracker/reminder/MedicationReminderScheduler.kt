package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        val groups = medicationGroupRepository.getGroups()

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            groups.forEach { group ->
                cancelReminder(group.uuid)
            }
            return
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(now)
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now
        )

        groups.forEach { group ->
            cancelReminder(group.uuid)
        }
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
    }

    suspend fun rescheduleGroup(
        groupUuid: UUID,
        after: LocalDateTime = LocalDateTime.now()
    ) {
        cancelReminder(groupUuid)

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            return
        }

        val group = medicationGroupRepository.getGroup(groupUuid) ?: return
        if (!group.notificationsEnabled) {
            return
        }

        val plan = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = medicationLogRepository.getScheduledGroupEntriesSince(after),
            now = after
        ).firstOrNull() ?: return

        scheduleReminder(plan)
    }

    fun cancelReminder(groupUuid: UUID) {
        alarmManager.cancel(buildReminderPendingIntent(groupUuid))
    }

    fun canScheduleExactReminders(): Boolean {
        return alarmManager.canScheduleExactAlarms()
    }

    private fun scheduleReminder(plan: MedicationReminderPlan) {
        val triggerAtMillis = plan.scheduledAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pendingIntent = buildReminderPendingIntent(
            groupUuid = plan.groupUuid,
            scheduledAt = plan.scheduledAt
        )

        if (canScheduleExactReminders()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun buildReminderPendingIntent(
        groupUuid: UUID,
        scheduledAt: LocalDateTime? = null
    ): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            putExtra(EXTRA_GROUP_UUID, groupUuid.toString())
            scheduledAt?.let { value ->
                putExtra(EXTRA_SCHEDULED_AT, value.toString())
            }
        }

        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(groupUuid),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal fun reminderRequestCode(groupUuid: UUID): Int {
    return (groupUuid.mostSignificantBits xor groupUuid.leastSignificantBits).toInt()
}

const val ACTION_MEDICATION_REMINDER = "com.mkx.hrttracker.action.MEDICATION_REMINDER"
const val EXTRA_GROUP_UUID = "groupUuid"
const val EXTRA_SCHEDULED_AT = "scheduledAt"
