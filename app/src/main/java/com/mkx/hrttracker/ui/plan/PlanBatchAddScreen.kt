package com.mkx.hrttracker.ui.plan

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun PlanBatchAddScreen(
    onNavigateBack: () -> Unit,
    onEntriesSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanBatchAddViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanBatchAddScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onEntriesSaved = onEntriesSaved,
        onGroupSelected = viewModel::selectGroup,
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
    onEntriesSaved: () -> Unit,
    onGroupSelected: (UUID) -> Unit,
    onStartDateSelected: (LocalDate) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    onSaveClick: () -> Unit,
    onSavedStateConsumed: () -> Unit,
    onSaveResultConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationAccess by remember(context) {
        mutableStateOf(canPostNotifications(context))
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    var isRangePickerVisible by remember {
        mutableStateOf(false)
    }
    var isConfirmationVisible by remember {
        mutableStateOf(false)
    }
    val saveFailureMessage = stringResource(R.string.plan_batch_add_failure)

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

    LaunchedEffect(uiState.selectedGroupUuid, uiState.groups.size) {
        if (uiState.selectedGroupUuid != null) {
            listState.animateScrollToItem(index = uiState.groups.size + 1)
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

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSavedStateConsumed()
            onEntriesSaved()
        }
    }

    if (isRangePickerVisible && uiState.startDate != null && uiState.endDate != null) {
        PlanBatchDateRangePickerDialog(
            startDate = uiState.startDate,
            endDate = uiState.endDate,
            onDateRangeSelected = { startDate, endDate ->
                onStartDateSelected(startDate)
                onEndDateSelected(endDate)
            },
            onDismiss = { isRangePickerVisible = false },
        )
    }

    if (isConfirmationVisible && uiState.startDate != null && uiState.endDate != null) {
        PlanBatchAddConfirmationDialog(
            groupName = uiState.selectedGroupName,
            startDate = uiState.startDate,
            endDate = uiState.endDate,
            entryCount = uiState.entryCount,
            manualEntryCount = uiState.manualEntryCount,
            dateFormatter = dateFormatter,
            isSaving = uiState.isSaving,
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
                title = { Text(text = stringResource(R.string.plan_batch_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior,
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
            item(key = "group-heading") {
                Text(
                    text = stringResource(R.string.plan_batch_add_select_group),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp)
                )
            }

            if (uiState.groups.isEmpty()) {
                item(key = "empty-groups") {
                    SupportMessageListItem(
                        text = stringResource(R.string.plan_empty_state),
                        painter = painterResource(R.drawable.ic_info),
                    )
                }
            } else {
                uiState.groups.forEachIndexed { index, group ->
                    item(key = group.uuid) {
                        RegimenGroupCard(
                            group = group,
                            remindersEnabled = uiState.remindersEnabled,
                            hasNotificationAccess = hasNotificationAccess,
                            appLocale = appLocale,
                            dateFormatter = { date -> date.format(dateFormatter) },
                            timeFormatter = timeFormatter,
                            upcomingOccurrences = uiState.nextOccurrencesByGroup[group.uuid].orEmpty(),
                            today = uiState.today,
                            onClick = { onGroupSelected(group.uuid) },
                            index = index,
                            itemCount = uiState.groups.size,
                            selected = group.uuid == uiState.selectedGroupUuid,
                            showNotificationIcon = false,
                            showChevron = false,
                            showUpcomingSection = false,
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
                    }
                }
            }

            if (uiState.selectedGroupUuid != null &&
                uiState.startDate != null &&
                uiState.endDate != null
            ) {
                item(key = "range-selector") {
                    Spacer(modifier = Modifier.height(14.dp))
                    PlanBatchAddRangeSelector(
                        startDate = uiState.startDate,
                        endDate = uiState.endDate,
                        entryCount = uiState.entryCount,
                        manualEntryCount = uiState.manualEntryCount,
                        canConfirm = uiState.canConfirm,
                        isSaving = uiState.isSaving,
                        dateFormatter = dateFormatter,
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
    canConfirm: Boolean,
    isSaving: Boolean,
    dateFormatter: DateTimeFormatter,
    onDateRangeClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
    ) {
        Text(
            text = stringResource(R.string.plan_batch_add_range_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp)
        )

        SupportMessageListItem(
            text = stringResource(R.string.plan_batch_add_date_range),
            supportingText = stringResource(
                R.string.plan_batch_add_date_range_value,
                startDate.format(dateFormatter),
                endDate.format(dateFormatter),
            ),
            onClick = onDateRangeClick,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Event,
            leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconSize = 24.dp,
            showChevron = true,
            index = 0,
            count = 2
        )

        if (entryCount > 0) {
            SupportMessageListItem(
                text = pluralStringResource(
                    R.plurals.plan_batch_add_entries_to_add,
                    entryCount,
                    entryCount
                ),
                supportingText = if (manualEntryCount > 0) {
                    stringResource(R.string.plan_batch_add_manual_before_start_note)
                } else {
                    null
                },
                painter = painterResource(R.drawable.ic_data_info_alert),
                leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconSize = 24.dp,
                index = 1,
                count = 2
            )
        } else {
            SupportMessageListItem(
                text = stringResource(R.string.plan_batch_add_no_entries),
                painter = painterResource(R.drawable.ic_data_info_alert),
                leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconSize = 24.dp,
                index = 1,
                count = 2
            )
        }

        HrtButton(
            text = stringResource(R.string.plan_batch_add_confirm_button),
            onClick = onConfirmClick,
            enabled = canConfirm,
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )

        if (isSaving) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanBatchDateRangePickerDialog(
    startDate: LocalDate,
    endDate: LocalDate,
    onDateRangeSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDate = startDate,
        initialSelectedEndDate = endDate,
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
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanBatchAddConfirmationDialog(
    groupName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    entryCount: Int,
    manualEntryCount: Int,
    dateFormatter: DateTimeFormatter,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = { Text(text = stringResource(R.string.plan_batch_add_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.plan_batch_add_confirm_message,
                        entryCount,
                        entryCount,
                        groupName,
                        startDate.format(dateFormatter),
                        endDate.format(dateFormatter),
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
                enabled = !isSaving,
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.plan_batch_add_confirm_button))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
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
            onEntriesSaved = { },
            onGroupSelected = { },
            onStartDateSelected = { },
            onEndDateSelected = { },
            onSaveClick = { },
            onSavedStateConsumed = { },
            onSaveResultConsumed = { },
        )
    }
}
