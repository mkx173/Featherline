package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.lowStockSeverityRank
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderActionHandler @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val medicineStockRepository: MedicineStockRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    suspend fun logNow(
        slots: List<MedicationReminderSlot>,
        logTargets: List<MedicationReminderLogTarget>?,
        notificationTag: String?,
        now: Instant = Instant.now(),
    ) {
        val normalizedSlots = slots.distinct()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_log_now_start slots=${normalizedSlots.size} " +
                    "rawSlots=${slots.size} logTargets=${logTargets?.size ?: -1} " +
                    "notificationTag=${notificationTag.orEmpty()} now=$now"
        )
        if (normalizedSlots.isEmpty()) {
            diagnosticsLogger.info(TAG, "reminder_action_log_now_skipped reason=empty_slots")
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        // First: each toast below reads the app language from it.
        val settings = settingsRepository.getCurrentSettings()
        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            normalizedSlots.minOf(MedicationReminderSlot::scheduledAt)
        )
        val groupsByUuid = loadRepresentedGroups(normalizedSlots)
        // null logTargets = legacy pending intent posted before the shipping
        // format existed; preserve the previous "log everything missing"
        // behaviour for that case so the action still does something useful.
        val restrictionBySlot: Map<MedicationReminderSlot, Set<MedicationSignature>>? =
            logTargets?.groupBy(MedicationReminderLogTarget::slot)
                ?.mapValues { (_, targets) ->
                    targets.mapTo(mutableSetOf(), MedicationReminderLogTarget::signature)
                }
        // A reminder can outlive its dose: the plan moved, dropped or deleted it since.
        val scheduledSlots = normalizedSlots.filter { slot ->
            groupsByUuid[slot.groupUuid]?.hasReminderOccurrence(slot) == true
        }
        val entriesToSave = scheduledSlots.flatMap { slot ->
            val group = groupsByUuid.getValue(slot.groupUuid)
            // With current-format extras present, a slot missing from the map
            // means every target for it failed to parse — restrict to nothing
            // so we don't silently write the legacy "everything missing" set.
            val restriction = if (restrictionBySlot != null) {
                restrictionBySlot[slot] ?: emptySet()
            } else {
                null
            }
            buildMissingScheduledLogEntries(
                group = group,
                slot = slot,
                entries = entries,
                appliedAt = now,
                restrictToSignatures = restriction,
            )
        }

        // Snapshot stock state before the deduction so the toast only fires
        // for medicines this log actually pushed into a worse tier.
        val beforeStockStates = if (entriesToSave.isNotEmpty()) {
            captureStockStatesForLog(
                medicineStockRepository = medicineStockRepository,
                now = now,
            )
        } else {
            emptyMap()
        }
        // The repository decides again under the write lock: the widget or the app may
        // have logged these doses since the read above.
        val savedEntries = if (entriesToSave.isNotEmpty()) {
            medicationLogRepository.saveNewEntries(entriesToSave)
        } else {
            emptyList()
        }
        if (savedEntries.isNotEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_log_now_saved entriesSaved=${savedEntries.size} slots=${normalizedSlots.size}"
            )
            showPostLogToast(
                entriesToSave = savedEntries,
                now = now,
                medicineStockRepository = medicineStockRepository,
                reminderNotificationManager = reminderNotificationManager,
                beforeStatesByUuid = beforeStockStates,
                hideMedicationDetails = settings.hideMedicationDetails,
            )
        } else if (scheduledSlots.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_log_now_no_scheduled_slots slots=${normalizedSlots.size}"
            )
            reminderNotificationManager.showDoseReminderNoLongerScheduledToast()
        } else {
            // Race: the slot was already fulfilled between the notification firing
            // and the tap. Don't render "Logged 0 doses" — show a separate
            // "already logged" confirmation instead so the tap still has visible
            // feedback.
            diagnosticsLogger.info(
                TAG,
                "reminder_action_log_now_no_missing_entries slots=${normalizedSlots.size}"
            )
            reminderNotificationManager.showDoseReminderNothingToAddToast()
        }

        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
        val groupUuidsToReschedule = normalizedSlots
            .map(MedicationReminderSlot::groupUuid)
            .distinct()
        groupUuidsToReschedule.forEach { groupUuid ->
            medicationReminderScheduler.rescheduleGroup(groupUuid)
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_action_log_now_complete slots=${normalizedSlots.size} " +
                    "entriesSaved=${savedEntries.size} groupsRescheduled=${groupUuidsToReschedule.size}"
        )
    }

    /**
     * Skip: the reminder goes, and its dose rings no more (the plan's skip watermark moves to
     * it). Nothing is logged, so Home still shows the dose as missed.
     */
    suspend fun skip(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
    ) {
        // One plan's dose per reminder.
        val slot = slots.maxByOrNull(MedicationReminderSlot::scheduledAt) ?: return
        diagnosticsLogger.info(
            TAG,
            "reminder_action_skip_start groupUuid=${slot.groupUuid} doseAt=${slot.scheduledAt} " +
                    "notificationTag=${notificationTag.orEmpty()}"
        )
        // One hold: a delivery can't post the dose again between the cancel and the watermark,
        // and the cancel comes first, so the reschedule's tidy-up doesn't repost it.
        withReminderScheduleLock {
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            medicationReminderScheduler.skipThrough(groupUuid = slot.groupUuid, doseAt = slot.scheduledAt)
        }
        reminderNotificationManager.showDoseReminderSkippedToast()
        diagnosticsLogger.info(TAG, "reminder_action_skip_complete groupUuid=${slot.groupUuid}")
    }

    /**
     * You swiped the reminder away, or tapped Clear all: its alerts go on. A toast names the
     * plan's next one, the alert its alarm is armed for.
     */
    suspend fun dismissed(
        slots: List<MedicationReminderSlot>,
        now: Instant = Instant.now(),
    ) {
        // First: the toast reads the app language from it.
        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(TAG, "reminder_action_dismissed_skipped reason=master_disabled")
            return
        }
        // One plan per reminder.
        val nextAlertAt = slots.firstOrNull()
            ?.let { slot -> medicationReminderScheduler.armedAlertAt(slot.groupUuid) }
            ?.takeIf { alertAt -> alertAt.isAfter(now) }
        diagnosticsLogger.info(TAG, "reminder_action_dismissed slots=${slots.size} nextAlertAt=$nextAlertAt")
        // None armed, or one due now: no toast.
        nextAlertAt?.let(reminderNotificationManager::showNextReminderToast)
    }

    // No archive or notifications gate for the explicit Log now: once a notification has
    // fired and the user taps it, the dose should be written even if the group was archived
    // or had its notifications disabled afterwards.
    private suspend fun loadRepresentedGroups(
        slots: List<MedicationReminderSlot>,
    ): Map<UUID, MedicationGroup> {
        val requestedGroupUuids = slots.map(MedicationReminderSlot::groupUuid).distinct()
        val groups = requestedGroupUuids
            .mapNotNull { groupUuid -> medicationGroupRepository.getGroup(groupUuid) }
            .associateBy(MedicationGroup::uuid)
        diagnosticsLogger.info(
            TAG,
            "reminder_action_groups_loaded requested=${requestedGroupUuids.size} loaded=${groups.size}"
        )
        return groups
    }
}

