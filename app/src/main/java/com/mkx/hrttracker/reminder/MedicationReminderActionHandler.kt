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
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.isEntryForPlanSlot
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.isSlotFulfilledForMedication
import com.mkx.hrttracker.model.medication.lowStockSeverityRank
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDateTime
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
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    suspend fun logNow(
        slots: List<MedicationReminderSlot>,
        logTargets: List<MedicationReminderLogTarget>?,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
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

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            normalizedSlots.minOf(MedicationReminderSlot::scheduledAt)
        )
        val groupsByUuid = loadRepresentedGroups(normalizedSlots, forLogAll = true)
        // null logTargets = legacy pending intent posted before the shipping
        // format existed; preserve the previous "log everything missing"
        // behaviour for that case so the action still does something useful.
        val restrictionBySlot: Map<MedicationReminderSlot, Set<MedicationSignature>>? =
            logTargets?.groupBy(MedicationReminderLogTarget::slot)
                ?.mapValues { (_, targets) ->
                    targets.mapTo(mutableSetOf(), MedicationReminderLogTarget::signature)
                }
        val entriesToSave = normalizedSlots.flatMap { slot ->
            val group = groupsByUuid[slot.groupUuid] ?: return@flatMap emptyList()
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

        if (entriesToSave.isNotEmpty()) {
            // Snapshot stock state before the deduction so the toast only fires
            // for medicines this log actually pushed into a worse tier.
            val beforeStockStates = captureStockStatesForLog(
                medicineStockRepository = medicineStockRepository,
                now = Instant.from(now.atZone(ZoneId.systemDefault())),
            )
            medicationLogRepository.saveNewEntries(entriesToSave)
            diagnosticsLogger.info(
                TAG,
                "reminder_action_log_now_saved entriesSaved=${entriesToSave.size} slots=${normalizedSlots.size}"
            )
            showPostLogToast(
                entriesToSave = entriesToSave,
                now = now,
                medicineStockRepository = medicineStockRepository,
                reminderNotificationManager = reminderNotificationManager,
                beforeStatesByUuid = beforeStockStates,
                hideMedicationDetails = settingsRepository.getCurrentSettings().hideMedicationDetails,
            )
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

        medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
        val groupUuidsToReschedule = normalizedSlots
            .map(MedicationReminderSlot::groupUuid)
            .distinct()
        groupUuidsToReschedule.forEach { groupUuid ->
            medicationReminderScheduler.rescheduleGroup(groupUuid, after = now)
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_action_log_now_complete slots=${normalizedSlots.size} " +
                    "entriesSaved=${entriesToSave.size} groupsRescheduled=${groupUuidsToReschedule.size}"
        )
    }

    suspend fun remindLater(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_remind_later_start slots=${normalizedSlots.size} " +
                    "rawSlots=${slots.size} notificationTag=${notificationTag.orEmpty()} now=$now"
        )
        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_remind_later_skipped reason=master_disabled slots=${normalizedSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val unfulfilledSlots = currentlyUnfulfilledSlots(normalizedSlots)
        if (unfulfilledSlots.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_remind_later_no_unfulfilled_slots slots=${normalizedSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
            reminderNotificationManager.showDoseReminderNothingToAddToast()
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            diagnosticsLogger.info(
                TAG,
                "reminder_action_remind_later_complete slots=${normalizedSlots.size} " +
                        "unfulfilledSlots=0 snoozes=0"
            )
            return
        }

        val scheduledSnoozes = medicationReminderSnoozeScheduler.snoozeSlots(
            slots = unfulfilledSlots,
            now = now,
        )
        if (scheduledSnoozes.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_remind_later_no_snoozes unfulfilledSlots=${unfulfilledSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(unfulfilledSlots)
        } else {
            reminderNotificationManager.showDoseReminderSnoozedToast(REMINDER_SNOOZE_MINUTES)
        }
        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
        diagnosticsLogger.info(
            TAG,
            "reminder_action_remind_later_complete slots=${normalizedSlots.size} " +
                    "unfulfilledSlots=${unfulfilledSlots.size} snoozes=${scheduledSnoozes.size}"
        )
    }

    suspend fun showSnoozedReminder(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_show_snoozed_start slots=${normalizedSlots.size} " +
                    "rawSlots=${slots.size} notificationTag=${notificationTag.orEmpty()} now=$now"
        )
        val settings = settingsRepository.getCurrentSettings()
        if (!settings.remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_show_snoozed_skipped reason=master_disabled slots=${normalizedSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val groupsByUuid = loadRepresentedGroups(normalizedSlots)
        if (groupsByUuid.isEmpty()) {
            diagnosticsLogger.info(TAG, "reminder_action_show_snoozed_skipped reason=no_groups")
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            normalizedSlots.minOf(MedicationReminderSlot::scheduledAt)
        )
        val bundleItems = normalizedSlots
            .mapNotNull { slot ->
                val group = groupsByUuid[slot.groupUuid] ?: return@mapNotNull null
                val planSlot = slot.toMedicationGroupSlotKey()
                if (isSlotFulfilled(group, planSlot, entries)) {
                    return@mapNotNull null
                }
                val displayedMedications = group.medications.filterNot { medication ->
                    isSlotFulfilledForMedication(
                        group = group,
                        slot = planSlot,
                        medication = medication,
                        entries = entries,
                    )
                }
                if (displayedMedications.isEmpty()) {
                    return@mapNotNull null
                }
                Triple(group, slot, displayedMedications)
            }
            .sortedBy { (group, _, _) -> group.createdAt }
            .map { (group, slot, medications) ->
                MedicationReminderBundleItem(
                    slot = slot,
                    groupName = group.name,
                    medications = medications,
                )
            }
        if (bundleItems.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_show_snoozed_skipped reason=no_unfulfilled_slots"
            )
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }
        val unfulfilledSlots = bundleItems.map(MedicationReminderBundleItem::slot)

        val recordsBySlot = medicationReminderSnoozeScheduler.getSnoozeRecords()
            .associateBy(MedicationReminderSnoozeRecord::slot)
        val canSnooze = unfulfilledSlots.any { slot ->
            recordsBySlot[slot]?.snoozeCount.orZero() < MAX_REMINDER_SNOOZE_COUNT
        }

        reminderNotificationManager.showDoseReminderNotification(
            bundle = MedicationReminderBundle(
                scheduledAt = unfulfilledSlots.minOf(MedicationReminderSlot::scheduledAt),
                items = bundleItems,
            ),
            canSnooze = canSnooze,
            hideMedicationDetails = settings.hideMedicationDetails,
        )
        diagnosticsLogger.info(
            TAG,
            "reminder_action_show_snoozed_complete slots=${normalizedSlots.size} " +
                    "unfulfilledSlots=${unfulfilledSlots.size} bundleItems=${bundleItems.size} canSnooze=$canSnooze"
        )
    }

    private suspend fun currentlyUnfulfilledSlots(
        slots: List<MedicationReminderSlot>,
    ): List<MedicationReminderSlot> {
        if (slots.isEmpty()) {
            return emptyList()
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            slots.minOf(MedicationReminderSlot::scheduledAt)
        )
        val groupsByUuid = loadRepresentedGroups(slots)
        val unfulfilledSlots = slots.filter { slot ->
            val group = groupsByUuid[slot.groupUuid] ?: return@filter false
            !isSlotFulfilled(
                group = group,
                slot = slot.toMedicationGroupSlotKey(),
                entries = entries,
            )
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_action_unfulfilled_slots_checked slots=${slots.size} " +
                    "groups=${groupsByUuid.size} entries=${entries.size} unfulfilled=${unfulfilledSlots.size}"
        )
        return unfulfilledSlots
    }

    // forLogAll relaxes the archive/notifications gate for the explicit "Log all" action:
    // once a notification has fired and the user taps it, the dose should be written even
    // if the group was archived or had its notifications disabled afterwards. The snooze
    // and remind-later paths keep the gate (default false) so they never resurrect such a
    // group for future nagging.
    private suspend fun loadRepresentedGroups(
        slots: List<MedicationReminderSlot>,
        forLogAll: Boolean = false,
    ): Map<UUID, MedicationGroup> {
        val requestedGroupUuids = slots.map(MedicationReminderSlot::groupUuid).distinct()
        val groups = requestedGroupUuids
            .mapNotNull { groupUuid ->
                medicationGroupRepository.getGroup(groupUuid)
                    ?.takeIf { group -> forLogAll || (group.isActive() && group.notificationsEnabled) }
            }
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
    appliedAt: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    restrictToSignatures: Set<MedicationSignature>? = null,
): List<MedicationLogEntryInput> {
    // Archive / notification state is intentionally not gated here — callers decide. Both
    // explicit log actions (widget quick-log and the reminder "Log all") log archived or
    // notifications-disabled groups on purpose, e.g. re-logging a deleted dose. The snooze
    // and remind-later paths filter such groups upstream in loadRepresentedGroups instead.
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

    val planSlot = slot.toMedicationGroupSlotKey()
    // Count every record attached to the slot, in or out of its fulfillment window.
    // A late (out-of-window) record doesn't count toward adherence, but it proves the
    // dose was logged — writing another one on a repeated tap would double the record
    // and deduct stock twice. Window-gated adherence stays in isSlotFulfilled.
    val slotLogs = entries.filter { entry ->
        isEntryForPlanSlot(
            group = group,
            slot = planSlot,
            entry = entry,
        )
    }
    val requiredCounts = candidateMedications
        .groupBy(MedicationSignature::fromGroupMedication)
        .mapValues { (_, medications) -> medications.sumOf { medication -> medication.count } }
    val loggedCounts = slotLogs
        .groupBy(MedicationSignature::fromLogEntry)
        .mapValues { (_, logs) -> logs.sumOf(MedicationLogEntry::count) }

    return candidateMedications
        .groupBy(MedicationSignature::fromGroupMedication)
        .mapNotNull { (signature, medications) ->
            val missingCount = requiredCounts.getValue(signature) -
                    loggedCounts.getOrDefault(signature, 0)
            if (missingCount <= 0) {
                return@mapNotNull null
            }
            val templateMedication = medications.first()
            MedicationLogEntryInput(
                medicineUuid = templateMedication.medicineUuid,
                applicationType = templateMedication.applicationType,
                doseInstruction = templateMedication.doseInstruction,
                sourceGroupUuid = group.uuid,
                scheduleTimeUuid = slot.scheduleTimeUuid,
                appliedAt = appliedAt.atZone(zoneId).toInstant(),
                scheduledFor = slot.scheduledAt,
                count = missingCount,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
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
    now: LocalDateTime,
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
            .projectAllOnce(now = Instant.from(now.atZone(ZoneId.systemDefault())))
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

private fun Int?.orZero(): Int = this ?: 0

private const val TAG = "MedicationReminderActionHandler"
