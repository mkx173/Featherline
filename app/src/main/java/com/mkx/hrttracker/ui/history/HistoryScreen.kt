package com.mkx.hrttracker.ui.history

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.CalendarLayoutInfo
import com.kizitonwose.calendar.compose.CalendarState
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
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
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
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
    onEntryClick: (Set<UUID>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreenContent(
        uiState = uiState,
        onEntryClick = { collapsedEntry ->
            when (historyEntryTapAction(uiState.selectedEntryIds)) {
                HistoryEntryTapAction.OPEN_EDITOR -> onEntryClick(collapsedEntry.entryIds)
                HistoryEntryTapAction.TOGGLE_SELECTION -> {
                    viewModel.toggleEntrySelection(collapsedEntry.entryIds)
                }
            }
        },
        onEntryLongClick = { collapsedEntry ->
            viewModel.toggleEntrySelection(collapsedEntry.entryIds)
        },
        onDayClick = viewModel::toggleSelectedDate,
        onDeleteSelectedClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteSelectedEntries,
        onDisplayedMonthChange = { month, clearSelection ->
            viewModel.setDisplayedMonth(month, clearSelection)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    uiState: HistoryUiState,
    onEntryClick: (HistoryCollapsedEntry) -> Unit,
    onEntryLongClick: (HistoryCollapsedEntry) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDisplayedMonthChange: (YearMonth, Boolean) -> Unit,
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
        historyMonthLabelFormatter(appLocale)
    }
    val calendarState = key(
        uiState.calendarStartMonth,
        uiState.calendarEndMonth,
        uiState.calendarFirstDayOfWeek
    ) {
        // rememberCalendarState keeps its initial bounds, so recreate it when the available
        // month range expands or contracts after the underlying history data changes.
        rememberCalendarState(
            startMonth = uiState.calendarStartMonth,
            endMonth = uiState.calendarEndMonth,
            firstVisibleMonth = uiState.displayedMonth,
            firstDayOfWeek = uiState.calendarFirstDayOfWeek,
            outDateStyle = OutDateStyle.EndOfGrid
        )
    }
    val displayedMonth = rememberFirstCompletelyVisibleMonth(calendarState)
    val pendingSelectedDate = remember { mutableStateOf<LocalDate?>(null) }
    val effectiveSelectedDate = remember(
        displayedMonth.yearMonth,
        pendingSelectedDate.value,
        uiState.selectedDate
    ) {
        resolveHistoryEffectiveSelectedDate(
            displayedMonth = displayedMonth.yearMonth,
            pendingSelectedDate = pendingSelectedDate.value,
            selectedDate = uiState.selectedDate
        )
    }
    val displayedSelectedDate = remember(
        displayedMonth.yearMonth,
        pendingSelectedDate.value,
        uiState.selectedDate
    ) {
        resolveHistoryDisplayedSelectedDate(
            displayedMonth = displayedMonth.yearMonth,
            pendingSelectedDate = pendingSelectedDate.value,
            selectedDate = uiState.selectedDate
        )
    }

    LaunchedEffect(displayedMonth.yearMonth) {
        onDisplayedMonthChange(
            displayedMonth.yearMonth,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = displayedMonth.yearMonth,
                pendingSelectedDate = pendingSelectedDate.value
            )
        )
    }

    LaunchedEffect(displayedMonth.yearMonth, pendingSelectedDate.value, uiState.selectedDate) {
        val pendingDate = pendingSelectedDate.value ?: return@LaunchedEffect
        if (
            YearMonth.from(pendingDate) == displayedMonth.yearMonth &&
            uiState.selectedDate != pendingDate
        ) {
            onDayClick(pendingDate)
        }
    }

    LaunchedEffect(uiState.selectedDate, pendingSelectedDate.value) {
        val pendingDate = pendingSelectedDate.value ?: return@LaunchedEffect
        if (uiState.selectedDate == pendingDate) {
            pendingSelectedDate.value = null
        }
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
    val visibleEntries = remember(uiState.entries, displayedMonth.yearMonth, effectiveSelectedDate) {
        buildHistoryVisibleEntries(
            entries = uiState.entries,
            displayedMonth = displayedMonth.yearMonth,
            selectedDate = effectiveSelectedDate
        )
    }
    val collapsedEntries = remember(visibleEntries) {
        collapseHistoryEntries(visibleEntries)
    }
    val groupedEntries = remember(collapsedEntries) {
        groupHistoryEntriesByDate(collapsedEntries)
    }
    val selectedCollapsedEntryCount = remember(collapsedEntries, uiState.selectedEntryIds) {
        countSelectedCollapsedEntries(
            selectedEntryIds = uiState.selectedEntryIds,
            collapsedEntries = collapsedEntries
        )
    }
    val groupNamesById = remember(uiState.medicationGroups) {
        uiState.medicationGroups.associate { group -> group.uuid to group.name }
    }
    val groupColorsById = remember(uiState.medicationGroups) {
        uiState.medicationGroups.associate { group -> group.uuid to group.colorKey }
    }
    val entryListTitle = if (effectiveSelectedDate != null) {
        stringResource(
            R.string.history_selected_day_records_title,
            effectiveSelectedDate.format(dateFormatter)
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
                        selectedCollapsedEntryCount
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            if (uiState.isSelectionMode) {
                FloatingActionButton(onClick = onDeleteSelectedClick) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete_entries_fab)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_history)) },
                scrollBehavior = scrollBehavior
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
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
        ) {
            item(key = "summary") {
                HistoryMonthSummaryStrip(summary = monthSummary)
            }

            item(key = "calendar") {
                HistoryMonthCalendar(
                    calendarState = calendarState,
                    displayedMonth = displayedMonth.yearMonth,
                    today = today,
                    firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                    dayStates = monthDayStates,
                    appLocale = appLocale,
                    selectedDate = displayedSelectedDate,
                    onDayClick = onDayClick,
                    onDeferredDaySelectionRequested = { pendingSelectedDate.value = it }
                )
            }

            item(key = "calendar-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            }

            item(key = "entries-title") {
                Text(
                    text = entryListTitle.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (collapsedEntries.isEmpty()) {
                item(key = "empty-state") {
                    HistoryEmptyStateCard(
                        text = stringResource(
                            if (effectiveSelectedDate != null) {
                                R.string.history_selected_day_empty_state
                            } else if (uiState.entries.isEmpty()) {
                                R.string.history_empty_state
                            } else {
                                R.string.history_month_empty_state
                            }
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                groupedEntries.entries.forEachIndexed { groupIndex, (date, dateEntries) ->
                    item(key = "header-$date") {
                        HistoryEntryGroupHeader(
                            date = date,
                            today = today,
                            dayStatus = monthDayStates[date]?.status ?: PlanCalendarDayStatus.NONE,
                            entryCount = dateEntries.size,
                            appLocale = appLocale
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                        ) {
                            dateEntries.forEachIndexed { index, collapsedEntry ->
                                HistoryEntryCard(
                                    collapsedEntry = collapsedEntry,
                                    timeFormatter = timeFormatter,
                                    groupName = collapsedEntry.representativeEntry.sourceGroupUuid?.let(
                                        groupNamesById::get
                                    ),
                                    groupColorKey = collapsedEntry.representativeEntry.sourceGroupUuid?.let(
                                        groupColorsById::get
                                    ),
                                    isSelected = isHistoryCollapsedEntrySelected(
                                        selectedEntryIds = uiState.selectedEntryIds,
                                        entryIds = collapsedEntry.entryIds
                                    ),
                                    index = index,
                                    count = dateEntries.size,
                                    onClick = { onEntryClick(collapsedEntry) },
                                    onLongClick = { onEntryLongClick(collapsedEntry) }
                                )
                            }
                            if (groupIndex < groupedEntries.size - 1) {
                                Spacer(modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp))
                            }
                        }
                    }
                }
            }

            if (uiState.selectedDate != null) {
                item(key = "clear-selection") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
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
                                    imageVector = Icons.Rounded.Close,
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
private fun HistoryMonthSummaryStrip(
    summary: HistoryMonthSummary,
    modifier: Modifier = Modifier
) {
    val indicatorColors = historyIndicatorColors(
        scheduledMode = HistoryIndicatorColorMode.Emphasized,
        partialMode = HistoryIndicatorColorMode.Emphasized
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val countText = summary.logged.toString()
            val labelText = pluralStringResource(
                R.plurals.history_summary_logged_entries,
                summary.logged
            )
            Text(
                text = stringResource(
                    R.string.history_summary_logged_strip,
                    countText,
                    labelText
                ).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HistorySummaryInlineStat(
                value = summary.onTrack,
                color = indicatorColors.fulfilled,
                kind = HistorySummaryInlineStatKind.CHECK
            )
            if (summary.partial > 0) {
                HistorySummaryInlineStat(
                    value = summary.partial,
                    color = indicatorColors.partial,
                    kind = HistorySummaryInlineStatKind.PARTIAL
                )
            }
            if (summary.missed > 0) {
                HistorySummaryInlineStat(
                    value = summary.missed,
                    color = indicatorColors.scheduled,
                    kind = HistorySummaryInlineStatKind.MISSED
                )
            }
            if (summary.offPlan > 0) {
                HistorySummaryInlineStat(
                    value = summary.offPlan,
                    color = indicatorColors.unplanned,
                    kind = HistorySummaryInlineStatKind.OFFPLAN
                )
            }
        }
    }
}

@Composable
private fun HistorySummaryInlineStat(
    value: Int,
    color: Color,
    kind: HistorySummaryInlineStatKind,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistorySummaryIndicatorGlyph(
            kind = kind,
            color = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class HistorySummaryInlineStatKind {
    CHECK,
    PARTIAL,
    MISSED,
    OFFPLAN,
    NO_RECORD
}

@Composable
private fun HistorySummaryIndicatorGlyph(
    kind: HistorySummaryInlineStatKind,
    color: Color,
    modifier: Modifier = Modifier
) {
    val imageVector = when (kind) {
        HistorySummaryInlineStatKind.CHECK -> Icons.Rounded.Check
        HistorySummaryInlineStatKind.PARTIAL -> Icons.Rounded.Contrast
        HistorySummaryInlineStatKind.MISSED -> Icons.Rounded.RadioButtonUnchecked
        HistorySummaryInlineStatKind.OFFPLAN -> Icons.Rounded.Circle
        HistorySummaryInlineStatKind.NO_RECORD -> Icons.Rounded.Remove
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = color,
        modifier = modifier
    )
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
    onDeferredDaySelectionRequested: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HistoryCalendarTitle(
                displayedMonth = displayedMonth,
                currentMonth = YearMonth.from(today),
                appLocale = appLocale,
                canGoToPrevious = displayedMonth > calendarState.startMonth,
                canGoToNext = displayedMonth < calendarState.endMonth,
                onGoToPrevious = {
                    coroutineScope.launch {
                        calendarState.animateScrollToMonth(displayedMonth.minusMonths(1))
                    }
                },
                onGoToCurrent = {
                    coroutineScope.launch {
                        calendarState.animateScrollToMonth(YearMonth.from(today))
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
                        isSelected = day.date == selectedDate &&
                            day.position == DayPosition.MonthDate,
                        onClick = { date ->
                            val targetMonth = historyCalendarDayClickTargetMonth(
                                position = day.position,
                                date = date
                            )
                            if (targetMonth == null) {
                                onDayClick(date)
                            } else {
                                onDeferredDaySelectionRequested(date)
                                coroutineScope.launch {
                                    calendarState.animateScrollToMonth(targetMonth)
                                }
                            }
                        }
                    )
                }
            )
        }
        HistoryCalendarLegend()
    }
}

@Composable
private fun HistoryCalendarLegend(modifier: Modifier = Modifier) {

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            12.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
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
            status = PlanCalendarDayStatus.MISSED,
            label = stringResource(R.string.history_summary_missed)
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.OFFPLAN,
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
    val indicatorColors = historyIndicatorColors(
        scheduledMode = HistoryIndicatorColorMode.Emphasized,
        partialMode = HistoryIndicatorColorMode.Emphasized
    )
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryStatusIndicator(
            status = status,
            colors = indicatorColors
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = dimensionResource(R.dimen.padding_xsmall))
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
    currentMonth: YearMonth,
    appLocale: Locale,
    canGoToPrevious: Boolean,
    canGoToNext: Boolean,
    onGoToPrevious: () -> Unit,
    onGoToCurrent: () -> Unit,
    onGoToNext: () -> Unit
) {
    val monthFormatter = remember(appLocale) {
        historyCalendarMonthTitleFormatter(appLocale)
    }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryCalendarNavigationButton(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    enabled = canGoToPrevious,
                    contentDescription = stringResource(R.string.history_previous_month),
                    onClick = onGoToPrevious,
                    modifier = Modifier.size(30.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryCalendarNavigationButton(
                    imageVector = Icons.Rounded.RestartAlt,
                    enabled = displayedMonth != currentMonth,
                    contentDescription = stringResource(R.string.history_current_month),
                    onClick = onGoToCurrent,
                    modifier = Modifier.size(24.dp)
                )
                HistoryCalendarNavigationButton(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    enabled = canGoToNext,
                    contentDescription = stringResource(R.string.history_next_month),
                    onClick = onGoToNext,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Text(
            text = displayedMonth.atDay(1).format(monthFormatter),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun HistoryCalendarNavigationButton(
    imageVector: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = modifier
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
    val dayAlpha = historyCalendarDayAlpha(
        position = day.position,
        isFuture = day.date.isAfter(today),
        isSelected = isSelected,
        isToday = day.date == today
    )
    val isFuture = day.date.isAfter(today)
    val isSelectable = canSelectHistoryCalendarDate(
        date = day.date,
        today = today
    )
    val isToday = day.date == today
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

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .padding(2.dp)
            .alpha(dayAlpha)
            .clip(MaterialTheme.shapes.medium)
            .background(
                color = containerColor,
                shape = MaterialTheme.shapes.medium
            )
            .combinedClickable(
                enabled = isSelectable,
                onClick = { onClick(day.date) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            HistoryCalendarDayIndicator(
                date = day.date,
                today = today,
                isSelected = isSelected,
                dayStatus = historyCalendarIndicatorStatus(
                    date = day.date,
                    today = today,
                    dayStatus = dayStatus ?: PlanCalendarDayStatus.NONE
                )
            )
        }
    }
}

internal fun historyCalendarDayAlpha(
    position: DayPosition,
    isFuture: Boolean,
    isSelected: Boolean,
    isToday: Boolean
): Float {
    return if (isSelected || isToday) {
        1f
    } else if (position != DayPosition.MonthDate || isFuture) {
        0.56f
    } else {
        1f
    }
}

internal fun historyCalendarIndicatorStatus(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus
): PlanCalendarDayStatus {
    return if (date.isAfter(today)) {
        PlanCalendarDayStatus.NONE
    } else {
        dayStatus
    }
}

internal fun historyCalendarDayClickTargetMonth(
    position: DayPosition,
    date: LocalDate
): YearMonth? {
    return if (position == DayPosition.MonthDate) {
        null
    } else {
        YearMonth.from(date)
    }
}

@Composable
private fun HistoryCalendarDayIndicator(
    date: LocalDate,
    today: LocalDate,
    isSelected: Boolean,
    dayStatus: PlanCalendarDayStatus
) {
    val indicatorMode = if (date.isBefore(today)) {
        HistoryIndicatorColorMode.Emphasized
    } else {
        HistoryIndicatorColorMode.Neutral
    }
    val selectedColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        null
    }

    HistoryStatusIndicator(
        status = dayStatus,
        colors = historyIndicatorColors(
            scheduledMode = indicatorMode,
            partialMode = indicatorMode,
            selectedColor = selectedColor
        )
    )
}

@Composable
private fun HistoryStatusIndicator(
    status: PlanCalendarDayStatus,
    colors: HistoryIndicatorColors,
    modifier: Modifier = Modifier
) {
    when (status) {
        PlanCalendarDayStatus.NONE -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.NO_RECORD,
                color = colors.neutral,
                modifier = modifier.size(12.dp)
            )
        }
        PlanCalendarDayStatus.OFFPLAN -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.OFFPLAN,
                color = colors.unplanned,
                modifier = modifier.size(12.dp)
            )
        }
        PlanCalendarDayStatus.MISSED -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.MISSED,
                color = colors.scheduled,
                modifier = modifier.size(12.dp)
            )
        }
        PlanCalendarDayStatus.PARTIAL -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.PARTIAL,
                color = colors.partial,
                modifier = modifier.size(12.dp)
            )
        }
        PlanCalendarDayStatus.FULFILLED -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.CHECK,
                color = colors.fulfilled,
                modifier = modifier.size(12.dp)
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
        historyEntryGroupDayFormatter(appLocale)
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
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryStatusDot(
                    status = dayStatus,
                    isPastScheduled = dayStatus == PlanCalendarDayStatus.MISSED && date.isBefore(today),
                    isToday = isToday
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = weekdayLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        Text(
            text = entryCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun historyEntryGroupDayFormatter(appLocale: Locale): DateTimeFormatter {
    return if (appLocale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("M月d日", appLocale)
    } else {
        DateTimeFormatter.ofPattern("MMM d", appLocale)
    }
}

internal fun historyMonthLabelFormatter(appLocale: Locale): DateTimeFormatter {
    return if (appLocale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("M月", appLocale)
    } else {
        DateTimeFormatter.ofPattern("LLLL", appLocale)
    }
}

internal fun historyCalendarMonthTitleFormatter(appLocale: Locale): DateTimeFormatter {
    return if (appLocale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("yyyy年M月", appLocale)
    } else {
        DateTimeFormatter.ofPattern("LLLL yyyy", appLocale)
    }
}

@Composable
private fun HistoryStatusDot(
    status: PlanCalendarDayStatus,
    isPastScheduled: Boolean = false,
    isToday: Boolean = false,
    modifier: Modifier = Modifier
) {
    HistoryStatusIndicator(
        status = status,
        colors = historyIndicatorColors(
            scheduledMode = if (isPastScheduled) {
                HistoryIndicatorColorMode.Emphasized
            } else {
                HistoryIndicatorColorMode.Neutral
            },
            partialMode = if (isToday) {
                HistoryIndicatorColorMode.Neutral
            } else {
                HistoryIndicatorColorMode.Emphasized
            }
        ),
        modifier = modifier
    )
}

private enum class HistoryIndicatorColorMode {
    Neutral,
    Emphasized
}

private data class HistoryIndicatorColors(
    val scheduled: Color,
    val partial: Color,
    val fulfilled: Color,
    val neutral: Color,
    val unplanned: Color
)

@Composable
private fun historyIndicatorColors(
    scheduledMode: HistoryIndicatorColorMode = HistoryIndicatorColorMode.Neutral,
    partialMode: HistoryIndicatorColorMode = HistoryIndicatorColorMode.Neutral,
    selectedColor: Color? = null
): HistoryIndicatorColors {
    if (selectedColor != null) {
        return HistoryIndicatorColors(
            scheduled = selectedColor,
            partial = selectedColor,
            fulfilled = selectedColor,
            neutral = selectedColor,
            unplanned = selectedColor
        )
    }
    return HistoryIndicatorColors(
        scheduled = historyIndicatorColor(
            mode = scheduledMode,
            emphasizedColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        partial = historyIndicatorColor(
            mode = partialMode,
            emphasizedColor = MaterialTheme.colorScheme.primary
        ),
        fulfilled = MaterialTheme.colorScheme.primary,
        neutral = MaterialTheme.colorScheme.outlineVariant,
        unplanned = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun historyIndicatorColor(
    mode: HistoryIndicatorColorMode,
    emphasizedColor: Color
): Color {
    return when (mode) {
        HistoryIndicatorColorMode.Neutral -> MaterialTheme.colorScheme.outline
        HistoryIndicatorColorMode.Emphasized -> emphasizedColor
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryEntryCard(
    collapsedEntry: HistoryCollapsedEntry,
    timeFormatter: DateTimeFormatter,
    groupName: String?,
    groupColorKey: MedicationGroupColorKey?,
    isSelected: Boolean,
    index: Int = 0,
    count: Int = 1,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val entry = collapsedEntry.representativeEntry
    val sourceVisual = remember(entry.sourceType, entry.scheduledFor != null) {
        historySourceVisual(entry.sourceType, entry.scheduledFor != null)
    }
    val groupColorScheme = rememberMedicationGroupColorScheme(groupColorKey)
    val supportingText = buildHistoryEntrySupportingText(
        entry = entry,
        count = collapsedEntry.count,
        groupName = groupName
    )
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        leadingContent = {
            Icon(
                imageVector = sourceVisual.icon,
                contentDescription = stringResource(sourceVisual.contentDescriptionRes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        },
        supportingContent = {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                    text = stringResource(entry.details.applicationType.labelRes).uppercase(),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .alignByBaseline(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = groupColorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Text(
                text = medicationDisplayName(entry.details),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun HistoryEmptyStateCard(
    text: String,
    modifier: Modifier = Modifier
) {
    ListItem(
        onClick = {},
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shapes = ListItemDefaults.shapes(
            shape = MaterialTheme.shapes.large
        ),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun buildHistoryEntrySupportingText(
    entry: MedicationLogEntry,
    count: Int,
    groupName: String?
): String {
    val doseText = medicationDoseText(entry.details)
    val fallbackText = stringResource(entry.details.applicationType.labelRes)
    return historyEntrySupportingText(
        primaryText = doseText ?: fallbackText,
        count = count,
        groupName = groupName
    )
}

internal fun historyEntrySupportingText(
    primaryText: String,
    count: Int,
    groupName: String?
): String {
    val parts = buildList {
        add(primaryText)
        historyEntryCountText(count)?.let(::add)
        if (!groupName.isNullOrBlank()) {
            add(groupName)
        }
    }
    return parts.joinToString(" \u00B7 ")
}

internal fun historyEntryCountText(count: Int): String? = count.takeIf { it > 1 }?.let { "${it}x" }

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
            onDisplayedMonthChange = { _, _ -> }
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
            onDisplayedMonthChange = { _, _ -> }
        )
    }
}

@Preview(
    name = "History Selected Day Empty",
    showBackground = true,
    widthDp = 420,
    heightDp = 900
)
@Composable
private fun HistoryScreenSelectedDayEmptyPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        HistoryScreenContent(
            uiState = buildHistoryPreviewUiState(
                selectedDate = LocalDate.now().minusDays(2)
            ),
            onEntryClick = { },
            onEntryLongClick = { },
            onDayClick = { },
            onDeleteSelectedClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDisplayedMonthChange = { _, _ -> }
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
            icon = Icons.Rounded.CalendarMonth,
            contentDescriptionRes = R.string.history_entry_source_group_schedule
        )
    }
    return when (sourceType) {
        MedicationLogEntrySourceType.MANUAL -> HistorySourceVisual(
            icon = Icons.Rounded.Edit,
            contentDescriptionRes = R.string.history_entry_source_manual
        )
        MedicationLogEntrySourceType.GROUP_MANUAL -> HistorySourceVisual(
            icon = Icons.AutoMirrored.Rounded.ViewList,
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
            uuid = UUID.fromString("0a9d4c97-0b4c-49db-ae37-dbc1b18b8fdd"),
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
fun rememberFirstCompletelyVisibleMonth(state: CalendarState): CalendarMonth {
    val visibleMonth = remember(state) { mutableStateOf(state.firstVisibleMonth) }
    // Only take non-null values as null will be produced when the
    // list is mid-scroll as no index will be completely visible.
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.completelyVisibleMonths.firstOrNull() }
            .filterNotNull()
            .collect { month -> visibleMonth.value = month }
    }
    return visibleMonth.value
}

private val CalendarLayoutInfo.completelyVisibleMonths: List<CalendarMonth>
    get() {
        val visibleItemsInfo = this.visibleMonthsInfo.toMutableList()
        return if (visibleItemsInfo.isEmpty()) {
            emptyList()
        } else {
            val lastItem = visibleItemsInfo.last()
            val viewportSize = this.viewportEndOffset + this.viewportStartOffset
            if (lastItem.offset + lastItem.size > viewportSize) {
                visibleItemsInfo.removeAt(visibleItemsInfo.lastIndex)
            }
            val firstItem = visibleItemsInfo.firstOrNull()
            if (firstItem != null && firstItem.offset < this.viewportStartOffset) {
                visibleItemsInfo.removeAt(0)
            }
            visibleItemsInfo.map { it.month }
        }
    }