internal fun buildMissingScheduledLogEntries(
    group: MedicationGroup,
    slot: MedicationReminderSlot,
    entries: List<MedicationLogEntry>,
    // An instant, not a wall time: a tap in a repeated fall-back hour names one pass.
    appliedAt: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    restrictToSignatures: Set<MedicationSignature>? = null,
): List<MedicationLogEntryInput> {
    // Archive / notification state is intentionally not gated here — callers decide. Both
    // explicit log actions (widget quick-log and the reminder's Log now) log archived or
    // notifications-disabled groups on purpose, e.g. re-logging a deleted dose.
    if (group.medications.isEmpty()) {
        return emptyList()
    }

    // When the caller pins the writable set (notification "Log all" with
    // shipped signatures), trim to medications the user actually saw. Null =
    // no restriction → log everything missing.
    val candidateMedications = if (restrictToSignatures == null) {
        group.medications
    } else {
        group.medications.filter { medication ->
            MedicationSignature.fromGroupMedication(medication) in restrictToSignatures
        }
    }
    if (candidateMedications.isEmpty()) {
        return emptyList()
    }

    // A late (out-of-window) record proves the dose was logged too: writing another one
    // on a repeated tap would double the record and deduct stock twice.
    val missingCounts = missingSlotCounts(
        group = group,
        slot = slot.toMedicationGroupSlotKey(),
        entries = entries,
        medications = candidateMedications,
    )

    return candidateMedications
        .groupBy(MedicationSignature::fromGroupMedication)
        .mapNotNull { (signature, medications) ->
            val missingCount = missingCounts[signature] ?: return@mapNotNull null
            val templateMedication = medications.first()
            MedicationLogEntryInput(
                medicineUuid = templateMedication.medicineUuid,
                applicationType = templateMedication.applicationType,
                doseInstruction = templateMedication.doseInstruction,
                sourceGroupUuid = group.uuid,
                scheduleTimeUuid = slot.scheduleTimeUuid,
                appliedAt = appliedAt,
                scheduledFor = slot.scheduledAt,
                count = missingCount,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
}

/**
 * Whether the plan still has the dated dose [slot] points at. A plan edit (weekdays,
 * interval, start, day ending) can drop it while its notification is still showing.
 */
internal fun MedicationGroup.hasReminderOccurrence(slot: MedicationReminderSlot): Boolean =
    occurrencesBetweenInPlanWindow(
        startDate = slot.scheduledAt.toLocalDate(),
        endDate = slot.scheduledAt.toLocalDate(),
    ).any { occurrence ->
        occurrence.scheduledFor == slot.scheduledAt &&
                (slot.scheduleTimeUuid == null || occurrence.scheduleTimeUuid == slot.scheduleTimeUuid)
    }

internal fun MedicationReminderSlot.toMedicationGroupSlotKey(): MedicationGroupSlotKey {
    return MedicationGroupSlotKey(
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledFor = scheduledAt,
    )
}

/**
 * Snapshots each medicine's current stock state, keyed by UUID, for use as the
 * "before" baseline of a post-log warning. Must be called before the log's
 * deduction is written. A projection failure degrades to an empty map (every
 * post-log warning tier then reads as newly worsened), but cancellation
 * propagates.
 */
internal suspend fun captureStockStatesForLog(
    medicineStockRepository: MedicineStockRepository,
    now: Instant,
): Map<UUID, MedicineStockState> {
    return runCatching {
        medicineStockRepository.projectAllOnce(now = now)
            .associate { projection -> projection.medicine.uuid to projection.state }
    }.getOrElse { failure ->
        if (failure is CancellationException) throw failure
        emptyMap()
    }
}

internal suspend fun showPostLogToast(
    entriesToSave: Collection<MedicationLogEntryInput>,
    now: Instant,
    medicineStockRepository: MedicineStockRepository,
    reminderNotificationManager: ReminderNotificationManager,
    beforeStatesByUuid: Map<UUID, MedicineStockState>,
    hideMedicationDetails: Boolean = false,
) {
    val affectedMedicineUuids = entriesToSave
        .mapNotNull(MedicationLogEntryInput::medicineUuid)
        .toSet()
    val projections = runCatching {
        medicineStockRepository
            .projectAllOnce(now = now)
    }.getOrElse { failure ->
        if (failure is CancellationException) throw failure
        reminderNotificationManager.showDoseReminderLoggedToast(entriesToSave.size)
        return
    }

    when (val warning =
        resolvePostLogStockWarning(projections, affectedMedicineUuids, beforeStatesByUuid)) {
        null -> reminderNotificationManager.showDoseReminderLoggedToast(entriesToSave.size)
        is PostLogStockWarning.Single -> {
            if (hideMedicationDetails) {
                when (warning.state) {
                    MedicineStockState.OUT -> reminderNotificationManager.showStockOutCountToast(1)
                    MedicineStockState.IMMINENT -> reminderNotificationManager.showStockImminentCountToast(
                        1
                    )

                    MedicineStockState.USER_LOW -> reminderNotificationManager.showStockUserLowCountToast(
                        1
                    )

                    else -> reminderNotificationManager.showDoseReminderLoggedToast(entriesToSave.size)
                }
            } else {
                when (warning.state) {
                    MedicineStockState.OUT -> reminderNotificationManager.showStockOutToast(warning.medicine)
                    MedicineStockState.IMMINENT -> reminderNotificationManager.showStockImminentToast(
                        warning.medicine
                    )

                    MedicineStockState.USER_LOW -> reminderNotificationManager.showStockUserLowToast(
                        warning.medicine
                    )

                    else -> reminderNotificationManager.showDoseReminderLoggedToast(entriesToSave.size)
                }
            }
        }

        is PostLogStockWarning.Many ->
            reminderNotificationManager.showStockManyAttentionToast(warning.count)
    }
}

sealed interface PostLogStockWarning {
    data class Single(val medicine: Medicine, val state: MedicineStockState) : PostLogStockWarning
    data class Many(val count: Int) : PostLogStockWarning
}

internal fun resolvePostLogStockWarning(
    projections: List<MedicineStockProjection>,
    affectedMedicineUuids: Set<UUID>,
    beforeStatesByUuid: Map<UUID, MedicineStockState>,
): PostLogStockWarning? {
    val warned = projections
        .filter { it.medicine.uuid in affectedMedicineUuids }
        // Only warn when this log pushed the medicine into a worse low-stock
        // tier. An already-low medicine that stays low (unchanged rank) must not
        // re-warn. Logs only deduct, so a higher rank means this log changed it;
        // the rank is 0 for non-warning states, so this also drops medicines that
        // are still healthy after the log.
        .filter { projection ->
            val beforeRank =
                beforeStatesByUuid[projection.medicine.uuid]?.lowStockSeverityRank() ?: 0
            projection.state.lowStockSeverityRank() > beforeRank
        }
    if (warned.isEmpty()) return null
    return if (warned.size == 1) {
        val only = warned.single()
        PostLogStockWarning.Single(only.medicine, only.state)
    } else {
        // Mixed severities collapse to one generic "needs attention" message
        // (see showStockManyAttentionToast); only the total count is shown.
        PostLogStockWarning.Many(warned.size)
    }
}


private const val TAG = "MedicationReminderActionHandler"
