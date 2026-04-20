package com.mkx.hrttracker.ui.log

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogPlannedDoseSheet(
    groupId: UUID,
    scheduledFor: LocalDateTime,
    onDismissRequest: () -> Unit,
    onEntriesSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickLogPlannedDoseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId, scheduledFor) {
        viewModel.initialize(groupId, scheduledFor)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            hideBottomSheet(scope, sheetState) {
                viewModel.reset()
                onEntriesSaved()
            }
        }
    }

    QuickLogPlannedDoseSheetContent(
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
        onItemDateChange = viewModel::updateItemDate,
        onItemTimeChange = viewModel::updateItemTime,
        onSaveClick = viewModel::saveEntries,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogPlannedDoseSheetContent(
    uiState: QuickLogPlannedDoseUiState,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
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
    val today = remember { LocalDate.now() }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
                ) {
                    Text(
                        text = uiState.group?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge
                    )
                    uiState.scheduledFor?.let { scheduled ->
                        val dayLabel = when (scheduled.toLocalDate()) {
                            today -> stringResource(R.string.quick_add_group_planned_slot_today)
                            today.minusDays(1) -> stringResource(R.string.quick_add_group_planned_slot_yesterday)
                            today.plusDays(1) -> stringResource(R.string.quick_add_group_planned_slot_tomorrow)
                            else -> scheduled.toLocalDate().format(dateFormatter)
                        }
                        Text(
                            text = stringResource(
                                R.string.quick_add_group_planned_slot_format,
                                dayLabel,
                                scheduled.toLocalTime().format(timeFormatter)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                TextButton(onClick = onCloseClick) {
                    Text(text = stringResource(R.string.cancel))
                }
            }

            uiState.draftEntries.forEach { entry ->
                QuickLogPlannedDoseEntryRow(
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

@Composable
private fun QuickLogPlannedDoseEntryRow(
    entry: QuickLogPlannedDoseItemUiState,
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
