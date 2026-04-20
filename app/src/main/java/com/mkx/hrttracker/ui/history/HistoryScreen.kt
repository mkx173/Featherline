package com.mkx.hrttracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.plan.buildPlanCalendarDayUiState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import kotlinx.coroutines.flow.filterNotNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
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
            CenterAlignedTopAppBar(
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

            if (uiState.selectedDate != null) {
                item(key = "selected-date-title") {
                    Text(
                        text = stringResource(
                            R.string.history_selected_day_records_title,
                            uiState.selectedDate.format(dateFormatter)
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (visibleEntries.isEmpty()) {
                item(key = "empty-state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.padding_large)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
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
                }
            } else {
                groupedEntries.forEach { (date, dateEntries) ->
                    item(key = "header-$date") {
                        Text(
                            text = date.format(dateFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(
                                top = dimensionResource(R.dimen.padding_small),
                                bottom = dimensionResource(R.dimen.padding_xsmall)
                            )
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
                        TextButton(onClick = { onDayClick(uiState.selectedDate) }) {
                            Text(text = stringResource(R.string.history_clear_selection))
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.history_month_summary_title, monthLabel),
                style = MaterialTheme.typography.labelMedium,
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
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
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
        HorizontalCalendar(
            modifier = Modifier.fillMaxWidth(),
            state = calendarState,
            monthHeader = {
                HistoryMonthHeader(
                    firstDayOfWeek = firstDayOfWeek,
                    appLocale = appLocale
                )
            },
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
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
        HorizontalDivider()
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onGoToPrevious,
            enabled = canGoToPrevious
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null
            )
        }
        Text(
            text = displayedMonth.atDay(1).format(monthFormatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onGoToNext,
            enabled = canGoToNext
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
    val textColor = if (isFuture) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        day.date == today -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
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
                fontWeight = FontWeight.Medium
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
private fun HistoryEntryCard(
    entry: MedicationLogEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        leadingContent = {
            val sourceVisual = remember(entry.sourceType, entry.scheduledFor != null) {
                historySourceVisual(entry.sourceType, entry.scheduledFor != null)
            }
            Icon(
                imageVector = sourceVisual.icon,
                contentDescription = stringResource(sourceVisual.contentDescriptionRes)
            )
        },
        overlineContent = {
            Text(text = stringResource(entry.routeOfAdministration.labelRes))
        },
        headlineContent = {
            Text(text = entry.medicineName)
        },
        trailingContent = {
            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
            )
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.entry_medicine_dose,
                    entry.dosageMgAsMedicine.formatDose(appLocale)
                )
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
            HistoryScreenContent(
            uiState = HistoryUiState(
                entries = listOf(
                    MedicationLogEntry(
                        uuid = UUID.fromString("f16ec8a7-5115-410a-b12d-f376fdb6f76b"),
                        routeOfAdministration = RouteOfAdministration.INTRAMUSCULAR,
                        medicineName = "Estradiol valerate",
                        dosageMgAsMedicine = 5.0,
                        dosageMgAsEstradiol = 3.82,
                        sourceType = MedicationLogEntrySourceType.MANUAL,
                        sourceGroupUuid = null,
                        appliedAt = Instant.parse("2026-04-16T08:30:00Z")
                    ),
                    MedicationLogEntry(
                        uuid = UUID.fromString("9b9a1efe-6df3-43da-871d-9584370fbca8"),
                        routeOfAdministration = RouteOfAdministration.ORAL,
                        medicineName = "Estradiol",
                        dosageMgAsMedicine = 2.0,
                        dosageMgAsEstradiol = 2.0,
                        sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                        sourceGroupUuid = UUID.fromString("3e11ae72-a197-4315-8f89-b5db6f21c2f9"),
                        appliedAt = Instant.parse("2026-04-15T22:00:00Z")
                    ),
                    MedicationLogEntry(
                        uuid = UUID.fromString("611d7af2-6108-45ab-a320-4064e0dd1233"),
                        routeOfAdministration = RouteOfAdministration.SUBLINGUAL,
                        medicineName = "Estradiol",
                        dosageMgAsMedicine = 1.0,
                        dosageMgAsEstradiol = 1.0,
                        sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                        sourceGroupUuid = UUID.fromString("a563870c-7f67-4c29-83d3-7592f40e5845"),
                        appliedAt = Instant.parse("2026-04-16T19:00:00Z"),
                        scheduledFor = java.time.LocalDateTime.parse("2026-04-16T19:00:00")
                    )
                ),
                calendarStartMonth = YearMonth.of(2026, 4),
                calendarEndMonth = YearMonth.of(2026, 4),
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
