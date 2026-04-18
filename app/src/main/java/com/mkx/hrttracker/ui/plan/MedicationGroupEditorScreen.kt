package com.mkx.hrttracker.ui.plan

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.log.MedicationEditorSheet
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun MedicationGroupEditorScreen(
    onNavigateBack: () -> Unit,
    onGroupSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicationGroupEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.consumeSavedState()
            onGroupSaved()
        }
    }

    MedicationGroupEditorScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onGroupNameChange = viewModel::updateGroupName,
        onScheduleTypeChange = viewModel::updateScheduleType,
        onWeeklyIntervalChange = viewModel::updateWeeklyIntervalWeeks,
        onWeeklyDayChange = viewModel::updateWeeklyDayOfWeek,
        onWeeklyTimeChange = viewModel::updateWeeklyTime,
        onDailyIntervalChange = viewModel::updateDailyIntervalDays,
        onAddDailyTime = viewModel::addDailyTime,
        onDailyTimeChange = viewModel::updateDailyTime,
        onRemoveDailyTime = viewModel::removeDailyTime,
        onAddMedication = viewModel::showAddMedicationEditor,
        onMedicationClick = viewModel::showMedicationEditor,
        onRemoveMedication = viewModel::removeMedication,
        onDismissMedicationEditor = viewModel::dismissMedicationEditor,
        onConsumeMedicationEditorSaved = viewModel::consumeMedicationEditorSaved,
        onMedicationRouteChange = viewModel::updateEditingMedicationRoute,
        onMedicationNameChange = viewModel::updateEditingMedicationName,
        onMedicationDosageChange = viewModel::updateEditingMedicationDosage,
        onSaveMedicationClick = viewModel::saveEditingMedication,
        onSaveClick = viewModel::saveGroup,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationGroupEditorScreenContent(
    uiState: MedicationGroupEditorUiState,
    onNavigateBack: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onScheduleTypeChange: (MedicationGroupScheduleType) -> Unit,
    onWeeklyIntervalChange: (String) -> Unit,
    onWeeklyDayChange: (DayOfWeek) -> Unit,
    onWeeklyTimeChange: (LocalTime) -> Unit,
    onDailyIntervalChange: (String) -> Unit,
    onAddDailyTime: () -> Unit,
    onDailyTimeChange: (String, LocalTime) -> Unit,
    onRemoveDailyTime: (String) -> Unit,
    onAddMedication: () -> Unit,
    onMedicationClick: (String) -> Unit,
    onRemoveMedication: (String) -> Unit,
    onDismissMedicationEditor: () -> Unit,
    onConsumeMedicationEditorSaved: () -> Unit,
    onMedicationRouteChange: (com.mkx.hrttracker.model.medication.RouteOfAdministration) -> Unit,
    onMedicationNameChange: (String) -> Unit,
    onMedicationDosageChange: (String) -> Unit,
    onSaveMedicationClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }

    LaunchedEffect(uiState.isMedicationEditorSaved) {
        if (uiState.isMedicationEditorSaved) {
            hideBottomSheet(scope, sheetState) {
                onConsumeMedicationEditorSaved()
                onDismissMedicationEditor()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) {
                                R.string.edit_medication_group
                            } else {
                                R.string.add_medication_group
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            item {
                OutlinedTextField(
                    value = uiState.groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text(text = stringResource(R.string.field_medication_group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text(
                    text = stringResource(R.string.group_schedule_title)
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MedicationGroupScheduleType.entries.forEachIndexed { index, scheduleType ->
                        SegmentedButton(
                            selected = uiState.scheduleType == scheduleType,
                            onClick = { onScheduleTypeChange(scheduleType) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = MedicationGroupScheduleType.entries.size
                            )
                        ) {
                            Text(
                                text = stringResource(
                                    if (scheduleType == MedicationGroupScheduleType.WEEKLY) {
                                        R.string.group_schedule_weekly
                                    } else {
                                        R.string.group_schedule_daily
                                    }
                                )
                            )
                        }
                    }
                }
            }

            item {
                if (uiState.scheduleType == MedicationGroupScheduleType.WEEKLY) {
                    WeeklyScheduleEditor(
                        intervalWeeks = uiState.weeklyIntervalWeeks,
                        dayOfWeek = uiState.weeklyDayOfWeek,
                        time = uiState.weeklyTime,
                        appLocale = appLocale,
                        timeFormatter = timeFormatter,
                        onIntervalChange = onWeeklyIntervalChange,
                        onDayChange = onWeeklyDayChange,
                        onTimeChange = { currentTime ->
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    onWeeklyTimeChange(LocalTime.of(hourOfDay, minute))
                                },
                                currentTime.hour,
                                currentTime.minute,
                                DateFormat.is24HourFormat(context)
                            ).show()
                        }
                    )
                } else {
                    DailyScheduleEditor(
                        intervalDays = uiState.dailyIntervalDays,
                        dailyTimes = uiState.dailyTimes,
                        appLocale = appLocale,
                        timeFormatter = timeFormatter,
                        onIntervalChange = onDailyIntervalChange,
                        onAddTime = onAddDailyTime,
                        onTimeClick = { localId, currentTime ->
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    onDailyTimeChange(localId, LocalTime.of(hourOfDay, minute))
                                },
                                currentTime.hour,
                                currentTime.minute,
                                DateFormat.is24HourFormat(context)
                            ).show()
                        },
                        onRemoveTime = onRemoveDailyTime
                    )
                }
            }

            items(
                items = uiState.medications,
                key = { it.localId }
            ) { medication ->
                MedicationGroupMedicationCard(
                    medication = medication,
                    appLocale = appLocale,
                    onClick = { onMedicationClick(medication.localId) },
                    onRemoveClick = { onRemoveMedication(medication.localId) }
                )
            }

            item {
                Button(
                    onClick = onAddMedication,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.add_medication_to_group),
                        modifier = Modifier.padding(start = dimensionResource(R.dimen.padding_xsmall))
                    )
                }
            }

            item {
                uiState.errorMessageRes?.let { errorMessageRes ->
                    Text(text = stringResource(errorMessageRes))
                }
            }

            item {
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text(text = stringResource(R.string.save_medication_group))
                }
            }
        }
    }

    uiState.editingMedication?.let { medication ->
        MedicationEditorSheet(
            title = stringResource(
                if (uiState.medications.any { it.localId == medication.localId }) {
                    R.string.edit_medication
                } else {
                    R.string.add_medication_to_group
                }
            ),
            sheetState = sheetState,
            confirmButtonText = stringResource(R.string.save_medication),
            onDismissRequest = onDismissMedicationEditor,
            onCloseClick = {
                hideBottomSheet(scope, sheetState, onDismissMedicationEditor)
            },
            routeOfAdministration = medication.routeOfAdministration,
            medicineName = medication.medicineName,
            dosageMg = medication.dosageMg,
            onRouteSelected = onMedicationRouteChange,
            onMedicineNameChange = onMedicationNameChange,
            onDosageChange = onMedicationDosageChange,
            errorMessageRes = uiState.medicationEditorErrorMessageRes,
            onConfirm = onSaveMedicationClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyScheduleEditor(
    intervalWeeks: String,
    dayOfWeek: DayOfWeek,
    time: LocalTime,
    appLocale: java.util.Locale,
    timeFormatter: DateTimeFormatter,
    onIntervalChange: (String) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    var isDayMenuExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        OutlinedTextField(
            value = intervalWeeks,
            onValueChange = onIntervalChange,
            label = { Text(text = stringResource(R.string.group_schedule_every_weeks)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenuBox(
            expanded = isDayMenuExpanded,
            onExpandedChange = { isDayMenuExpanded = !isDayMenuExpanded }
        ) {
            OutlinedTextField(
                value = dayOfWeek.getDisplayName(TextStyle.FULL, appLocale),
                onValueChange = {},
                readOnly = true,
                label = { Text(text = stringResource(R.string.group_schedule_day_of_week)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDayMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = isDayMenuExpanded,
                onDismissRequest = { isDayMenuExpanded = false }
            ) {
                DayOfWeek.entries.forEach { weekday ->
                    DropdownMenuItem(
                        text = { Text(text = weekday.getDisplayName(TextStyle.FULL, appLocale)) },
                        onClick = {
                            onDayChange(weekday)
                            isDayMenuExpanded = false
                        }
                    )
                }
            }
        }

        TimeRow(
            label = stringResource(R.string.group_schedule_time),
            formattedTime = time.format(timeFormatter),
            onClick = { onTimeChange(time) }
        )
    }
}

@Composable
private fun DailyScheduleEditor(
    intervalDays: String,
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    appLocale: java.util.Locale,
    timeFormatter: DateTimeFormatter,
    onIntervalChange: (String) -> Unit,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    onRemoveTime: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        OutlinedTextField(
            value = intervalDays,
            onValueChange = onIntervalChange,
            label = { Text(text = stringResource(R.string.group_schedule_every_days)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(text = stringResource(R.string.group_schedule_times))

        dailyTimes.forEach { dailyTime ->
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                headlineContent = {
                    Text(text = dailyTime.time.format(timeFormatter))
                },
                supportingContent = {
                    Text(
                        text = stringResource(
                            R.string.group_schedule_time_item,
                            dailyTime.time.format(timeFormatter)
                        )
                    )
                },
                trailingContent = {
                    Row {
                        Text(
                            text = stringResource(R.string.edit_time),
                            modifier = Modifier
                                .clickable { onTimeClick(dailyTime.localId, dailyTime.time) }
                                .padding(dimensionResource(R.dimen.padding_xsmall))
                        )
                        IconButton(onClick = { onRemoveTime(dailyTime.localId) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove_time)
                            )
                        }
                    }
                }
            )
        }

        Button(
            onClick = onAddTime,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.add_time))
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    formattedTime: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        overlineContent = {
            Text(text = label)
        },
        headlineContent = {
            Text(text = formattedTime)
        }
    )
}

@Composable
private fun MedicationGroupMedicationCard(
    medication: MedicationGroupMedicationItemUiState,
    appLocale: java.util.Locale,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = {
            Text(text = medication.medicineName)
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.plan_group_medication_summary,
                    medication.medicineName,
                    medication.dosageMg.toDoubleOrNull()?.formatDose(appLocale) ?: medication.dosageMg,
                    stringResource(medication.routeOfAdministration.labelRes)
                )
            )
        },
        trailingContent = {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_medication_from_group)
                )
            }
        }
    )
}
