package com.mkx.hrttracker.ui.history

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.CalendarLayoutInfo
import com.kizitonwose.calendar.compose.CalendarState
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.buildPlanCalendarDayUiState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onEntryClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreenContent(
        uiState = uiState,
        onEntryClick = { entryId ->
            if (uiState.isSelectionMode) {
                viewModel.toggleEntrySelection(entryId)
            } else {
                onEntryClick(entryId)
            }
        },
        onEntryLongClick = viewModel::toggleEntrySelection,
        onDayClick = viewModel::toggleSelectedDate,
        onDeleteSelectedClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteSelectedEntries,
        onDisplayedMonthChange = viewModel::setDisplayedMonth,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    uiState: HistoryUiState,
    onEntryClick: (UUID) -> Unit,
    onEntryLongClick: (UUID) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDisplayedMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val monthLabelFormatter = remember(appLocale) {
        DateTimeFormatter.ofPattern("LLLL").withLocale(appLocale)
    }
    val calendarState = rememberCalendarState(
        startMonth = uiState.calendarStartMonth,
        endMonth = uiState.calendarEndMonth,
        firstVisibleMonth = uiState.displayedMonth,
        firstDayOfWeek = uiState.calendarFirstDayOfWeek
    )
    val displayedMonth = rememberFirstMostVisibleMonth(calendarState, viewportPercent = 90f)

    LaunchedEffect(displayedMonth.yearMonth) {
        onDisplayedMonthChange(displayedMonth.yearMonth)
    }

    val monthDayStates = remember(
        uiState.medicationGroups,
        uiState.entries,
        displayedMonth.yearMonth,
        uiState.calendarStartMonth,
        uiState.calendarEndMonth
    ) {
        val rangeStartMonth = displayedMonth.yearMonth.minusMonths(2)
            .coerceAtLeast(uiState.calendarStartMonth)
        val rangeEndMonth = displayedMonth.yearMonth.plusMonths(2)
            .coerceAtMost(uiState.calendarEndMonth)
        buildPlanCalendarDayUiState(
            groups = uiState.medicationGroups,
            entries = uiState.entries,
            startDate = rangeStartMonth.atDay(1),
            endDate = rangeEndMonth.atEndOfMonth()
        )
    }
    val monthSummary = remember(uiState.entries, displayedMonth.yearMonth, monthDayStates, today) {
        buildHistoryMonthSummary(
            entries = uiState.entries,
            displayedMonth = displayedMonth.yearMonth,
            dayStates = monthDayStates,
            today = today
        )
    }
    val visibleEntries = remember(uiState.entries, displayedMonth.yearMonth, uiState.selectedDate) {
        buildHistoryVisibleEntries(
            entries = uiState.entries,
            displayedMonth = displayedMonth.yearMonth,
            selectedDate = uiState.selectedDate
        )
    }
    val groupedEntries = remember(visibleEntries) {
        visibleEntries.groupBy { entry ->
            entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    val groupNamesById = remember(uiState.medicationGroups) {
        uiState.medicationGroups.associate { group -> group.uuid to group.name }
    }
    val groupColorsById = remember(uiState.medicationGroups) {
        uiState.medicationGroups.associate { group -> group.uuid to group.colorKey }
    }
    val entryListTitle = if (uiState.selectedDate != null) {
        stringResource(
            R.string.history_selected_day_records_title,
            uiState.selectedDate.format(dateFormatter)
        )
    } else {
        stringResource(
            R.string.history_month_entries_title,
            displayedMonth.yearMonth.atDay(1).format(monthLabelFormatter)
        )
    }

    if (uiState.isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(text = stringResource(R.string.delete_entries_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_entries_confirmation,
                        uiState.selectedEntryIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (uiState.isSelectionMode) {
                FloatingActionButton(onClick = onDeleteSelectedClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_entries_fab)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_history)) }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_small)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            item(key = "summary") {
                HistoryMonthSummaryCard(
                    displayedMonth = displayedMonth.yearMonth,
                    summary = monthSummary,
                    appLocale = appLocale
                )
            }

            item(key = "calendar") {
                HistoryMonthCalendar(
                    calendarState = calendarState,
                    displayedMonth = displayedMonth.yearMonth,
                    today = today,
                    firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                    dayStates = monthDayStates,
                    appLocale = appLocale,
                    selectedDate = uiState.selectedDate,
                    onDayClick = onDayClick
                )
            }

            item(key = "calendar-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            }

            item(key = "entries-title") {
                Text(
                    text = entryListTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (visibleEntries.isEmpty()) {
                item(key = "empty-state") {
                    HistoryEmptyStateCard(
                        text = stringResource(
                            if (uiState.selectedDate != null) {
                                R.string.history_selected_day_empty_state
                            } else if (uiState.entries.isEmpty()) {
                                R.string.history_empty_state
                            } else {
                                R.string.history_month_empty_state
                            }
                        )
                    )
                }
            } else {
                groupedEntries.forEach { (date, dateEntries) ->
                    item(key = "header-$date") {
                        HistoryEntryGroupHeader(
                            date = date,
                            today = today,
                            dayStatus = monthDayStates[date]?.status ?: PlanCalendarDayStatus.NONE,
                            entryCount = dateEntries.size,
                            appLocale = appLocale
                        )
                    }

                    items(
                        items = dateEntries,
                        key = { it.uuid }
                    ) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            appLocale = appLocale,
                            timeFormatter = timeFormatter,
                            groupName = entry.sourceGroupUuid?.let(groupNamesById::get),
                            groupColorKey = entry.sourceGroupUuid?.let(groupColorsById::get),
                            isSelected = entry.uuid in uiState.selectedEntryIds,
                            onClick = { onEntryClick(entry.uuid) },
                            onLongClick = { onEntryLongClick(entry.uuid) }
                        )
                    }
                }
            }

            if (uiState.selectedDate != null) {
                item(key = "clear-selection") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .combinedClickable(onClick = { onDayClick(uiState.selectedDate) })
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.history_clear_selection),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMonthSummaryCard(
    displayedMonth: YearMonth,
    summary: HistoryMonthSummary,
    appLocale: Locale,
    modifier: Modifier = Modifier
) {
    val monthFormatter = remember(appLocale) {
        DateTimeFormatter.ofPattern("LLLL").withLocale(appLocale)
    }
    val scrollState = rememberScrollState()
    val monthLabel = remember(displayedMonth, monthFormatter) {
        displayedMonth.atDay(1).format(monthFormatter)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.history_month_summary_title, monthLabel),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistorySummaryChip(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    value = summary.logged,
                    label = stringResource(R.string.history_summary_logged),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    accentColor = MaterialTheme.colorScheme.primary
                )
                HistorySummaryChip(
                    icon = Icons.Default.Check,
                    value = summary.onTrack,
                    label = stringResource(R.string.history_summary_on_track),
                    containerColor = Color(0xFFE5F0E5),
                    contentColor = Color(0xFF1C4D20),
                    accentColor = HistoryFulfilledIndicatorColor
                )
                if (summary.partial > 0) {
                    HistorySummaryChip(
                        icon = Icons.Default.PieChart,
                        value = summary.partial,
                        label = stringResource(R.string.history_summary_partial),
                        containerColor = Color(0xFFFCEEDA),
                        contentColor = Color(0xFF7A4006),
                        accentColor = OverduePartialIndicatorColor
                    )
                }
                if (summary.missed > 0) {
                    HistorySummaryChip(
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        value = summary.missed,
                        label = stringResource(R.string.history_summary_missed),
                        containerColor = Color(0xFFFFD9D6),
                        contentColor = Color(0xFF8C1C1C),
                        accentColor = OverdueScheduledIndicatorColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryChip(
    icon: ImageVector,
    value: Int,
    label: String,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun HistoryMonthCalendar(
    calendarState: CalendarState,
    displayedMonth: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    dayStates: Map<LocalDate, com.mkx.hrttracker.ui.plan.PlanCalendarDayUiState>,
    appLocale: Locale,
    selectedDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HistoryCalendarTitle(
                    displayedMonth = displayedMonth,
                    appLocale = appLocale,
                    canGoToPrevious = displayedMonth > calendarState.startMonth,
                    canGoToNext = displayedMonth < calendarState.endMonth,
                    onGoToPrevious = {
                        coroutineScope.launch {
                            calendarState.animateScrollToMonth(displayedMonth.minusMonths(1))
                        }
                    },
                    onGoToNext = {
                        coroutineScope.launch {
                            calendarState.animateScrollToMonth(displayedMonth.plusMonths(1))
                        }
                    }
                )
                HistoryMonthHeader(
                    firstDayOfWeek = firstDayOfWeek,
                    appLocale = appLocale
                )
                HorizontalCalendar(
                    modifier = Modifier.fillMaxWidth(),
                    state = calendarState,
                    monthHeader = { _ -> },
                    dayContent = { day ->
                        HistoryCalendarDay(
                            day = day,
                            today = today,
                            dayStatus = dayStates[day.date]?.status,
                            isSelected = day.date == selectedDate,
                            onClick = onDayClick
                        )
                    }
                )
            }
        }

        HistoryCalendarLegend()
    }
}

@Composable
private fun HistoryCalendarLegend(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.FULFILLED,
            label = stringResource(R.string.history_summary_on_track)
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.PARTIAL,
            label = stringResource(R.string.history_summary_partial)
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.SCHEDULED,
            label = stringResource(R.string.history_summary_missed)
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.UNPLANNED,
            label = stringResource(R.string.history_legend_unplanned)
        )
    }
}

@Composable
private fun HistoryCalendarLegendItem(
    status: PlanCalendarDayStatus,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HistoryStatusDot(
            status = status,
            isPastScheduled = status == PlanCalendarDayStatus.SCHEDULED
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryMonthHeader(
    firstDayOfWeek: DayOfWeek,
    appLocale: Locale
) {
    val weekdayLabels = remember(firstDayOfWeek, appLocale) {
        historyDayOfWeekLabels(
            firstDayOfWeek = firstDayOfWeek,
            locale = appLocale
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryCalendarTitle(
    displayedMonth: YearMonth,
    appLocale: Locale,
    canGoToPrevious: Boolean,
    canGoToNext: Boolean,
    onGoToPrevious: () -> Unit,
    onGoToNext: () -> Unit
) {
    val monthFormatter = remember(appLocale) {
        DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(appLocale)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistoryCalendarNavigationButton(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            enabled = canGoToPrevious,
            onClick = onGoToPrevious
        )
        Text(
            text = displayedMonth.atDay(1).format(monthFormatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        HistoryCalendarNavigationButton(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            enabled = canGoToNext,
            onClick = onGoToNext
        )
    }
}

@Composable
private fun HistoryCalendarNavigationButton(
    imageVector: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun HistoryCalendarDay(
    day: CalendarDay,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus?,
    isSelected: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (day.position != DayPosition.MonthDate) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .fillMaxWidth()
        )
        return
    }

    val isFuture = day.date.isAfter(today)
    val isToday = day.date == today
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderModifier = if (isToday && !isSelected) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp)
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .padding(2.dp)
            .background(
                color = containerColor,
                shape = RoundedCornerShape(14.dp)
            )
            .then(borderModifier)
            .combinedClickable(onClick = { onClick(day.date) }),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (isFuture) {
                Box(modifier = Modifier.size(12.dp))
            } else {
                HistoryCalendarDayIndicator(
                    date = day.date,
                    today = today,
                    dayStatus = dayStatus ?: PlanCalendarDayStatus.NONE
                )
            }
        }
    }
}

@Composable
private fun HistoryCalendarDayIndicator(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus
) {
    val neutralIndicatorColor = MaterialTheme.colorScheme.outline
    val scheduledIndicatorColor = if (date.isBefore(today)) {
        OverdueScheduledIndicatorColor
    } else {
        neutralIndicatorColor
    }
    val partialIndicatorColor = if (date.isBefore(today)) {
        OverduePartialIndicatorColor
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
                tint = HistoryFulfilledIndicatorColor,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun HistoryEntryGroupHeader(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus,
    entryCount: Int,
    appLocale: Locale,
    modifier: Modifier = Modifier
) {
    val dayFormatter = remember(appLocale) {
        DateTimeFormatter.ofPattern("MMM d").withLocale(appLocale)
    }
    val isToday = date == today
    val label = if (isToday) {
        stringResource(R.string.quick_add_group_planned_slot_today)
    } else {
        date.format(dayFormatter)
    }
    val weekdayLabel = remember(date, appLocale) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isToday) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryStatusDot(
                    status = dayStatus,
                    isPastScheduled = dayStatus == PlanCalendarDayStatus.SCHEDULED && date.isBefore(today)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = weekdayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
        Text(
            text = entryCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryStatusDot(
    status: PlanCalendarDayStatus,
    isPastScheduled: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (status) {
        PlanCalendarDayStatus.FULFILLED -> Box(
            modifier = modifier
                .size(8.dp)
                .background(HistoryFulfilledIndicatorColor, CircleShape)
        )
        PlanCalendarDayStatus.PARTIAL -> Box(
            modifier = modifier
                .size(8.dp)
                .background(OverduePartialIndicatorColor, CircleShape)
        )
        PlanCalendarDayStatus.SCHEDULED -> {
            if (isPastScheduled) {
                Box(
                    modifier = modifier
                        .size(8.dp)
                        .background(OverdueScheduledIndicatorColor, CircleShape)
                )
            } else {
                Box(
                    modifier = modifier
                        .size(8.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
            }
        }
        PlanCalendarDayStatus.UNPLANNED -> Box(
            modifier = modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.outline, CircleShape)
        )
        PlanCalendarDayStatus.NONE -> Box(
            modifier = modifier
                .size(8.dp)
                .background(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    CircleShape
                )
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: MedicationLogEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    groupName: String?,
    groupColorKey: MedicationGroupColorKey?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val sourceVisual = remember(entry.sourceType, entry.scheduledFor != null) {
        historySourceVisual(entry.sourceType, entry.scheduledFor != null)
    }
    val groupColorScheme = rememberMedicationGroupColorScheme(groupColorKey)
    val supportingText = buildHistoryEntrySupportingText(
        entry = entry,
        groupName = groupName
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sourceVisual.icon,
                        contentDescription = stringResource(sourceVisual.contentDescriptionRes),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = groupColorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(entry.details.applicationType.labelRes),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = groupColorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = medicationDisplayName(entry.details),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun HistoryEmptyStateCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimensionResource(R.dimen.padding_large)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun buildHistoryEntrySupportingText(
    entry: MedicationLogEntry,
    groupName: String?
): String {
    val doseText = medicationDoseText(entry.details)
    val fallbackText = stringResource(entry.details.applicationType.labelRes)
    val parts = buildList {
        if (!doseText.isNullOrBlank()) {
            add(doseText)
        } else {
            add(fallbackText)
        }
        if (!groupName.isNullOrBlank()) {
            add(groupName)
        }
    }
    return parts.joinToString(" \u00B7 ")
}

@Preview(
    name = "History Month",
    showBackground = true,
    widthDp = 420,
    heightDp = 900
)
@Composable
private fun HistoryScreenMonthPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        HistoryScreenContent(
            uiState = buildHistoryPreviewUiState(),
            onEntryClick = { },
            onEntryLongClick = { },
            onDayClick = { },
            onDeleteSelectedClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDisplayedMonthChange = { _ -> }
        )
    }
}

@Preview(
    name = "History Selected Day",
    showBackground = true,
    widthDp = 420,
    heightDp = 900
)
@Composable
private fun HistoryScreenSelectedDayPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        HistoryScreenContent(
            uiState = buildHistoryPreviewUiState(
                selectedDate = LocalDate.now(),
                selectedEntryIds = setOf(UUID.fromString("611d7af2-6108-45ab-a320-4064e0dd1233"))
            ),
            onEntryClick = { },
            onEntryLongClick = { },
            onDayClick = { },
            onDeleteSelectedClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDisplayedMonthChange = { _ -> }
        )
    }
}

private data class HistorySourceVisual(
    val icon: ImageVector,
    val contentDescriptionRes: Int
)

private fun historySourceVisual(
    sourceType: MedicationLogEntrySourceType,
    fulfillsSchedule: Boolean
): HistorySourceVisual {
    if (fulfillsSchedule) {
        return HistorySourceVisual(
            icon = Icons.Default.CalendarMonth,
            contentDescriptionRes = R.string.history_entry_source_group_schedule
        )
    }
    return when (sourceType) {
        MedicationLogEntrySourceType.MANUAL -> HistorySourceVisual(
            icon = Icons.Default.Edit,
            contentDescriptionRes = R.string.history_entry_source_manual
        )
        MedicationLogEntrySourceType.GROUP_MANUAL -> HistorySourceVisual(
            icon = Icons.AutoMirrored.Filled.ViewList,
            contentDescriptionRes = R.string.history_entry_source_group_manual
        )
    }
}

private fun historyDayOfWeekLabels(
    firstDayOfWeek: DayOfWeek,
    locale: Locale
): List<String> {
    return List(7) { offset ->
        firstDayOfWeek
            .plus(offset.toLong())
            .getDisplayName(TextStyle.NARROW, locale)
    }
}

private val HistoryFulfilledIndicatorColor = Color(0xFF2E7D32)
private val OverdueScheduledIndicatorColor = Color(0xFFC62828)
private val OverduePartialIndicatorColor = Color(0xFFEF6C00)

private fun buildHistoryPreviewUiState(
    selectedDate: LocalDate? = null,
    selectedEntryIds: Set<UUID> = emptySet()
): HistoryUiState {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val oralGroupId = UUID.fromString("3e11ae72-a197-4315-8f89-b5db6f21c2f9")
    val nightlyGroupId = UUID.fromString("a563870c-7f67-4c29-83d3-7592f40e5845")

    val entries = listOf(
        MedicationLogEntry(
            uuid = UUID.fromString("f16ec8a7-5115-410a-b12d-f376fdb6f76b"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_VALERATE,
                applicationType = MedicationApplicationType.INJECTION,
                dose = MedicationDose.MgAsMedicine(5.0)
            ),
            dosageMgAsEstradiol = 3.82,
            sourceType = MedicationLogEntrySourceType.MANUAL,
            sourceGroupUuid = null,
            appliedAt = previewInstant(today, LocalTime.of(8, 30), zoneId)
        ),
        MedicationLogEntry(
            uuid = UUID.fromString("9b9a1efe-6df3-43da-871d-9584370fbca8"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = oralGroupId,
            appliedAt = previewInstant(today.minusDays(1), LocalTime.of(22, 0), zoneId)
        ),
        MedicationLogEntry(
            uuid = UUID.fromString("611d7af2-6108-45ab-a320-4064e0dd1233"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.SUBLINGUAL,
                dose = MedicationDose.MgAsMedicine(1.0)
            ),
            dosageMgAsEstradiol = 1.0,
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = nightlyGroupId,
            appliedAt = previewInstant(today, LocalTime.of(19, 0), zoneId),
            scheduledFor = LocalDateTime.of(today, LocalTime.of(19, 0))
        ),
        MedicationLogEntry(
            uuid = UUID.fromString("0db2cb5b-bf7b-45aa-9f42-d1bddcb00c88"),
            details = previewCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_GEL,
                applicationType = MedicationApplicationType.GEL,
                dose = MedicationDose.GelEquivalentEstradiolMg(1.5)
            ),
            dosageMgAsEstradiol = 1.5,
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = oralGroupId,
            appliedAt = previewInstant(today.minusDays(3), LocalTime.of(7, 45), zoneId),
            scheduledFor = LocalDateTime.of(today.minusDays(3), LocalTime.of(7, 30))
        )
    )

    val groups = listOf(
        MedicationGroup(
            uuid = oralGroupId,
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
                    uuid = UUID.fromString("c6ebfec7-5412-49a6-9040-a845cd5dd9f3"),
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
            uuid = nightlyGroupId,
            name = "Nightly estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusMonths(1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(19, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("b08dbe5d-f225-491f-b2b0-07beb7fe47f3"),
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
        )
    )

    return HistoryUiState(
        isLoading = false,
        entries = entries,
        medicationGroups = groups,
        calendarStartMonth = month.minusMonths(1),
        calendarEndMonth = month,
        displayedMonth = month,
        selectedDate = selectedDate,
        selectedEntryIds = selectedEntryIds
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

@Composable
private fun rememberFirstMostVisibleMonth(
    state: CalendarState,
    viewportPercent: Float = 50f,
): CalendarMonth {
    val visibleMonth = remember(state) { mutableStateOf(state.firstVisibleMonth) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.firstMostVisibleMonth(viewportPercent) }
            .filterNotNull()
            .collect { month -> visibleMonth.value = month }
    }
    return visibleMonth.value
}

private fun CalendarLayoutInfo.firstMostVisibleMonth(viewportPercent: Float = 50f): CalendarMonth? {
    return if (visibleMonthsInfo.isEmpty()) {
        null
    } else {
        val viewportSize = (viewportEndOffset + viewportStartOffset) * viewportPercent / 100f
        visibleMonthsInfo.firstOrNull { itemInfo ->
            if (itemInfo.offset < 0) {
                itemInfo.offset + itemInfo.size >= viewportSize
            } else {
                itemInfo.size - itemInfo.offset >= viewportSize
            }
        }?.month
    }
}
