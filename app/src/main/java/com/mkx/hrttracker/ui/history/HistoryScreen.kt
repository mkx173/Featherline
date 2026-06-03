package com.mkx.hrttracker.ui.history

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.FlipSlot
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.MedicationCard
import com.mkx.hrttracker.ui.components.MedicationCardMissingGroupColorTreatment
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.medication.doseInstructionSummary
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.plan.PlanCalendarDayStatus
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.calendarMonthTitleFormatter
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.formatEntryWallTime
import com.mkx.hrttracker.util.historyEntryGroupDateFormatter
import com.mkx.hrttracker.util.historyMonthLabelFormatter
import com.mkx.hrttracker.util.isCrossZone
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.swmansion.kmpwheelpicker.WheelPicker
import com.swmansion.kmpwheelpicker.WheelPickerState
import com.swmansion.kmpwheelpicker.rememberWheelPickerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private const val HistoryCalendarNavigationSettleTimeoutMillis = 500L

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onEntryClick: (Set<UUID>) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    scrollToTopSignal: Int = 0,
    viewModel: HistoryViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.resetCalendarViewport()
        }
    }

    HistoryScreenContent(
        uiState = uiState,
        onEntryClick = { entry ->
            when (historyEntryTapAction(uiState.selectedEntryIds)) {
                HistoryEntryTapAction.OPEN_EDITOR -> onEntryClick(setOf(entry.uuid))
                HistoryEntryTapAction.TOGGLE_SELECTION -> {
                    viewModel.toggleEntrySelection(entry.uuid)
                }
            }
        },
        onEntryLongClick = { entry ->
            viewModel.toggleEntrySelection(entry.uuid)
        },
        onDayClick = viewModel::toggleSelectedDate,
        onDeleteSelectedClick = viewModel::showDeleteConfirmation,
        onCancelEntrySelectionClick = viewModel::clearEntrySelection,
        onSelectAllEntriesClick = viewModel::selectEntries,
        onReverseEntrySelectionClick = viewModel::reverseEntrySelection,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteSelectedEntries,
        onDeleteAllClick = viewModel::deleteAllEntries,
        onDeleteSelectedResultConsumed = viewModel::consumeDeleteSelectedEntriesResult,
        onDeleteAllResultConsumed = viewModel::consumeDeleteAllEntriesResult,
        onDisplayedMonthChange = { month, clearSelection ->
            viewModel.setDisplayedMonth(month, clearSelection)
        },
        onNavigateBack = onNavigateBack,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    modifier: Modifier = Modifier,
    uiState: HistoryUiState,
    onEntryClick: (MedicationLogEntry) -> Unit,
    onEntryLongClick: (MedicationLogEntry) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onCancelEntrySelectionClick: () -> Unit,
    onSelectAllEntriesClick: (Set<UUID>) -> Unit,
    onReverseEntrySelectionClick: (Set<UUID>) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onDeleteSelectedResultConsumed: () -> Unit,
    onDeleteAllResultConsumed: () -> Unit,
    onDisplayedMonthChange: (YearMonth, Boolean) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    scrollToTopSignal: Int = 0,
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val today = uiState.today
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val monthLabelFormatter = remember(appLocale) {
        historyMonthLabelFormatter(appLocale)
    }
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    var isSelectionFabVisible by remember { mutableStateOf(true) }
    val calendarState = key(
        uiState.calendarStartMonth,
        uiState.calendarEndMonth,
        uiState.calendarFirstDayOfWeek
    ) {
        // rememberCalendarState keeps its initial bounds, so recreate it when the available
        // month range expands or contracts after the underlying history data changes. Keep the
        // first visible month stable inside that range; rememberCalendarState uses it as an input
        // and would otherwise recreate the calendar on every displayed-month update.
        val initialVisibleMonth = remember { uiState.displayedMonth }
        rememberCalendarState(
            startMonth = uiState.calendarStartMonth,
            endMonth = uiState.calendarEndMonth,
            firstVisibleMonth = initialVisibleMonth,
            firstDayOfWeek = uiState.calendarFirstDayOfWeek,
            outDateStyle = OutDateStyle.EndOfGrid
        )
    }
    val displayedMonth = rememberMostVisibleHistoryCalendarMonth(calendarState)
    val settledDisplayedMonth = rememberSettledHistoryCalendarMonth(calendarState)
    val visibleCalendarMonths = rememberVisibleHistoryCalendarMonths(calendarState)
    var calendarNavigationMonth by remember(calendarState) {
        mutableStateOf(displayedMonth.yearMonth)
    }
    val pendingSelectedDate = remember { mutableStateOf<LocalDate?>(null) }
    val pendingSelectionResetTargetMonth = remember { mutableStateOf<YearMonth?>(null) }
    val effectiveSelectedDate = remember(
        displayedMonth.yearMonth,
        pendingSelectedDate.value,
        pendingSelectionResetTargetMonth.value,
        uiState.selectedDate
    ) {
        resolveHistoryEffectiveSelectedDate(
            displayedMonth = displayedMonth.yearMonth,
            pendingSelectedDate = pendingSelectedDate.value,
            selectedDate = uiState.selectedDate,
            pendingSelectionResetTargetMonth = pendingSelectionResetTargetMonth.value
        )
    }
    var isActionMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isDeleteAllConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val calendarSelectedDate = remember(
        displayedMonth.yearMonth,
        pendingSelectedDate.value,
        pendingSelectionResetTargetMonth.value,
        uiState.selectedDate
    ) {
        resolveHistoryCalendarSelectedDate(
            displayedMonth = displayedMonth.yearMonth,
            pendingSelectedDate = pendingSelectedDate.value,
            selectedDate = uiState.selectedDate,
            pendingSelectionResetTargetMonth = pendingSelectionResetTargetMonth.value
        )
    }

    LaunchedEffect(settledDisplayedMonth.yearMonth, pendingSelectionResetTargetMonth.value) {
        onDisplayedMonthChange(
            settledDisplayedMonth.yearMonth,
            shouldClearHistorySelectionOnMonthChange(
                displayedMonth = settledDisplayedMonth.yearMonth,
                pendingSelectedDate = pendingSelectedDate.value,
                selectedDate = uiState.selectedDate,
                pendingSelectionResetTargetMonth = pendingSelectionResetTargetMonth.value
            )
        )
    }

    LaunchedEffect(
        settledDisplayedMonth.yearMonth,
        pendingSelectedDate.value,
        uiState.selectedDate
    ) {
        val pendingDate = pendingSelectedDate.value
        val shouldCommitPendingSelection = shouldCommitPendingHistorySelection(
            settledDisplayedMonth = settledDisplayedMonth.yearMonth,
            pendingSelectedDate = pendingDate,
            selectedDate = uiState.selectedDate,
        )
        if (shouldCommitPendingSelection) {
            onDayClick(pendingDate ?: return@LaunchedEffect)
        }
    }

    LaunchedEffect(
        settledDisplayedMonth.yearMonth,
        calendarNavigationMonth,
        pendingSelectedDate.value
    ) {
        val shouldClearPendingSelection = shouldClearPendingHistorySelection(
            settledDisplayedMonth = settledDisplayedMonth.yearMonth,
            navigationMonth = calendarNavigationMonth,
            pendingSelectedDate = pendingSelectedDate.value,
        )
        if (shouldClearPendingSelection) {
            pendingSelectedDate.value = null
        }
    }

    LaunchedEffect(uiState.selectedDate, pendingSelectedDate.value) {
        val pendingDate = pendingSelectedDate.value ?: return@LaunchedEffect
        if (uiState.selectedDate == pendingDate) {
            pendingSelectedDate.value = null
        }
    }

    LaunchedEffect(uiState.selectedDate, pendingSelectionResetTargetMonth.value) {
        if (uiState.selectedDate == null) {
            pendingSelectionResetTargetMonth.value = null
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

    LaunchedEffect(listState, uiState.isSelectionMode) {
        if (!uiState.isSelectionMode) {
            isSelectionFabVisible = true
            return@LaunchedEffect
        }

        isSelectionFabVisible = true
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val isScrollingDown = index > previousIndex ||
                        (index == previousIndex && offset > previousOffset)
                val isScrollingUp = index < previousIndex ||
                        (index == previousIndex && offset < previousOffset)

                when {
                    isScrollingDown -> isSelectionFabVisible = false
                    isScrollingUp -> isSelectionFabVisible = true
                }

                previousIndex = index
                previousOffset = offset
            }
    }

    val dayStateMonthRanges = remember(
        displayedMonth.yearMonth,
        visibleCalendarMonths,
        calendarNavigationMonth,
        uiState.calendarStartMonth,
        uiState.calendarEndMonth
    ) {
        historyCalendarDayStateMonthRanges(
            displayedMonth = displayedMonth.yearMonth,
            visibleMonths = visibleCalendarMonths + calendarNavigationMonth,
            calendarStartMonth = uiState.calendarStartMonth,
            calendarEndMonth = uiState.calendarEndMonth
        )
    }
    val monthDayStates = remember(
        uiState.calendarMedicationGroups,
        uiState.entries,
        dayStateMonthRanges,
    ) {
        dayStateMonthRanges.fold(linkedMapOf<LocalDate, HistoryCalendarDayUiState>()) { dayStates, range ->
            dayStates.apply {
                putAll(
                    buildHistoryCalendarDayUiState(
                        groups = uiState.calendarMedicationGroups,
                        entries = uiState.entries,
                        startDate = range.startMonth.atDay(1),
                        endDate = range.endMonth.atEndOfMonth(),
                    )
                )
            }
        }
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
    val groupedEntries = remember(visibleEntries) {
        groupHistoryEntriesByDate(visibleEntries)
    }
    val visibleEntryIds = remember(visibleEntries) {
        visibleEntries.mapTo(linkedSetOf()) { entry -> entry.uuid }
    }
    val selectedEntryCount = uiState.selectedEntryIds.size
    val canSelectAllVisibleEntries = remember(visibleEntryIds, uiState.selectedEntryIds) {
        visibleEntryIds.isNotEmpty() && visibleEntryIds.any { entryId ->
            entryId !in uiState.selectedEntryIds
        }
    }
    // The SELECTION face keeps rendering for ~110ms while the top app bar flips out.
    // Latch the in-selection values so the cleared selection can't change them mid-animation and blink.
    val selectAllEnabled = remember { mutableStateOf(canSelectAllVisibleEntries) }
    val displayedSelectedEntryCount = remember { mutableStateOf(selectedEntryCount) }
    if (uiState.isSelectionMode) {
        selectAllEnabled.value = canSelectAllVisibleEntries
        displayedSelectedEntryCount.value = selectedEntryCount
    }
    val groupColorsById = remember(uiState.medicationGroups) {
        uiState.medicationGroups.associate { group -> group.uuid to group.colorKey }
    }
    val archivedGroupUuids = remember(uiState.medicationGroups) {
        uiState.medicationGroups
            .filter(MedicationGroup::isArchived)
            .mapTo(mutableSetOf()) { group -> group.uuid }
    }
    val entryListTitle = if (effectiveSelectedDate != null) {
        stringResource(
            R.string.history_selected_day_records_title,
            dateFormatter(effectiveSelectedDate)
        )
    } else {
        stringResource(
            R.string.history_month_entries_title,
            displayedMonth.yearMonth.atDay(1).format(monthLabelFormatter)
        )
    }
    val deleteAllEntriesSuccessMessage =
        stringResource(R.string.history_delete_all_entries_success)
    val deleteAllEntriesFailureMessage =
        stringResource(R.string.history_delete_all_entries_failure)
    val deleteSelectedEntriesSuccessMessage =
        stringResource(R.string.history_delete_selected_entries_success)
    val deleteSelectedEntriesFailureMessage =
        stringResource(R.string.history_delete_selected_entries_failure)

    BackHandler(enabled = uiState.isSelectionMode) {
        onCancelEntrySelectionClick()
    }

    LaunchedEffect(uiState.deleteSelectedEntriesResult) {
        when (uiState.deleteSelectedEntriesResult) {
            HistoryDeleteSelectedEntriesResult.SUCCESS -> {
                Toast.makeText(
                    context,
                    deleteSelectedEntriesSuccessMessage,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleteSelectedResultConsumed()
            }

            HistoryDeleteSelectedEntriesResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteSelectedEntriesFailureMessage,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleteSelectedResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.deleteAllEntriesResult) {
        when (uiState.deleteAllEntriesResult) {
            HistoryDeleteAllEntriesResult.SUCCESS -> {
                Toast.makeText(
                    context,
                    deleteAllEntriesSuccessMessage,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleteAllResultConsumed()
            }

            HistoryDeleteAllEntriesResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteAllEntriesFailureMessage,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleteAllResultConsumed()
            }

            null -> Unit
        }
    }

    if (uiState.isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeletingSelectedEntries) {
                    onDeleteDismiss()
                }
            },
            title = { Text(text = stringResource(R.string.delete_entry_title)) },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.delete_entries_confirmation,
                        selectedEntryCount,
                        selectedEntryCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isDeletingSelectedEntries) onDeleteConfirm()
                    },
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isDeletingSelectedEntries) onDeleteDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isDeleteAllConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeletingAllEntries) {
                    isDeleteAllConfirmationVisible = false
                }
            },
            title = {
                Text(text = stringResource(R.string.history_delete_all_entries_title))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.history_delete_all_entries_confirmation,
                            uiState.allEntryCount,
                            uiState.allEntryCount,
                        )
                    )
                    if (uiState.hiddenArchivedGroupRecordCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.history_delete_all_entries_hidden_archived_warning,
                                uiState.hiddenArchivedGroupRecordCount,
                                uiState.hiddenArchivedGroupRecordCount,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeletingAllEntries) return@TextButton
                        isDeleteAllConfirmationVisible = false
                        onDeleteAllClick()
                    }
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeletingAllEntries) return@TextButton
                        isDeleteAllConfirmationVisible = false
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode && isSelectionFabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FloatingActionButton(
                    onClick = onDeleteSelectedClick,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete_entries_fab)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    FlipSlot(
                        flipped = uiState.isSelectionMode,
                        contentAlignment = Alignment.CenterStart,
                        front = {
                            val title = stringResource(R.string.tab_history)
                            Text(
                                text = title,
                                modifier = Modifier.cjkTextOffset(title, amount = (-2).dp),
                            )
                        },
                        back = {
                            val title = pluralStringResource(
                                R.plurals.history_selected_entries_title,
                                displayedSelectedEntryCount.value,
                                displayedSelectedEntryCount.value,
                            )
                            Text(
                                text = title,
                                modifier = Modifier.cjkTextOffset(title, amount = (-2).dp),
                            )
                        },
                    )
                },
                navigationIcon = {
                    FlipSlot(
                        flipped = uiState.isSelectionMode,
                        front = {
                            if (onNavigateBack != null) {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.navigate_back),
                                    )
                                }
                            }
                        },
                        back = {
                            IconButton(onClick = onCancelEntrySelectionClick) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.history_cancel_selection),
                                )
                            }
                        },
                    )
                },
                actions = {
                    FlipSlot(
                        flipped = uiState.isSelectionMode,
                        contentAlignment = Alignment.CenterEnd,
                        front = {
                            Box {
                                IconButton(onClick = { isActionMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.plan_more_options),
                                    )
                                }
                                HrtDropdownMenu(
                                    expanded = isActionMenuExpanded,
                                    onDismissRequest = { isActionMenuExpanded = false },
                                    items = listOf(
                                        HrtDropdownMenuItem(
                                            text = stringResource(R.string.history_delete_all_entries),
                                            enabled = uiState.allEntryCount > 0 &&
                                                !uiState.isDeletingAllEntries,
                                            onClick = {
                                                isDeleteAllConfirmationVisible = true
                                            },
                                        )
                                    ),
                                )
                            }
                        },
                        back = {
                            Row {
                                IconButton(
                                    enabled = selectAllEnabled.value,
                                    onClick = { onSelectAllEntriesClick(visibleEntryIds) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SelectAll,
                                        contentDescription = stringResource(R.string.history_select_all),
                                    )
                                }
                                IconButton(
                                    enabled = visibleEntryIds.isNotEmpty(),
                                    onClick = { onReverseEntrySelectionClick(visibleEntryIds) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FlipToBack,
                                        contentDescription = stringResource(R.string.history_reverse_selection),
                                    )
                                }
                            }
                        },
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
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
                contentPadding = appContentPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
            item(key = "history-header") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
                ) {
                    HistoryMonthSummaryStrip(summary = monthSummary)
                    HistoryMonthCalendar(
                        calendarState = calendarState,
                        displayedMonth = displayedMonth.yearMonth,
                        settledDisplayedMonth = settledDisplayedMonth.yearMonth,
                        today = today,
                        firstDayOfWeek = uiState.calendarFirstDayOfWeek,
                        dayStates = monthDayStates,
                        appLocale = appLocale,
                        selectedDate = calendarSelectedDate,
                        hasResettableSelection = uiState.selectedDate != null &&
                                pendingSelectionResetTargetMonth.value == null,
                        onDayClick = { date ->
                            pendingSelectionResetTargetMonth.value = null
                            val selectedMonth = YearMonth.from(date)
                            if (selectedMonth == settledDisplayedMonth.yearMonth) {
                                pendingSelectedDate.value = null
                                onDayClick(date)
                            } else {
                                calendarNavigationMonth = selectedMonth
                                pendingSelectedDate.value = date
                            }
                        },
                        onSelectionReset = { targetMonth ->
                            pendingSelectedDate.value = null
                            if (uiState.selectedDate != null) {
                                pendingSelectionResetTargetMonth.value = targetMonth
                            }
                        },
                        onDeferredDaySelectionRequested = {
                            pendingSelectionResetTargetMonth.value = null
                            pendingSelectedDate.value = it
                        },
                        onNavigationMonthChange = { calendarNavigationMonth = it },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = entryListTitle.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (visibleEntries.isEmpty()) {
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
                            hasOffPlanRecord = monthDayStates[date]?.hasOffPlanRecord == true,
                            countLabel = dateEntries.size.toString(),
                            appLocale = appLocale
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                        ) {
                            dateEntries.forEachIndexed { index, entry ->
                                HistoryEntryCard(
                                    entry = entry,
                                    timeFormatter = timeFormatter,
                                    groupColorKey = entry.sourceGroupUuid?.let(groupColorsById::get),
                                    isFromArchivedGroup = entry.sourceGroupUuid != null &&
                                            entry.sourceGroupUuid in archivedGroupUuids,
                                    isSelected = entry.uuid in uiState.selectedEntryIds,
                                    isSelectionMode = uiState.isSelectionMode,
                                    index = index,
                                    count = dateEntries.size,
                                    onClick = { onEntryClick(entry) },
                                    onLongClick = { onEntryLongClick(entry) },
                                    onSelectionClick = { onEntryLongClick(entry) }
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

            if (effectiveSelectedDate != null) {
                item(key = "clear-selection") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HrtOutlinedButton(
                            text = stringResource(R.string.history_clear_selection),
                            onClick = {
                                val selectedDate = uiState.selectedDate
                                pendingSelectedDate.value = null
                                pendingSelectionResetTargetMonth.value = null
                                if (selectedDate != null) {
                                    onDayClick(selectedDate)
                                }
                            },
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
    val painter = when (kind) {
        HistorySummaryInlineStatKind.CHECK -> R.drawable.ic_check_circle
        HistorySummaryInlineStatKind.PARTIAL -> R.drawable.ic_contrast
        HistorySummaryInlineStatKind.MISSED -> R.drawable.ic_radio_button_unchecked
        HistorySummaryInlineStatKind.OFFPLAN -> R.drawable.ic_circle
        HistorySummaryInlineStatKind.NO_RECORD -> R.drawable.ic_remove
    }
    Icon(
        painter = painterResource(painter),
        contentDescription = null,
        tint = color,
        modifier = modifier
    )
}

@Composable
private fun HistoryMonthCalendar(
    calendarState: CalendarState,
    displayedMonth: YearMonth,
    settledDisplayedMonth: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    dayStates: Map<LocalDate, HistoryCalendarDayUiState>,
    appLocale: Locale,
    selectedDate: LocalDate?,
    hasResettableSelection: Boolean,
    onDayClick: (LocalDate) -> Unit,
    onSelectionReset: (YearMonth) -> Unit,
    onDeferredDaySelectionRequested: (LocalDate) -> Unit,
    onNavigationMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var navigationTargetMonth by remember(calendarState) { mutableStateOf<YearMonth?>(null) }
    var navigationRequestId by remember(calendarState) { mutableIntStateOf(0) }
    var navigationJob by remember(calendarState) { mutableStateOf<Job?>(null) }
    val navigationMonth = navigationTargetMonth ?: displayedMonth

    LaunchedEffect(displayedMonth, navigationTargetMonth) {
        if (navigationTargetMonth == null) {
            onNavigationMonthChange(displayedMonth)
        }
    }

    suspend fun awaitNavigationSettled(targetMonth: YearMonth) {
        val visibleMonth = calendarState.layoutInfo.mostVisibleMonth()?.yearMonth
        if (visibleMonth == targetMonth) {
            return
        }

        withTimeoutOrNull(HistoryCalendarNavigationSettleTimeoutMillis) {
            snapshotFlow { calendarState.layoutInfo.mostVisibleMonth()?.yearMonth }
                .first { month -> month == targetMonth }
        }
    }

    suspend fun scrollToNavigationTarget(
        targetMonth: YearMonth,
        animate: Boolean,
    ) {
        if (animate) {
            calendarState.animateScrollToMonth(targetMonth)
        } else {
            calendarState.scrollToMonth(targetMonth)
        }

        if (calendarState.layoutInfo.mostVisibleMonth()?.yearMonth != targetMonth) {
            calendarState.scrollToMonth(targetMonth)
        }

        awaitNavigationSettled(targetMonth)
    }

    fun navigateToMonth(month: YearMonth, animate: Boolean) {
        val targetMonth = month.coerceIn(calendarState.startMonth, calendarState.endMonth)
        if (navigationTargetMonth == targetMonth && navigationJob?.isActive == true) {
            return
        }

        navigationTargetMonth = targetMonth
        navigationRequestId += 1
        val requestId = navigationRequestId
        onNavigationMonthChange(targetMonth)

        if (navigationJob?.isActive == true) {
            navigationJob?.cancel()
        }

        navigationJob = coroutineScope.launch {
            try {
                scrollToNavigationTarget(
                    targetMonth = targetMonth,
                    animate = animate,
                )
            } finally {
                val superseded = navigationRequestId != requestId
                val shouldClearTarget = !superseded && navigationTargetMonth == targetMonth
                if (shouldClearTarget) {
                    navigationTargetMonth = null
                }
            }
        }
    }

    fun latestNavigationMonth(): YearMonth {
        return navigationTargetMonth
            ?: calendarState.layoutInfo.mostVisibleMonth()?.yearMonth
            ?: displayedMonth
    }

    fun animateToMonth(month: YearMonth) {
        navigateToMonth(month = month, animate = true)
    }

    fun jumpToMonth(month: YearMonth) {
        navigateToMonth(month = month, animate = false)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HistoryCalendarTitle(
                titleMonth = displayedMonth,
                currentMonth = YearMonth.from(today),
                calendarStartMonth = calendarState.startMonth,
                calendarEndMonth = calendarState.endMonth,
                appLocale = appLocale,
                canGoToPrevious = navigationMonth > calendarState.startMonth,
                canGoToCurrent = canResetHistoryCalendar(
                    navigationMonth = navigationMonth,
                    currentMonth = YearMonth.from(today),
                    hasResettableSelection = hasResettableSelection,
                ),
                canGoToNext = navigationMonth < calendarState.endMonth,
                onGoToPrevious = {
                    val baseMonth = latestNavigationMonth()
                    animateToMonth(baseMonth.minusMonths(1))
                },
                onGoToCurrent = {
                    val currentMonth = YearMonth.from(today)
                    val baseMonth = latestNavigationMonth()
                    onSelectionReset(currentMonth)
                    if (shouldAnimateHistoryCalendarReset(
                            navigationMonth = baseMonth,
                            currentMonth = currentMonth,
                        )
                    ) {
                        animateToMonth(currentMonth)
                    } else {
                        jumpToMonth(currentMonth)
                    }
                },
                onGoToMonth = { month ->
                    jumpToMonth(month)
                },
                onGoToNext = {
                    val baseMonth = latestNavigationMonth()
                    animateToMonth(baseMonth.plusMonths(1))
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
                    val dayState = dayStates[day.date] ?: HistoryCalendarDayUiState()
                    val targetMonth = historyCalendarDayClickTargetMonth(
                        position = day.position,
                        date = day.date
                    )
                    val isSettledMonthDate = day.position != DayPosition.MonthDate ||
                            YearMonth.from(day.date) == settledDisplayedMonth
                    HistoryCalendarDay(
                        day = day,
                        today = today,
                        dayStatus = dayState.status,
                        hasOffPlanRecord = dayState.hasOffPlanRecord,
                        isSelected = day.date == selectedDate &&
                                day.position == DayPosition.MonthDate,
                        isSelectable = canSelectHistoryCalendarDate(
                            date = day.date,
                            today = today,
                            targetMonth = targetMonth,
                            calendarStartMonth = calendarState.startMonth,
                            calendarEndMonth = calendarState.endMonth
                        ) && isSettledMonthDate,
                        onClick = { date ->
                            if (targetMonth == null) {
                                onDayClick(date)
                            } else {
                                onDeferredDaySelectionRequested(date)
                                animateToMonth(targetMonth)
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
            label = stringResource(R.string.history_summary_on_track),
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.PARTIAL,
            label = stringResource(R.string.history_summary_partial),
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.MISSED,
            label = stringResource(R.string.history_summary_missed),
        )
        HistoryCalendarLegendItem(
            status = PlanCalendarDayStatus.OFFPLAN,
            label = stringResource(R.string.history_legend_unplanned),
        )
    }
}

@Composable
private fun HistoryCalendarLegendItem(
    status: PlanCalendarDayStatus,
    label: String,
    modifier: Modifier = Modifier,
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
            colors = indicatorColors,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = dimensionResource(R.dimen.padding_xsmall))
                .cjkTextOffset(label)
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
    titleMonth: YearMonth,
    currentMonth: YearMonth,
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
    appLocale: Locale,
    canGoToPrevious: Boolean,
    canGoToCurrent: Boolean,
    canGoToNext: Boolean,
    onGoToPrevious: () -> Unit,
    onGoToCurrent: () -> Unit,
    onGoToMonth: (YearMonth) -> Unit,
    onGoToNext: () -> Unit
) {
    var isMonthPickerVisible by remember { mutableStateOf(false) }
    val monthFormatter = remember(appLocale, currentMonth) {
        calendarMonthTitleFormatter(
            locale = appLocale,
            currentYear = currentMonth.year
        )
    }

    if (isMonthPickerVisible) {
        HistoryMonthPickerDialog(
            selectedMonth = titleMonth,
            calendarStartMonth = calendarStartMonth,
            calendarEndMonth = calendarEndMonth,
            appLocale = appLocale,
            onDismiss = { isMonthPickerVisible = false },
            onConfirm = { month ->
                isMonthPickerVisible = false
                onGoToMonth(month)
            }
        )
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
                    modifier = Modifier.offset(x = (-8).dp),
                    iconSize = 30.dp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryCalendarNavigationButton(
                    painter = painterResource(R.drawable.ic_restart_alt),
                    enabled = canGoToCurrent,
                    contentDescription = stringResource(R.string.history_current_month),
                    onClick = onGoToCurrent,
                    modifier = Modifier.offset(x = 8.dp),
                    iconSize = 22.dp
                )
                HistoryCalendarNavigationButton(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    enabled = canGoToNext,
                    contentDescription = stringResource(R.string.history_next_month),
                    onClick = onGoToNext,
                    modifier = Modifier.offset(x = 8.dp),
                    iconSize = 30.dp
                )
            }
        }

        val dateLabel = monthFormatter(titleMonth.atDay(1))
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.history_select_month),
                    onClick = { isMonthPickerVisible = true }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .cjkTextOffset(dateLabel)
        )
    }
}

@Composable
private fun HistoryMonthPickerDialog(
    selectedMonth: YearMonth,
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
    appLocale: Locale,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    var pickerMonth by remember(selectedMonth, calendarStartMonth, calendarEndMonth) {
        mutableStateOf(selectedMonth.coerceIn(calendarStartMonth, calendarEndMonth))
    }
    val wheelState = remember(pickerMonth, calendarStartMonth, calendarEndMonth) {
        historyMonthPickerWheelState(
            selectedMonth = pickerMonth,
            calendarStartMonth = calendarStartMonth,
            calendarEndMonth = calendarEndMonth
        )
    }
    val yearWheelState = rememberWheelPickerState(
        itemCount = wheelState.yearOptions.size,
        initialIndex = wheelState.selectedYearIndex
    )

    LaunchedEffect(wheelState.selectedYearIndex) {
        if (yearWheelState.index != wheelState.selectedYearIndex) {
            yearWheelState.scrollTo(wheelState.selectedYearIndex)
        }
    }

    LaunchedEffect(yearWheelState, pickerMonth, calendarStartMonth, calendarEndMonth) {
        snapshotFlow { yearWheelState.index }
            .distinctUntilChanged()
            .collect { selectedYearIndex ->
                val updatedMonth = historyMonthPickerSelectionForYearIndex(
                    selectedYearIndex = selectedYearIndex,
                    currentMonthValue = pickerMonth.monthValue,
                    calendarStartMonth = calendarStartMonth,
                    calendarEndMonth = calendarEndMonth
                )
                if (updatedMonth != pickerMonth) {
                    pickerMonth = updatedMonth
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.history_month_picker_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_month_picker_month),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.history_month_picker_year),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    key(wheelState.monthOptions) {
                        val monthWheelState = rememberWheelPickerState(
                            itemCount = wheelState.monthOptions.size,
                            initialIndex = wheelState.selectedMonthIndex
                        )

                        LaunchedEffect(wheelState.selectedMonthIndex) {
                            if (monthWheelState.index != wheelState.selectedMonthIndex) {
                                monthWheelState.scrollTo(wheelState.selectedMonthIndex)
                            }
                        }

                        LaunchedEffect(
                            monthWheelState,
                            wheelState.selectedMonth.year,
                            calendarStartMonth,
                            calendarEndMonth
                        ) {
                            snapshotFlow { monthWheelState.index }
                                .distinctUntilChanged()
                                .collect { selectedMonthIndex ->
                                    val updatedMonth = historyMonthPickerSelectionForMonthIndex(
                                        selectedYear = wheelState.selectedMonth.year,
                                        selectedMonthIndex = selectedMonthIndex,
                                        calendarStartMonth = calendarStartMonth,
                                        calendarEndMonth = calendarEndMonth
                                    )
                                    if (updatedMonth != pickerMonth) {
                                        pickerMonth = updatedMonth
                                    }
                                }
                        }

                        HistoryMonthPickerWheel(
                            options = wheelState.monthOptions.map { monthValue ->
                                historyMonthPickerMonthLabel(monthValue, appLocale)
                            },
                            state = monthWheelState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HistoryMonthPickerWheel(
                        options = wheelState.yearOptions.map(Int::toString),
                        state = yearWheelState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerMonth) }) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun HistoryMonthPickerWheel(
    options: List<String>,
    state: WheelPickerState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        WheelPicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            bufferSize = 1,
            window = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            },
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        ) { index ->
            val isSelected = state.index == index
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = options[index],
                    style = if (isSelected) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(if (isSelected) 1f else 0.72f).padding(vertical = 8.dp)
                )
            }
        }
    }
}

private fun historyMonthPickerMonthLabel(
    monthValue: Int,
    locale: Locale,
): String {
    return Month.of(monthValue).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
}

@Composable
private fun HistoryCalendarNavigationButton(
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
private fun HistoryCalendarDay(
    day: CalendarDay,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus,
    hasOffPlanRecord: Boolean,
    isSelected: Boolean,
    isSelectable: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayAlpha = historyCalendarDayAlpha(
        position = day.position,
        isFuture = day.date.isAfter(today),
        isSelected = isSelected,
        isToday = day.date == today
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
    val indicatorStatus = historyCalendarIndicatorStatus(
        date = day.date,
        today = today,
        dayStatus = dayStatus
    )
    val showOffPlanBadge = historyShouldShowOffPlanBadge(
        status = indicatorStatus,
        hasOffPlanRecord = !day.date.isAfter(today) && hasOffPlanRecord
    )

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
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(bottom = 2.dp)
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
                dayStatus = indicatorStatus,
                showOffPlanBadge = showOffPlanBadge,
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
    return when {
        isSelected -> 1f
        position != DayPosition.MonthDate -> 0.56f
        isToday -> 1f
        isFuture -> 0.56f
        else -> 1f
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
    dayStatus: PlanCalendarDayStatus,
    showOffPlanBadge: Boolean,
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
        ),
        modifier = Modifier.size(12.dp),
        showOffPlanBadge = showOffPlanBadge
    )
}

@Composable
private fun HistoryStatusIndicator(
    status: PlanCalendarDayStatus,
    colors: HistoryIndicatorColors,
    modifier: Modifier = Modifier,
    showOffPlanBadge: Boolean = false,
) {
    Box(modifier = modifier) {
        HistoryPrimaryStatusIndicator(
            status = status,
            colors = colors,
            modifier = modifier
        )

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

@Composable
private fun HistoryPrimaryStatusIndicator(
    status: PlanCalendarDayStatus,
    colors: HistoryIndicatorColors,
    modifier: Modifier = Modifier,
) {
    when (status) {
        PlanCalendarDayStatus.NONE -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.NO_RECORD,
                color = colors.neutral,
                modifier = modifier
            )
        }
        PlanCalendarDayStatus.OFFPLAN -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.OFFPLAN,
                color = colors.unplanned,
                modifier = modifier
            )
        }
        PlanCalendarDayStatus.MISSED -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.MISSED,
                color = colors.scheduled,
                modifier = modifier
            )
        }
        PlanCalendarDayStatus.PARTIAL -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.PARTIAL,
                color = colors.partial,
                modifier = modifier
            )
        }
        PlanCalendarDayStatus.FULFILLED -> {
            HistorySummaryIndicatorGlyph(
                kind = HistorySummaryInlineStatKind.CHECK,
                color = colors.fulfilled,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun HistoryEntryGroupHeader(
    date: LocalDate,
    today: LocalDate,
    dayStatus: PlanCalendarDayStatus,
    hasOffPlanRecord: Boolean,
    countLabel: String?,
    appLocale: Locale,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember(appLocale, today) {
        historyEntryGroupDateFormatter(appLocale, today)
    }
    val isToday = date == today
    val label = dateFormatter(date)
    val weekdayLabelShort = remember(date, appLocale) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)
    }
    val weekdayLabel = if (isToday) {
        "$weekdayLabelShort · ${stringResource(R.string.quick_add_group_planned_slot_today)}"
    } else {
        weekdayLabelShort
    }
    val weekdayDisplayLabel = weekdayLabel.uppercase()
    val showOffPlanBadge = historyShouldShowOffPlanBadge(
        status = dayStatus,
        hasOffPlanRecord = hasOffPlanRecord
    )

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
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryStatusIndicator(
                    status = dayStatus,
                    colors = historyIndicatorColors(
                        scheduledMode = if (dayStatus == PlanCalendarDayStatus.MISSED && date.isBefore(today)) {
                            HistoryIndicatorColorMode.Emphasized
                        } else {
                            HistoryIndicatorColorMode.Neutral
                        },
                        partialMode = if (isToday) {
                            HistoryIndicatorColorMode.Neutral
                        } else {
                            HistoryIndicatorColorMode.Emphasized
                        },
                        selectedColor = if (isToday) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            null
                        }
                    ),
                    modifier = Modifier.size(14.dp),
                    showOffPlanBadge = showOffPlanBadge
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.alignByBaseline().cjkTextOffset(label)
                )
                Text(
                    text = weekdayDisplayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.alignByBaseline().cjkTextOffset(weekdayDisplayLabel),
                )
            }
        }
        if (!countLabel.isNullOrBlank()) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
    entry: MedicationLogEntry,
    timeFormatter: DateTimeFormatter,
    groupColorKey: MedicationGroupColorKey?,
    isFromArchivedGroup: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    index: Int = 0,
    count: Int = 1,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectionClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    MedicationCard(
        medicine = entry.medicine,
        doseInstruction = entry.doseInstruction,
        applicationType = entry.applicationType,
        medicationCount = entry.count,
        groupColorKey = groupColorKey,
        doseAmountDelta = entry.doseAmountDelta,
        // Manual entries have no source group; render the leading icon in
        // the neutral group palette to match other manual-log surfaces
        // instead of falling through to secondaryContainer.
        missingGroupColorTreatment =
            MedicationCardMissingGroupColorTreatment.NEUTRAL_GROUP_PALETTE,
        onClick = onClick,
        onLongClick = onLongClick,
        index = index,
        itemCount = count,
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        isSelected = isSelected,
        onLeadingIconClick = if (isSelectionMode) null else onSelectionClick,
        leadingIconContentDescription = stringResource(
            if (isSelected) {
                R.string.history_entry_selected
            } else {
                R.string.history_select_entry
            }
        ),
        trailingContent = {
            val deviceZone = remember { ZoneId.systemDefault() }
            val crossZone = remember(entry) { isCrossZone(entry, deviceZone) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isFromArchivedGroup) {
                    Icon(
                        painter = painterResource(R.drawable.ic_archive),
                        contentDescription = stringResource(
                            R.string.archived_group_record_indicator
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (crossZone) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = stringResource(R.string.cross_timezone_entry_indicator),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = formatEntryWallTime(entry, timeFormatter, deviceZone),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun HistoryEmptyStateCard(
    text: String,
    modifier: Modifier = Modifier
) {
    SupportMessageListItem(
        text = text,
        painter = painterResource(R.drawable.ic_info),
        modifier = modifier,
    )
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
            onCancelEntrySelectionClick = { },
            onSelectAllEntriesClick = { },
            onReverseEntrySelectionClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDeleteAllClick = { },
            onDeleteSelectedResultConsumed = { },
            onDeleteAllResultConsumed = { },
            onDisplayedMonthChange = { _, _ -> }
        )
    }
}

private fun historyShouldShowOffPlanBadge(
    status: PlanCalendarDayStatus,
    hasOffPlanRecord: Boolean
): Boolean {
    return hasOffPlanRecord && status != PlanCalendarDayStatus.OFFPLAN
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
            onCancelEntrySelectionClick = { },
            onSelectAllEntriesClick = { },
            onReverseEntrySelectionClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDeleteAllClick = { },
            onDeleteSelectedResultConsumed = { },
            onDeleteAllResultConsumed = { },
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
            onCancelEntrySelectionClick = { },
            onSelectAllEntriesClick = { },
            onReverseEntrySelectionClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDeleteAllClick = { },
            onDeleteSelectedResultConsumed = { },
            onDeleteAllResultConsumed = { },
            onDisplayedMonthChange = { _, _ -> }
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
        previewLogEntry(
            uuid = "f16ec8a7-5115-410a-b12d-f376fdb6f76b",
            key = MedicationKey.ESTRADIOL_VALERATE,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 3.82,
            sourceGroupUuid = null,
            appliedAt = previewInstant(today, LocalTime.of(8, 30), zoneId)
        ),
        previewLogEntry(
            uuid = "9b9a1efe-6df3-43da-871d-9584370fbca8",
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = oralGroupId,
            appliedAt = previewInstant(today.minusDays(1), LocalTime.of(22, 0), zoneId)
        ),
        previewLogEntry(
            uuid = "611d7af2-6108-45ab-a320-4064e0dd1233",
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.SUBLINGUAL,
            equivalentE2Mg = 1.0,
            sourceGroupUuid = nightlyGroupId,
            appliedAt = previewInstant(today, LocalTime.of(19, 0), zoneId),
            scheduledFor = LocalDateTime.of(today, LocalTime.of(19, 0))
        ),
        previewLogEntry(
            uuid = "0a9d4c97-0b4c-49db-ae37-dbc1b18b8fdd",
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.SUBLINGUAL,
            equivalentE2Mg = 1.0,
            sourceGroupUuid = nightlyGroupId,
            appliedAt = previewInstant(today, LocalTime.of(19, 0), zoneId),
            scheduledFor = LocalDateTime.of(today, LocalTime.of(19, 0))
        ),
        previewLogEntry(
            uuid = "0db2cb5b-bf7b-45aa-9f42-d1bddcb00c88",
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 1.5,
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
                    medicine = previewMedicine(MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
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
                    medicine = previewMedicine(MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.SUBLINGUAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
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

private fun previewMedicine(
    key: MedicationKey,
): com.mkx.hrttracker.model.medication.Medicine {
    val selection = com.mkx.hrttracker.model.medication.MedicineSelection.Catalog(key)
    val preparation = com.mkx.hrttracker.model.medication.MedicinePreparation.Pill(
        strengthMgPerTablet = 2.0,
    )
    return com.mkx.hrttracker.model.medication.Medicine(
        uuid = UUID.nameUUIDFromBytes("history-preview-${key.name}".toByteArray()),
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

private fun previewLogEntry(
    uuid: String,
    key: MedicationKey,
    applicationType: MedicationApplicationType,
    equivalentE2Mg: Double?,
    sourceGroupUuid: UUID?,
    appliedAt: Instant,
    scheduledFor: LocalDateTime? = null,
): MedicationLogEntry {
    return MedicationLogEntry(
        uuid = UUID.fromString(uuid),
        medicine = previewMedicine(key),
        category = key.category,
        applicationType = applicationType,
        doseInstruction = DoseInstruction.TabletFraction(1, 1),
        equivalentE2Mg = equivalentE2Mg,
        sourceGroupUuid = sourceGroupUuid,
        appliedAt = appliedAt,
        scheduledFor = scheduledFor,
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
private fun rememberMostVisibleHistoryCalendarMonth(state: CalendarState): CalendarMonth {
    val visibleMonth = remember(state) { mutableStateOf(state.firstVisibleMonth) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.mostVisibleMonth() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { month -> visibleMonth.value = month }
    }
    return visibleMonth.value
}

@Composable
private fun rememberSettledHistoryCalendarMonth(state: CalendarState): CalendarMonth {
    val visibleMonth = remember(state) { mutableStateOf(state.firstVisibleMonth) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.completelyVisibleMonths.firstOrNull() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { month -> visibleMonth.value = month }
    }
    return visibleMonth.value
}

@Composable
private fun rememberVisibleHistoryCalendarMonths(state: CalendarState): Set<YearMonth> {
    val visibleMonths = remember(state) {
        mutableStateOf(setOf(state.firstVisibleMonth.yearMonth))
    }
    LaunchedEffect(state) {
        snapshotFlow {
            state.layoutInfo.visibleMonthsInfo
                .mapTo(linkedSetOf()) { monthInfo -> monthInfo.month.yearMonth }
        }
            .distinctUntilChanged()
            .collect { months ->
                if (months.isNotEmpty()) {
                    visibleMonths.value = months
                }
            }
    }
    return visibleMonths.value
}

internal data class HistoryCalendarDayStateMonthRange(
    val startMonth: YearMonth,
    val endMonth: YearMonth,
)

internal fun historyCalendarDayStateMonthRanges(
    displayedMonth: YearMonth,
    visibleMonths: Collection<YearMonth>,
    calendarStartMonth: YearMonth,
    calendarEndMonth: YearMonth,
    preloadMonths: Long = historyCalendarDayStatePreloadMonths,
): List<HistoryCalendarDayStateMonthRange> {
    val anchorMonths = (visibleMonths + displayedMonth)
        .map { month -> month.coerceIn(calendarStartMonth, calendarEndMonth) }
        .distinct()
    val ranges = anchorMonths
        .map { month ->
            HistoryCalendarDayStateMonthRange(
                startMonth = month.minusMonths(preloadMonths).coerceAtLeast(calendarStartMonth),
                endMonth = month.plusMonths(preloadMonths).coerceAtMost(calendarEndMonth),
            )
        }
        .sortedBy(HistoryCalendarDayStateMonthRange::startMonth)

    return ranges.fold(emptyList()) { mergedRanges, range ->
        val lastRange = mergedRanges.lastOrNull()
        if (lastRange == null || range.startMonth.isAfter(lastRange.endMonth.plusMonths(1))) {
            mergedRanges + range
        } else {
            mergedRanges.dropLast(1) + lastRange.copy(
                endMonth = if (range.endMonth.isAfter(lastRange.endMonth)) {
                    range.endMonth
                } else {
                    lastRange.endMonth
                }
            )
        }
    }
}

private const val historyCalendarDayStatePreloadMonths = 2L

private fun CalendarLayoutInfo.mostVisibleMonth(): CalendarMonth? {
    return visibleMonthsInfo
        .maxByOrNull { itemInfo ->
            val visibleStart = maxOf(itemInfo.offset, viewportStartOffset)
            val visibleEnd = minOf(itemInfo.offset + itemInfo.size, viewportEndOffset)
            (visibleEnd - visibleStart).coerceAtLeast(0)
        }
        ?.month
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
