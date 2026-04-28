package com.mkx.hrttracker.ui.plan

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarLayoutInfo
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState
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
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.history.historyCalendarMonthTitleFormatter
import com.mkx.hrttracker.ui.history.historyEntryGroupDayFormatter
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
    onBatchAddClick: () -> Unit,
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
        onBatchAddClick = onBatchAddClick,
        onDateSelected = viewModel::toggleSelectedDate,
        onDateSelectionReset = viewModel::clearSelectedDate,
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
    onBatchAddClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onDateSelectionReset: () -> Unit,
    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationAccess by remember(context) {
        mutableStateOf(canPostNotifications(context))
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val dateFormatter = remember(appLocale, uiState.today) {
        planUpcomingDateFormatter(
            appLocale = appLocale,
            today = uiState.today
        )
    }
    val monthFormatter = remember(appLocale, uiState.today) {
        historyCalendarMonthTitleFormatter(
            appLocale = appLocale,
            currentYear = uiState.today.year
        )
    }
    val selection = uiState.selectedDate
    val daySchedule = uiState.daySchedule
    val displayedDate = daySchedule.date
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    var isActionMenuExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val state = rememberWeekCalendarState(
        startDate = uiState.calendarStartDate,
        endDate = uiState.calendarEndDate,
        firstVisibleWeekDate = uiState.today,
        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
    )
    val visibleWeek = rememberFirstMostVisibleWeek(state, viewportPercent = 90f)
    val visibleWeekStartDate = visibleWeek.days.first().date
    val weekPageProgress = rememberWeekPageProgress(state)

    LaunchedEffect(selection) {
        if (selection != null && visibleWeek.days.none { it.date == selection }) {
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
                            imageVector = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.plan_open_history)
                        )
                    }
                    Box {
                        IconButton(onClick = { isActionMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.plan_more_options)
                            )
                        }
                        HrtDropdownMenu(
                            expanded = isActionMenuExpanded,
                            onDismissRequest = { isActionMenuExpanded = false },
                            items = listOf(
                                HrtDropdownMenuItem(
                                    text = stringResource(R.string.plan_batch_add_from_plan),
                                    onClick = onBatchAddClick,
                                ),
                            ),
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
                LoadingIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
        ) {
            item(key = "week-calendar") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PlanWeekHeader(
                        weekStartDate = visibleWeekStartDate,
                        today = uiState.today,
                        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                        monthFormatter = monthFormatter,
                        hasSelection = selection != null,
                        pageProgress = weekPageProgress,
                        onPreviousClick = {
                            scope.launch {
                                state.animateScrollToWeek(visibleWeekStartDate.minusWeeks(1))
                            }
                        },
                        onCurrentClick = {
                            onDateSelectionReset()
                            scope.launch {
                                state.animateScrollToWeek(uiState.today)
                            }
                        },
                        onNextClick = {
                            scope.launch {
                                state.animateScrollToWeek(visibleWeekStartDate.plusWeeks(1))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    WeekCalendar(
                        modifier = Modifier.fillMaxWidth(),
                        state = state,
                        dayContent = { day ->
                            val dayState = uiState.calendarDays[day.date] ?: PlanCalendarDayUiState()
                            Day(
                                date = day.date,
                                today = uiState.today,
                                appLocale = appLocale,
                                dayState = dayState,
                                isSelected = selection == day.date
                            ) { clicked ->
                                onDateSelected(clicked)
                            }
                        }
                    )
                }
            }

            item(key = "selected-day") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SelectedDaySection(
                        date = displayedDate,
                        today = uiState.today,
                        overallStatus = uiState.calendarDays[displayedDate]?.status
                            ?: PlanCalendarDayStatus.NONE,
                        daySchedule = daySchedule,
                        appLocale = appLocale,
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
                    if (selection != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HrtOutlinedButton(
                                text = stringResource(R.string.history_clear_selection),
                                onClick = onDateSelectionReset,
                                icon = Icons.Rounded.Close,
                                iconModifier = Modifier.size(14.dp),
                                iconSpacing = 6.dp,
                                compact = true,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                            )
                        }
                    }
                }
            }

            item(key = "regimen-section") {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                RegimenSection(
                    groups = uiState.medicationGroups,
                    remindersEnabled = uiState.remindersEnabled,
                    hasNotificationAccess = hasNotificationAccess,
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
                    text = stringResource(R.string.add),
                    onClick = onAddGroupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
    monthFormatter: (LocalDate) -> String,
    hasSelection: Boolean,
    pageProgress: Float,
    onPreviousClick: () -> Unit,
    onCurrentClick: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlanWeekNavigationButton(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        enabled = pageIndex > 0,
                        contentDescription = stringResource(R.string.plan_previous_week),
                        onClick = onPreviousClick,
                        modifier = Modifier.offset(x = (-8).dp),
                        iconSize = 30.dp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlanWeekNavigationButton(
                        imageVector = Icons.Rounded.RestartAlt,
                        enabled = pageIndex != 1 || hasSelection,
                        contentDescription = stringResource(R.string.plan_week_current),
                        onClick = onCurrentClick,
                        modifier = Modifier.offset(x = 8.dp),
                        iconSize = 22.dp
                    )
                    PlanWeekNavigationButton(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        enabled = pageIndex < 2,
                        contentDescription = stringResource(R.string.plan_next_week),
                        onClick = onNextClick,
                        modifier = Modifier.offset(x = 8.dp),
                        iconSize = 30.dp
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-2).dp)
            ) {
                val dateLabel = monthFormatter(headerDate)
                val pageLabel = stringResource(pageLabelRes)
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.cjkTextOffset(dateLabel)
                )
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(pageLabel)
                )
            }
        }

        PlanWeekPageIndicator(pageProgress = pageProgress)
    }
}

