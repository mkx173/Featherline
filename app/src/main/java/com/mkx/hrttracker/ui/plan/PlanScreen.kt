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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
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
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )

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

    val initialScrollToTopSignal = remember { scrollToTopSignal }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal != initialScrollToTopSignal) {
            listState.animateScrollToItem(0)
            topAppBarState.contentOffset = 0f
            topAppBarState.heightOffset = 0f
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_plan)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior,
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
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
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

private fun remainingQuickLogCount(
    totalCount: Int,
    fulfilledCount: Int
): Int {
    return totalCount - fulfilledCount
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd")
internal val fulfilledIndicatorColor = Color(0xFF2E7D32)
internal val overdueScheduledIndicatorColor = Color(0xFFC62828)
internal val overduePartialIndicatorColor = Color(0xFFEF6C00)

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

internal fun buildPlanPreviewUiState(): PlanUiState {
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
