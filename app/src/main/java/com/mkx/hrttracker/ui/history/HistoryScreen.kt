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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.flow.collect
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
    viewModel: HistoryViewModel = hiltViewModel()
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
        onDeleteSelectedClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteSelectedEntries,
        onDisplayedMonthChange = viewModel::clearSelection,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    uiState: HistoryUiState,
    onEntryClick: (UUID) -> Unit,
    onEntryLongClick: (UUID) -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDisplayedMonthChange: () -> Unit,
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
        firstVisibleMonth = uiState.calendarEndMonth,
        firstDayOfWeek = uiState.calendarFirstDayOfWeek
    )
    val displayedMonth = rememberFirstMostVisibleMonth(calendarState, viewportPercent = 90f)

    LaunchedEffect(displayedMonth.yearMonth) {
        onDisplayedMonthChange()
    }

    val monthDayStates = remember(uiState.medicationGroups, uiState.entries, displayedMonth) {
        buildPlanCalendarDayUiState(
            groups = uiState.medicationGroups,
            entries = uiState.entries,
            startDate = displayedMonth.yearMonth.atDay(1),
            endDate = displayedMonth.yearMonth.atEndOfMonth()
        )
    }
    val visibleEntries = remember(uiState.entries, displayedMonth.yearMonth) {
        uiState.entries
            .filter { entry ->
                YearMonth.from(entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDate()) ==
                        displayedMonth.yearMonth
            }
            .sortedByDescending { it.appliedAt }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_small)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            item(key = "calendar") {
                HistoryMonthCalendar(
                    calendarState = calendarState,
                    displayedMonth = displayedMonth.yearMonth,
                    today = today,
                    firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                    dayStates = monthDayStates,
                    appLocale = appLocale
                )
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
                                if (uiState.entries.isEmpty()) {
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
                    dayStatus = dayStates[day.date]?.status
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

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
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
            val sourceVisual = remember(entry.sourceType) {
                entry.sourceType.toHistorySourceVisual()
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
                        sourceType = MedicationLogEntrySourceType.GROUP_SCHEDULE,
                        sourceGroupUuid = UUID.fromString("a563870c-7f67-4c29-83d3-7592f40e5845"),
                        appliedAt = Instant.parse("2026-04-16T19:00:00Z")
                    )
                ),
                calendarStartMonth = YearMonth.of(2026, 4),
                calendarEndMonth = YearMonth.of(2026, 4),
                selectedEntryIds = setOf(UUID.fromString("611d7af2-6108-45ab-a320-4064e0dd1233"))
            ),
            onEntryClick = { },
            onEntryLongClick = { },
            onDeleteSelectedClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDisplayedMonthChange = { }
        )
    }
}

private data class HistorySourceVisual(
    val icon: ImageVector,
    val contentDescriptionRes: Int
)

private fun MedicationLogEntrySourceType.toHistorySourceVisual(): HistorySourceVisual {
    return when (this) {
        MedicationLogEntrySourceType.MANUAL -> HistorySourceVisual(
            icon = Icons.Default.Edit,
            contentDescriptionRes = R.string.history_entry_source_manual
        )
        MedicationLogEntrySourceType.GROUP_MANUAL -> HistorySourceVisual(
            icon = Icons.AutoMirrored.Filled.ViewList,
            contentDescriptionRes = R.string.history_entry_source_group_manual
        )
        MedicationLogEntrySourceType.GROUP_SCHEDULE -> HistorySourceVisual(
            icon = Icons.Default.CalendarMonth,
            contentDescriptionRes = R.string.history_entry_source_group_schedule
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
fun rememberFirstMostVisibleMonth(
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