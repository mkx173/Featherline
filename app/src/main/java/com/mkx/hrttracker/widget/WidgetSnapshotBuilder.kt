package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HomePkProjectionRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationEntryTitle
import com.mkx.hrttracker.util.medicationRouteLabel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun buildWidgetSnapshotRecord(
    context: Context,
    homeSnapshot: HomeSnapshotRecord,
    settings: SettingsState,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    timeFormatter: DateTimeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = true),
): WidgetSnapshotRecord {
    val today = now.toLocalDate()
    val yesterday = today.minusDays(1)
    val comingUpEnd = today.plusDays(1).atTime(6, 0)
    val activeGroups = homeSnapshot.activeGroups
    val allGroups = activeGroups + homeSnapshot.archivedGroups
    val scheduleEntries = homeSnapshot.scheduleEntries
    val visibleEntries = visibleMedicationEntries(
        homeSnapshot.scheduleEntries,
        allGroups,
        settings.showArchivedGroupRecords,
    )
    val scheduleGroups = if (settings.showArchivedGroupRecords) allGroups else activeGroups
    val groupColorByUuid = allGroups.associate { group -> group.uuid to group.colorKey }
    val archivedGroupUuids = allGroups.filter(MedicationGroup::isArchived)
        .mapTo(mutableSetOf()) { group -> group.uuid }

    val isOvernight = now.toLocalTime().isBefore(LocalTime.of(6, 0))
    val lastNightRows = if (isOvernight) {
        val yesterdaySchedule = buildPlanDaySchedule(
            date = yesterday,
            groups = scheduleGroups,
            entries = visibleEntries,
            now = now,
            zoneId = zoneId,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
        )
        val eveningCutoff = LocalTime.of(18, 0)
        val scheduledLastNight = yesterdaySchedule.scheduledEntries
            .filter { entry -> !entry.scheduledFor.toLocalTime().isBefore(eveningCutoff) }
            .map { entry ->
                entry.toWidgetDoseRow(
                    context,
                    timeFormatter,
                    WidgetDoseChip.LAST_NIGHT,
                    isFromArchivedGroup = entry.groupUuid in archivedGroupUuids,
                )
            }
        val manualLastNight = yesterdaySchedule.unplannedEntries
            .map { entry ->
                entry.toManualWidgetDoseRow(
                    context = context,
                    zoneId = zoneId,
                    colorKey = entry.sourceGroupUuid?.let(groupColorByUuid::get),
                    contextChip = WidgetDoseChip.LAST_NIGHT,
                    isFromArchivedGroup = entry.sourceGroupUuid != null &&
                        entry.sourceGroupUuid in archivedGroupUuids,
                )
            }
            .filter { row -> !row.scheduledAt.toLocalTime().isBefore(eveningCutoff) }
        (scheduledLastNight + manualLastNight).sortedBy { row -> row.scheduledAt }
    } else {
        emptyList()
    }

    val todaySchedule = buildPlanDaySchedule(
        date = today,
        groups = scheduleGroups,
        entries = visibleEntries,
        now = now,
        zoneId = zoneId,
        includeUnloggedArchivedSlots = false,
        unloggedArchivedSlotCutoff = now,
    )
    val todayScheduledRows = todaySchedule.scheduledEntries
        .map { entry ->
            entry.toWidgetDoseRow(
                context,
                timeFormatter,
                null,
                isFromArchivedGroup = entry.groupUuid in archivedGroupUuids,
            )
        }
    val manualRows = todaySchedule.unplannedEntries
        .map { entry ->
            entry.toManualWidgetDoseRow(
                context = context,
                zoneId = zoneId,
                colorKey = entry.sourceGroupUuid?.let(groupColorByUuid::get),
                contextChip = null,
                isFromArchivedGroup = entry.sourceGroupUuid != null &&
                    entry.sourceGroupUuid in archivedGroupUuids,
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
            .map { entry ->
                entry.toWidgetDoseRow(
                    context,
                    timeFormatter,
                    WidgetDoseChip.COMING_UP,
                    isFromArchivedGroup = entry.groupUuid in archivedGroupUuids,
                )
            }
    } else {
        emptyList()
    }

    return WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = zoneId.id,
        anchorDateEpochDay = today.toEpochDay(),
        doneCount = todayScheduledRows.count { row -> row.status == WidgetDoseStatus.DONE },
        totalCount = todayScheduledRows.size,
        manualCount = manualRows.size,
        hasActiveGroups = activeGroups.isNotEmpty(),
        hideMedicationDetails = settings.hideMedicationDetails,
        adaptiveColorEnabled = settings.adaptiveColorEnabled,
        widgetContentScale = settings.widgetContentScale,
        widgetBackgroundAlpha = settings.widgetBackgroundAlpha,
        e2DisplayUnit = settings.homeE2DisplayUnit.storageValue,
        appLanguageTag = settings.appLanguageOption.languageTag,
        forcedDark = when (settings.widgetDarkModeOption) {
            DarkModeOption.LIGHT -> false
            DarkModeOption.DARK -> true
            DarkModeOption.FOLLOW_SYSTEM -> null
        },
        doseRows = lastNightRows + (todayScheduledRows + manualRows).sortedBy { row -> row.scheduledAt } + comingUpRows,
        pkProjection = homeSnapshot.widgetPkProjection?.toWidgetRecord(),
    )
}

private fun MedicationLogEntry.toManualWidgetDoseRow(
    context: Context,
    zoneId: ZoneId,
    colorKey: com.mkx.hrttracker.model.medication.MedicationGroupColorKey?,
    contextChip: WidgetDoseChip?,
    isFromArchivedGroup: Boolean,
): WidgetDoseRow {
    return WidgetDoseRow(
        medicationName = medicationEntryTitle(medicine, applicationType, context),
        groupName = "",
        colorKey = colorKey,
        routeLabel = medicationRouteLabel(applicationType, context),
        doseText = listOfNotNull(
            doseInstructionText(
                context = context,
                medicine = medicine,
                doseInstruction = doseInstruction,
                count = count,
                doseAmountDelta = doseAmountDelta,
            ),
        ).joinToString(separator = " · "),
        status = WidgetDoseStatus.DONE,
        scheduledAt = appliedAt.atZone(zoneId).toLocalDateTime(),
        trailingText = context.getString(R.string.plan_entry_label_manual),
        isManualRecord = true,
        isFromArchivedGroup = isFromArchivedGroup,
        contextChip = contextChip,
        groupUuid = null,
        scheduleTimeUuid = null,
        entryUuid = uuid.toString(),
    )
}

private fun PlanDayScheduleEntry.toWidgetDoseRow(
    context: Context,
    timeFormatter: DateTimeFormatter,
    contextChip: WidgetDoseChip?,
    isFromArchivedGroup: Boolean,
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
        medicationName = medicationEntryTitle(
            medication.medicine, medication.applicationType, context,
        ),
        groupName = groupName,
        colorKey = groupColorKey,
        routeLabel = medicationRouteLabel(medication.applicationType, context),
        doseText = listOfNotNull(
            doseInstructionText(
                context = context,
                medicine = medication.medicine,
                doseInstruction = medication.doseInstruction,
                count = medication.count,
                doseAmountDelta = doseAmountDelta,
            ),
        ).joinToString(separator = " · "),
        status = status,
        scheduledAt = scheduledFor,
        trailingText = displayTime,
        isManualRecord = false,
        isFromArchivedGroup = isFromArchivedGroup,
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