@Composable
private fun PlanWeekNavigationButton(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun PlanWeekPageIndicator(pageProgress: Float) {
    val clampedProgress = pageProgress.coerceIn(0f, 2f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val selectedFraction = (1f - kotlin.math.abs(clampedProgress - index))
                    .coerceIn(0f, 1f)
                val indicatorWidth = 6.dp + 12.dp * selectedFraction
                val indicatorColor = lerp(
                    start = MaterialTheme.colorScheme.outlineVariant,
                    stop = MaterialTheme.colorScheme.primary,
                    fraction = selectedFraction
                )
                Box(
                    modifier = Modifier
                        .size(
                            width = indicatorWidth,
                            height = 6.dp
                        )
                        .background(
                            color = indicatorColor,
                            shape = RoundedCornerShape(percent = 50)
                        )
                )
            }
        }
    }
}

@Composable
private fun RegimenSection(
    groups: List<MedicationGroup>,
    remindersEnabled: Boolean,
    hasNotificationAccess: Boolean,
    appLocale: Locale,
    dateFormatter: (LocalDate) -> String,
    timeFormatter: DateTimeFormatter,
    nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>>,
    today: LocalDate,
    onGroupClick: (UUID) -> Unit
) {
    val dailyCount = remember(groups) {
        groups.count { it.schedule.type == MedicationGroupScheduleType.DAILY }
    }
    val weeklyCount = groups.size - dailyCount
    val regimenSummary = if (groups.isEmpty()) {
        null
    } else {
        listOfNotNull(
            pluralStringResource(
                R.plurals.plan_regimen_group_count,
                groups.size,
                groups.size
            ),
            if (dailyCount > 0) {
                pluralStringResource(
                    R.plurals.plan_regimen_daily_count,
                    dailyCount,
                    dailyCount
                )
            } else {
                null
            },
            if (weeklyCount > 0) {
                pluralStringResource(
                    R.plurals.plan_regimen_weekly_count,
                    weeklyCount,
                    weeklyCount
                )
            } else {
                null
            }
        ).joinToString(" · ")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.plan_regimen_title).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline()
            )
            if (regimenSummary != null) {
                Text(
                    text = regimenSummary.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }

        if (groups.isEmpty()) {
            SupportMessageListItem(
                text = stringResource(R.string.plan_empty_state),
                painter = painterResource(R.drawable.ic_info),
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
            ) {
                groups.forEachIndexed { index, group ->
                    RegimenGroupCard(
                        group = group,
                        remindersEnabled = remindersEnabled,
                        hasNotificationAccess = hasNotificationAccess,
                        appLocale = appLocale,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        upcomingOccurrences = nextOccurrencesByGroup[group.uuid].orEmpty(),
                        today = today,
                        onClick = { onGroupClick(group.uuid) },
                        index = index,
                        itemCount = groups.size
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

private fun planUpcomingDateFormatter(
    appLocale: Locale,
    today: LocalDate
): (LocalDate) -> String {
    val currentYearFormatter = historyEntryGroupDayFormatter(appLocale)
    val nextYearFormatter = if (appLocale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("yyyy年M月d日", appLocale)
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy", appLocale)
    }

    return { date ->
        date.format(
            if (date.year > today.year) {
                nextYearFormatter
            } else {
                currentYearFormatter
            }
        )
    }
}

@Composable
private fun Day(
    date: LocalDate,
    today: LocalDate,
    appLocale: Locale,
    dayState: PlanCalendarDayUiState,
    isSelected: Boolean,
    onClick: (LocalDate) -> Unit
) {
    val isToday = date == today
    val dayAlpha = planWeekCalendarDayAlpha(
        isFuture = date.isAfter(today),
        isSelected = isSelected,
        isToday = isToday
    )
    val weekdayLabel = remember(date.dayOfWeek, appLocale) {
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale)
    }
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val weekdayColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .alpha(dayAlpha)
            .clip(MaterialTheme.shapes.medium)
            .background(
                color = containerColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        ) {
            Text(
                text = weekdayLabel,
                color = weekdayColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            DayStatusIndicator(
                date = date,
                today = today,
                dayStatus = dayState.status,
                hasOffPlanRecord = dayState.hasOffPlanRecord,
                isSelected = isSelected
            )
        }
    }
}

internal fun planWeekCalendarDayAlpha(
    isFuture: Boolean,
    isSelected: Boolean,
    isToday: Boolean
): Float {
    return if (isSelected || isToday) {
        1f
    } else if (isFuture) {
        0.56f
    } else {
        1f
    }
}

@Composable
private fun DayStatusIndicator(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus,
    hasOffPlanRecord: Boolean,
    isSelected: Boolean
) {
    val indicatorMode = if (date.isBefore(today)) {
        PlanDayIndicatorColorMode.Emphasized
    } else {
        PlanDayIndicatorColorMode.Neutral
    }
    val selectedColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else null

    PlanDayStatusIndicator(
        status = dayStatus,
        colors = planDayIndicatorColors(
            scheduledMode = indicatorMode,
            partialMode = indicatorMode,
            selectedColor = selectedColor
        ),
        modifier = Modifier.size(12.dp),
        showFutureMissedIcon = date.isAfter(today),
        showOffPlanBadge = planShouldShowOffPlanBadge(
            status = dayStatus,
            hasOffPlanRecord = hasOffPlanRecord
        )
    )
}

@Composable
private fun PlanDayStatusIndicator(
    status: PlanCalendarDayStatus,
    colors: PlanDayIndicatorColors,
    modifier: Modifier = Modifier,
    showFutureMissedIcon: Boolean = false,
    showOffPlanBadge: Boolean = false
) {
    Box(modifier = modifier) {
        when (status) {
            PlanCalendarDayStatus.NONE -> {
                PlanDayIndicatorGlyph(
                    kind = PlanDayIndicatorKind.NO_RECORD,
                    color = colors.neutral,
                    modifier = modifier
                )
            }
            PlanCalendarDayStatus.OFFPLAN -> {
                PlanDayIndicatorGlyph(
                    kind = PlanDayIndicatorKind.OFFPLAN,
                    color = colors.unplanned,
                    modifier = modifier
                )
            }
            PlanCalendarDayStatus.MISSED -> {
                PlanDayIndicatorGlyph(
                    kind = if (showFutureMissedIcon) {
                        PlanDayIndicatorKind.FUTURE
                    } else {
                        PlanDayIndicatorKind.MISSED
                    },
                    color = colors.scheduled,
                    modifier = modifier
                )
            }
            PlanCalendarDayStatus.PARTIAL -> {
                PlanDayIndicatorGlyph(
                    kind = PlanDayIndicatorKind.PARTIAL,
                    color = colors.partial,
                    modifier = modifier
                )
            }
            PlanCalendarDayStatus.FULFILLED -> {
                PlanDayIndicatorGlyph(
                    kind = PlanDayIndicatorKind.CHECK,
                    color = colors.fulfilled,
                    modifier = modifier
                )
            }
        }

        if (showOffPlanBadge) {
            Badge(
                containerColor = colors.unplanned,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(4.dp)
            )
        }
    }
}

private enum class PlanDayIndicatorKind {
    CHECK,
    PARTIAL,
    MISSED,
    FUTURE,
    OFFPLAN,
    NO_RECORD
}

@Composable
private fun PlanDayIndicatorGlyph(
    kind: PlanDayIndicatorKind,
    color: Color,
    modifier: Modifier = Modifier
) {
    val painter = when (kind) {
        PlanDayIndicatorKind.CHECK -> R.drawable.ic_check_circle
        PlanDayIndicatorKind.PARTIAL -> R.drawable.ic_contrast
        PlanDayIndicatorKind.MISSED -> R.drawable.ic_radio_button_unchecked
        PlanDayIndicatorKind.FUTURE -> R.drawable.ic_donut_large
        PlanDayIndicatorKind.OFFPLAN -> R.drawable.ic_circle
        PlanDayIndicatorKind.NO_RECORD -> R.drawable.ic_remove
    }
    Icon(
        painter = painterResource(painter),
        contentDescription = null,
        tint = color,
        modifier = modifier
    )
}

private enum class PlanDayIndicatorColorMode {
    Neutral,
    Emphasized
}

private data class PlanDayIndicatorColors(
    val scheduled: Color,
    val partial: Color,
    val fulfilled: Color,
    val neutral: Color,
    val unplanned: Color
)

@Composable
private fun planDayIndicatorColors(
    scheduledMode: PlanDayIndicatorColorMode = PlanDayIndicatorColorMode.Neutral,
    partialMode: PlanDayIndicatorColorMode = PlanDayIndicatorColorMode.Neutral,
    selectedColor: Color? = null
): PlanDayIndicatorColors {
    if (selectedColor != null) {
        return PlanDayIndicatorColors(
            scheduled = selectedColor,
            partial = selectedColor,
            fulfilled = selectedColor,
            neutral = selectedColor,
            unplanned = selectedColor
        )
    }
    return PlanDayIndicatorColors(
        scheduled = planDayIndicatorColor(
            mode = scheduledMode,
            emphasizedColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        partial = planDayIndicatorColor(
            mode = partialMode,
            emphasizedColor = MaterialTheme.colorScheme.primary
        ),
        fulfilled = MaterialTheme.colorScheme.primary,
        neutral = MaterialTheme.colorScheme.outlineVariant,
        unplanned = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun planDayIndicatorColor(
    mode: PlanDayIndicatorColorMode,
    emphasizedColor: Color
): Color {
    return when (mode) {
        PlanDayIndicatorColorMode.Neutral -> MaterialTheme.colorScheme.outline
        PlanDayIndicatorColorMode.Emphasized -> emphasizedColor
    }
}

private fun planShouldShowOffPlanBadge(
    status: PlanCalendarDayStatus,
    hasOffPlanRecord: Boolean
): Boolean {
    return hasOffPlanRecord && status != PlanCalendarDayStatus.OFFPLAN
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
    state: WeekCalendarState,
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

@Composable
private fun rememberWeekPageProgress(state: WeekCalendarState): Float {
    val pageProgress = remember(state) { mutableStateOf(1f) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.weekPageProgress() }
            .filterNotNull()
            .collect { progress -> pageProgress.value = progress }
    }
    return pageProgress.value
}

private fun WeekCalendarLayoutInfo.weekPageProgress(): Float? {
    val firstVisibleWeekInfo = visibleWeeksInfo.firstOrNull() ?: return null
    if (firstVisibleWeekInfo.size == 0) {
        return firstVisibleWeekInfo.index.toFloat().coerceIn(0f, 2f)
    }
    val firstVisibleScrollOffset = viewportStartOffset - firstVisibleWeekInfo.offset
    return (firstVisibleWeekInfo.index + firstVisibleScrollOffset.toFloat() / firstVisibleWeekInfo.size)
        .coerceIn(0f, 2f)
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
            onBatchAddClick = { },
            onDateSelected = { },
            onDateSelectionReset = { }
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
