package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class MedicationReminderScheduler private constructor(
    private val context: Context,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduleStore: ReminderScheduleStore,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    private val sdkIntProvider: () -> Int,
    /** The clock: in the hour the clocks go back, it tells which pass a `now` is on. */
    private val instantNow: () -> Instant = Instant::now,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        settingsRepository: SettingsRepository,
        reminderScheduleStore: ReminderScheduleStore,
        medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
        reminderNotificationManager: ReminderNotificationManager,
        homeSnapshotRepository: HomeSnapshotRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
        reminderNotificationManager = reminderNotificationManager,
        homeSnapshotRepository = homeSnapshotRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { Build.VERSION.SDK_INT },
    )

    internal constructor(
        context: Context,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        settingsRepository: SettingsRepository,
        reminderScheduleStore: ReminderScheduleStore,
        medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
        reminderNotificationManager: ReminderNotificationManager,
        homeSnapshotRepository: HomeSnapshotRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
        sdkInt: Int,
        instantNow: () -> Instant = Instant::now,
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
        reminderNotificationManager = reminderNotificationManager,
        homeSnapshotRepository = homeSnapshotRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { sdkInt },
        instantNow = instantNow,
    )

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    // ponytail: one stored flag, and the marks still open live only in memory. While any
    // writer is between its mark and its commit, no pass clears the flag, so the committed
    // writers' marks wait with it for the open writer's own pass. Store a token per writer if
    // a pass ever has to clear around one that stays open.
    private val openReconcileMarks: MutableSet<ReconcileMark> = ConcurrentHashMap.newKeySet()

    /**
     * For a data change reminders must follow, before it commits: marks a reconcile owed
     * until a [rescheduleAll] that starts after [ReconcileMark.committed] completes, so startup
     * still runs one ([isReconcilePending]) if the process dies or the writer's own fails.
     * Under the passes' lock, so a pass that read the data before it doesn't clear it. A new
     * process has no writer in flight, so its first completed pass clears what an earlier
     * one left.
     */
    suspend fun markReconcilePending(): ReconcileMark = withReminderScheduleLock {
        // Open before the write: a caller cancelled once the flag is down never gets the mark
        // but may still commit, so its mark stays open for the rest of the process.
        val mark = ReconcileMark().also(openReconcileMarks::add)
        try {
            reminderScheduleStore.setReconcilePending(true)
        } catch (error: CancellationException) {
            // A caller still active got the store's own failure, and may go on without the mark.
            if (currentCoroutineContext().isActive) openReconcileMarks.remove(mark)
            throw error
        } catch (error: Throwable) {
            // Nothing written, and the caller has no mark to release.
            openReconcileMarks.remove(mark)
            throw error
        }
        mark
    }

    inner class ReconcileMark internal constructor() {
        /**
         * Once the write has committed or failed, before the writer's own pass: a pass that
         * starts after this reads its result, so it may clear the mark. No lock, so a writer
         * inside the Home mutation gate can call it; a pass already running leaves the mark.
         */
        fun committed() {
            openReconcileMarks.remove(this)
        }
    }

    suspend fun isReconcilePending(): Boolean = reminderScheduleStore.isReconcilePending()

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) = withReminderScheduleLock {
        // Before any read: a writer still between its mark and its commit is owed a later pass.
        val settlesReconcile = openReconcileMarks.isEmpty()
        diagnosticsLogger.info(TAG, "reminder_reschedule_all_start now=$now")
        val groups = medicationGroupRepository.getGroups()
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val stored = reminderScheduleStore.getScheduledReminders()
        val storedGroupUuids = stored.keys
        val uuidsToCancel = storedGroupUuids + groupUuids

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_all_disabled groups=${groups.size} " +
                        "stored=${storedGroupUuids.size} cancel=${uuidsToCancel.size}"
            )
            uuidsToCancel.forEach(::cancelReminderAlarm)
            reminderScheduleStore.replaceScheduledReminders(emptyMap())
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_all_complete groups=${groups.size} entries=0 plans=0 stored=0"
            )
            reconcileDoseReminderNotifications(remindersEnabled = false)
            if (settlesReconcile) reminderScheduleStore.setReconcilePending(false)
            return@withReminderScheduleLock
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(now)
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now,
            armed = stored.armedTimes(),
            clockNow = instantNow(),
        )

        // Stored first: a failure after leaves a key with no alarm, which the next pass arms,
        // not an alarm with no key, which posts nothing.
        reminderScheduleStore.replaceScheduledReminders(
            plans.associate { plan -> plan.groupUuid to plan.scheduledAt }
        )
        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_all_complete groups=${groups.size} " +
                    "entries=${entries.size} plans=${plans.size} " +
                    "stored=${
                        plans.mapTo(
                            mutableSetOf(),
                            MedicationReminderPlan::groupUuid
                        ).size
                    } " +
                    "cancel=${uuidsToCancel.size}"
        )
        reconcileDoseReminderNotifications(remindersEnabled = true)
        if (settlesReconcile) reminderScheduleStore.setReconcilePending(false)
    }

    /**
     * Reschedules from [snapshot], which its caller read before this took the lock. False, with
     * nothing changed, once Home data changed since: that writer's own reschedule, or a full
     * pass, has the newer plans.
     */
    suspend fun rescheduleFromHomeSnapshot(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean = withReminderScheduleLock {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_from_snapshot_start now=$now " +
                    "generation=${snapshot.generation} groups=${snapshot.activeGroups.size} " +
                    "snapshotEntries=${snapshot.scheduleEntries.size}"
        )
        val currentGeneration = homeSnapshotRepository.generation.value
        if (snapshot.generation != currentGeneration) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_stale generation=${snapshot.generation} " +
                        "current=$currentGeneration"
            )
            return@withReminderScheduleLock false
        }
        val groups = snapshot.activeGroups
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val stored = reminderScheduleStore.getScheduledReminders()
        val storedGroupUuids = stored.keys
        val uuidsToCancel = storedGroupUuids + groupUuids

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_disabled groups=${groups.size} " +
                        "stored=${storedGroupUuids.size} cancel=${uuidsToCancel.size}"
            )
            uuidsToCancel.forEach(::cancelReminderAlarm)
            reminderScheduleStore.replaceScheduledReminders(emptyMap())
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_complete groups=${groups.size} entries=0 plans=0 stored=0"
            )
            return@withReminderScheduleLock true
        }

        val entries = snapshot.scheduleEntries.filter { entry ->
            val scheduledFor = entry.scheduledFor
            entry.sourceGroupUuid != null &&
                    scheduledFor != null &&
                    !scheduledFor.isBefore(now)
        }
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now,
            armed = stored.armedTimes(),
            clockNow = instantNow(),
        )

        // Stored first, as in rescheduleAll.
        reminderScheduleStore.replaceScheduledReminders(
            plans.associate { plan -> plan.groupUuid to plan.scheduledAt }
        )
        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_from_snapshot_complete groups=${groups.size} " +
                    "entries=${entries.size} plans=${plans.size} " +
                    "stored=${
                        plans.mapTo(
                            mutableSetOf(),
                            MedicationReminderPlan::groupUuid
                        ).size
                    } " +
                    "cancel=${uuidsToCancel.size}"
        )
        true
    }

    /**
     * Whether [groupUuid]'s alarm armed for [scheduledAt] still stands: no other time was armed
     * since, and no delivery of it was marked ([markReminderDelivered]). A group with no key was
     * delivered, cancelled or turned off; one stored with no time, as from an older build, takes
     * any. The receiver checks, posts and marks in one hold of the lock, so of an alarm that
     * comes twice, as one a pass armed again while it was in flight, only the first posts.
     */
    suspend fun awaitsReminderDelivery(groupUuid: UUID, scheduledAt: LocalDateTime): Boolean =
        withReminderScheduleLock {
            val stored = reminderScheduleStore.getScheduledReminders()
            groupUuid in stored && (stored[groupUuid] == null || stored[groupUuid] == scheduledAt)
        }

    /**
     * Each group whose alarm armed for [slotAt] or before is not delivered yet, with the time it
     * is armed for. A delivery at [slotAt] names another group's dose only while it is here, and
     * marks it, so that group's own alarm finds it delivered.
     */
    suspend fun undeliveredRemindersBy(slotAt: LocalDateTime): Map<UUID, LocalDateTime> =
        withReminderScheduleLock {
            reminderScheduleStore.getScheduledReminders().armedTimes()
                .filterValues { armedAt -> !armedAt.isAfter(slotAt) }
        }

    /**
     * Drops [groupUuid]'s stored time once its alarm for [scheduledAt] is delivered, before the
     * receiver reschedules: a stored time that has passed then always names a dose not delivered
     * yet, which [rescheduleGroup] and [rescheduleAll] arm again. A time armed since stays.
     */
    // ponytail: marked after the post, so a death in between posts it again at the next pass, in
    // place by its tag. Marked before, a failed read or post lost it.
    suspend fun markReminderDelivered(groupUuid: UUID, scheduledAt: LocalDateTime) =
        withReminderScheduleLock {
            if (reminderScheduleStore.getScheduledReminders()[groupUuid] == scheduledAt) {
                reminderScheduleStore.removeScheduledGroupUuid(groupUuid)
            }
        }

    suspend fun rescheduleGroup(
        groupUuid: UUID,
        after: LocalDateTime = LocalDateTime.now()
    ) = withReminderScheduleLock {
        // Every way out, an archived, deleted or muted plan included, leaves a
        // reminder on screen that may name doses no longer pending.
        scheduleGroupReminder(groupUuid, after)
        reconcileDoseReminderNotifications(
            remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        )
    }

    private suspend fun scheduleGroupReminder(groupUuid: UUID, after: LocalDateTime) {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_start groupUuid=$groupUuid after=$after"
        )
        val clockNow = instantNow()
        val passed = after.passedAsOf(clockNow)
        // Every read and the snooze tidy-up before the cancel: one that fails leaves the alarm
        // and its key as they were, and an alarm left armed reschedules its plan when it comes.
        val armedAt = reminderScheduleStore.getScheduledReminders()[groupUuid]
        val remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        val group = if (remindersEnabled) medicationGroupRepository.getGroup(groupUuid) else null
        group?.let { medicationReminderSnoozeScheduler.clearStaleSnoozesForGroup(it) }
        val active = group != null && !group.isArchived() && group.notificationsEnabled
        val entries = if (active) {
            medicationLogRepository.getScheduledGroupEntriesSince(minOf(passed, armedAt ?: passed))
        } else {
            emptyList()
        }
        cancelReminderSchedule(groupUuid)

        if (!remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_group_skipped reason=master_disabled groupUuid=$groupUuid"
            )
            return
        }
        if (group == null) {
            return diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_group_skipped reason=missing_group groupUuid=$groupUuid"
            )
        }
        if (!active) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_group_skipped reason=inactive_or_notifications_disabled " +
                        "groupUuid=$groupUuid archived=${group.isArchived()} " +
                        "notificationsEnabled=${group.notificationsEnabled}"
            )
            return
        }

        // Armed for a dose that passed and hasn't come, as an inexact alarm can be an hour late
        // when another dose is logged: while the dose its receiver would name still misses some,
        // it's armed again and fires at once, as rescheduleAll does. The receiver drops a
        // delivered one first ([markReminderDelivered]).
        val undelivered = armedAt?.takeIf { at ->
            !at.isAfter(passed) && buildMedicationReminderBundle(
                scheduledAt = group.reminderSlotAt(armedAt = at, now = passed),
                groups = listOf(group),
                entries = entries,
            ) != null
        }
        val plan = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = entries,
            now = passed,
            armed = undelivered?.let { at -> mapOf(groupUuid to at) }.orEmpty(),
            clockNow = clockNow,
        ).firstOrNull() ?: return diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_skipped reason=no_plan groupUuid=$groupUuid entries=${entries.size}"
        )

        // Stored first, as in rescheduleAll.
        reminderScheduleStore.addScheduledReminder(plan.groupUuid, plan.scheduledAt)
        scheduleReminder(plan)
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_complete groupUuid=${plan.groupUuid} " +
                    "scheduledAt=${plan.scheduledAt} scheduleTimeUuid=${plan.scheduleTimeUuid} entries=${entries.size}"
        )
    }

    /**
     * Brings the dose reminders on screen in line with the plans: one whose doses
     * are all gone (dropped or moved by an edit, logged, plan archived, deleted or
     * muted) is cancelled, and one with only some gone, or whose plan's medications
     * changed, is reposted silently with what is pending now.
     */
    private suspend fun reconcileDoseReminderNotifications(remindersEnabled: Boolean) {
        if (!remindersEnabled) {
            reminderNotificationManager.cancelAllDoseReminderNotifications()
            return
        }
        val shown = reminderNotificationManager.activeDoseReminders()
        if (shown.isEmpty()) {
            return
        }
        val groups = medicationGroupRepository.getGroups()
        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            shown.values.flatMap(ShownDoseReminder::slots).minOf(MedicationReminderSlot::scheduledAt)
        )
        val hideMedicationDetails = settingsRepository.getCurrentSettings().hideMedicationDetails
        var cancelled = 0
        var reposted = 0
        shown.forEach { (tag, reminder) ->
            val remaining = remainingMedicationReminderBundle(reminder.slots, groups, entries)
            // Same doses can still mean other medications, counts or names, and Log
            // now would log the old ones. Or the medications are now shown or hidden.
            if (remaining != null && remaining.postedContentHash(hideMedicationDetails) == reminder.contentHash) {
                return@forEach
            }
            // The same doses keep the tag, and posting under it replaces the old one.
            if (remaining?.notificationTag != tag) {
                reminderNotificationManager.cancelDoseReminderNotification(tag)
            }
            if (remaining == null) {
                cancelled++
                return@forEach
            }
            val recordsBySlot = medicationReminderSnoozeScheduler.getSnoozeRecords()
                .associateBy(MedicationReminderSnoozeRecord::slot)
            reminderNotificationManager.showDoseReminderNotification(
                bundle = remaining,
                // As showSnoozedReminder decides it: hidden once every dose hit the limit.
                canSnooze = remaining.slots.any { slot ->
                    (recordsBySlot[slot]?.snoozeCount ?: 0) < MAX_REMINDER_SNOOZE_COUNT
                },
                hideMedicationDetails = hideMedicationDetails,
                silent = true,
            )
            reposted++
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_notifications_reconciled shown=${shown.size} cancelled=$cancelled reposted=$reposted"
        )
    }

    /** Reposts the reminders on screen silently, after a change to how they show, as Hide medication details. */
    suspend fun refreshDoseReminderNotifications() = withReminderScheduleLock {
        reconcileDoseReminderNotifications(
            remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        )
    }

    suspend fun cancelReminder(groupUuid: UUID) = withReminderScheduleLock {
        cancelReminderSchedule(groupUuid)
        // A deleted plan reaches only this, not a reschedule.
        reconcileDoseReminderNotifications(
            remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        )
    }

    private suspend fun cancelReminderSchedule(groupUuid: UUID) {
        diagnosticsLogger.info(TAG, "reminder_cancel_start groupUuid=$groupUuid")
        cancelReminderAlarm(groupUuid)
        reminderScheduleStore.removeScheduledGroupUuid(groupUuid)
        diagnosticsLogger.info(TAG, "reminder_cancel_complete groupUuid=$groupUuid")
    }

    fun canScheduleExactReminders(): Boolean {
        return canScheduleExactAlarms(alarmManager, sdkIntProvider())
    }

    private fun scheduleReminder(plan: MedicationReminderPlan) {
        val triggerAtMillis = plan.scheduledAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pendingIntent = buildReminderPendingIntent(
            groupUuid = plan.groupUuid,
            scheduledAt = plan.scheduledAt,
            scheduleTimeUuid = plan.scheduleTimeUuid,
        )

        val exactAlarm = canScheduleExactReminders()
        diagnosticsLogger.info(
            TAG,
            "reminder_alarm_schedule groupUuid=${plan.groupUuid} " +
                    "scheduleTimeUuid=${plan.scheduleTimeUuid} scheduledAt=${plan.scheduledAt} " +
                    "triggerAtMillis=$triggerAtMillis exact=$exactAlarm"
        )

        if (exactAlarm) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (error: SecurityException) {
                // TOCTOU: the OS could revoke SCHEDULE_EXACT_ALARM between the
                // capability check and the setExact call. Fall back to inexact
                // so the alarm still fires (with reduced precision) instead of
                // the exception propagating up to BroadcastReceiver crash.
                diagnosticsLogger.warning(
                    TAG,
                    "reminder_alarm_schedule_exact_revoked groupUuid=${plan.groupUuid} " +
                            "scheduledAt=${plan.scheduledAt}",
                    error,
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
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
        scheduledAt: LocalDateTime? = null,
        scheduleTimeUuid: UUID? = null,
    ): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            data = reminderIntentData(groupUuid)
            putExtra(EXTRA_GROUP_UUID, groupUuid.toString())
            scheduleTimeUuid?.let { value ->
                putExtra(EXTRA_SCHEDULE_TIME_UUID, value.toString())
            }
            scheduledAt?.let { value ->
                putExtra(EXTRA_SCHEDULED_AT, value.toString())
            }
        }

        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelReminderAlarm(groupUuid: UUID) {
        diagnosticsLogger.info(TAG, "reminder_alarm_cancel groupUuid=$groupUuid")
        alarmManager.cancel(buildReminderPendingIntent(groupUuid))
    }

    private companion object {
        const val TAG = "MedicationReminderScheduler"
    }
}

