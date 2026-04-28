package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.formatSummary
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.history.HistoryEntryGroupHeader
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseSupportingText
import com.mkx.hrttracker.ui.medication.medicationSupportingText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberManualMedicationColorScheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SelectedDaySection(
    date: LocalDate,
    today: LocalDate,
    overallStatus: PlanCalendarDayStatus,
    daySchedule: PlanDaySchedule,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    onScheduledClick: (PlanDayScheduleEntry) -> Unit,
    onUnplannedClick: (MedicationLogEntry) -> Unit
) {
    val rows = remember(daySchedule) {
        buildList<SelectedDayRowModel> {
            addAll(
                daySchedule.scheduledEntries.map { entry ->
                    SelectedDayRowModel.Scheduled(entry)
                }
            )
            addAll(
                daySchedule.unplannedEntries.map { entry ->
                    SelectedDayRowModel.Unplanned(
                        entry = entry,
                        sortTime = entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalTime()
                    )
                }
            )
        }.sortedBy { it.sortTime }
    }
    val scheduledCount = daySchedule.scheduledEntries.size
    val completedScheduledCount = daySchedule.scheduledEntries.count { it.isFulfilled }
    val offPlanCount = daySchedule.unplannedEntries.size
    val countLabel = selectedDayHeaderCountLabel(
        date = date,
        today = today,
        completedScheduledCount = completedScheduledCount,
        scheduledCount = scheduledCount,
        offPlanCount = offPlanCount
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
    ) {
        HistoryEntryGroupHeader(
            date = date,
            today = today,
            dayStatus = overallStatus,
            hasOffPlanRecord = offPlanCount > 0,
            countLabel = countLabel,
            appLocale = appLocale
        )

        if (rows.isEmpty()) {
            SupportMessageListItem(
                text = stringResource(R.string.plan_selected_day_records_empty),
                painter = painterResource(R.drawable.ic_info),
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
            ) {
                rows.forEachIndexed { index, row ->
                    when (row) {
                        is SelectedDayRowModel.Scheduled -> {
                            SelectedDayRow(
                                date = date,
                                today = today,
                                row = row,
                                index = index,
                                itemCount = rows.size,
                                timeFormatter = timeFormatter,
                                onClick = { onScheduledClick(row.entry) }
                            )
                        }

                        is SelectedDayRowModel.Unplanned -> {
                            SelectedDayRow(
                                date = date,
                                today = today,
                                row = row,
                                index = index,
                                itemCount = rows.size,
                                timeFormatter = timeFormatter,
                                onClick = { onUnplannedClick(row.entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayRow(
    date: LocalDate,
    today: LocalDate,
    row: SelectedDayRowModel,
    index: Int,
    itemCount: Int,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val rowColorScheme = when (row.groupColorKey) {
        null -> rememberManualMedicationColorScheme()
        else -> rememberMedicationGroupColorScheme(row.groupColorKey)
    }
    val rowState = when (row) {
        is SelectedDayRowModel.Scheduled -> when {
            row.entry.isFulfilled -> SelectedDayRowState.LOGGED
            row.entry.isDueSoon -> SelectedDayRowState.DUE
            date == today && row.entry.isPastDue -> SelectedDayRowState.PAST_DUE
            date.isBefore(today) -> SelectedDayRowState.MISSED
            else -> SelectedDayRowState.PLANNED
        }

        is SelectedDayRowModel.Unplanned -> SelectedDayRowState.MANUAL
    }
    val labelText = when (rowState) {
        SelectedDayRowState.LOGGED -> stringResource(R.string.plan_schedule_entry_logged)
        SelectedDayRowState.DUE -> stringResource(R.string.plan_schedule_entry_due_soon)
        SelectedDayRowState.PAST_DUE -> stringResource(R.string.plan_schedule_entry_past_due)
        SelectedDayRowState.MISSED -> stringResource(R.string.plan_schedule_entry_missed)
        SelectedDayRowState.PLANNED -> stringResource(R.string.plan_schedule_entry_planned)
        SelectedDayRowState.MANUAL -> stringResource(R.string.plan_entry_label_manual)
    }
    val labelColor = when (rowState) {
        SelectedDayRowState.LOGGED -> MaterialTheme.colorScheme.primary
        SelectedDayRowState.DUE -> MaterialTheme.colorScheme.tertiary
        SelectedDayRowState.PAST_DUE,
        SelectedDayRowState.MISSED,
        SelectedDayRowState.PLANNED,
        SelectedDayRowState.MANUAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val supportingText = medicationSupportingText(
        details = row.details,
        medicationCount = row.medicationCount,
        extraSupportingText = row.groupName
    )
    val timeLabel = when (row) {
        is SelectedDayRowModel.Scheduled -> if (row.entry.isFulfilled) {
            row.entry.loggedAt?.format(timeFormatter)
                ?: row.entry.scheduledTime.format(timeFormatter)
        } else {
            row.entry.scheduledTime.format(timeFormatter)
        }
        is SelectedDayRowModel.Unplanned -> row.entry.appliedAt
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }
    val titleText = medicationDisplayName(row.details)

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
                Text(
                    text = labelText.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    textAlign = TextAlign.End
                )
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectedDayMedicationIconSurface(
                state = rowState,
                applicationType = row.details.applicationType,
                colorScheme = rowColorScheme
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.cjkTextOffset(titleText),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RegimenGroupCard(
    group: MedicationGroup,
    remindersEnabled: Boolean,
    hasNotificationAccess: Boolean,
    appLocale: Locale,
    dateFormatter: (LocalDate) -> String,
    timeFormatter: DateTimeFormatter,
    upcomingOccurrences: List<LocalDateTime>,
    today: LocalDate,
    onClick: () -> Unit,
    index: Int = 0,
    itemCount: Int = 1,
    selected: Boolean = false,
    showNotificationIcon: Boolean = true,
    showChevron: Boolean = true,
    showUpcomingSection: Boolean = true,
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(group.colorKey)
    val groupStartDate = remember(group.schedule.since, dateFormatter) {
        dateFormatter(group.schedule.since)
    }
    val grouStartDateText = stringResource(
        R.string.group_schedule_since_summary,
        groupStartDate,
    )

    EditorSegmentedListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        index = index,
        count = itemCount,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .padding(vertical = 4.dp)
                        .fillMaxHeight()
                        .background(
                            color = groupColorScheme.primary,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )

                        val groupScheduleSummary = group.schedule.formatSummary(
                            locale = appLocale,
                            timeFormatter = timeFormatter,
                            dailyIntervalLabel = scheduleIntervalLabel(
                                interval = group.schedule.interval,
                                singleIntervalLabel = stringResource(
                                    R.string.group_schedule_daily_single_interval_summary
                                ),
                                multipleIntervalLabel = pluralStringResource(
                                    R.plurals.group_schedule_daily_interval_summary,
                                    group.schedule.interval,
                                    group.schedule.interval
                                )
                            ),
                            weeklyIntervalLabel = scheduleIntervalLabel(
                                interval = group.schedule.interval,
                                singleIntervalLabel = stringResource(
                                    R.string.group_schedule_weekly_single_interval_summary
                                ),
                                multipleIntervalLabel = pluralStringResource(
                                    R.plurals.group_schedule_weekly_interval_summary,
                                    group.schedule.interval,
                                    group.schedule.interval
                                )
                            )
                        )
                        Text(
                            text = groupScheduleSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).cjkTextOffset(groupScheduleSummary),
                            softWrap = true
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = grouStartDateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).cjkTextOffset(grouStartDateText),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showNotificationIcon &&
                        shouldShowRegimenNotificationIcon(remindersEnabled, hasNotificationAccess)
                    ) {
                        val notificationsEnabled = isRegimenNotificationIconEnabled(
                            remindersEnabled = remindersEnabled,
                            hasNotificationAccess = hasNotificationAccess,
                            groupNotificationsEnabled = group.notificationsEnabled
                        )
                        Icon(
                            imageVector = if (notificationsEnabled) {
                                Icons.Rounded.Notifications
                            } else {
                                Icons.Rounded.NotificationsOff
                            },
                            contentDescription = stringResource(
                                if (notificationsEnabled) {
                                    R.string.plan_group_notifications_enabled
                                } else {
                                    R.string.plan_group_notifications_disabled
                                }
                            ),
                            tint = if (notificationsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (selected || showChevron) {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.ChevronRight,
                            contentDescription = if (selected) {
                                stringResource(R.string.plan_batch_add_group_selected)
                            } else {
                                null
                            },
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                group.medications.forEach { medication ->
                    RegimenMedicationChip(
                        groupColorScheme = groupColorScheme,
                        applicationType = medication.details.applicationType,
                        medicationName = medicationDisplayName(medication.details),
                        doseSummary = medicationDoseSupportingText(
                            details = medication.details,
                            medicationCount = medication.count
                        )
                    )
                }
            }

            if (showUpcomingSection) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.plan_group_upcoming_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        upcomingOccurrences.forEachIndexed { index, occurrence ->
                            val dayLabel = when (occurrence.toLocalDate()) {
                                today -> stringResource(R.string.plan_group_upcoming_today)
                                today.plusDays(1) -> stringResource(R.string.plan_group_upcoming_tomorrow)
                                else -> dateFormatter(occurrence.toLocalDate())
                            }
                            UpcomingOccurrenceChip(
                                label = dayLabel,
                                time = occurrence.toLocalTime().format(timeFormatter),
                                emphasized = index == 0
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldShowRegimenNotificationIcon(
    remindersEnabled: Boolean,
    hasNotificationAccess: Boolean
): Boolean {
    return remindersEnabled || !hasNotificationAccess
}

internal fun isRegimenNotificationIconEnabled(
    remindersEnabled: Boolean,
    hasNotificationAccess: Boolean,
    groupNotificationsEnabled: Boolean
): Boolean {
    return remindersEnabled && hasNotificationAccess && groupNotificationsEnabled
}

private fun scheduleIntervalLabel(
    interval: Int,
    singleIntervalLabel: String,
    multipleIntervalLabel: String
): String {
    return if (interval == 1) {
        singleIntervalLabel
    } else {
        multipleIntervalLabel
    }
}

@Composable
private fun SelectedDayMedicationIconSurface(
    state: SelectedDayRowState,
    applicationType: MedicationApplicationType,
    colorScheme: ColorScheme
) {
    val applicationTypeLabel = stringResource(applicationType.labelRes)
    val showDueBadge = state == SelectedDayRowState.DUE
    val useOutlinedIcon = state == SelectedDayRowState.PAST_DUE ||
        state == SelectedDayRowState.MISSED

    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.small,
            color = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state == SelectedDayRowState.LOGGED) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    MedicationApplicationIcon(
                        applicationType = applicationType,
                        contentDescription = applicationTypeLabel,
                        modifier = Modifier.size(20.dp),
                        scheduleIconSize = 9.dp,
                        outlined = useOutlinedIcon,
                    )
                }
            }
        }
        if (showDueBadge) {
            Badge(
                containerColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .offset(x = 1.dp, y = (-1).dp)
            )
        }
    }
}

private fun selectedDayHeaderCountLabel(
    date: LocalDate,
    today: LocalDate,
    completedScheduledCount: Int,
    scheduledCount: Int,
    offPlanCount: Int
): String {
    return if (date.isAfter(today)) {
        scheduledCount.toString()
    } else {
        buildString {
            if (scheduledCount > 0) {
                append(completedScheduledCount)
                append("/")
                append(scheduledCount)
            } else {
                append("0")
            }
            if (offPlanCount > 0) {
                if (isNotEmpty()) {
                    append(" ")
                }
                append("(")
                append(offPlanCount)
                append(")")
            }
        }
    }
}

@Composable
private fun RegimenMedicationChip(
    groupColorScheme: ColorScheme,
    applicationType: MedicationApplicationType,
    medicationName: String,
    doseSummary: String,
) {
    val applicationTypeLabel = stringResource(applicationType.labelRes)

    Surface(
        shape = CircleShape,
        color = groupColorScheme.primaryContainer,
        contentColor = groupColorScheme.onPrimaryContainer,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            MedicationApplicationIcon(
                applicationType = applicationType,
                contentDescription = applicationTypeLabel,
                modifier = Modifier.size(14.dp),
                scheduleIconSize = 6.dp,
            )
            val medicationString =
                listOfNotNull(medicationName, doseSummary.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            Text(
                text = medicationString,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.cjkTextOffset(medicationString)
            )
        }
    }
}

@Composable
private fun UpcomingOccurrenceChip(
    label: String,
    time: String,
    emphasized: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private sealed interface SelectedDayRowModel {
    val sortTime: LocalTime
    val details: com.mkx.hrttracker.model.medication.MedicationDetails
    val medicationCount: Int
    val groupName: String?
    val groupColorKey: MedicationGroupColorKey?

    data class Scheduled(
        val entry: PlanDayScheduleEntry
    ) : SelectedDayRowModel {
        override val sortTime: LocalTime = entry.scheduledTime
        override val details = entry.medication.details
        override val medicationCount: Int = entry.medication.count
        override val groupName: String = entry.groupName
        override val groupColorKey: MedicationGroupColorKey = entry.groupColorKey
    }

    data class Unplanned(
        val entry: MedicationLogEntry,
        override val sortTime: LocalTime
    ) : SelectedDayRowModel {
        override val details = entry.details
        override val medicationCount: Int = entry.count
        override val groupName: String? = null
        override val groupColorKey: MedicationGroupColorKey? = null
    }
}

private enum class SelectedDayRowState {
    LOGGED,
    DUE,
    PAST_DUE,
    MISSED,
    PLANNED,
    MANUAL,
}

@Preview(showBackground = true, widthDp = 420, heightDp = 560)
@Composable
private fun SelectedDaySectionPreview() {
    val uiState = buildPlanPreviewUiState()
    val appLocale = Locale.US
    val timeFormatter = localizedShortTimeFormatter(appLocale)

    PlanScreenComponentPreviewContainer {
        SelectedDaySection(
            date = uiState.daySchedule.date,
            today = uiState.today,
            overallStatus = uiState.calendarDays[uiState.daySchedule.date]?.status
                ?: PlanCalendarDayStatus.NONE,
            daySchedule = uiState.daySchedule,
            appLocale = appLocale,
            timeFormatter = timeFormatter,
            onScheduledClick = { },
            onUnplannedClick = { }
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun SelectedDayRowPreview() {
    val uiState = buildPlanPreviewUiState()
    val timeFormatter = localizedShortTimeFormatter(Locale.US)
    val row = SelectedDayRowModel.Scheduled(uiState.daySchedule.scheduledEntries.first())

    PlanScreenComponentPreviewContainer {
        SelectedDayRow(
            date = uiState.daySchedule.date,
            today = uiState.today,
            row = row,
            index = 0,
            itemCount = 1,
            timeFormatter = timeFormatter,
            onClick = { }
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun SelectedDayManualRowPreview() {
    val uiState = buildPlanPreviewUiState()
    val entry = uiState.daySchedule.unplannedEntries.first()
    val timeFormatter = localizedShortTimeFormatter(Locale.US)

    PlanScreenComponentPreviewContainer {
        SelectedDayRow(
            date = uiState.daySchedule.date,
            today = uiState.today,
            row = SelectedDayRowModel.Unplanned(
                entry = entry,
                sortTime = entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalTime()
            ),
            index = 0,
            itemCount = 1,
            timeFormatter = timeFormatter,
            onClick = { }
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun RegimenGroupCardPreview() {
    val uiState = buildPlanPreviewUiState()
    val appLocale = Locale.US
    val dateFormatter = remember(appLocale, uiState.today) {
        dateLabelFormatter(appLocale, uiState.today)
    }
    val timeFormatter = localizedShortTimeFormatter(appLocale)
    val group = uiState.medicationGroups.last()

    PlanScreenComponentPreviewContainer {
        RegimenGroupCard(
            group = group,
            remindersEnabled = uiState.remindersEnabled,
            hasNotificationAccess = true,
            appLocale = appLocale,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            upcomingOccurrences = uiState.nextOccurrencesByGroup[group.uuid].orEmpty(),
            today = uiState.today,
            onClick = { }
        )
    }
}

@Composable
private fun PlanScreenComponentPreviewContainer(
    content: @Composable () -> Unit
) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}
