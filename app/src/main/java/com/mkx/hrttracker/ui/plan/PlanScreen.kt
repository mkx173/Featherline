package com.mkx.hrttracker.ui.plan

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarLayoutInfo
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.Week
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.formatSummary
import com.mkx.hrttracker.ui.medication.applicationTypeBadgeLabel
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.medication.medicationSummary
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.rememberAppLocale
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

@Composable
fun PlanScreen(
    onGroupClick: (UUID) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    onQuickLogClick: (UUID, LocalDateTime, MedicationDetails, Int) -> Unit,
    onAddGroupClick: () -> Unit,
    onHistoryClick: () -> Unit,
    scrollToTopSignal: Int = 0,
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
        onAddGroupClick = onAddGroupClick,
        onHistoryClick = onHistoryClick,
        onDateSelected = viewModel::setSelectedDate,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanScreenContent(
    uiState: PlanUiState,
    onGroupClick: (UUID) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    onQuickLogClick: (UUID, LocalDateTime, MedicationDetails, Int) -> Unit,
    onAddGroupClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val selectedDayHeaderFormatter = remember(appLocale) {
        DateTimeFormatter.ofPattern("EEEE, MMM d", appLocale)
    }
    val monthFormatter = remember(appLocale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", appLocale)
    }
    val selection = uiState.selectedDate
    val daySchedule = uiState.daySchedule
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val state = rememberWeekCalendarState(
        startDate = uiState.calendarStartDate,
        endDate = uiState.calendarEndDate,
        firstVisibleWeekDate = uiState.today,
        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
    )
    val visibleWeek = rememberFirstMostVisibleWeek(state, viewportPercent = 90f)
    val visibleWeekStartDate = visibleWeek.days.first().date

    LaunchedEffect(selection) {
        if (visibleWeek.days.none { it.date == selection }) {
            state.animateScrollToWeek(selection)
        }
    }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_plan)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = stringResource(R.string.plan_open_history)
                        )
                    }
                }
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = dimensionResource(R.dimen.padding_small),
                top = dimensionResource(R.dimen.padding_small),
                end = dimensionResource(R.dimen.padding_small),
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            item(key = "week-header") {
                PlanWeekHeader(
                    weekStartDate = visibleWeekStartDate,
                    today = uiState.today,
                    firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                    monthFormatter = monthFormatter,
                    canNavigateBackward = state.canScrollBackward,
                    canNavigateForward = state.canScrollForward,
                    onPreviousClick = {
                        if (state.canScrollBackward) {
                            scope.launch {
                                state.animateScrollToWeek(visibleWeekStartDate.minusWeeks(1))
                            }
                        }
                    },
                    onNextClick = {
                        if (state.canScrollForward) {
                            scope.launch {
                                state.animateScrollToWeek(visibleWeekStartDate.plusWeeks(1))
                            }
                        }
                    }
                )
            }

            item(key = "week-calendar") {
                WeekCalendar(
                    modifier = Modifier.fillMaxWidth(),
                    state = state,
                    dayContent = { day ->
                        Day(
                            date = day.date,
                            today = uiState.today,
                            dayStatus = if (day.date.month == uiState.today.month) {
                                uiState.calendarDays[day.date]?.status ?: PlanCalendarDayStatus.NONE
                            } else {
                                PlanCalendarDayStatus.NONE
                            },
                            isSelected = selection == day.date
                        ) { clicked ->
                            onDateSelected(clicked)
                        }
                    },
                )
            }

            item(key = "calendar-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium)),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            }

            item(key = "selected-day") {
                SelectedDaySection(
                    date = selection,
                    today = uiState.today,
                    overallStatus = uiState.calendarDays[selection]?.status ?: PlanCalendarDayStatus.NONE,
                    daySchedule = daySchedule,
                    appLocale = appLocale,
                    headerFormatter = selectedDayHeaderFormatter,
                    timeFormatter = timeFormatter,
                    onScheduledClick = { scheduled ->
                        val editingEntryIds = plannedEntryEditorIds(scheduled)
                        if (scheduled.isFulfilled && editingEntryIds.isNotEmpty()) {
                            onEntryClick(editingEntryIds)
                        } else {
                            onQuickLogClick(
                                scheduled.groupUuid,
                                LocalDateTime.of(daySchedule.date, scheduled.scheduledTime),
                                scheduled.medication.details,
                                remainingQuickLogCount(
                                    totalCount = scheduled.medication.count,
                                    fulfilledCount = scheduled.loggedCount
                                )
                            )
                        }
                    },
                    onUnplannedClick = { entry ->
                        onEntryClick(unplannedEntryEditorIds(entry))
                    }
                )
            }

            item(key = "regimen-section") {
                RegimenSection(
                    groups = uiState.medicationGroups,
                    remindersEnabled = uiState.remindersEnabled,
                    appLocale = appLocale,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter,
                    nextOccurrencesByGroup = uiState.nextOccurrencesByGroup,
                    today = uiState.today,
                    onGroupClick = onGroupClick
                )
            }

            item(key = "add-group-button") {
                HrtButton(
                    text = stringResource(R.string.fab_add_medication_group),
                    onClick = onAddGroupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    icon = Icons.Rounded.Add,
                )
            }
        }
    }
}

