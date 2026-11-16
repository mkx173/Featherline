package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the alarms that re-post a dose reminder while its dose is still pending
 * ("repeat until logged"). Shaped like [MedicationReminderSnoozeScheduler]:
 * records live in [MedicationReminderRepeatStore], records sharing a time share
 * one alarm, and the alarm fires [MedicationReminderActionReceiver] with
 * [ACTION_MEDICATION_REMINDER_REPEAT_ALARM].
 *
 * A chain starts when a reminder is posted (first fire or returning snooze),
 * moves one link per repeat, and ends on log, snooze, dismiss, the repeat
 * limit, or the group's next dose falling due. Every check of what is still
 * pending happens when the alarm fires, so a stale record costs one wake-up,
 * never a reminder for a dose already logged.
 */
@Singleton
class MedicationReminderRepeatScheduler private constructor(
    private val context: Context,
    private val repeatStore: MedicationReminderRepeatStore,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger,
    private val sdkIntProvider: () -> Int,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repeatStore: MedicationReminderRepeatStore,
        settingsRepository: SettingsRepository,
        diagnosticsLogger: AppDiagnosticsLogger,
    ) : this(
        context = context,
        repeatStore = repeatStore,
        settingsRepository = settingsRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { Build.VERSION.SDK_INT },
    )

    internal constructor(
        context: Context,
        repeatStore: MedicationReminderRepeatStore,
        settingsRepository: SettingsRepository,
        diagnosticsLogger: AppDiagnosticsLogger,
        sdkInt: Int,
    ) : this(
        context = context,
        repeatStore = repeatStore,
        settingsRepository = settingsRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { sdkInt },
    )

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * Arms the next repeat for [slots], one interval after [now], or ends their
     * chains when repeats are off or a slot used up its limit. Returns the
     * records armed.
     */
    suspend fun scheduleNextRepeats(
        slots: List<MedicationReminderSlot>,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<MedicationReminderRepeatRecord> {
        if (slots.isEmpty()) {
            return emptyList()
        }
        val settings = settingsRepository.getCurrentSettings()
        val persistent = settings.persistentReminders
        if (!settings.remindersEnabled || !persistent.repeatEnabled) {
            diagnosticsLogger.info(
                TAG,
                "repeat_schedule_skipped reason=disabled slots=${slots.size} " +
                        "master=${settings.remindersEnabled} repeat=${persistent.repeatEnabled}"
            )
            clearRepeatsForSlots(slots)
            return emptyList()
        }

        // A plan left out of persistent reminders never starts or keeps a chain.
        val (includedSlots, excludedSlots) = slots.partition { slot -> persistent.appliesTo(slot.groupUuid) }
        if (excludedSlots.isNotEmpty()) {
            clearRepeatsForSlots(excludedSlots)
        }
        if (includedSlots.isEmpty()) {
            return emptyList()
        }

        val existingRecords = repeatStore.getRepeatRecords()
        val repeatAt = now.plusMinutes(persistent.repeatInterval.minutes)
        val nextRecords = buildNextRepeatRecords(
            slots = includedSlots,
            existingRecords = existingRecords,
            repeatAt = repeatAt,
            maxRepeats = persistent.repeatLimit.maxRepeats,
        )
        // A slot can sit in an older bundle; drop it there so that alarm no
        // longer re-posts it alongside the new one.
        clearSlotsFromScheduledBundles(existingRecords, includedSlots)
        val updatedRecords = existingRecords
            .filterNot { record -> record.slot in includedSlots }
            .plus(nextRecords)
        repeatStore.replaceRepeatRecords(updatedRecords)
        scheduleRecords(nextRecords, useAlarmClock = persistent.fullScreenAlarmEnabled)
        diagnosticsLogger.info(
            TAG,
            "repeat_schedule_complete slots=${slots.size} excluded=${excludedSlots.size} " +
                    "scheduled=${nextRecords.size} " +
                    "repeatAt=$repeatAt limit=${persistent.repeatLimit.maxRepeats ?: -1}"
        )
        return nextRecords
    }

    suspend fun clearRepeatsForSlots(slots: List<MedicationReminderSlot>) {
        if (slots.isEmpty()) {
            return
        }
        val existingRecords = repeatStore.getRepeatRecords()
        if (existingRecords.none { record -> record.slot in slots }) {
            return
        }
        clearSlotsFromScheduledBundles(existingRecords, slots)
        repeatStore.replaceRepeatRecords(existingRecords.filterNot { record -> record.slot in slots })
        diagnosticsLogger.info(TAG, "repeat_clear_slots_complete slots=${slots.size}")
    }

    /** Ends every chain belonging to one of [groupUuids], as when those plans leave persistent reminders. */
    suspend fun clearRepeatsForGroups(groupUuids: Set<UUID>) {
        if (groupUuids.isEmpty()) {
            return
        }
        val slots = repeatStore.getRepeatRecords()
            .map(MedicationReminderRepeatRecord::slot)
            .filter { slot -> slot.groupUuid in groupUuids }
        clearRepeatsForSlots(slots)
    }

    suspend fun clearAllRepeats() {
        val existingRecords = repeatStore.getRepeatRecords()
        existingRecords
            .groupBy(MedicationReminderRepeatRecord::repeatAt)
            .values
            .forEach(::cancelRepeatBundle)
        repeatStore.replaceRepeatRecords(emptyList())
        diagnosticsLogger.info(TAG, "repeat_clear_all_complete cleared=${existingRecords.size}")
    }

    /**
     * Re-arms every stored chain after the process lost its alarms (reboot, app
     * update, time change). A repeat whose time passed meanwhile fires at once:
     * the dose is still pending as far as the store knows, and the fire-time
     * checks drop it if it is not.
     */
    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        val settings = settingsRepository.getCurrentSettings()
        if (!settings.remindersEnabled || !settings.persistentReminders.repeatEnabled) {
            clearAllRepeats()
            return
        }
        val records = repeatStore.getRepeatRecords()
        val rearmed = records.map { record ->
            if (record.repeatAt.isBefore(now)) record.copy(repeatAt = now) else record
        }
        if (rearmed != records) {
            repeatStore.replaceRepeatRecords(rearmed)
        }
        scheduleRecords(rearmed, useAlarmClock = settings.persistentReminders.fullScreenAlarmEnabled)
        diagnosticsLogger.info(TAG, "repeat_reschedule_all_complete records=${rearmed.size}")
    }

    suspend fun getRepeatRecords(): List<MedicationReminderRepeatRecord> {
        return repeatStore.getRepeatRecords()
    }

    /**
     * Cancels each alarm holding one of [slots] and re-arms it for the rest of
     * its bundle, so only [slots] leave it. The store is the caller's to update.
     */
    private suspend fun clearSlotsFromScheduledBundles(
        existingRecords: List<MedicationReminderRepeatRecord>,
        slots: List<MedicationReminderSlot>,
    ) {
        val affectedTimes = existingRecords
            .filter { record -> record.slot in slots }
            .mapTo(mutableSetOf(), MedicationReminderRepeatRecord::repeatAt)
        if (affectedTimes.isEmpty()) {
            return
        }
        val affectedBundles = existingRecords
            .filter { record -> record.repeatAt in affectedTimes }
            .groupBy(MedicationReminderRepeatRecord::repeatAt)
            .values
        affectedBundles.forEach(::cancelRepeatBundle)
        val useAlarmClock = settingsRepository.getCurrentSettings()
            .persistentReminders.fullScreenAlarmEnabled
        affectedBundles
            .map { bundle -> bundle.filterNot { record -> record.slot in slots } }
            .filter(List<MedicationReminderRepeatRecord>::isNotEmpty)
            .forEach { bundle -> scheduleRepeatBundle(bundle, useAlarmClock) }
    }

    private fun scheduleRecords(
        records: List<MedicationReminderRepeatRecord>,
        useAlarmClock: Boolean,
    ) {
        records
            .groupBy(MedicationReminderRepeatRecord::repeatAt)
            .values
            .forEach { bundle -> scheduleRepeatBundle(bundle, useAlarmClock) }
    }

    private fun scheduleRepeatBundle(
        records: List<MedicationReminderRepeatRecord>,
        useAlarmClock: Boolean,
    ) {
        if (records.isEmpty()) {
            return
        }
        val repeatAt = records.first().repeatAt
        val triggerAtMillis = repeatAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = buildRepeatPendingIntent(records)
        val exactAlarm = canScheduleExactAlarms(alarmManager, sdkIntProvider())
        diagnosticsLogger.info(
            TAG,
            "repeat_alarm_schedule repeatAt=$repeatAt slots=${records.size} " +
                    "exact=$exactAlarm alarmClock=${exactAlarm && useAlarmClock}"
        )
        if (!exactAlarm) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }
        try {
            if (useAlarmClock) {
                // An alarm clock leaves Doze and is not rate limited. The
                // while-idle variants fire at most about once per nine minutes
                // per app in Doze, which would stretch a short repeat interval
                // on an idle, locked phone: the case a full-screen alarm is for.
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, buildShowAppPendingIntent()),
                    pendingIntent,
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (error: SecurityException) {
            // TOCTOU, as in the snooze scheduler: exact access revoked since the check.
            diagnosticsLogger.warning(
                TAG,
                "repeat_alarm_schedule_exact_revoked repeatAt=$repeatAt slots=${records.size}",
                error,
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelRepeatBundle(records: List<MedicationReminderRepeatRecord>) {
        if (records.isEmpty()) {
            return
        }
        alarmManager.cancel(buildRepeatPendingIntent(records))
    }

    private fun buildRepeatPendingIntent(
        records: List<MedicationReminderRepeatRecord>,
    ): PendingIntent {
        val slots = records.map(MedicationReminderRepeatRecord::slot)
        val intent = Intent(context, MedicationReminderActionReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER_REPEAT_ALARM
            data = repeatIntentData(records)
            putStringArrayListExtra(
                EXTRA_REMINDER_SLOTS,
                ArrayList(slots.map(MedicationReminderSlot::toStorageValue)),
            )
        }
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REPEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** What the system opens from the alarm-clock entry it shows for a repeat. */
    private fun buildShowAppPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REMINDER_REPEAT_REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun repeatIntentData(records: List<MedicationReminderRepeatRecord>): Uri {
    val repeatAt = records.firstOrNull()?.repeatAt ?: LocalDateTime.MIN
    val slotsKey = records
        .map { record -> record.slot.toStorageValue() }
        .sorted()
        .joinToString(separator = ";")
    val bundleUuid = UUID.nameUUIDFromBytes(slotsKey.toByteArray(StandardCharsets.UTF_8))
    return "$REMINDER_REPEAT_URI_PREFIX/$repeatAt/$bundleUuid".toUri()
}

private const val REMINDER_REPEAT_REQUEST_CODE = 0
private const val REMINDER_REPEAT_URI_PREFIX = "hrttracker://medication-reminder-repeat"
private const val TAG = "MedicationReminderRepeatScheduler"
