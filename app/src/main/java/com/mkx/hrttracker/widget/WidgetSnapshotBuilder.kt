package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.model.medication.MedicationDayTimeRange
import com.mkx.hrttracker.model.medication.includesInLastNight
import com.mkx.hrttracker.model.medication.isLastNightVisible
import com.mkx.hrttracker.model.medication.isTonightVisible
import com.mkx.hrttracker.model.medication.lastNightMedicationDay
import com.mkx.hrttracker.model.medication.tonightMedicationDay
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationEntryTitle
import com.mkx.hrttracker.util.medicationRouteLabel
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.medicationDay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun buildWidgetSnapshotRecord(
    context: Context,
    homeSnapshot: HomeSnapshotRecord,
    /**
     * The widget's E2 estimate at [now] (E2EstimateRepository.widgetEstimate).
     * Null builds none; WidgetSnapshotRepository.writeWidgetSnapshot then
     * keeps the stored one.
     */
    widgetEstimate: PkProjectionResult?,
    settings: SettingsState,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    timeFormatter: DateTimeFormatter = localizedShortTimeFormatter(
        Locale.US,
        uses24HourFormat = true
    ),
): WidgetSnapshotRecord {
    val boundary = settings.medicationDayBoundary
    val today = medicationDay(now, boundary)
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

    val lastNightRows = if (boundary.isLastNightVisible(now)) {
        val previousDay = boundary.lastNightMedicationDay(now)
        val previousSchedule = buildPlanDaySchedule(
            date = previousDay,
            boundary = boundary,
            groups = scheduleGroups,
            entries = visibleEntries,
            now = now,
            zoneId = zoneId,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
        )
        val scheduledLastNight = previousSchedule.scheduledEntries
            .filter { entry -> boundary.includesInLastNight(entry.scheduledFor) }
            .map { entry ->
                entry.toWidgetDoseRow(
                    context = context,
                    timeFormatter = timeFormatter,
                    contextChip = WidgetDoseChip.LAST_NIGHT,
                    isFromArchivedGroup = entry.groupUuid in archivedGroupUuids,
                )
            }
        val manualLastNight = previousSchedule.unplannedEntries
            .filter { entry -> boundary.includesInLastNight(appliedAtAsLocalDateTime(entry, zoneId)) }
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
        (scheduledLastNight + manualLastNight).sortedBy { row -> row.scheduledAt }
    } else {
        emptyList()
    }

    val todaySchedule = buildPlanDaySchedule(
        date = today,
        boundary = boundary,
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
                context = context,
                timeFormatter = timeFormatter,
                contextChip = null,
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

    val comingUpRows = if (boundary.isTonightVisible(now)) {
        val tomorrow = boundary.tonightMedicationDay(now)
        val tomorrowSchedule = buildPlanDaySchedule(
            date = tomorrow,
            boundary = boundary,
            groups = activeGroups,
            entries = scheduleEntries,
            now = now,
            zoneId = zoneId,
        )
        tomorrowSchedule.scheduledEntries
            .filter { entry ->
                MedicationDayTimeRange.of(entry.scheduledFor.toLocalTime()) ==
                    MedicationDayTimeRange.MIDNIGHT
            }
            .map { entry ->
                entry.toWidgetDoseRow(
                    context = context,
                    timeFormatter = timeFormatter,
                    contextChip = WidgetDoseChip.COMING_UP,
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
        e2DisplayUnit = settings.homeE2DisplayUnit.storageValue,
        appLanguageTag = settings.appLanguageOption.languageTag,
        doseRows = lastNightRows + (todayScheduledRows + manualRows).sortedBy { row -> row.scheduledAt } + comingUpRows,
        pkProjection = widgetEstimate?.toWidgetRecord(generatedAt = now.atZone(zoneId).toInstant()),
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
        routeLabel = medicationRouteLabel(medicine, applicationType, context),
        doseText = doseInstructionText(
            context = context,
            medicine = medicine,
            doseInstruction = doseInstruction,
            count = count,
            doseAmountDelta = doseAmountDelta,
        ).orEmpty(),
        status = WidgetDoseStatus.DONE,
        scheduledAt = appliedAtAsLocalDateTime(this, zoneId),
        trailingText = context.getString(R.string.plan_entry_label_manual),
        isManualRecord = true,
        isImportedRecord = importSourceApp != null,
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

        else -> scheduledFor.toLocalTime().format(timeFormatter)
    }
    return WidgetDoseRow(
        medicationName = medicationEntryTitle(
            medication.medicine, medication.applicationType, context,
        ),
        groupName = groupName,
        colorKey = groupColorKey,
        routeLabel = medicationRouteLabel(
            medication.medicine,
            medication.applicationType,
            context,
        ),
        doseText = doseInstructionText(
            context = context,
            medicine = medication.medicine,
            doseInstruction = medication.doseInstruction,
            count = medication.count,
            doseAmountDelta = doseAmountDelta,
        ).orEmpty(),
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

private fun PkProjectionResult.toWidgetRecord(generatedAt: Instant): WidgetPkProjectionRecord =
    WidgetPkProjectionRecord(
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        windowStartEpochMillis = windowStart.toEpochMilli(),
        windowEndEpochMillis = windowEnd.toEpochMilli(),
        // Logged doses only: nothing goes stale before the window end.
        pkProjectionExpiresAtEpochMillis = windowEnd.toEpochMilli(),
        concentrationUnit = concentrationUnit.name,
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