internal fun plannedEntryEditorIds(scheduled: PlanDayScheduleEntry): Set<UUID> {
    return scheduled.fulfillingEntryUuids.toSet()
}

internal fun unplannedEntryEditorIds(entry: MedicationLogEntry): Set<UUID> {
    return setOf(entry.uuid)
}

@Composable
private fun PlanWeekHeader(
    weekStartDate: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    monthFormatter: DateTimeFormatter,
    canNavigateBackward: Boolean,
    canNavigateForward: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val pageIndex = currentWeekPageIndex(
        weekStartDate = weekStartDate,
        today = today,
        firstDayOfWeek = firstDayOfWeek
    )
    val pageLabelRes = when (pageIndex) {
        0 -> R.string.plan_week_previous
        1 -> R.string.plan_week_current
        else -> R.string.plan_week_next
    }
    val headerDate = weekStartDate.plusDays(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousClick,
                enabled = canNavigateBackward
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.plan_previous_week)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = headerDate.format(monthFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(pageLabelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onNextClick,
                enabled = canNavigateForward
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.plan_next_week)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (index == pageIndex) 18.dp else 6.dp,
                                height = 6.dp
                            )
                            .background(
                                color = if (index == pageIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(percent = 50)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDaySection(
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
private fun ScheduledDayRow(
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
@OptIn(ExperimentalLayoutApi::class)
private fun RegimenSection(
    groups: List<MedicationGroup>,
    remindersEnabled: Boolean,
    appLocale: Locale,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>>,
    today: LocalDate,
    onGroupClick: (UUID) -> Unit
) {
    val dailyCount = remember(groups) {
        groups.count { it.schedule.type == MedicationGroupScheduleType.DAILY }
    }
    val weeklyCount = groups.size - dailyCount

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.plan_regimen_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.plan_regimen_summary,
                    groups.size,
                    dailyCount,
                    weeklyCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.padding_large)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.plan_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groups.forEach { group ->
                    RegimenGroupCard(
                        group = group,
                        remindersEnabled = remindersEnabled,
                        appLocale = appLocale,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        upcomingOccurrences = nextOccurrencesByGroup[group.uuid].orEmpty(),
                        today = today,
                        onClick = { onGroupClick(group.uuid) }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RegimenGroupCard(
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

private fun remainingQuickLogCount(
    totalCount: Int,
    fulfilledCount: Int
): Int {
    return totalCount - fulfilledCount
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
    val isToday = date == today
    val alpha = if (date.month == today.month) 1f else 0.6f
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val borderColor = if (isToday && !isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val dayLabelColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
    }
    val dayNumberColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else if (isToday) {
        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = date.dayOfWeek.displayText(uppercase = true, narrow = true),
                fontSize = 11.sp,
                color = dayLabelColor,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = dateFormatter.format(date),
                fontSize = 18.sp,
                color = dayNumberColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            )
            DayStatusIndicator(
                date = date,
                today = today,
                dayStatus = dayStatus,
                isSelected = isSelected
            )
        }
    }
}

@Composable
private fun DayStatusIndicator(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus,
    isSelected: Boolean
) {
    val neutralIndicatorColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val scheduledIndicatorColor = if (date.isBefore(today)) {
        if (isSelected) MaterialTheme.colorScheme.onPrimary else overdueScheduledIndicatorColor
    } else {
        neutralIndicatorColor
    }
    val partialIndicatorColor = if (date.isBefore(today)) {
        if (isSelected) MaterialTheme.colorScheme.onPrimary else overduePartialIndicatorColor
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
        PlanCalendarDayStatus.OFFPLAN -> {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = neutralIndicatorColor,
                        shape = CircleShape
                    )
            )
        }
        PlanCalendarDayStatus.MISSED -> {
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
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else fulfilledIndicatorColor,
                modifier = Modifier.size(14.dp)
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

private fun currentWeekPageIndex(
    weekStartDate: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek
): Int {
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val weeksFromCurrent = ChronoUnit.WEEKS.between(currentWeekStart, weekStartDate).toInt()
    return (weeksFromCurrent + 1).coerceIn(0, 2)
}

@Composable
private fun rememberFirstMostVisibleWeek(
    state: com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState,
    viewportPercent: Float = 50f,
): Week {
    val visibleWeek = remember(state) { mutableStateOf(state.firstVisibleWeek) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.firstMostVisibleWeek(viewportPercent) }
            .filterNotNull()
            .collect { week -> visibleWeek.value = week }
    }
    return visibleWeek.value
}

private fun WeekCalendarLayoutInfo.firstMostVisibleWeek(viewportPercent: Float = 50f): Week? {
    return if (visibleWeeksInfo.isEmpty()) {
        null
    } else {
        val viewportSize = (viewportEndOffset + viewportStartOffset) * viewportPercent / 100f
        visibleWeeksInfo.firstOrNull { itemInfo ->
            if (itemInfo.offset < 0) {
                itemInfo.offset + itemInfo.size >= viewportSize
            } else {
                itemInfo.size - itemInfo.offset >= viewportSize
            }
        }?.week
    }
}

@Preview(
    name = "Plan Screen",
    showBackground = true,
    widthDp = 420,
    heightDp = 900
)
@Composable
private fun PlanScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PlanScreenContent(
            uiState = buildPlanPreviewUiState(),
            onGroupClick = { },
            onEntryClick = { },
            onQuickLogClick = { _, _, _, _ -> },
            onAddGroupClick = { },
            onHistoryClick = { },
            onDateSelected = { }
        )
    }
}

private fun buildPlanPreviewUiState(): PlanUiState {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now()
    val now = LocalDateTime.of(today, LocalTime.of(19, 15))
    val range = buildPlanCalendarRange(
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY
    )

    val morningGroupId = UUID.fromString("ef1b16ca-86d7-4872-9540-5b0d0e286e10")
    val eveningGroupId = UUID.fromString("f1db9123-b83c-4230-a0a7-269843f38de0")
    val blockerGroupId = UUID.fromString("7bba70a0-3b70-4d20-bc46-ed38d7f0b48d")
    val weeklyGroupId = UUID.fromString("9e71c6e4-72b7-4a49-ba04-d9ec9c0f5b44")

    val groups = listOf(
        MedicationGroup(
            uuid = morningGroupId,
            name = "Morning estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusMonths(2),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("072753ea-3f23-457e-b0f3-a102ff318f37"),
                    details = previewCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    )
                )
            ),
            notificationsEnabled = true,
            createdAt = previewInstant(today.minusMonths(2), LocalTime.NOON, zoneId),
            updatedAt = previewInstant(today.minusDays(1), LocalTime.NOON, zoneId)
        ),
        MedicationGroup(
            uuid = eveningGroupId,
            name = "Evening estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusMonths(1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("4233f227-405b-45d4-8c89-e25b52dfb20c"),
                    details = previewCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.SUBLINGUAL,
                        dose = MedicationDose.MgAsMedicine(1.0)
                    )
                )
            ),
            notificationsEnabled = false,
            createdAt = previewInstant(today.minusMonths(1), LocalTime.NOON, zoneId),
            updatedAt = previewInstant(today, LocalTime.NOON, zoneId)
        ),
        MedicationGroup(
            uuid = blockerGroupId,
            name = "Spironolactone",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusWeeks(8),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(22, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("548f616c-f347-4aa1-a460-8dd8235e3bb7"),
                    details = previewCatalogMedicationDetails(
                        key = MedicationKey.SPIRONOLACTONE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(50.0)
                    )
                )
            ),
            notificationsEnabled = true,
            createdAt = previewInstant(today.minusWeeks(8), LocalTime.NOON, zoneId),
            updatedAt = previewInstant(today.minusDays(2), LocalTime.NOON, zoneId)
        ),
        MedicationGroup(
            uuid = weeklyGroupId,
            name = "Weekly injection",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = today.minusWeeks(6).with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)),
                weeklyDaysOfWeek = setOf(DayOfWeek.FRIDAY),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("190964f5-c5f3-4f14-a4b2-394cbd4222dc"),
                    details = previewCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL_VALERATE,
                        applicationType = MedicationApplicationType.INJECTION,
                        dose = MedicationDose.MgAsMedicine(5.0)
                    )
                )
            ),
            notificationsEnabled = true,
            createdAt = previewInstant(today.minusWeeks(6), LocalTime.NOON, zoneId),
            updatedAt = previewInstant(today.minusDays(3), LocalTime.NOON, zoneId)
        )
    )

    val entries = listOf(
        MedicationLogEntry(
            uuid = UUID.fromString("06aa8f47-e08b-489f-8700-13421995cae1"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = morningGroupId,
            appliedAt = previewInstant(today, LocalTime.of(8, 2), zoneId),
            scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        ),
        MedicationLogEntry(
            uuid = UUID.fromString("5e8d60cc-4df3-4a88-a14e-3cb35c4f6fc6"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_GEL,
                applicationType = MedicationApplicationType.GEL,
                dose = MedicationDose.GelEquivalentEstradiolMg(1.5)
            ),
            dosageMgAsEstradiol = 1.5,
            sourceGroupUuid = null,
            appliedAt = previewInstant(today, LocalTime.of(13, 30), zoneId)
        ),
        MedicationLogEntry(
            uuid = UUID.fromString("ee7d7612-9281-4a26-a74b-6eb39532fd76"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_VALERATE,
                applicationType = MedicationApplicationType.INJECTION,
                dose = MedicationDose.MgAsMedicine(5.0)
            ),
            dosageMgAsEstradiol = 3.82,
            sourceGroupUuid = weeklyGroupId,
            appliedAt = previewInstant(
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)),
                LocalTime.of(9, 5),
                zoneId
            ),
            scheduledFor = LocalDateTime.of(
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)),
                LocalTime.of(9, 0)
            )
        )
    )

    val daySchedule = buildPlanDaySchedule(
        date = today,
        groups = groups,
        entries = entries,
        now = now,
        zoneId = zoneId
    )
    val calendarDays = buildPlanCalendarDayUiState(
        groups = groups,
        entries = entries,
        startDate = range.startDate,
        endDate = range.endDate
    )

    return PlanUiState(
        isLoading = false,
        today = today,
        calendarFirstDayOfWeek = DayOfWeek.MONDAY,
        calendarStartDate = range.startDate,
        calendarEndDate = range.endDate,
        selectedDate = today,
        entries = entries,
        medicationGroups = groups,
        remindersEnabled = true,
        calendarDays = calendarDays,
        daySchedule = daySchedule,
        nextOccurrencesByGroup = mapOf(
            morningGroupId to listOf(
                LocalDateTime.of(today.plusDays(1), LocalTime.of(8, 0)),
                LocalDateTime.of(today.plusDays(2), LocalTime.of(8, 0)),
                LocalDateTime.of(today.plusDays(3), LocalTime.of(8, 0))
            ),
            eveningGroupId to listOf(
                LocalDateTime.of(today, LocalTime.of(20, 0)),
                LocalDateTime.of(today.plusDays(1), LocalTime.of(20, 0)),
                LocalDateTime.of(today.plusDays(2), LocalTime.of(20, 0))
            ),
            blockerGroupId to listOf(
                LocalDateTime.of(today, LocalTime.of(22, 0)),
                LocalDateTime.of(today.plusDays(1), LocalTime.of(22, 0)),
                LocalDateTime.of(today.plusDays(2), LocalTime.of(22, 0))
            ),
            weeklyGroupId to listOf(
                LocalDateTime.of(
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)),
                    LocalTime.of(9, 0)
                ),
                LocalDateTime.of(
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).plusWeeks(1),
                    LocalTime.of(9, 0)
                )
            )
        )
    )
}

private fun previewCatalogMedicationDetails(
    key: MedicationKey,
    applicationType: MedicationApplicationType,
    dose: MedicationDose,
): MedicationDetails {
    return MedicationDetails(
        category = key.category,
        applicationType = applicationType,
        selection = MedicationSelection.Catalog(key),
        dose = dose
    )
}

private fun previewInstant(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId
): Instant {
    return LocalDateTime.of(date, time).atZone(zoneId).toInstant()
}
