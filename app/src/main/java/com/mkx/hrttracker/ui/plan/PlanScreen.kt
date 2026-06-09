package com.mkx.hrttracker.ui.plan

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarLayoutInfo
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.Week
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.hazeChrome
import com.mkx.hrttracker.ui.components.hazeTopAppBarColors
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.calendarMonthTitleFormatter
import com.mkx.hrttracker.util.planUpcomingDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    onGroupClick: (UUID) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    onQuickLogClick: (UUID, UUID?, LocalDateTime, MedicationGroupMedication, Int) -> Unit,
    onAddGroupClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBatchAddClick: () -> Unit,
    onArchivedGroupsClick: () -> Unit,
    onMedicinesClick: () -> Unit,
    scrollToTopSignal: Int = 0,
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
        onArchivedGroupsClick = onArchivedGroupsClick,
        onMedicinesClick = onMedicinesClick,
        onDateSelected = viewModel::toggleSelectedDate,
        onDateSelectionReset = viewModel::clearSelectedDate,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanScreenContent(
    modifier: Modifier = Modifier,
    uiState: PlanUiState,
    onGroupClick: (UUID) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    onQuickLogClick: (UUID, UUID?, LocalDateTime, MedicationGroupMedication, Int) -> Unit,
    onAddGroupClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBatchAddClick: () -> Unit,
    onArchivedGroupsClick: () -> Unit,
    onMedicinesClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onDateSelectionReset: () -> Unit,
    scrollToTopSignal: Int = 0,
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val reminderCapabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    val hasNotificationAccess = reminderCapabilityState.hasNotificationAccess
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val dateFormatter = remember(appLocale, uiState.today) {
        planUpcomingDateFormatter(
            locale = appLocale,
            today = uiState.today
        )
    }
    val monthFormatter = remember(appLocale, uiState.today) {
        calendarMonthTitleFormatter(
            locale = appLocale,
            currentYear = uiState.today.year
        )
    }
    val selection = uiState.selectedDate
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    var isActionMenuExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )

    val state = key(
        uiState.calendarStartDate,
        uiState.calendarEndDate,
        uiState.calendarFirstDayOfWeek,
        uiState.today,
    ) {
        val initialVisibleWeekDate = remember {
            resolvePlanInitialVisibleWeekDate(
                selectedDate = selection,
                calendarStartDate = uiState.calendarStartDate,
                calendarEndDate = uiState.calendarEndDate,
                today = uiState.today,
            )
        }
        rememberWeekCalendarState(
            startDate = uiState.calendarStartDate,
            endDate = uiState.calendarEndDate,
            firstVisibleWeekDate = initialVisibleWeekDate,
            firstDayOfWeek = uiState.calendarFirstDayOfWeek,
        )
    }
    val visibleWeek = rememberFirstMostVisibleWeek(state, viewportPercent = 90f)
    val visibleWeekStartDate = visibleWeek.days.first().date
    val weekPageProgress = rememberWeekPageProgress(state)
    val currentWeekStartDate = remember(uiState.today, uiState.calendarFirstDayOfWeek) {
        uiState.today.with(TemporalAdjusters.previousOrSame(uiState.calendarFirstDayOfWeek))
    }
    var pendingCalendarSelectedDate by remember(state) { mutableStateOf<LocalDate?>(null) }
    var pendingCurrentWeekSelectionReset by remember(state) { mutableStateOf<LocalDate?>(null) }
    val calendarSelectedDate = planCalendarVisualSelectedDate(
        selectedDate = selection,
        pendingCalendarSelectedDate = pendingCalendarSelectedDate,
    )
    val isPendingResetAtCurrentWeek = shouldResetPlanSelectionForCurrentWeekNavigation(
        visibleWeekStartDate = visibleWeekStartDate,
        today = uiState.today,
        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
        pendingSelectionReset = pendingCurrentWeekSelectionReset,
    )
    val displaySelection = if (isPendingResetAtCurrentWeek) null else selection
    val daySchedule = if (isPendingResetAtCurrentWeek) {
        buildPlanDaySchedule(
            date = uiState.today,
            groups = uiState.scheduleMedicationGroups,
            entries = uiState.entries,
            now = uiState.now,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = uiState.now,
        )
    } else {
        uiState.daySchedule
    }
    val displayedDate = daySchedule.date
    val archivedGroupUuids = remember(uiState.scheduleMedicationGroups) {
        uiState.scheduleMedicationGroups
            .filter(MedicationGroup::isArchived)
            .mapTo(mutableSetOf()) { group -> group.uuid }
    }

    LaunchedEffect(
        visibleWeekStartDate,
        pendingCurrentWeekSelectionReset,
        uiState.today,
        uiState.calendarFirstDayOfWeek,
    ) {
        if (
            shouldResetPlanSelectionForCurrentWeekNavigation(
                visibleWeekStartDate = visibleWeekStartDate,
                today = uiState.today,
                firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                pendingSelectionReset = pendingCurrentWeekSelectionReset,
            )
        ) {
            onDateSelectionReset()
        }
    }
    LaunchedEffect(selection, pendingCurrentWeekSelectionReset) {
        val pendingReset = pendingCurrentWeekSelectionReset ?: return@LaunchedEffect
        if (selection != pendingReset) {
            pendingCurrentWeekSelectionReset = null
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
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                }.hazeChrome(),
                title = {
                    val title = stringResource(R.string.tab_plan)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                    )
                },
                colors = hazeTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onMedicinesClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_medication),
                            contentDescription = stringResource(R.string.medicines_title),
                        )
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
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
                        val menuItems = buildList {
                            add(
                                HrtDropdownMenuItem(
                                    text = stringResource(R.string.plan_archived_groups),
                                    onClick = onArchivedGroupsClick,
                                )
                            )
                            add(
                                HrtDropdownMenuItem(
                                    text = stringResource(R.string.plan_batch_add_from_plan),
                                    onClick = onBatchAddClick,
                                )
                            )
                        }
                        HrtDropdownMenu(
                            expanded = isActionMenuExpanded,
                            onDismissRequest = { isActionMenuExpanded = false },
                            items = menuItems,
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
                return@AppContentContainer
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
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
                            selectedDate = displaySelection,
                            pageProgress = weekPageProgress,
                            onPreviousClick = {
                                scope.launch {
                                    state.animateScrollToWeek(visibleWeekStartDate.minusWeeks(1))
                                }
                            },
                            onCurrentClick = {
                                val previousSelection = selection
                                if (previousSelection != null) {
                                    pendingCalendarSelectedDate = previousSelection
                                    pendingCurrentWeekSelectionReset = previousSelection
                                    if (visibleWeekStartDate == currentWeekStartDate) {
                                        onDateSelectionReset()
                                    }
                                }
                                scope.launch {
                                    try {
                                        state.animateScrollToWeek(uiState.today)
                                    } finally {
                                        if (pendingCalendarSelectedDate == previousSelection) {
                                            pendingCalendarSelectedDate = null
                                        }
                                    }
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
                                val dayState =
                                    uiState.calendarDays[day.date] ?: PlanCalendarDayUiState()
                                Day(
                                    date = day.date,
                                    today = uiState.today,
                                    appLocale = appLocale,
                                    dayState = dayState,
                                    isSelected = calendarSelectedDate == day.date
                                ) { clicked ->
                                    pendingCalendarSelectedDate = null
                                    onDateSelected(clicked)
                                }
                            }
                        )
                    }
                }

                item(key = "selected-day") {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)) {
                        SelectedDaySection(
                            date = displayedDate,
                            today = uiState.today,
                            overallStatus = uiState.calendarDays[displayedDate]?.status
                                ?: PlanCalendarDayStatus.NONE,
                            daySchedule = daySchedule,
                            appLocale = appLocale,
                            timeFormatter = timeFormatter,
                            archivedGroupUuids = archivedGroupUuids,
                            onScheduledClick = { scheduled ->
                                val editingEntryIds = plannedEntryEditorIds(scheduled)
                                if (
                                    editingEntryIds.isNotEmpty() &&
                                    (scheduled.isFulfilled || scheduled.hasOutsideScheduleWindowEntry)
                                ) {
                                    onEntryClick(editingEntryIds)
                                } else {
                                    onQuickLogClick(
                                        scheduled.groupUuid,
                                        scheduled.scheduleTimeUuid,
                                        scheduled.scheduledFor,
                                        scheduled.medication,
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
                        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
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

}

internal fun plannedEntryEditorIds(scheduled: PlanDayScheduleEntry): Set<UUID> {
    return (scheduled.fulfillingEntryUuids + scheduled.outsideScheduleWindowEntryUuids).toSet()
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
    selectedDate: LocalDate?,
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
    val monthLabel = planWeekHeaderMonthLabel(
        weekStartDate = weekStartDate,
        selectedDate = selectedDate,
        monthFormatter = monthFormatter
    )

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
                        painter = painterResource(R.drawable.ic_restart_alt),
                        enabled = pageIndex != 1 || selectedDate != null,
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
                val pageLabel = stringResource(pageLabelRes)
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.cjkTextOffset(monthLabel)
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
    imageVector: ImageVector? = null,
    painter: Painter? = null,
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
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Icon(
                    imageVector = checkNotNull(imageVector),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize)
                )
            }
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
    firstDayOfWeek: DayOfWeek,
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

    HrtSection(
        title = stringResource(R.string.plan_regimen_title),
        headerTrailingAlignByBaseline = true,
        headerTrailing = if (regimenSummary != null) {
            {
                Text(
                    text = regimenSummary.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        } else {
            null
        },
    ) {
        if (groups.isEmpty()) {
            item {
                SupportMessageListItem(
                    text = stringResource(R.string.plan_empty_state),
                    painter = painterResource(R.drawable.ic_info),
                )
            }
        } else {
            groups.forEach { group ->
                item {
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
                        firstDayOfWeek = firstDayOfWeek,
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

internal fun planWeekHeaderMonthLabel(
    weekStartDate: LocalDate,
    selectedDate: LocalDate?,
    monthFormatter: (LocalDate) -> String,
): String {
    selectedDate?.let(monthFormatter)?.let { selectedMonth ->
        return selectedMonth
    }

    val weekEndDate = weekStartDate.plusDays(6)
    return if (YearMonth.from(weekStartDate) == YearMonth.from(weekEndDate)) {
        monthFormatter(weekStartDate)
    } else {
        "${monthFormatter(weekStartDate)} / ${monthFormatter(weekEndDate)}"
    }
}

internal fun planCalendarVisualSelectedDate(
    selectedDate: LocalDate?,
    pendingCalendarSelectedDate: LocalDate?,
): LocalDate? = pendingCalendarSelectedDate ?: selectedDate

internal fun shouldResetPlanSelectionForCurrentWeekNavigation(
    visibleWeekStartDate: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    pendingSelectionReset: LocalDate?,
): Boolean {
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return pendingSelectionReset != null && visibleWeekStartDate == currentWeekStart
}

internal fun resolvePlanInitialVisibleWeekDate(
    selectedDate: LocalDate?,
    calendarStartDate: LocalDate,
    calendarEndDate: LocalDate,
    today: LocalDate,
): LocalDate {
    if (selectedDate == null) {
        return today.coerceIn(calendarStartDate, calendarEndDate)
    }

    return if (
        !selectedDate.isBefore(calendarStartDate) &&
        !selectedDate.isAfter(calendarEndDate)
    ) {
        selectedDate
    } else {
        calendarStartDate
    }
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
    val pageProgress = remember(state) { mutableFloatStateOf(1f) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.weekPageProgress() }
            .filterNotNull()
            .collect { progress -> pageProgress.floatValue = progress }
    }
    return pageProgress.floatValue
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
            onQuickLogClick = { _, _, _, _, _ -> },
            onAddGroupClick = { },
            onHistoryClick = { },
            onBatchAddClick = { },
            onArchivedGroupsClick = { },
            onMedicinesClick = { },
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
                    medicine = previewMedicine(MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            notificationsEnabled = true,
            createdAt = previewInstant(today.minusMonths(2), LocalTime.NOON, zoneId),
            updatedAt = previewInstant(today.minusDays(1), LocalTime.NOON, zoneId)
        ),
    )

    val entries = listOf(
        MedicationLogEntry(
            uuid = UUID.fromString("06aa8f47-e08b-489f-8700-13421995cae1"),
            medicine = previewMedicine(MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = morningGroupId,
            appliedAt = previewInstant(today, LocalTime.of(8, 2), zoneId),
            scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        ),
        MedicationLogEntry(
            uuid = UUID.fromString("5e8d60cc-4df3-4a88-a14e-3cb35c4f6fc6"),
            medicine = previewMedicine(MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 1.5,
            sourceGroupUuid = null,
            appliedAt = previewInstant(today, LocalTime.of(13, 30), zoneId)
        ),
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
        scheduleMedicationGroups = groups,
        remindersEnabled = true,
        calendarDays = calendarDays,
        daySchedule = daySchedule,
        nextOccurrencesByGroup = mapOf(
            morningGroupId to listOf(
                LocalDateTime.of(today.plusDays(1), LocalTime.of(8, 0)),
                LocalDateTime.of(today.plusDays(2), LocalTime.of(8, 0)),
                LocalDateTime.of(today.plusDays(3), LocalTime.of(8, 0))
            ),
        )
    )
}

private fun previewMedicine(key: MedicationKey): com.mkx.hrttracker.model.medication.Medicine {
    val selection = com.mkx.hrttracker.model.medication.MedicineSelection.Catalog(key)
    val preparation = com.mkx.hrttracker.model.medication.MedicinePreparation.Pill(
        strengthMgPerTablet = 2.0,
    )
    return com.mkx.hrttracker.model.medication.Medicine(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        selection = selection,
        category = key.category,
        preparation = preparation,
        displayName = null,
        identityKey = com.mkx.hrttracker.model.medication.MedicineIdentityKey.catalog(
            key, preparation,
        ),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = com.mkx.hrttracker.model.medication.MedicineStock(),
    )
}

private fun previewInstant(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId
): Instant {
    return LocalDateTime.of(date, time).atZone(zoneId).toInstant()
}
