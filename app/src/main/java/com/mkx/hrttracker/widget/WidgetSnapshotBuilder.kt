package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HomePkProjectionRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.medicationDisplayName
import com.mkx.hrttracker.util.medicationDoseText
import com.mkx.hrttracker.util.medicationRouteLabel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal fun buildWidgetSnapshotRecord(
    context: Context,
    homeSnapshot: HomeSnapshotRecord,
    settings: SettingsState,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): WidgetSnapshotRecord {
    val today = now.toLocalDate()
    val yesterday = today.minusDays(1)
    val comingUpEnd = today.plusDays(1).atTime(6, 0)
    val activeGroups = homeSnapshot.activeGroups
    val scheduleEntries = homeSnapshot.scheduleEntries
    val groupColorByUuid = activeGroups.associate { group -> group.uuid to group.colorKey }

    val isOvernight = now.toLocalTime().isBefore(LocalTime.of(6, 0))
    val lastNightRows = if (isOvernight) {
        val yesterdaySchedule = buildPlanDaySchedule(
            date = yesterday,
            groups = activeGroups,
            entries = scheduleEntries,
            now = now,
            zoneId = zoneId,
        )
        val eveningCutoff = LocalTime.of(18, 0)
        val scheduledLastNight = yesterdaySchedule.scheduledEntries
            .filter { entry -> !entry.scheduledFor.toLocalTime().isBefore(eveningCutoff) }
            .map { entry -> entry.toWidgetDoseRow(context, WidgetDoseChip.LAST_NIGHT) }
        val manualLastNight = yesterdaySchedule.unplannedEntries
            .map { entry ->
                entry.toManualWidgetDoseRow(
                    context = context,
                    zoneId = zoneId,
                    colorKey = entry.sourceGroupUuid?.let(groupColorByUuid::get),
                    contextChip = WidgetDoseChip.LAST_NIGHT,
                )
            }
            .filter { row -> !row.scheduledAt.toLocalTime().isBefore(eveningCutoff) }
        (scheduledLastNight + manualLastNight).sortedBy { row -> row.scheduledAt }
    } else {
        emptyList()
    }

    val todaySchedule = buildPlanDaySchedule(
        date = today,
        groups = activeGroups,
        entries = scheduleEntries,
        now = now,
        zoneId = zoneId,
    )
    val todayScheduledRows = todaySchedule.scheduledEntries
        .map { entry -> entry.toWidgetDoseRow(context, null) }
    val manualRows = todaySchedule.unplannedEntries
        .map { entry ->
            entry.toManualWidgetDoseRow(
                context = context,
                zoneId = zoneId,
                colorKey = entry.sourceGroupUuid?.let(groupColorByUuid::get),
                contextChip = null,
            )
        }

    val isEvening = now.toLocalTime() >= LocalTime.of(18, 0)
    val comingUpRows = if (isEvening) {
        val tomorrowSchedule = buildPlanDaySchedule(
            date = today.plusDays(1),
            groups = activeGroups,
            entries = scheduleEntries,
            now = now,
            zoneId = zoneId,
        )
        tomorrowSchedule.scheduledEntries
            .filter { entry -> entry.scheduledFor.isBefore(comingUpEnd) }
            .map { entry -> entry.toWidgetDoseRow(context, WidgetDoseChip.COMING_UP) }
    } else {
        emptyList()
    }

    return WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = zoneId.id,
        doneCount = todayScheduledRows.count { row -> row.status == WidgetDoseStatus.DONE },
        totalCount = todayScheduledRows.size,
        manualCount = manualRows.size,
        hideMedicationDetails = settings.hideMedicationDetails,
        adaptiveColorEnabled = settings.adaptiveColorEnabled,
        widgetContentScale = settings.widgetContentScale,
        widgetBackgroundAlpha = settings.widgetBackgroundAlpha,
        e2DisplayUnit = settings.homeE2DisplayUnit.storageValue,
        doseRows = lastNightRows + (todayScheduledRows + manualRows).sortedBy { row -> row.scheduledAt } + comingUpRows,
        pkProjection = homeSnapshot.widgetPkProjection?.toWidgetRecord(),
    )
}

private fun MedicationLogEntry.toManualWidgetDoseRow(
    context: Context,
    zoneId: ZoneId,
    colorKey: com.mkx.hrttracker.model.medication.MedicationGroupColorKey?,
    contextChip: WidgetDoseChip?,
): WidgetDoseRow {
    return WidgetDoseRow(
        medicationName = medicationDisplayName(details, context),
        groupName = "",
        colorKey = colorKey,
        routeLabel = medicationRouteLabel(details, context),
        doseText = medicationDoseText(context, details) ?: "",
        status = WidgetDoseStatus.DONE,
        scheduledAt = appliedAt.atZone(zoneId).toLocalDateTime(),
        trailingText = context.getString(R.string.plan_entry_label_manual),
        isManualRecord = true,
        contextChip = contextChip,
        groupUuid = null,
        scheduleTimeUuid = null,
        entryUuid = uuid.toString(),
    )
}

private fun PlanDayScheduleEntry.toWidgetDoseRow(
    context: Context,
    contextChip: WidgetDoseChip?,
): WidgetDoseRow {
    val status = when {
        isFulfilled -> WidgetDoseStatus.DONE
        hasOutsideScheduleWindowEntry -> WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
        isPastDue -> WidgetDoseStatus.OVERDUE
        isDueSoon -> WidgetDoseStatus.DUE_SOON
        else -> WidgetDoseStatus.UPCOMING
    }
    val displayTime = when (status) {
        WidgetDoseStatus.DONE,
        WidgetDoseStatus.LOGGED_OUT_OF_WINDOW,
        -> null
        else -> scheduledFor.format(timeFormatter)
    }
    return WidgetDoseRow(
        medicationName = medicationDisplayName(medication.details, context),
        groupName = groupName,
        colorKey = groupColorKey,
        routeLabel = medicationRouteLabel(medication.details, context),
        doseText = medicationDoseText(context, medication.details) ?: "",
        status = status,
        scheduledAt = scheduledFor,
        trailingText = displayTime,
        isManualRecord = false,
        contextChip = contextChip,
        groupUuid = groupUuid.toString(),
        scheduleTimeUuid = scheduleTimeUuid?.toString(),
        medicationUuid = medication.uuid.toString(),
    )
}

private fun HomePkProjectionRecord.toWidgetRecord(): WidgetPkProjectionRecord =
    WidgetPkProjectionRecord(
        generatedAtEpochMillis = generatedAtEpochMillis,
        windowStartEpochMillis = windowStartEpochMillis,
        windowEndEpochMillis = windowEndEpochMillis,
        pkProjectionExpiresAtEpochMillis = pkProjectionExpiresAtEpochMillis,
        concentrationUnit = concentrationUnit,
        timeH = timeH,
        concentrations = concentrations,
        doseMarkers = doseMarkers.map { marker ->
            WidgetPkDoseMarkerRecord(
                timeH = marker.timeH,
                concentration = marker.concentration,
                isPlanned = marker.isPlanned,
            )
        },
    )

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
