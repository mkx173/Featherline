package com.mkx.hrttracker.ui.log

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.ui.hideBottomSheet
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
                        onDateClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    onItemDateChange(
                                        entry.localId,
                                        LocalDate.of(year, month + 1, dayOfMonth)
                                    )
                                },
                                entry.appliedDate.year,
                                entry.appliedDate.monthValue - 1,
                                entry.appliedDate.dayOfMonth
                            ).show()
                        },
                        onTimeClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    onItemTimeChange(
                                        entry.localId,
                                        LocalTime.of(hourOfDay, minute)
                                    )
                                },
                                entry.appliedTime.hour,
                                entry.appliedTime.minute,
                                DateFormat.is24HourFormat(context)
                            ).show()
                        }
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
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        overlineContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.plan_group_medication_count,
                    group.medications.size,
                    group.medications.size
                )
            )
        },
        headlineContent = {
            Text(text = group.name)
        },
        supportingContent = {
            group.medications.firstOrNull()?.let { medication ->
                Text(
                    text = stringResource(
                        R.string.plan_group_medication_summary,
                        medication.medicineName,
                        medication.dosageMgAsMedicine.formatDose(appLocale),
                        stringResource(medication.routeOfAdministration.labelRes)
                    )
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
            Text(text = stringResource(entry.routeOfAdministration.labelRes))
        },
        headlineContent = {
            Text(text = entry.medicineName)
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.entry_medicine_dose,
                    entry.dosageMgAsMedicine.formatDose(appLocale)
                )
            )
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
