package com.mkx.hrttracker.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
fun CalibrationEditorScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val is24Hour = DateFormat.is24HourFormat(context)
    var isDatePickerVisible by rememberSaveable { mutableStateOf(false) }
    var isTimePickerVisible by rememberSaveable { mutableStateOf(false) }
    var isAddAnalyteSheetVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.consumeSavedState()
            onSaved()
        }
    }

    if (isDatePickerVisible) {
        DatePickerModal(
            onDateSelected = viewModel::updateCollectedDate,
            onDismiss = { isDatePickerVisible = false },
            initialSelectedDate = uiState.collectedDate,
        )
    }

    if (isTimePickerVisible) {
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                viewModel.updateCollectedTime(selectedTime)
                true
            },
            onDismiss = { isTimePickerVisible = false },
            initialTime = uiState.collectedTime,
            is24Hour = is24Hour,
        )
    }

    CalibrationEditorScreenContent(
        uiState = uiState,
        dateFormatter = dateFormatter,
        timeFormatter = timeFormatter,
        onNavigateBack = onNavigateBack,
        onDateClick = { isDatePickerVisible = true },
        onTimeClick = { isTimePickerVisible = true },
        onE2ValueChange = viewModel::updateE2Value,
        onE2UnitChange = viewModel::updateE2Unit,
        onAnalyteValueChange = viewModel::updateAnalyteValue,
        onAnalyteUnitChange = viewModel::updateAnalyteUnit,
        onRemoveAnalyteClick = viewModel::removeAnalyte,
        onAddAnalyteClick = { isAddAnalyteSheetVisible = true },
        onSaveClick = viewModel::save,
        modifier = modifier,
    )

    if (isAddAnalyteSheetVisible) {
        CalibrationAddAnalyteSheet(
            availableAnalytes = calibrationAdditionalAnalyteOptions(uiState),
            onDismissRequest = { isAddAnalyteSheetVisible = false },
            onAnalyteClick = { analyteKey ->
                viewModel.addAnalyte(analyteKey)
                isAddAnalyteSheetVisible = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationEditorScreenContent(
    uiState: CalibrationEditorUiState,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onNavigateBack: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onE2ValueChange: (String) -> Unit,
    onE2UnitChange: (BloodUnitKey) -> Unit,
    onAnalyteValueChange: (BloodAnalyteKey, String) -> Unit,
    onAnalyteUnitChange: (BloodAnalyteKey, BloodUnitKey) -> Unit,
    onRemoveAnalyteClick: (BloodAnalyteKey) -> Unit,
    onAddAnalyteClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSave = canSaveCalibrationEditorState(uiState) && !uiState.isLoading && !uiState.isSaving

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) {
                                R.string.settings_calibration_edit_result
                            } else {
                                R.string.settings_calibration_add_result
                            }
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSaveClick,
                        enabled = canSave,
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            ) {
                item {
                    CalibrationDateTimeCard(
                        dateLabel = uiState.collectedDate.format(dateFormatter),
                        timeLabel = uiState.collectedTime.format(timeFormatter),
                        timeZoneId = uiState.timeZoneId,
                        onDateClick = onDateClick,
                        onTimeClick = onTimeClick,
                    )
                }
                item {
                    CalibrationAnalyteCard(
                        analyteKey = uiState.e2Draft.analyteKey,
                        valueText = uiState.e2Draft.valueText,
                        unit = uiState.e2Draft.unit,
                        onValueChange = onE2ValueChange,
                        onUnitChange = onE2UnitChange,
                    )
                }
                if (uiState.additionalDrafts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_calibration_additional_results),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(
                        items = uiState.additionalDrafts,
                        key = { draft -> draft.analyteKey.storageValue },
                    ) { draft ->
                        CalibrationAnalyteCard(
                            analyteKey = draft.analyteKey,
                            valueText = draft.valueText,
                            unit = draft.unit,
                            onValueChange = { value ->
                                onAnalyteValueChange(draft.analyteKey, value)
                            },
                            onUnitChange = { unit ->
                                onAnalyteUnitChange(draft.analyteKey, unit)
                            },
                            onRemoveClick = {
                                onRemoveAnalyteClick(draft.analyteKey)
                            },
                        )
                    }
                }
                if (calibrationAdditionalAnalyteOptions(uiState).isNotEmpty()) {
                    item {
                        Button(
                            onClick = onAddAnalyteClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.settings_calibration_add_analyte),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationDateTimeCard(
    dateLabel: String,
    timeLabel: String,
    timeZoneId: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    CalibrationEditorCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Text(
                text = stringResource(R.string.settings_calibration_collected_at),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                CalibrationMetadataChip(
                    label = stringResource(R.string.settings_calibration_date_label),
                    value = dateLabel,
                    onClick = onDateClick,
                    modifier = Modifier.weight(1f),
                )
                CalibrationMetadataChip(
                    label = stringResource(R.string.settings_calibration_time_label),
                    value = timeLabel,
                    onClick = onTimeClick,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.settings_calibration_entry_timezone, timeZoneId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationMetadataChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_small)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CalibrationAnalyteCard(
    analyteKey: BloodAnalyteKey,
    valueText: String,
    unit: BloodUnitKey,
    onValueChange: (String) -> Unit,
    onUnitChange: (BloodUnitKey) -> Unit,
    onRemoveClick: (() -> Unit)? = null,
) {
    CalibrationEditorCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = calibrationAnalyteLabel(analyteKey),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                onRemoveClick?.let { removeClick ->
                    IconButton(onClick = removeClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove_time),
                        )
                    }
                }
            }
            CalibrationUnitDropdown(
                analyteKey = analyteKey,
                selectedUnit = unit,
                onUnitSelected = onUnitChange,
            )
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = stringResource(R.string.settings_calibration_value_label))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationUnitDropdown(
    analyteKey: BloodAnalyteKey,
    selectedUnit: BloodUnitKey,
    onUnitSelected: (BloodUnitKey) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allowedUnits = remember(analyteKey) {
        calibrationAllowedUnitsFor(analyteKey)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = calibrationUnitLabel(selectedUnit),
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
            readOnly = true,
            label = {
                Text(text = stringResource(R.string.settings_calibration_unit_label))
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            allowedUnits.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(text = calibrationUnitLabel(unit)) },
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CalibrationEditorCard(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationAddAnalyteSheet(
    availableAnalytes: List<BloodAnalyteKey>,
    onDismissRequest: () -> Unit,
    onAnalyteClick: (BloodAnalyteKey) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_medium),
                ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_calibration_add_analyte_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
            availableAnalytes.forEach { analyteKey ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hideBottomSheet(scope, sheetState) {
                                onAnalyteClick(analyteKey)
                            }
                        },
                    headlineContent = {
                        Text(text = calibrationAnalyteLabel(analyteKey))
                    },
                    supportingContent = {
                        Text(text = calibrationUnitLabelFor(analyteKey))
                    },
                )
            }
        }
    }
}

@Preview(
    name = "Calibration Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 920,
)
@Composable
private fun CalibrationEditorScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationEditorScreenContent(
            uiState = CalibrationEditorUiState(
                isEditing = true,
                collectedDate = LocalDate.of(2026, 4, 24),
                collectedTime = LocalTime.of(9, 30),
                timeZoneId = "Asia/Tokyo",
                e2Draft = CalibrationResultDraftUiState(
                    analyteKey = BloodAnalyteKey.E2,
                    valueText = "152.4",
                    unit = BloodUnitKey.PMOL_L,
                ),
                additionalDrafts = listOf(
                    CalibrationResultDraftUiState(
                        analyteKey = BloodAnalyteKey.T,
                        resultUuid = UUID.fromString("c047ef42-a1d6-4764-8ae8-203ef1ed54d6"),
                        valueText = "31.7",
                        unit = BloodUnitKey.NMOL_L,
                    ),
                    CalibrationResultDraftUiState(
                        analyteKey = BloodAnalyteKey.FSH,
                        resultUuid = UUID.fromString("342f9176-5346-48ea-bb6a-7e9e5837fc2f"),
                        valueText = "0.2",
                        unit = BloodUnitKey.MIU_ML,
                    ),
                ),
            ),
            dateFormatter = previewCalibrationEditorDateFormatter(),
            timeFormatter = previewCalibrationEditorTimeFormatter(),
            onNavigateBack = { },
            onDateClick = { },
            onTimeClick = { },
            onE2ValueChange = { },
            onE2UnitChange = { },
            onAnalyteValueChange = { _, _ -> },
            onAnalyteUnitChange = { _, _ -> },
            onRemoveAnalyteClick = { },
            onAddAnalyteClick = { },
            onSaveClick = { },
        )
    }
}

private fun previewCalibrationEditorDateFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH)
}

private fun previewCalibrationEditorTimeFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.ENGLISH)
}
