package com.mkx.hrttracker.ui.plan

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.getSelectedEndDate
import androidx.compose.material3.getSelectedStartDate
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.FlipSlot
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.datePickerSelectableDates
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.dateRangeLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate
import java.util.UUID

@Composable
fun PlanBatchAddScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanBatchAddViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanBatchAddScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onGroupSelected = viewModel::selectGroup,
        onSelectionCleared = viewModel::clearSelection,
        onStartDateSelected = viewModel::updateStartDate,
        onEndDateSelected = viewModel::updateEndDate,
        onSaveClick = viewModel::saveSelectedRange,
        onSavedStateConsumed = viewModel::consumeSavedState,
        onSaveResultConsumed = viewModel::onSaveResultConsumed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanBatchAddScreenContent(
    uiState: PlanBatchAddUiState,
    onNavigateBack: () -> Unit,
    onGroupSelected: (UUID) -> Unit,
    onSelectionCleared: () -> Unit,
    onStartDateSelected: (LocalDate) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    onSaveClick: () -> Unit,
    onSavedStateConsumed: () -> Unit,
    onSaveResultConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val reminderCapabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    val hasNotificationAccess = reminderCapabilityState.hasNotificationAccess
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val dateFormatter = remember(appLocale, uiState.today) {
        dateLabelFormatter(appLocale, uiState.today)
    }
    val rangeDateFormatter = remember(
        appLocale,
        uiState.today,
        uiState.startDate,
        uiState.endDate,
    ) {
        dateRangeLabelFormatter(
            locale = appLocale,
            today = uiState.today,
            startDate = uiState.startDate,
            endDate = uiState.endDate,
        )
    }
    var isRangePickerVisible by remember {
        mutableStateOf(false)
    }
    var isConfirmationVisible by remember {
        mutableStateOf(false)
    }
    val saveFailureMessage = stringResource(R.string.plan_batch_add_failure)
    val savedEntryCount = uiState.savedEntryCount ?: 0
    val saveSuccessMessage = pluralStringResource(
        R.plurals.plan_batch_add_success,
        savedEntryCount,
        savedEntryCount,
    )
    val shouldDeselectOnBack = uiState.selectedGroupUuid != null && !uiState.isSaving

    fun clearSelectionAndDismissDialogs() {
        isRangePickerVisible = false
        isConfirmationVisible = false
        onSelectionCleared()
    }

    BackHandler(enabled = shouldDeselectOnBack) {
        clearSelectionAndDismissDialogs()
    }

    LaunchedEffect(uiState.selectedGroupUuid, uiState.groups.size) {
        if (uiState.selectedGroupUuid != null) {
            listState.animateScrollToItem(index = 1)
        }
    }

    LaunchedEffect(uiState.saveResult) {
        when (uiState.saveResult) {
            PlanBatchAddSaveResult.FAILURE -> {
                Toast.makeText(context, saveFailureMessage, Toast.LENGTH_SHORT).show()
                onSaveResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.isSaved, savedEntryCount) {
        if (uiState.isSaved) {
            if (savedEntryCount > 0) {
                Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
            }
            onSavedStateConsumed()
        }
    }

    if (isRangePickerVisible) {
        PlanBatchDateRangePickerDialog(
            startDate = uiState.startDate,
            endDate = uiState.endDate,
            today = uiState.today,
            onDateRangeSelected = { startDate, endDate ->
                onStartDateSelected(startDate)
                onEndDateSelected(endDate)
            },
            onDismiss = { isRangePickerVisible = false },
        )
    }

    if (isConfirmationVisible && uiState.selectedGroupUuid != null) {
        PlanBatchAddConfirmationDialog(
            groupName = uiState.selectedGroupName,
            startDate = uiState.startDate,
            endDate = uiState.endDate,
            entryCount = uiState.entryCount,
            manualEntryCount = uiState.manualEntryCount,
            dateFormatter = rangeDateFormatter,
            onDismiss = { isConfirmationVisible = false },
            onConfirm = {
                isConfirmationVisible = false
                onSaveClick()
            },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(R.string.plan_batch_add_title)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                    )
                },
                navigationIcon = {
                    FlipSlot(
                        flipped = shouldDeselectOnBack,
                        front = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.navigate_back),
                                )
                            }
                        },
                        back = {
                            IconButton(onClick = { clearSelectionAndDismissDialogs() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.history_cancel_selection),
                                )
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
                return@AppContentContainer
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValues(),
            ) {
                item(key = "group-section") {
                    HrtSection(
                        title = stringResource(R.string.plan_batch_add_select_group),
                        topPadding = false,
                    ) {
                        if (uiState.groups.isEmpty()) {
                            item {
                                SupportMessageListItem(
                                    text = stringResource(R.string.plan_empty_state),
                                    painter = painterResource(R.drawable.ic_info),
                                )
                            }
                        } else {
                            uiState.groups.forEach { group ->
                                item {
                                    RegimenGroupCard(
                                        group = group,
                                        remindersEnabled = uiState.remindersEnabled,
                                        hasNotificationAccess = hasNotificationAccess,
                                        appLocale = appLocale,
                                        dateFormatter = dateFormatter,
                                        timeFormatter = timeFormatter,
                                        upcomingOccurrences = uiState.nextOccurrencesByGroup[group.uuid].orEmpty(),
                                        today = uiState.today,
                                        onClick = { onGroupSelected(group.uuid) },
                                        selected = group.uuid == uiState.selectedGroupUuid,
                                        showNotificationIcon = false,
                                        showChevron = false,
                                        showUpcomingSection = false,
                                        firstDayOfWeek = uiState.firstDayOfWeek,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "range-selector") {
                    Spacer(modifier = Modifier.height(14.dp))
                    PlanBatchAddRangeSelector(
                        startDate = uiState.startDate,
                        endDate = uiState.endDate,
                        entryCount = uiState.entryCount,
                        manualEntryCount = uiState.manualEntryCount,
                        skippedEntryCount = uiState.skippedEntryCount,
                        canConfirm = uiState.canConfirm,
                        hasSelectedGroup = uiState.selectedGroupUuid != null,
                        selectedGroupStartsInFuture = uiState.selectedGroupStartsInFuture,
                        dateFormatter = rangeDateFormatter,
                        onDateRangeClick = { isRangePickerVisible = true },
                        onConfirmClick = { isConfirmationVisible = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanBatchAddRangeSelector(
    startDate: LocalDate,
    endDate: LocalDate,
    entryCount: Int,
    manualEntryCount: Int,
    skippedEntryCount: Int,
    canConfirm: Boolean,
    hasSelectedGroup: Boolean,
    selectedGroupStartsInFuture: Boolean,
    dateFormatter: LocalDateFormatter,
    onDateRangeClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
    ) {
        HrtSection(title = stringResource(R.string.plan_batch_add_range_title)) {
            if (!hasSelectedGroup || selectedGroupStartsInFuture) {
                item {
                    // A not-yet-started plan reuses the "no group selected" prompt path,
                    // since there's no valid past range to pick for it.
                    SupportMessageListItem(
                        text = stringResource(
                            if (selectedGroupStartsInFuture) {
                                R.string.plan_batch_add_group_not_started
                            } else {
                                R.string.plan_batch_add_select_group_prompt
                            }
                        ),
                        painter = painterResource(R.drawable.ic_info),
                        leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    SupportMessageListItem(
                        text = stringResource(R.string.plan_batch_add_date_range),
                        supportingText = stringResource(
                            R.string.plan_batch_add_date_range_value,
                            dateFormatter(startDate),
                            dateFormatter(endDate),
                        ),
                        onClick = onDateRangeClick,
                        modifier = Modifier.fillMaxWidth(),
                        painter = painterResource(R.drawable.ic_date_range),
                        leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIconSize = 24.dp,
                        showChevron = true,
                    )
                }
                if (entryCount > 0 || skippedEntryCount > 0) {
                    item {
                        val entriesToAddLabel = if (entryCount > 0) {
                            pluralStringResource(
                                R.plurals.plan_batch_add_entries_to_add,
                                entryCount,
                                entryCount
                            )
                        } else {
                            // entryCount == 0 but skippedEntryCount > 0 — the range
                            // already has matching records, nothing new can be added.
                            // Avoid rendering "0 records will be added" alongside the
                            // skipped chip; show a clearer "no new records" line.
                            stringResource(R.string.plan_batch_add_no_new_entries)
                        }
                        val skippedEntriesLabel = pluralStringResource(
                            R.plurals.plan_batch_add_entries_skipped,
                            skippedEntryCount,
                            skippedEntryCount
                        )
                        SupportMessageListItem(
                            text = if (skippedEntryCount > 0) {
                                stringResource(
                                    R.string.plan_batch_add_entries_to_add_with_skipped,
                                    entriesToAddLabel,
                                    skippedEntriesLabel
                                )
                            } else {
                                entriesToAddLabel
                            },
                            supportingText = if (manualEntryCount > 0) {
                                stringResource(R.string.plan_batch_add_manual_before_start_note)
                            } else {
                                null
                            },
                            painter = painterResource(R.drawable.ic_data_info_alert),
                            leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            leadingIconSize = 24.dp,
                        )
                    }
                } else {
                    item {
                        SupportMessageListItem(
                            text = stringResource(R.string.plan_batch_add_no_entries),
                            painter = painterResource(R.drawable.ic_data_info_alert),
                            leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            leadingIconSize = 24.dp,
                        )
                    }
                }
            }
        }

        HrtButton(
            text = stringResource(R.string.plan_batch_add_confirm_button),
            onClick = onConfirmClick,
            enabled = canConfirm,
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanBatchDateRangePickerDialog(
    startDate: LocalDate,
    endDate: LocalDate,
    today: LocalDate,
    onDateRangeSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Block future dates: batch-add only backfills occurrences up to now, so a
    // future end has nothing to add. Keyed on today so it stays correct if the
    // date rolls over while the screen is open.
    val selectableDates = remember(today) {
        datePickerSelectableDates(maximumDate = today)
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDate = startDate,
        initialSelectedEndDate = endDate,
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.getSelectedStartDate() != null &&
                    state.getSelectedEndDate() != null,
                onClick = {
                    val selectedStartDate = state.getSelectedStartDate()
                    val selectedEndDate = state.getSelectedEndDate()
                    if (selectedStartDate != null && selectedEndDate != null) {
                        onDateRangeSelected(selectedStartDate, selectedEndDate)
                    }
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    ) {
        val current = MaterialTheme.typography
        MaterialTheme(
            typography = current.copy(
                titleLarge = current.titleLarge.copy(
                    fontSize = 18.sp,
                )
            ),
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                title = {
                    DateRangePickerDefaults.DateRangePickerTitle(
                        displayMode = state.displayMode,
                        modifier = Modifier.padding(PaddingValues(start = 16.dp, top = 16.dp)),
                        contentColor = DatePickerDefaults.colors().titleContentColor,
                    )
                },
                headline = {
                    DateRangePickerDefaults.DateRangePickerHeadline(
                        selectedStartDateMillis = state.selectedStartDateMillis,
                        selectedEndDateMillis = state.selectedEndDateMillis,
                        displayMode = state.displayMode,
                        dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                        modifier = Modifier.padding(PaddingValues(start = 16.dp, bottom = 12.dp)),
                        contentColor = DatePickerDefaults.colors().headlineContentColor,
                    )
                }
            )
        }
    }
}

@Composable
private fun PlanBatchAddConfirmationDialog(
    groupName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    entryCount: Int,
    manualEntryCount: Int,
    dateFormatter: LocalDateFormatter,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plan_batch_add_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.plan_batch_add_confirm_message,
                        entryCount,
                        entryCount,
                        groupName,
                        dateFormatter(startDate),
                        dateFormatter(endDate),
                    )
                )
                if (manualEntryCount > 0) {
                    Text(
                        text = stringResource(R.string.plan_batch_add_manual_before_start_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.plan_batch_add_confirm_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun PlanBatchAddScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PlanBatchAddScreenContent(
            uiState = PlanBatchAddUiState(
                isLoading = false,
                groups = buildPlanPreviewUiState().medicationGroups,
                today = buildPlanPreviewUiState().today,
                remindersEnabled = true,
                selectedGroupUuid = buildPlanPreviewUiState().medicationGroups.first().uuid,
                selectedGroupName = buildPlanPreviewUiState().medicationGroups.first().name,
                startDate = buildPlanPreviewUiState().medicationGroups.first().schedule.since,
                endDate = buildPlanPreviewUiState().today,
                nextOccurrencesByGroup = buildPlanPreviewUiState().nextOccurrencesByGroup,
            ),
            onNavigateBack = { },
            onGroupSelected = { },
            onSelectionCleared = { },
            onStartDateSelected = { },
            onEndDateSelected = { },
            onSaveClick = { },
            onSavedStateConsumed = { },
            onSaveResultConsumed = { },
        )
    }
}

@Preview(
    name = "Batch Add Date Range Picker",
    showBackground = true,
    widthDp = 420,
    heightDp = 900
)
@Composable
private fun PlanBatchDateRangePickerDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PlanBatchDateRangePickerDialog(
            startDate = LocalDate.of(2026, 4, 20),
            endDate = LocalDate.of(2026, 4, 26),
            today = LocalDate.of(2026, 4, 26),
            onDateRangeSelected = { _, _ -> },
            onDismiss = { },
        )
    }
}
