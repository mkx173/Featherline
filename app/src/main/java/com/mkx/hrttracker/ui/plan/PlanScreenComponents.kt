package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.formatSummary
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.history.HistoryEntryGroupHeader
import com.mkx.hrttracker.ui.medication.medicationDoseSupportingText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationSupportingText
import com.mkx.hrttracker.ui.medication.rememberMedicationApplicationIcons
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberManualMedicationColorScheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.padding_large)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.plan_selected_day_records_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        SelectedDayRowState.LOGGED -> fulfilledIndicatorColor
        SelectedDayRowState.DUE -> MaterialTheme.colorScheme.primary
        SelectedDayRowState.PAST_DUE -> overdueScheduledIndicatorColor
        SelectedDayRowState.MISSED -> overdueScheduledIndicatorColor
        SelectedDayRowState.PLANNED -> MaterialTheme.colorScheme.onSurfaceVariant
        SelectedDayRowState.MANUAL -> rowColorScheme.primary
    }
    val supportingText = medicationSupportingText(
        details = row.details,
        medicationCount = row.medicationCount,
        extraSupportingText = row.groupName
    )
    val timeLabel = when (row) {
        is SelectedDayRowModel.Scheduled -> row.entry.scheduledTime.format(timeFormatter)
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
            SelectedDayRowLeading(
                state = rowState,
                colorScheme = rowColorScheme
            )
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .background(
                        color = rowColorScheme.primary,
                        shape = RoundedCornerShape(3.dp)
                    )
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
    appLocale: Locale,
    dateFormatter: (LocalDate) -> String,
    timeFormatter: DateTimeFormatter,
    upcomingOccurrences: List<LocalDateTime>,
    today: LocalDate,
    onClick: () -> Unit
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(group.colorKey)

    EditorSegmentedListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        index = 0,
        count = 1
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 40.dp)
                        .background(
                            color = groupColorScheme.primary,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
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
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (remindersEnabled) {
                        val notificationsEnabled = group.notificationsEnabled
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
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            HorizontalDivider(
                modifier = Modifier.padding(top = 2.dp),
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
private fun SelectedDayRowLeading(
    state: SelectedDayRowState,
    colorScheme: ColorScheme
) {
    when (state) {
        SelectedDayRowState.LOGGED -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = fulfilledIndicatorColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        SelectedDayRowState.PAST_DUE -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = 2.dp,
                        color = overdueScheduledIndicatorColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = overdueScheduledIndicatorColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        SelectedDayRowState.MISSED -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = 2.dp,
                        color = overdueScheduledIndicatorColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = overdueScheduledIndicatorColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        SelectedDayRowState.DUE -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        SelectedDayRowState.PLANNED -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }

        SelectedDayRowState.MANUAL -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = colorScheme.secondaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    val applicationTypeIcon = rememberMedicationApplicationIcons()
        .getValue(applicationType)
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
            Icon(
                imageVector = applicationTypeIcon,
                contentDescription = applicationTypeLabel,
                modifier = Modifier.size(14.dp)
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
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)

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
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)
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
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)

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
    val dateFormatter = remember(appLocale) {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(appLocale)
        return@remember { date: LocalDate -> date.format(formatter) }
    }
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    val group = uiState.medicationGroups.last()

    PlanScreenComponentPreviewContainer {
        RegimenGroupCard(
            group = group,
            remindersEnabled = uiState.remindersEnabled,
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
