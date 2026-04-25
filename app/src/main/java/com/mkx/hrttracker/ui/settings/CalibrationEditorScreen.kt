package com.mkx.hrttracker.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
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
    var isDeleteDialogVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.consumeSavedState()
            onSaved()
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            viewModel.consumeDeletedState()
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

    if (isDeleteDialogVisible && uiState.isEditing) {
        AlertDialog(
            onDismissRequest = { isDeleteDialogVisible = false },
            title = {
                Text(text = stringResource(R.string.delete_entry_title))
            },
            text = {
                Text(text = stringResource(R.string.delete_editing_entry_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteDialogVisible = false
                        viewModel.delete()
                    },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isDeleteDialogVisible = false },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    CalibrationEditorScreenContent(
        uiState = uiState,
        dateFormatter = dateFormatter,
        timeFormatter = timeFormatter,
        onNavigateBack = onNavigateBack,
        onDateClick = { isDatePickerVisible = true },
        onTimeClick = { isTimePickerVisible = true },
        onNotesCommit = viewModel::updateNotes,
        onAnalyteValueChange = viewModel::updateAnalyteValue,
        onAnalyteUnitChange = viewModel::updateAnalyteUnit,
        onRemoveAnalyteClick = viewModel::removeAnalyte,
        onAddAnalyteClick = { isAddAnalyteSheetVisible = true },
        onDeleteClick = { isDeleteDialogVisible = true },
        onSaveClick = { notes ->
            viewModel.updateNotes(notes)
            viewModel.save()
        },
        modifier = modifier,
    )

    if (isAddAnalyteSheetVisible) {
        CalibrationAddAnalyteSheet(
            availableAnalytes = calibrationAnalyteOptions(uiState),
            onDismissRequest = { isAddAnalyteSheetVisible = false },
            onAnalyteClick = { analyteKey ->
                viewModel.addAnalyte(analyteKey)
                isAddAnalyteSheetVisible = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationEditorScreenContent(
    uiState: CalibrationEditorUiState,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onNavigateBack: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNotesCommit: (String) -> Unit,
    onAnalyteValueChange: (BloodAnalyteKey, String) -> Unit,
    onAnalyteUnitChange: (BloodAnalyteKey, BloodUnitKey) -> Unit,
    onRemoveAnalyteClick: (BloodAnalyteKey) -> Unit,
    onAddAnalyteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSaveClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSave = canSaveCalibrationEditorState(uiState) &&
        !uiState.isLoading &&
        !uiState.isSaving &&
        !uiState.isDeleting
    val remainingAnalyteCount = calibrationAnalyteOptions(uiState).size
    var notesDraft by rememberSaveable { mutableStateOf(uiState.notes) }

    LaunchedEffect(uiState.panelUuid, uiState.isLoading) {
        if (!uiState.isLoading) {
            notesDraft = uiState.notes
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { onSaveClick(notesDraft) },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                },
                scrollBehavior = scrollBehavior
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
            ) {
                item {
                    CalibrationDateTimeCard(
                        dateLabel = uiState.collectedDate.format(dateFormatter),
                        timeLabel = uiState.collectedTime.format(timeFormatter),
                        timeSinceLastEstradiolDoseMillis = uiState.timeSinceLastEstradiolDoseMillis,
                        onDateClick = onDateClick,
                        onTimeClick = onTimeClick,
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                    Text(
                        text = stringResource(R.string.settings_calibration_results).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp, top = 4.dp),
                    )
                }
                itemsIndexed(
                    items = uiState.drafts,
                    key = { _, draft -> draft.analyteKey.storageValue },
                ) { index, draft ->
                    val totalCount = uiState.drafts.size

                    CalibrationAnalyteCard(
                        index = index,
                        count = totalCount,
                        analyteKey = draft.analyteKey,
                        valueText = draft.valueText,
                        unit = draft.unit,
                        defaultUnit = draft.defaultUnit,
                        originalUnit = draft.originalUnit,
                        onValueChange = { value ->
                            onAnalyteValueChange(draft.analyteKey, value)
                        },
                        onUnitChange = { unit ->
                            onAnalyteUnitChange(draft.analyteKey, unit)
                        },
                        onRemoveClick = { onRemoveAnalyteClick(draft.analyteKey) },
                    )

                    if (index < totalCount - 1) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = dimensionResource(R.dimen.padding_small))
                    ) {
                        Text(
                            text = stringResource(
                                R.string.settings_calibration_remaining_analytes,
                                remainingAnalyteCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = onAddAnalyteClick,
                            enabled = remainingAnalyteCount > 0,
                            contentPadding =
                                ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight))
                            )
                            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
                            Text(
                                text = stringResource(R.string.settings_calibration_add_analyte),
                            )
                        }
                    }

                }
                item {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                    Text(
                        text = stringResource(R.string.settings_calibration_notes_label).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp),
                    )
                    CalibrationNotesCard(
                        notes = notesDraft,
                        onNotesChange = { notesDraft = it },
                        onNotesCommit = { onNotesCommit(notesDraft) },
                    )
                }
                if (uiState.isEditing) {
                    item {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                        Button(
                            onClick = onDeleteClick,
                            enabled = !uiState.isSaving && !uiState.isDeleting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                            )
                            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
                            Text(text = stringResource(R.string.delete_entries_confirm))
                        }
                    }
                }
            }
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
        CalibrationAddAnalyteSheetContent(
            availableAnalytes = availableAnalytes,
            onDismissRequest = onDismissRequest,
            onAnalyteClick = { analyteKey ->
                hideBottomSheet(scope, sheetState) {
                    onAnalyteClick(analyteKey)
                }
            },
        )
    }
}

