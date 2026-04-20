package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.formatSummary
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun PlanScreen(
    onGroupClick: (UUID) -> Unit,
    onEntryClick: (UUID) -> Unit,
    onQuickLogClick: (UUID, LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanScreenContent(
        uiState = uiState,
        onGroupClick = onGroupClick,
        onEntryClick = onEntryClick,
        onQuickLogClick = onQuickLogClick,
        onDateSelected = viewModel::setSelectedDate,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanScreenContent(
    uiState: PlanUiState,
    onGroupClick: (UUID) -> Unit,
    onEntryClick: (UUID) -> Unit,
    onQuickLogClick: (UUID, LocalDateTime) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val selection = uiState.selectedDate
    val daySchedule = uiState.daySchedule

    val state = rememberWeekCalendarState(
        startDate = uiState.calendarStartDate,
        endDate = uiState.calendarEndDate,
        firstVisibleWeekDate = selection,
        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_plan)) }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(innerPadding)
        ) {
            WeekCalendar(
                state = state,
                dayContent = { day ->
                    Day(
                        date = day.date,
                        today = uiState.today,
                        dayStatus = uiState.calendarDays[day.date]?.status ?: PlanCalendarDayStatus.NONE,
                        isSelected = selection == day.date
                    ) { clicked ->
                        onDateSelected(clicked)
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(dimensionResource(R.dimen.padding_small)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
            ) {
                item(key = "schedule-title") {
                    Text(
                        text = stringResource(
                            R.string.plan_selected_day_schedule_title,
                            selection.format(dateFormatter)
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (daySchedule.scheduledEntries.isEmpty()) {
                    item(key = "schedule-empty") {
                        Text(
                            text = stringResource(R.string.plan_selected_day_schedule_empty),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(
                        items = daySchedule.scheduledEntries,
                        key = { "${it.groupUuid}|${it.scheduledTime}" }
                    ) { scheduled ->
                        PlanScheduleEntryCard(
                            entry = scheduled,
                            appLocale = appLocale,
                            timeFormatter = timeFormatter,
                            onClick = {
                                val fulfillingEntryUuid = scheduled.fulfillingEntryUuids.firstOrNull()
                                if (fulfillingEntryUuid != null) {
                                    onEntryClick(fulfillingEntryUuid)
                                } else {
                                    onQuickLogClick(
                                        scheduled.groupUuid,
                                        LocalDateTime.of(daySchedule.date, scheduled.scheduledTime)
                                    )
                                }
                            }
                        )
                    }
                }

                if (daySchedule.unplannedEntries.isNotEmpty()) {
                    item(key = "unplanned-title") {
                        Text(
                            text = stringResource(R.string.plan_selected_day_unplanned_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(
                        items = daySchedule.unplannedEntries,
                        key = { it.uuid }
                    ) { entry ->
                        SelectedDayEntryCard(
                            entry = entry,
                            appLocale = appLocale,
                            timeFormatter = timeFormatter,
                            onClick = { onEntryClick(entry.uuid) }
                        )
                    }
                }

                item(key = "groups-title") {
                    Text(
                        text = stringResource(R.string.tab_plan),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (uiState.medicationGroups.isEmpty()) {
                    item(key = "groups-empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimensionResource(R.dimen.padding_large)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = stringResource(R.string.plan_empty_state))
                        }
                    }
                } else {
                    items(
                        items = uiState.medicationGroups,
                        key = { it.uuid }
                    ) { group ->
                        MedicationGroupCard(
                            group = group,
                            remindersEnabled = uiState.remindersEnabled,
                            appLocale = appLocale,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            upcomingOccurrences = uiState.nextOccurrencesByGroup[group.uuid].orEmpty(),
                            today = uiState.today,
                            onClick = { onGroupClick(group.uuid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayEntryCard(
    entry: MedicationLogEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        overlineContent = {
            Text(text = stringResource(entry.routeOfAdministration.labelRes))
        },
        headlineContent = {
            Text(text = entry.medicineName)
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                Text(
                    text = stringResource(
                        R.string.entry_medicine_dose,
                        entry.dosageMgAsMedicine.formatDose(appLocale)
                    )
                )
                Text(
                    text = stringResource(R.string.plan_entry_label_manual),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
            )
        }
    )
}

@Composable
private fun PlanScheduleEntryCard(
    entry: PlanDayScheduleEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val statusLabel = when {
        entry.isFulfilled -> stringResource(R.string.plan_schedule_entry_logged)
        entry.isDueSoon -> stringResource(R.string.plan_schedule_entry_due_soon)
        else -> stringResource(R.string.plan_schedule_entry_not_logged)
    }
    val statusColor = when {
        entry.isFulfilled -> fulfilledIndicatorColor
        entry.isDueSoon -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = {
            if (entry.isFulfilled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = fulfilledIndicatorColor
                )
            } else if (entry.isDueSoon) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
            }
        },
        overlineContent = {
            Text(text = entry.scheduledTime.format(timeFormatter))
        },
        headlineContent = {
            Text(text = entry.groupName)
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                entry.medications.take(3).forEach { medication ->
                    Text(
                        text = stringResource(
                            R.string.plan_group_medication_summary,
                            medication.medicineName,
                            medication.dosageMgAsMedicine.formatDose(appLocale),
                            stringResource(medication.routeOfAdministration.labelRes)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val hiddenCount = entry.medications.size - 3
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.plan_group_more_medications, hiddenCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    )
}

@Composable
private fun MedicationGroupCard(
    group: MedicationGroup,
    remindersEnabled: Boolean,
    appLocale: Locale,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    upcomingOccurrences: List<LocalDateTime>,
    today: LocalDate,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        overlineContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.plan_group_medication_count,
                    group.medications.size,
                    group.medications.size
                )
            )
        },
        headlineContent = {
            Text(text = group.name)
        },
        trailingContent = {
            if (remindersEnabled) {
                val notificationsEnabled = group.notificationsEnabled
                Icon(
                    imageVector = if (notificationsEnabled) {
                        Icons.Default.Notifications
                    } else {
                        Icons.Default.NotificationsOff
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
                    }
                )
            }
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
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
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = stringResource(
                        R.string.group_schedule_since_summary,
                        group.schedule.since.format(dateFormatter)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                group.medications.take(3).forEach { medication ->
                    Text(
                        text = stringResource(
                            R.string.plan_group_medication_summary,
                            medication.medicineName,
                            medication.dosageMgAsMedicine.formatDose(appLocale),
                            stringResource(medication.routeOfAdministration.labelRes)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val hiddenMedicationCount = group.medications.size - 3
                if (hiddenMedicationCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.plan_group_more_medications,
                            hiddenMedicationCount
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (upcomingOccurrences.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.plan_group_upcoming_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    upcomingOccurrences.forEach { occurrence ->
                        val dayLabel = when (occurrence.toLocalDate()) {
                            today -> stringResource(R.string.plan_group_upcoming_today)
                            today.plusDays(1) -> stringResource(R.string.plan_group_upcoming_tomorrow)
                            else -> occurrence.toLocalDate().format(dateFormatter)
                        }
                        Text(
                            text = stringResource(
                                R.string.plan_group_upcoming_format,
                                dayLabel,
                                occurrence.toLocalTime().format(timeFormatter)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    )
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd")
private val fulfilledIndicatorColor = Color(0xFF2E7D32)
private val overdueScheduledIndicatorColor = Color(0xFFC62828)
private val overduePartialIndicatorColor = Color(0xFFEF6C00)

@Composable
private fun Day(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus,
    isSelected: Boolean,
    onClick: (LocalDate) -> Unit
) {
    val dayLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dayNumberColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = date.dayOfWeek.displayText(),
                fontSize = 12.sp,
                color = dayLabelColor,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = dateFormatter.format(date),
                fontSize = 14.sp,
                color = dayNumberColor,
                fontWeight = FontWeight.Bold,
            )
            DayStatusIndicator(
                date = date,
                today = today,
                dayStatus = dayStatus
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DayStatusIndicator(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus
) {
    val neutralIndicatorColor = MaterialTheme.colorScheme.outline
    val scheduledIndicatorColor = if (date.isBefore(today)) {
        overdueScheduledIndicatorColor
    } else {
        neutralIndicatorColor
    }
    val partialIndicatorColor = if (date.isBefore(today)) {
        overduePartialIndicatorColor
    } else {
        neutralIndicatorColor
    }

    when (dayStatus) {
        PlanCalendarDayStatus.NONE -> {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 2.dp)
                    .background(
                        color = neutralIndicatorColor,
                        shape = RoundedCornerShape(percent = 50)
                    )
            )
        }
        PlanCalendarDayStatus.UNPLANNED -> {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = neutralIndicatorColor,
                        shape = CircleShape
                    )
            )
        }
        PlanCalendarDayStatus.SCHEDULED -> {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(
                        width = 1.5.dp,
                        color = scheduledIndicatorColor,
                        shape = CircleShape
                    )
            )
        }
        PlanCalendarDayStatus.PARTIAL -> {
            Canvas(modifier = Modifier.size(10.dp)) {
                val strokeWidth = 1.5.dp.toPx()
                drawCircle(
                    color = partialIndicatorColor,
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = partialIndicatorColor,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
        PlanCalendarDayStatus.FULFILLED -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = fulfilledIndicatorColor,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

fun DayOfWeek.displayText(uppercase: Boolean = false, narrow: Boolean = false): String {
    val style = if (narrow) TextStyle.NARROW else TextStyle.SHORT
    return getDisplayName(style, Locale.ENGLISH).let { value ->
        if (uppercase) value.uppercase(Locale.ENGLISH) else value
    }
}
