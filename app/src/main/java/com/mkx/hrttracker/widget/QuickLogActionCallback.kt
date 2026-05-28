package com.mkx.hrttracker.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import com.mkx.hrttracker.reminder.showPostLogToast
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
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
        val appContext = context.applicationContext

        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WidgetEntryPoint::class.java,
        )
        val groupRepository = entryPoint.medicationGroupRepository()
        val logRepository = entryPoint.medicationLogRepository()
        val medicineStockRepository = entryPoint.medicineStockRepository()
        val settingsRepository = entryPoint.settingsRepository()
        val reminderNotificationManager = entryPoint.reminderNotificationManager()
        val diagnosticsLogger = entryPoint.diagnosticsLogger()

        val group = groupRepository.getGroup(groupUuid) ?: run {
            diagnosticsLogger.warning(TAG, "widget_quick_log_group_not_found uuid=$groupUuid")
            updateAllHrtWidgets(appContext)
            return
        }

        if (!group.isActive()) {
            updateAllHrtWidgets(appContext)
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
                updateAllHrtWidgets(appContext)
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

        try {
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
                showPostLogToast(
                    entriesToSave = missingEntries,
                    now = appliedAt,
                    medicineStockRepository = medicineStockRepository,
                    reminderNotificationManager = reminderNotificationManager,
                    hideMedicationDetails = settingsRepository.getCurrentSettings().hideMedicationDetails,
                )
            } else {
                diagnosticsLogger.info(TAG, "widget_quick_log_already_fulfilled slot=$scheduledAt group=$groupUuid")
                updateAllHrtWidgets(appContext)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnosticsLogger.warning(
                TAG,
                "widget_quick_log_failed slot=$scheduledAt group=$groupUuid",
                error,
            )
            refreshWidgetsBestEffort(appContext)
            showQuickLogFailureToast(appContext)
        }
    }

    private suspend fun refreshWidgetsBestEffort(context: Context) {
        try {
            updateAllHrtWidgets(context)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The stale widget state is secondary to surfacing the quick-log failure.
        }
    }

    private fun showQuickLogFailureToast(context: Context) {
        val message = context.getString(R.string.widget_quick_log_failed)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT,
            ).show()
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
