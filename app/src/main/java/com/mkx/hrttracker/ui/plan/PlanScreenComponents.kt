package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.formatSummary
import com.mkx.hrttracker.ui.medication.applicationTypeBadgeLabel
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.medication.medicationSummary
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
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
    headerFormatter: DateTimeFormatter,
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
    val summaryColor = selectedDaySummaryColor(
        date = date,
        today = today,
        overallStatus = overallStatus
    )
    val summaryText = when {
        scheduledCount > 0 && date.isBefore(today) -> stringResource(
            R.string.plan_selected_day_summary_past,
            completedScheduledCount,
            scheduledCount
        )
        scheduledCount > 0 && date == today -> stringResource(
            R.string.plan_selected_day_summary_today,
            completedScheduledCount,
            scheduledCount
        )
        scheduledCount > 0 -> stringResource(
            R.string.plan_selected_day_summary_future,
            completedScheduledCount,
            scheduledCount
        )
        daySchedule.unplannedEntries.isNotEmpty() -> stringResource(
            R.string.plan_selected_day_summary_manual_only
        )
        date.isAfter(today) -> stringResource(R.string.plan_selected_day_summary_empty_future)
        else -> stringResource(R.string.plan_selected_day_summary_empty_past)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(R.dimen.padding_xsmall)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = date.format(headerFormatter),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor
                )
            }
            if (date == today) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(percent = 50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.plan_group_upcoming_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

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
            Column(modifier = Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    when (row) {
                        is SelectedDayRowModel.Scheduled -> {
                            ScheduledDayRow(
                                date = date,
                                today = today,
                                entry = row.entry,
                                appLocale = appLocale,
                                timeFormatter = timeFormatter,
                                onClick = { onScheduledClick(row.entry) }
                            )
                        }

                        is SelectedDayRowModel.Unplanned -> {
                            UnplannedDayRow(
                                entry = row.entry,
                                appLocale = appLocale,
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
@OptIn(ExperimentalLayoutApi::class)
internal fun ScheduledDayRow(
    date: LocalDate,
    today: LocalDate,
    entry: PlanDayScheduleEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(entry.groupColorKey)
    val rowState = when {
        entry.isFulfilled -> ScheduledDayRowState.LOGGED
        entry.isDueSoon -> ScheduledDayRowState.DUE
        date == today && entry.isPastDue -> ScheduledDayRowState.PAST_DUE
        date.isBefore(today) -> ScheduledDayRowState.MISSED
        else -> ScheduledDayRowState.PLANNED
    }
    val labelText = when (rowState) {
        ScheduledDayRowState.LOGGED -> stringResource(R.string.plan_schedule_entry_logged)
        ScheduledDayRowState.DUE -> stringResource(R.string.plan_schedule_entry_due_soon)
        ScheduledDayRowState.PAST_DUE -> stringResource(R.string.plan_schedule_entry_past_due)
        ScheduledDayRowState.MISSED -> stringResource(R.string.plan_schedule_entry_missed)
        ScheduledDayRowState.PLANNED -> stringResource(R.string.plan_schedule_entry_planned)
    }
    val labelColor = when (rowState) {
        ScheduledDayRowState.LOGGED -> fulfilledIndicatorColor
        ScheduledDayRowState.DUE -> MaterialTheme.colorScheme.primary
        ScheduledDayRowState.PAST_DUE -> overdueScheduledIndicatorColor
        ScheduledDayRowState.MISSED -> overdueScheduledIndicatorColor
        ScheduledDayRowState.PLANNED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val supportingText = medicationSummary(entry.medication.details)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScheduledDayRowLeading(state = rowState)
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 40.dp)
                .background(
                    color = groupColorScheme.primary,
                    shape = RoundedCornerShape(3.dp)
                )
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.groupName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MedicationCountBadge(
                    count = entry.medication.count,
                    containerColor = groupColorScheme.secondaryContainer,
                    contentColor = groupColorScheme.onSecondaryContainer
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.scheduledTime.format(timeFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RegimenGroupCard(
    group: MedicationGroup,
    remindersEnabled: Boolean,
    appLocale: Locale,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    upcomingOccurrences: List<LocalDateTime>,
    today: LocalDate,
    onClick: () -> Unit
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(group.colorKey)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = group.schedule.formatSummary(
                                locale = appLocale,
                                timeFormatter = timeFormatter,
                                dailyLabel = stringResource(
                                    R.string.group_schedule_daily_summary,
                                    group.schedule.interval
                                ),
                                weeklyLabel = stringResource(
                                    R.string.group_schedule_weekly_summary,
                                    group.schedule.interval
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        medicationName = medicationDisplayName(medication.details),
                        doseLabel = medicationDoseText(medication.details),
                        applicationType = medication.details.applicationType,
                        count = medication.count
                    )
                }
            }

            DashedDivider()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.plan_group_upcoming_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    upcomingOccurrences.forEachIndexed { index, occurrence ->
                        val dayLabel = when (occurrence.toLocalDate()) {
                            today -> stringResource(R.string.plan_group_upcoming_today)
                            today.plusDays(1) -> stringResource(R.string.plan_group_upcoming_tomorrow)
                            else -> occurrence.toLocalDate().format(dateFormatter)
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

@Composable
private fun ScheduledDayRowLeading(
    state: ScheduledDayRowState
) {
    when (state) {
        ScheduledDayRowState.LOGGED -> {
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

        ScheduledDayRowState.PAST_DUE -> {
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

        ScheduledDayRowState.MISSED -> {
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

        ScheduledDayRowState.DUE -> {
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

        ScheduledDayRowState.PLANNED -> {
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
    }
}

@Composable
private fun UnplannedDayRow(
    entry: MedicationLogEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val applicationLabel = stringResource(entry.details.applicationType.labelRes)
    val doseLabel = medicationDoseText(entry.details)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = medicationDisplayName(entry.details),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (doseLabel != null) {
                    "$doseLabel · $applicationLabel"
                } else {
                    applicationLabel
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.plan_entry_label_manual),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun selectedDaySummaryColor(
    date: LocalDate,
    today: LocalDate,
    overallStatus: PlanCalendarDayStatus
): Color {
    return when {
        overallStatus == PlanCalendarDayStatus.FULFILLED -> fulfilledIndicatorColor
        overallStatus == PlanCalendarDayStatus.PARTIAL -> overduePartialIndicatorColor
        overallStatus == PlanCalendarDayStatus.MISSED && date.isBefore(today) -> overdueScheduledIndicatorColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun RegimenMedicationChip(
    groupColorScheme: ColorScheme,
    medicationName: String,
    doseLabel: String?,
    applicationType: MedicationApplicationType,
    count: Int
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = groupColorScheme.primaryContainer,
        contentColor = groupColorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = groupColorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = applicationTypeBadgeLabel(applicationType),
                    style = MaterialTheme.typography.labelSmall,
                    color = groupColorScheme.onPrimaryContainer
                )
            }
            Text(
                text = medicationName,
                style = MaterialTheme.typography.labelMedium
            )
            MedicationCountBadge(
                count = count,
                containerColor = groupColorScheme.secondaryContainer,
                contentColor = groupColorScheme.onSecondaryContainer
            )
            if (doseLabel != null) {
                Text(
                    text = "· $doseLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = groupColorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun MedicationCountBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = medicationCountIndicatorText(count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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

@Composable
private fun DashedDivider() {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = dividerColor,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}

private sealed interface SelectedDayRowModel {
    val sortTime: LocalTime

    data class Scheduled(
        val entry: PlanDayScheduleEntry
    ) : SelectedDayRowModel {
        override val sortTime: LocalTime = entry.scheduledTime
    }

    data class Unplanned(
        val entry: MedicationLogEntry,
        override val sortTime: LocalTime
    ) : SelectedDayRowModel
}

private enum class ScheduledDayRowState {
    LOGGED,
    DUE,
    PAST_DUE,
    MISSED,
    PLANNED,
}

@Preview(showBackground = true, widthDp = 420, heightDp = 560)
@Composable
private fun SelectedDaySectionPreview() {
    val uiState = buildPlanPreviewUiState()
    val appLocale = Locale.US
    val headerFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", appLocale)
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)

    PlanScreenComponentPreviewContainer {
        SelectedDaySection(
            date = uiState.selectedDate,
            today = uiState.today,
            overallStatus = uiState.calendarDays[uiState.selectedDate]?.status ?: PlanCalendarDayStatus.NONE,
            daySchedule = uiState.daySchedule,
            appLocale = appLocale,
            headerFormatter = headerFormatter,
            timeFormatter = timeFormatter,
            onScheduledClick = { },
            onUnplannedClick = { }
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun ScheduledDayRowPreview() {
    val uiState = buildPlanPreviewUiState()
    val appLocale = Locale.US
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    val entry = uiState.daySchedule.scheduledEntries.first()

    PlanScreenComponentPreviewContainer {
        ScheduledDayRow(
            date = uiState.selectedDate,
            today = uiState.today,
            entry = entry,
            appLocale = appLocale,
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
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
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
