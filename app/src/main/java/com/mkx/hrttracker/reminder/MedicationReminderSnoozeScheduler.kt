package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.occurrencesInPlanWindowFrom
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderSnoozeScheduler private constructor(
    private val context: Context,
    private val snoozeStore: MedicationReminderSnoozeStore,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    private val sdkIntProvider: () -> Int,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        snoozeStore: MedicationReminderSnoozeStore,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    ) : this(
        context = context,
        snoozeStore = snoozeStore,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { Build.VERSION.SDK_INT },
    )

    internal constructor(
        context: Context,
        snoozeStore: MedicationReminderSnoozeStore,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
        sdkInt: Int,
    ) : this(
        context = context,
        snoozeStore = snoozeStore,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { sdkInt },
    )

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    suspend fun snoozeSlots(
        slots: List<MedicationReminderSlot>,
        now: Instant = Instant.now(),
    ): List<MedicationReminderSnoozeRecord> = withReminderScheduleLock {
        if (slots.isEmpty()) {
            diagnosticsLogger.info(TAG, "snooze_slots_skipped reason=empty_slots now=$now")
            return@withReminderScheduleLock emptyList()
        }

        diagnosticsLogger.info(TAG, "snooze_slots_start slots=${slots.size} now=$now")
        val existingRecords = snoozeStore.getSnoozeRecords()
        // In millis, as stored, so the alarm and the record name the same time.
        val snoozeAt = now.plus(REMINDER_SNOOZE_MINUTES, ChronoUnit.MINUTES)
            .truncatedTo(ChronoUnit.MILLIS)
        val nextRecords = buildNextSnoozeRecords(
            slots = slots,
            existingRecords = existingRecords,
            snoozeAt = snoozeAt,
        )
        // A slot at the limit keeps its record, and with it the count.
        val snoozedSlots = nextRecords.mapTo(mutableSetOf(), MedicationReminderSnoozeRecord::slot)
        val updatedRecords = existingRecords
            .filterNot { record -> record.slot in snoozedSlots }
            .plus(nextRecords)

        replaceSnoozeRecords(existingRecords, updatedRecords)
        diagnosticsLogger.info(
            TAG,
            "snooze_slots_complete slots=${slots.size} scheduled=${nextRecords.size} " +
                    "existing=${existingRecords.size} stored=${updatedRecords.size} snoozeAt=$snoozeAt"
        )
        nextRecords
    }

    suspend fun clearSnoozesForSlots(slots: List<MedicationReminderSlot>) = withReminderScheduleLock {
        if (slots.isEmpty()) {
            diagnosticsLogger.info(TAG, "snooze_clear_slots_skipped reason=empty_slots")
            return@withReminderScheduleLock
        }

        diagnosticsLogger.info(TAG, "snooze_clear_slots_start slots=${slots.size}")
        clearSnoozesMatching { record -> record.slot in slots }
    }

    suspend fun clearStaleSnoozesForGroup(group: MedicationGroup) = withReminderScheduleLock {
        diagnosticsLogger.info(TAG, "snooze_clear_stale_group_start groupUuid=${group.uuid}")
        val slotTimesByUuid = group.schedule.timeSlots
            .associate { slot -> slot.uuid to slot.time }
        clearSnoozesMatching { record ->
            val slot = record.slot
            slot.groupUuid == group.uuid &&
                    (!slot.matchesCurrentScheduleTime(slotTimesByUuid) ||
                            // Same slot and time, but the dated dose is gone, as after
                            // a weekday, interval, start or day ending change.
                            !group.hasReminderOccurrence(slot))
        }
    }

    /**
     * Clear every snooze record tied to [groupUuid], regardless of whether the slot
     * still matches the group's current schedule. Use after archive or delete to
     * avoid waking the device for a slot whose owning group no longer exists.
     */
    suspend fun clearSnoozesForGroup(groupUuid: UUID) = withReminderScheduleLock {
        diagnosticsLogger.info(TAG, "snooze_clear_group_start groupUuid=$groupUuid")
        clearSnoozesMatching { record -> record.slot.groupUuid == groupUuid }
    }

    private suspend fun clearSnoozesMatching(
        shouldClear: (MedicationReminderSnoozeRecord) -> Boolean,
    ) {
        val existingRecords = snoozeStore.getSnoozeRecords()
        val remainingRecords = existingRecords.filterNot(shouldClear)
        replaceSnoozeRecords(existingRecords, remainingRecords)
        diagnosticsLogger.info(
            TAG,
            "snooze_clear_matching_complete cleared=${existingRecords.size - remainingRecords.size} " +
                    "stored=${remainingRecords.size}"
        )
    }

    suspend fun clearAllSnoozes() = withReminderScheduleLock {
        diagnosticsLogger.info(TAG, "snooze_clear_all_start")
        val existingRecords = snoozeStore.getSnoozeRecords()
        replaceSnoozeRecords(existingRecords, emptyList())
        diagnosticsLogger.info(TAG, "snooze_clear_all_complete cleared=${existingRecords.size}")
    }

    /**
     * Arms every snooze not delivered yet, and drops each record that outlived its dose
     * ([isWithinDoseLifetime]). A record stays as long as its dose, fired or not, so its count
     * keeps the limit. One whose time passed undelivered, as when the phone was off or the app
     * force-stopped through it, is armed at that time and fires at once. Nothing can fire
     * before this pass runs.
     */
    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) = withReminderScheduleLock {
        diagnosticsLogger.info(TAG, "snooze_reschedule_all_start now=$now")
        val records = snoozeStore.getSnoozeRecords()
        // Room is read only when a record needs it.
        val liveRecords = if (records.isEmpty()) {
            records
        } else {
            val groupsByUuid = records.map { record -> record.slot.groupUuid }
                .distinct()
                .mapNotNull { groupUuid -> medicationGroupRepository.getGroup(groupUuid) }
                .associateBy(MedicationGroup::uuid)
            val entries = medicationLogRepository.getScheduledGroupEntriesSince(
                records.minOf { record -> record.slot.scheduledAt }
            )
            records.filter { record ->
                record.isWithinDoseLifetime(groupsByUuid[record.slot.groupUuid], entries, now)
            }
        }
        // Every live bundle, not just the changed ones: after a reboot or a force-stop none is armed.
        replaceSnoozeRecords(records, liveRecords, rearm = true)
        if (liveRecords.size != records.size) {
            diagnosticsLogger.info(
                TAG,
                "snooze_reschedule_all_pruned expired=${records.size - liveRecords.size} " +
                        "remaining=${liveRecords.size}"
            )
        }
        diagnosticsLogger.info(
            TAG,
            "snooze_reschedule_all_complete records=${liveRecords.size} " +
                    "bundles=${liveRecords.armedBundles().size}"
        )
    }

    /**
     * The [slots] a snooze alarm armed for [snoozeAt] still stands for, now marked fired so a
     * later [rescheduleAll] doesn't arm them again. A slot whose record was armed for another
     * time since (Remind later again), cleared, or delivered already is a stale delivery, and
     * left out. [snoozeAt] is null from an alarm an older build armed, which any record not
     * delivered yet takes.
     */
    // ponytail: marked before the reminder posts, so a process death between the two loses it.
    suspend fun claimSnoozeDelivery(
        slots: List<MedicationReminderSlot>,
        snoozeAt: Instant?,
    ): List<MedicationReminderSlot> = withReminderScheduleLock {
        val existingRecords = snoozeStore.getSnoozeRecords()
        val claimedRecords = existingRecords.filter { record ->
            record.slot in slots && !record.fired && (snoozeAt == null || record.snoozeAt == snoozeAt)
        }
        replaceSnoozeRecords(
            existingRecords,
            existingRecords.map { record ->
                if (record in claimedRecords) record.copy(fired = true) else record
            },
        )
        val claimedSlots = slots.filter { slot -> claimedRecords.any { record -> record.slot == slot } }
        diagnosticsLogger.info(
            TAG,
            "snooze_delivery_claimed slots=${slots.size} claimed=${claimedSlots.size} snoozeAt=$snoozeAt"
        )
        claimedSlots
    }

    suspend fun getSnoozeRecords(): List<MedicationReminderSnoozeRecord> {
        return snoozeStore.getSnoozeRecords()
    }

    /**
     * Stores [updatedRecords] in place of [existingRecords]. A bundle's alarm is keyed by its
     * time and slots, so after the write each bundle that changed is cancelled and armed as it
     * is now; the others keep their alarms, or with [rearm] are armed again. Written first, so a
     * failed write leaves every alarm as it was.
     */
    private suspend fun replaceSnoozeRecords(
        existingRecords: List<MedicationReminderSnoozeRecord>,
        updatedRecords: List<MedicationReminderSnoozeRecord>,
        rearm: Boolean = false,
    ) {
        if (updatedRecords != existingRecords) {
            snoozeStore.replaceSnoozeRecords(updatedRecords)
        }
        val armedBefore = existingRecords.armedBundles()
        val armedAfter = updatedRecords.armedBundles()
        armedBefore.forEach { (snoozeAt, bundle) ->
            if (armedAfter[snoozeAt]?.toSet() != bundle.toSet()) cancelSnoozeBundle(bundle)
        }
        armedAfter.forEach { (snoozeAt, bundle) ->
            if (rearm || armedBefore[snoozeAt]?.toSet() != bundle.toSet()) scheduleSnoozeBundle(bundle)
        }
    }

    private fun scheduleSnoozeBundle(records: List<MedicationReminderSnoozeRecord>) {
        if (records.isEmpty()) {
            return
        }

        val triggerAtMillis = records.first().snoozeAt.toEpochMilli()
        val pendingIntent = buildSnoozePendingIntent(records)
        val exactAlarm = canScheduleExactAlarms(alarmManager, sdkIntProvider())

        diagnosticsLogger.info(
            TAG,
            "snooze_alarm_schedule snoozeAt=${records.first().snoozeAt} " +
                    "slots=${records.size} triggerAtMillis=$triggerAtMillis exact=$exactAlarm"
        )
        if (exactAlarm) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } catch (error: SecurityException) {
                // TOCTOU: exact-alarm access can be revoked between the capability
                // check and scheduling. Keep the snooze armed even if precision drops.
                diagnosticsLogger.warning(
                    TAG,
                    "snooze_alarm_schedule_exact_revoked snoozeAt=${records.first().snoozeAt} " +
                            "slots=${records.size}",
                    error,
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun cancelSnoozeBundle(records: List<MedicationReminderSnoozeRecord>) {
        if (records.isEmpty()) {
            return
        }
        diagnosticsLogger.info(
            TAG,
            "snooze_alarm_cancel snoozeAt=${records.first().snoozeAt} slots=${records.size}"
        )
        alarmManager.cancel(buildSnoozePendingIntent(records))
    }

    private fun buildSnoozePendingIntent(
        records: List<MedicationReminderSnoozeRecord>,
    ): PendingIntent {
        val slots = records.map(MedicationReminderSnoozeRecord::slot)
        val intent = Intent(context, MedicationReminderActionReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER_SNOOZE_ALARM
            data = snoozeIntentData(records)
            putStringArrayListExtra(
                EXTRA_REMINDER_SLOTS,
                ArrayList(slots.map(MedicationReminderSlot::toStorageValue)),
            )
            // The fire is checked against the records: one armed for another time is stale.
            putExtra(EXTRA_SNOOZE_AT, records.first().snoozeAt.toEpochMilli())
        }

        return PendingIntent.getBroadcast(
            context,
            REMINDER_SNOOZE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun snoozeIntentData(records: List<MedicationReminderSnoozeRecord>): Uri {
    val snoozeAt = records.firstOrNull()?.snoozeAt?.toEpochMilli() ?: 0L
    val slotsKey = records
        .map { record -> record.slot.toStorageValue() }
        .sorted()
        .joinToString(separator = ";")
    val bundleUuid = UUID.nameUUIDFromBytes(slotsKey.toByteArray(StandardCharsets.UTF_8))
    return "$REMINDER_SNOOZE_URI_PREFIX/$snoozeAt/$bundleUuid".toUri()
}

/** The bundles with an alarm: the records not delivered yet, by the time they share. */
private fun List<MedicationReminderSnoozeRecord>.armedBundles():
        Map<Instant, List<MedicationReminderSnoozeRecord>> =
    filterNot(MedicationReminderSnoozeRecord::fired).groupBy(MedicationReminderSnoozeRecord::snoozeAt)

/**
 * Whether this record's dose still owns its snooze, and its count, at [now]: the plan, active
 * with reminders on, still has the dose and misses some of it, and its next dose at the same
 * slot hasn't come.
 */
private fun MedicationReminderSnoozeRecord.isWithinDoseLifetime(
    group: MedicationGroup?,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
): Boolean {
    if (group == null || !group.isActive() || !group.notificationsEnabled ||
        !group.hasReminderOccurrence(slot) ||
        missingSlotCounts(group, slot.toMedicationGroupSlotKey(), entries).isEmpty()
    ) {
        return false
    }
    // However far off; the plan has this slot, so it comes again. None only past an archive.
    val nextDoseAtSlot = group
        .occurrencesInPlanWindowFrom(start = slot.scheduledAt)
        .firstOrNull { occurrence ->
            occurrence.scheduledFor.isAfter(slot.scheduledAt) &&
                    (slot.scheduleTimeUuid == null || occurrence.scheduleTimeUuid == slot.scheduleTimeUuid)
        }
    return nextDoseAtSlot == null || nextDoseAtSlot.scheduledFor.isAfter(now)
}

private fun MedicationReminderSlot.matchesCurrentScheduleTime(
    slotTimesByUuid: Map<UUID, LocalTime>,
): Boolean {
    val scheduleTimeUuid = scheduleTimeUuid ?: return false
    val currentTime = slotTimesByUuid[scheduleTimeUuid] ?: return false
    return currentTime == scheduledAt.toLocalTime()
}

private const val REMINDER_SNOOZE_REQUEST_CODE = 0
private const val REMINDER_SNOOZE_URI_PREFIX = "hrttracker://medication-reminder-snooze"
private const val TAG = "MedicationReminderSnoozeScheduler"