@Composable
private fun CalibrationAddAnalyteSheetContent(
    availableAnalytes: List<BloodAnalyteKey>,
    onDismissRequest: () -> Unit,
    onAnalyteClick: (BloodAnalyteKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = dimensionResource(R.dimen.padding_medium),
                end = dimensionResource(R.dimen.padding_medium),
                bottom = dimensionResource(R.dimen.padding_medium),
            ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_calibration_add_analyte),
                style = MaterialTheme.typography.titleLarge,
            )
            FilledTonalButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cancel))
            }
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        availableAnalytes.forEachIndexed { index, analyteKey ->
            val unitsLabel = calibrationAllowedUnitsFor(analyteKey).joinToString(
                separator = " · ",
            ) { unit -> calibrationUnitLabel(unit) }

            EditorSegmentedListItem(
                index = index,
                count = availableAnalytes.size,
                onClick = { onAnalyteClick(analyteKey) },
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = {
                    Text(text = "${calibrationAnalyteLabel(analyteKey)} - $unitsLabel")
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ) {
                Text(text = stringResource(calibrationAnalyteFullNameRes(analyteKey)))
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
                timeSinceLastEstradiolDoseMillis = 34_200_000L,
                notes = "Trough draw before morning dose.",
                drafts = listOf(
                    CalibrationResultDraftUiState(
                        analyteKey = BloodAnalyteKey.E2,
                        valueText = "152.4",
                        unit = BloodUnitKey.PMOL_L,
                    ),
                    CalibrationResultDraftUiState(
                        analyteKey = BloodAnalyteKey.T,
                        resultUuid = UUID.fromString("c047ef42-a1d6-4764-8ae8-203ef1ed54d6"),
                        valueText = "31.7",
                        unit = BloodUnitKey.NMOL_L,
                    ),
                ),
            ),
            dateFormatter = previewCalibrationEditorDateFormatter(),
            timeFormatter = previewCalibrationEditorTimeFormatter(),
            onNavigateBack = { },
            onDateClick = { },
            onTimeClick = { },
            onNotesCommit = { _ -> },
            onAnalyteValueChange = { _, _ -> },
            onAnalyteUnitChange = { _, _ -> },
            onRemoveAnalyteClick = { },
            onAddAnalyteClick = { },
            onDeleteClick = { },
            onSaveClick = { _ -> },
        )
    }
}

@Preview(
    name = "Calibration Add Analyte Sheet",
    showBackground = true,
    widthDp = 420,
    heightDp = 520,
)
@Composable
private fun CalibrationAddAnalyteSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                CalibrationAddAnalyteSheetContent(
                    availableAnalytes = listOf(
                        BloodAnalyteKey.T,
                        BloodAnalyteKey.PROG,
                        BloodAnalyteKey.PRL,
                    ),
                    onDismissRequest = { },
                    onAnalyteClick = { },
                )
            }
        }
    }
}

private fun previewCalibrationEditorDateFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH)
}

private fun previewCalibrationEditorTimeFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.ENGLISH)
}
