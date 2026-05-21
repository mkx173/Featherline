package com.mkx.hrttracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class QuickLogActionCallback : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val groupUuidStr = parameters[GroupUuidKey] ?: return
        val scheduleTimeUuidStr = parameters[ScheduleTimeUuidKey]
        val scheduledAtStr = parameters[ScheduledAtKey] ?: return
        val medicationUuidStr = parameters[MedicationUuidKey]

        val groupUuid = runCatching { UUID.fromString(groupUuidStr) }.getOrNull() ?: return
        val scheduleTimeUuid = scheduleTimeUuidStr?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val scheduledAt = runCatching { LocalDateTime.parse(scheduledAtStr) }.getOrNull() ?: return
        val medicationUuid = medicationUuidStr?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val groupRepository = entryPoint.medicationGroupRepository()
        val logRepository = entryPoint.medicationLogRepository()
        val diagnosticsLogger = entryPoint.diagnosticsLogger()

        val group = groupRepository.getGroup(groupUuid) ?: run {
            diagnosticsLogger.warning(TAG, "widget_quick_log_group_not_found uuid=$groupUuid")
            updateAllHrtWidgets(context.applicationContext)
            return
        }

        if (!group.isActive()) {
            updateAllHrtWidgets(context.applicationContext)
            return
        }

        // Restrict to the tapped medication so the callback doesn't log all
        // medications in the group when only one row was tapped. A non-null
        // uuid that no longer resolves means the snapshot is stale (e.g. the
        // medication was removed) — refresh and bail rather than logging the
        // remaining siblings.
        val targetGroup = if (medicationUuid != null) {
            val match = group.medications.firstOrNull { it.uuid == medicationUuid }
            if (match == null) {
                diagnosticsLogger.warning(TAG, "widget_quick_log_medication_not_found uuid=$medicationUuid")
                updateAllHrtWidgets(context.applicationContext)
                return
            }
            group.copy(medications = listOf(match))
        } else {
            group
        }

        val zoneId = ZoneId.systemDefault()
        val appliedAt = LocalDateTime.now()
        val slot = MedicationReminderSlot(
            groupUuid = groupUuid,
            scheduledAt = scheduledAt,
            scheduleTimeUuid = scheduleTimeUuid,
        )

        // Load entries from scheduledAt onward — same approach used by the reminder action handler.
        val entries = logRepository.getScheduledGroupEntriesSince(scheduledAt)

        val missingEntries = buildMissingScheduledLogEntries(
            group = targetGroup,
            slot = slot,
            entries = entries,
            appliedAt = appliedAt,
            zoneId = zoneId,
        )

        if (missingEntries.isNotEmpty()) {
            // saveNewEntries goes through runHomeDataMutation, which rewrites the home
            // snapshot. HomeWidgetManager's home-snapshot observer picks that up and
            // re-derives the widget snapshot — no explicit widget refresh needed.
            logRepository.saveNewEntries(missingEntries)
        } else {
            diagnosticsLogger.info(TAG, "widget_quick_log_already_fulfilled slot=$scheduledAt group=$groupUuid")
            updateAllHrtWidgets(context.applicationContext)
        }
    }

    companion object {
        private const val TAG = "QuickLogActionCallback"
    }
}

val GroupUuidKey = ActionParameters.Key<String>("widget_group_uuid")
val ScheduleTimeUuidKey = ActionParameters.Key<String>("widget_schedule_time_uuid")
val ScheduledAtKey = ActionParameters.Key<String>("widget_scheduled_at")
val MedicationUuidKey = ActionParameters.Key<String>("widget_medication_uuid")
