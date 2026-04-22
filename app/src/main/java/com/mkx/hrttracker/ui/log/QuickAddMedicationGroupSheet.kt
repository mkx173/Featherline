package com.mkx.hrttracker.ui.log

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.totalMedicationCount
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.medication.medicationSummary
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddMedicationGroupSheet(
    onDismissRequest: () -> Unit,
    onEntriesSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickAddMedicationGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            hideBottomSheet(scope, sheetState) {
                viewModel.reset()
                onEntriesSaved()
            }
        }
    }

    QuickAddMedicationGroupSheetContent(
        uiState = uiState,
        sheetState = sheetState,
        onDismissRequest = {
            viewModel.reset()
            onDismissRequest()
        },
        onCloseClick = {
            hideBottomSheet(scope, sheetState) {
                viewModel.reset()
                onDismissRequest()
            }
        },
        onGroupSelected = viewModel::selectGroup,
        onChangeGroupClick = viewModel::clearSelectedGroup,
        onSlotSelected = viewModel::selectSlot,
        onItemDateChange = viewModel::updateItemDate,
        onItemTimeChange = viewModel::updateItemTime,
        onSaveClick = viewModel::saveEntries,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddMedicationGroupSheetContent(
    uiState: QuickAddMedicationGroupUiState,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onGroupSelected: (UUID) -> Unit,
    onChangeGroupClick: () -> Unit,
    onSlotSelected: (String?) -> Unit,
    onItemDateChange: (String, LocalDate) -> Unit,
    onItemTimeChange: (String, LocalTime) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val is24Hour = DateFormat.is24HourFormat(context)
    var datePickerEntryId by remember { mutableStateOf<String?>(null) }
    var timePickerEntryId by remember { mutableStateOf<String?>(null) }
    val datePickerEntry = uiState.draftEntries.firstOrNull { it.localId == datePickerEntryId }
    val timePickerEntry = uiState.draftEntries.firstOrNull { it.localId == timePickerEntryId }

    datePickerEntry?.let { entry ->
        DatePickerModal(
            onDateSelected = { selectedDate ->
                onItemDateChange(entry.localId, selectedDate)
            },
            onDismiss = { datePickerEntryId = null },
            initialSelectedDate = entry.appliedDate
        )
    }

    timePickerEntry?.let { entry ->
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                onItemTimeChange(entry.localId, selectedTime)
                true
            },
            onDismiss = { timePickerEntryId = null },
            initialTime = entry.appliedTime,
            is24Hour = is24Hour
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_medium)
                ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            QuickAddMedicationGroupSheetHeader(
                selectedGroup = uiState.selectedGroup,
                onCloseClick = onCloseClick,
                onChangeGroupClick = onChangeGroupClick
            )

            if (uiState.selectedGroup == null) {
                Text(
                    text = stringResource(R.string.quick_add_group_pick_group),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (uiState.groups.isEmpty()) {
                    Text(text = stringResource(R.string.quick_add_group_empty_state))
                } else {
                    uiState.groups.forEach { group ->
                        MedicationGroupSelectionRow(
                            group = group,
                            appLocale = appLocale,
                            onClick = { onGroupSelected(group.uuid) }
                        )
                    }
                }
            } else {
                if (uiState.availableSlots.isNotEmpty()) {
                    QuickAddPlannedSlotRow(
                        slots = uiState.availableSlots,
                        selectedSlotId = uiState.selectedSlotId,
                        timeFormatter = timeFormatter,
                        dateFormatter = dateFormatter,
                        today = remember { LocalDate.now() },
                        onSlotSelected = onSlotSelected
                    )
                }

                uiState.draftEntries.forEach { entry ->
                    QuickAddMedicationGroupEntryRow(
                        entry = entry,
                        appLocale = appLocale,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        onDateClick = { datePickerEntryId = entry.localId },
                        onTimeClick = { timePickerEntryId = entry.localId }
                    )
                }

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.draftEntries.isNotEmpty() && !uiState.isSaving
                ) {
                    Text(text = stringResource(R.string.quick_add_group_save_entries))
                }
            }
        }
    }
}

@Composable
private fun QuickAddMedicationGroupSheetHeader(
    selectedGroup: MedicationGroup?,
    onCloseClick: () -> Unit,
    onChangeGroupClick: () -> Unit
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(selectedGroup?.colorKey)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (selectedGroup == null) {
            Text(
                text = stringResource(R.string.quick_add_group_title),
                style = MaterialTheme.typography.titleLarge
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))) {
                IconButton(onClick = onChangeGroupClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.quick_add_group_change_group)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = groupColorScheme.primary,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Text(
                    text = selectedGroup.name,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        TextButton(onClick = onCloseClick) {
            Text(text = stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun MedicationGroupSelectionRow(
    group: MedicationGroup,
    appLocale: Locale,
    onClick: () -> Unit
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(group.colorKey)
    val totalMedicationCount = group.medications.totalMedicationCount()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 36.dp)
                    .background(
                        color = groupColorScheme.primary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
            )
        },
        overlineContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.plan_group_medication_count,
                    totalMedicationCount,
                    totalMedicationCount
                )
            )
        },
        headlineContent = {
            Text(text = group.name)
        },
        supportingContent = {
            group.medications.firstOrNull()?.let { medication ->
                Text(
                    text = medicationSummary(medication.details)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddPlannedSlotRow(
    slots: List<QuickAddScheduleSlotUiOption>,
    selectedSlotId: String?,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    today: LocalDate,
    onSlotSelected: (String?) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
    ) {
        Text(
            text = stringResource(R.string.quick_add_group_planned_slot_label),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
        ) {
            FilterChip(
                selected = selectedSlotId == null,
                onClick = { onSlotSelected(null) },
                label = { Text(text = stringResource(R.string.quick_add_group_planned_slot_unplanned)) }
            )
            slots.forEach { slot ->
                val dayLabel = when (slot.date) {
                    today -> stringResource(R.string.quick_add_group_planned_slot_today)
                    today.minusDays(1) -> stringResource(R.string.quick_add_group_planned_slot_yesterday)
                    today.plusDays(1) -> stringResource(R.string.quick_add_group_planned_slot_tomorrow)
                    else -> slot.date.format(dateFormatter)
                }
                val label = stringResource(
                    R.string.quick_add_group_planned_slot_format,
                    dayLabel,
                    slot.time.format(timeFormatter)
                )
                FilterChip(
                    selected = selectedSlotId == slot.slotId,
                    onClick = {
                        val next = if (selectedSlotId == slot.slotId) null else slot.slotId
                        onSlotSelected(next)
                    },
                    label = { Text(text = label) }
                )
            }
        }
    }
}

@Composable
private fun QuickAddMedicationGroupEntryRow(
    entry: QuickAddMedicationGroupItemUiState,
    appLocale: Locale,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        overlineContent = {
            Text(text = stringResource(entry.details.applicationType.labelRes))
        },
        headlineContent = {
            Text(text = medicationDisplayName(entry.details))
        },
        supportingContent = {
            medicationDoseText(entry.details)?.let { doseText ->
                Text(text = doseText)
            }
        },
        trailingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                TextButton(onClick = onDateClick) {
                    Text(text = entry.appliedDate.format(dateFormatter))
                }
                TextButton(onClick = onTimeClick) {
                    Text(text = entry.appliedTime.format(timeFormatter))
                }
            }
        }
    )
}