// ponytail: one process-wide lock for both schedulers; per-plan locks if passes ever queue.
private val reminderScheduleMutex = Mutex()

private object ReminderScheduleLockHeld :
    CoroutineContext.Element,
    CoroutineContext.Key<ReminderScheduleLockHeld> {
    override val key: CoroutineContext.Key<*>
        get() = this
}

/**
 * Runs [block] under the lock every alarm and schedule-store change of both schedulers takes,
 * so no two passes interleave their reads and writes; a dose or snooze reminder's delivery holds
 * it from its check to its reschedule or mark. A call made under it, as rescheduleGroup
 * clearing its plan's stale snoozes, runs in the same hold. Nothing under it may wait on the
 * Home mutation gate: a restore marks a reconcile while it holds the gate.
 */
internal suspend fun <T> withReminderScheduleLock(block: suspend () -> T): T =
    if (currentCoroutineContext()[ReminderScheduleLockHeld] != null) {
        block()
    } else {
        reminderScheduleMutex.withLock { withContext(ReminderScheduleLockHeld) { block() } }
    }

internal fun reminderIntentData(groupUuid: UUID): Uri {
    return "$REMINDER_INTENT_URI_PREFIX/$groupUuid".toUri()
}

private fun Map<UUID, LocalDateTime?>.armedTimes(): Map<UUID, LocalDateTime> {
    return mapNotNull { (uuid, scheduledAt) -> scheduledAt?.let { uuid to it } }.toMap()
}

private const val REMINDER_REQUEST_CODE = 0
private const val REMINDER_INTENT_URI_PREFIX = "hrttracker://medication-reminder"
const val ACTION_MEDICATION_REMINDER = "com.mkx.hrttracker.action.MEDICATION_REMINDER"
const val EXTRA_GROUP_UUID = "groupUuid"
const val EXTRA_SCHEDULE_TIME_UUID = "scheduleTimeUuid"
const val EXTRA_SCHEDULED_AT = "scheduledAt"
