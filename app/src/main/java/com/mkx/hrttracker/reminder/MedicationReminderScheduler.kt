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
    private val reminderNotificationManager: ReminderNotificationManager,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    private val sdkIntProvider: () -> Int,
    /** The clock: the armed alerts are instants, and in the hour the clocks go back it tells which pass a `now` is on. */
    private val instantNow: () -> Instant = Instant::now,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        settingsRepository: SettingsRepository,
        reminderScheduleStore: ReminderScheduleStore,
        reminderNotificationManager: ReminderNotificationManager,
        homeSnapshotRepository: HomeSnapshotRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
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

    // ponytail: in memory, so a process death between two plans' alarms at one alert time gives
    // two sounds, which is the safe way to fail.
    private var soundedAlertAt: Instant? = null

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

    /**
     * Arms every plan's next alert and tidies the reminders on screen. [repostShown] reposts each
     * one still due, silently, even when unchanged: after a restore, it shows in the restored
     * language and Hide medication details. A pass that starts with a reconcile owed reposts too,
     * whichever of a new process's passes runs first, so a restore cut short by a death still
     * gets its repost.
     */
    suspend fun rescheduleAll(
        now: Instant? = null,
        repostShown: Boolean = false,
    ) = withReminderScheduleLock {
        val now = now ?: instantNow()
        // Before any read: a writer still between its mark and its commit is owed a later pass.
        val settlesReconcile = openReconcileMarks.isEmpty()
        val repostAll = repostShown || reminderScheduleStore.isReconcilePending()
        diagnosticsLogger.info(TAG, "reminder_reschedule_all_start now=$now")
        val groups = medicationGroupRepository.getGroups()
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val stored = reminderScheduleStore.getScheduledReminders()
        val storedGroupUuids = stored.keys
        val uuidsToCancel = storedGroupUuids + groupUuids
        val settings = settingsRepository.getCurrentSettings()

        if (!settings.remindersEnabled) {
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

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            reminderDosesFrom(now, settings.reminderRepeats, ZoneId.systemDefault())
        )
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now,
            armed = stored.armedAlerts(),
            repeats = settings.reminderRepeats,
            skippedThrough = reminderScheduleStore.getSkippedThrough(),
        )

        // Stored first: a failure after leaves a key with no alarm, which the next pass arms,
        // not an alarm with no key, which posts nothing.
        reminderScheduleStore.replaceScheduledReminders(
            plans.associate { plan -> plan.groupUuid to plan.alertAt }
        )
        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach(::scheduleReminder)
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_all_complete groups=${groups.size} " +
                    "entries=${entries.size} plans=${plans.size} cancel=${uuidsToCancel.size}"
        )
        reconcileDoseReminderNotifications(remindersEnabled = true, repostAll = repostAll)
        if (settlesReconcile) reminderScheduleStore.setReconcilePending(false)
    }

    /**
     * Reschedules from [snapshot], which its caller read before this took the lock. False, with
     * nothing changed, once Home data changed since: that writer's own reschedule, or a full
     * pass, has the newer plans.
     */
    suspend fun rescheduleFromHomeSnapshot(
        snapshot: HomeSnapshotRecord,
        now: Instant? = null,
    ): Boolean = withReminderScheduleLock {
        val now = now ?: instantNow()
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
        val settings = settingsRepository.getCurrentSettings()

        if (!settings.remindersEnabled) {
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

        // ponytail: the snapshot holds the entries of a buffered window of days around its build
        // day, which takes in the day and repeat span back of any dose a fresh snapshot rings.
        val since = reminderDosesFrom(now, settings.reminderRepeats, ZoneId.systemDefault())
        val entries = snapshot.scheduleEntries.filter { entry ->
            val scheduledFor = entry.scheduledFor
            entry.sourceGroupUuid != null &&
                    scheduledFor != null &&
                    !scheduledFor.isBefore(since)
        }
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now,
            armed = stored.armedAlerts(),
            repeats = settings.reminderRepeats,
            skippedThrough = reminderScheduleStore.getSkippedThrough(),
        )

        // Stored first, as in rescheduleAll.
        reminderScheduleStore.replaceScheduledReminders(
            plans.associate { plan -> plan.groupUuid to plan.alertAt }
        )
        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach(::scheduleReminder)
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_from_snapshot_complete groups=${groups.size} " +
                    "entries=${entries.size} plans=${plans.size} cancel=${uuidsToCancel.size}"
        )
        true
    }

    /**
     * Whether [groupUuid]'s alarm armed for [alertAt] still stands: no other alert was armed
     * since, and no delivery of it was marked ([markReminderDelivered]). Compared as instants,
     * so after a zone change an old alarm that shares the new one's local time is stale. A group
     * with no key was delivered, cancelled or turned off; one stored with no time, by v1.4.2,
     * takes any. The receiver checks, posts and marks in one hold of the lock, so of an alarm
     * that comes twice, as one a pass armed again while it was in flight, only the first posts.
     */
    suspend fun awaitsReminderDelivery(groupUuid: UUID, alertAt: Instant?): Boolean =
        withReminderScheduleLock {
            val stored = reminderScheduleStore.getScheduledReminders()
            groupUuid in stored && (stored[groupUuid] == null || stored[groupUuid] == alertAt)
        }

    /**
     * Drops [groupUuid]'s stored alert once its alarm for [alertAt] is delivered, before the
     * receiver reschedules: a stored alert that has passed then always names one not delivered
     * yet, which [rescheduleGroup] and [rescheduleAll] arm again. An alert armed since stays.
     */
    // ponytail: marked after the post, so a death in between posts it again at the next pass, in
    // place by its tag. Marked before, a failed read or post lost it.
    suspend fun markReminderDelivered(groupUuid: UUID, alertAt: Instant?) =
        withReminderScheduleLock {
            val stored = reminderScheduleStore.getScheduledReminders()
            if (groupUuid in stored && stored[groupUuid] == alertAt) {
                reminderScheduleStore.removeScheduledGroupUuid(groupUuid)
            }
        }

    /**
     * Whether a post for the alert at [alertAt] should sound: the first plan to arrive at an alert
     * time does, and the others at the same time post silently. So does a late post for an
     * earlier alert than one that sounded, as another plan's late alarm can be: compared by time
     * only, it would sound that alert time a second time. A sounded alert later than the clock is
     * forgotten: an alert sounds at or after its time, so the clock was set back since. The
     * caller holds the lock.
     */
    internal fun claimAlertSound(alertAt: Instant): Boolean {
        if (soundedAlertAt?.isAfter(instantNow()) == true) soundedAlertAt = null
        if (soundedAlertAt?.let { sounded -> !alertAt.isAfter(sounded) } == true) return false
        soundedAlertAt = alertAt
        return true
    }

    /** The alert [groupUuid]'s alarm is armed for, if any. */
    suspend fun armedAlertAt(groupUuid: UUID): Instant? =
        withReminderScheduleLock { reminderScheduleStore.getScheduledReminders()[groupUuid] }

    /**
     * Skip: [groupUuid]'s doses at or before [doseAt] ring no more. Its alarm moves to its next
     * dose. Nothing is logged.
     */
    suspend fun skipThrough(groupUuid: UUID, doseAt: LocalDateTime, now: Instant? = null) =
        withReminderScheduleLock {
            reminderScheduleStore.skipThrough(groupUuid, doseAt)
            rescheduleGroup(groupUuid, after = now)
        }

    suspend fun skippedThrough(groupUuid: UUID): LocalDateTime? =
        reminderScheduleStore.getSkippedThrough()[groupUuid]

    /** Drops every skip watermark, as a restore does: a skipped dose rings again if still due. */
    suspend fun clearSkips() = withReminderScheduleLock { reminderScheduleStore.clearSkippedThrough() }

    suspend fun rescheduleGroup(
        groupUuid: UUID,
        after: Instant? = null
    ) = withReminderScheduleLock {
        val after = after ?: instantNow()
        // Every way out, an archived, deleted or muted plan included, leaves a
        // reminder on screen that may name doses no longer pending.
        scheduleGroupReminder(groupUuid, after)
        reconcileDoseReminderNotifications(
            remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        )
    }

    private suspend fun scheduleGroupReminder(groupUuid: UUID, after: Instant) {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_start groupUuid=$groupUuid after=$after"
        )
        val clockNow = instantNow()
        // Every read before the cancel: one that fails leaves the alarm and its key as they
        // were, and an alarm left armed reschedules its plan when it comes.
        val armedAt = reminderScheduleStore.getScheduledReminders()[groupUuid]
        val settings = settingsRepository.getCurrentSettings()
        val repeats = settings.reminderRepeats
        val group = if (settings.remindersEnabled) medicationGroupRepository.getGroup(groupUuid) else null
        val active = group != null && !group.isArchived() && group.notificationsEnabled
        // Armed for an alert that passed and hasn't come, as an inexact alarm can be an hour
        // late when another dose is logged.
        val armedPassed = armedAt?.takeUnless { at -> at.isAfter(clockNow) }
        val entries = if (active) {
            medicationLogRepository.getScheduledGroupEntriesSince(
                reminderDosesFrom(minOf(after, armedPassed ?: after), repeats, ZoneId.systemDefault())
            )
        } else {
            emptyList()
        }
        val skippedThrough = if (active) reminderScheduleStore.getSkippedThrough()[groupUuid] else null
        cancelReminderSchedule(groupUuid)

        if (!settings.remindersEnabled) {
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

        // While the alert its receiver would ring is still due, it's armed again and fires at
        // once, as rescheduleAll does. The receiver drops a delivered one first
        // ([markReminderDelivered]).
        val undelivered = armedPassed?.takeIf { since ->
            group.latestDueReminderAlert(
                since = since,
                now = after,
                entries = entries,
                repeats = repeats,
                skippedThrough = skippedThrough,
            ) != null
        }
        val plan = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = entries,
            now = after,
            armed = undelivered?.let { at -> mapOf(groupUuid to at) }.orEmpty(),
            repeats = repeats,
            skippedThrough = skippedThrough?.let { at -> mapOf(groupUuid to at) }.orEmpty(),
        ).firstOrNull() ?: return diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_skipped reason=no_plan groupUuid=$groupUuid entries=${entries.size}"
        )

        // Stored first, as in rescheduleAll.
        reminderScheduleStore.addScheduledReminder(plan.groupUuid, plan.alertAt)
        scheduleReminder(plan)
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_complete groupUuid=${plan.groupUuid} " +
                    "alertAt=${plan.alertAt} doseAt=${plan.doseAt} entries=${entries.size}"
        )
    }

    /**
     * Brings the dose reminders on screen in line with the plans: one whose doses are all gone
     * (dropped or moved by an edit, logged, plan archived, deleted or muted) is cancelled, and one
     * with only some gone, or whose plan's medications changed, is reposted silently with what is
     * pending now. [repostAll] reposts the rest too. Each names one plan's doses. v1.4.2's
     * reminders carried no doses in their extras, so none is here: the system cleared them when
     * the package was replaced.
     */
    private suspend fun reconcileDoseReminderNotifications(
        remindersEnabled: Boolean,
        repostAll: Boolean = false,
    ) {
        if (!remindersEnabled) {
            reminderNotificationManager.cancelAllDoseReminderNotifications()
            return
        }
        val shown = reminderNotificationManager.activeDoseReminders()
        if (shown.isEmpty()) {
            return
        }
        val groupsByUuid = medicationGroupRepository.getGroups().associateBy { group -> group.uuid }
        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            shown.values.flatMap(ShownDoseReminder::slots).minOf(MedicationReminderSlot::scheduledAt)
        )
        val hideMedicationDetails = settingsRepository.getCurrentSettings().hideMedicationDetails
        var cancelled = 0
        var reposted = 0
        shown.forEach { (tag, reminder) ->
            val remaining = groupsByUuid[reminder.slots.first().groupUuid]
                ?.let { group -> remainingMedicationReminderBundle(reminder.slots, group, entries) }
            // Same doses can still mean other medications, counts or names, and Log
            // now would log the old ones. Or the medications are now shown or hidden.
            if (!repostAll && remaining != null && remaining.notificationTag == tag &&
                remaining.postedContentHash(hideMedicationDetails) == reminder.contentHash
            ) {
                return@forEach
            }
            // The same doses keep the tag, and posting under it replaces the old one.
            if (remaining?.notificationTag != tag) {
                reminderNotificationManager.cancelDoseReminderNotification(tag)
                cancelled++
            }
            if (remaining != null) {
                reminderNotificationManager.showDoseReminderNotification(
                    bundle = remaining,
                    hideMedicationDetails = hideMedicationDetails,
                    silent = true,
                )
                reposted++
            }
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
        val triggerAtMillis = plan.alertAt.toEpochMilli()
        val pendingIntent = buildReminderPendingIntent(groupUuid = plan.groupUuid, alertAt = plan.alertAt)

        val exactAlarm = canScheduleExactReminders()
        diagnosticsLogger.info(
            TAG,
            "reminder_alarm_schedule groupUuid=${plan.groupUuid} doseAt=${plan.doseAt} " +
                    "alertAt=${plan.alertAt} exact=$exactAlarm"
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
                            "alertAt=${plan.alertAt}",
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
        alertAt: Instant? = null,
    ): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            data = reminderIntentData(groupUuid)
            putExtra(EXTRA_GROUP_UUID, groupUuid.toString())
            alertAt?.let { value -> putExtra(EXTRA_ALERT_AT, value.toEpochMilli()) }
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

// ponytail: one process-wide lock for every reminder pass and delivery; per-plan locks if passes
// ever queue.
private val reminderScheduleMutex = Mutex()

private object ReminderScheduleLockHeld :
    CoroutineContext.Element,
    CoroutineContext.Key<ReminderScheduleLockHeld> {
    override val key: CoroutineContext.Key<*>
        get() = this
}

/**
 * Runs [block] under the lock every alarm and schedule-store change takes, so no two passes
 * interleave their reads and writes; a dose reminder's delivery holds it from its check to its
 * reschedule. A call made under it, as skipThrough's reschedule, runs in the same hold. Nothing
 * under it may wait on the Home mutation gate: a restore marks a reconcile while it holds the gate.
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

private fun Map<UUID, Instant?>.armedAlerts(): Map<UUID, Instant> {
    return mapNotNull { (uuid, alertAt) -> alertAt?.let { uuid to it } }.toMap()
}

private const val REMINDER_REQUEST_CODE = 0
private const val REMINDER_INTENT_URI_PREFIX = "hrttracker://medication-reminder"
const val ACTION_MEDICATION_REMINDER = "com.mkx.hrttracker.action.MEDICATION_REMINDER"
const val EXTRA_GROUP_UUID = "groupUuid"
/** The alert's instant, in epoch millis: what the delivery is identified by. */
const val EXTRA_ALERT_AT = "alertAt"
/** The local dose time v1.4.2's alarms carried. */
const val EXTRA_SCHEDULED_AT = "scheduledAt"
