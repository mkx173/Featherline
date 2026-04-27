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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
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
        onBuiltinAnalyteValueChange = viewModel::updateAnalyteValue,
        onCustomAnalyteValueChange = viewModel::updateCustomAnalyteValue,
        onBuiltinAnalyteUnitChange = viewModel::updateAnalyteUnit,
        onRemoveBuiltinAnalyteClick = viewModel::removeAnalyte,
        onRemoveCustomAnalyteClick = viewModel::removeCustomAnalyte,
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
            availableAnalytes = calibrationAddAnalyteOptions(uiState),
            onDismissRequest = { isAddAnalyteSheetVisible = false },
            onAnalyteClick = { option ->
                when (option) {
                    is CalibrationAddAnalyteOption.Builtin -> {
                        viewModel.addAnalyte(option.analyteKey)
                    }

                    is CalibrationAddAnalyteOption.Custom -> {
                        viewModel.addCustomAnalyte(option.customAnalyte)
                    }
                }
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
    onBuiltinAnalyteValueChange: (BloodAnalyteKey, String) -> Unit,
    onCustomAnalyteValueChange: (UUID, String) -> Unit,
    onBuiltinAnalyteUnitChange: (BloodAnalyteKey, BloodUnitKey) -> Unit,
    onRemoveBuiltinAnalyteClick: (BloodAnalyteKey) -> Unit,
    onRemoveCustomAnalyteClick: (UUID) -> Unit,
    onAddAnalyteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSaveClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addAnalyteOptions = remember(uiState.drafts, uiState.customAnalytes) {
        calibrationAddAnalyteOptions(uiState)
    }
    val canSave = canSaveCalibrationEditorState(uiState) &&
        !uiState.isLoading &&
        !uiState.isSaving &&
        !uiState.isDeleting
    val remainingAnalyteCount = addAnalyteOptions.size
    var notesDraft by rememberSaveable { mutableStateOf(uiState.notes) }
    val analyteFocusRequesters = remember(uiState.drafts.map { it.draftKey }) {
        uiState.drafts.associate { draft ->
            draft.draftKey to FocusRequester()
        }
    }

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
                    HrtButton(
                        text = stringResource(R.string.save),
                        onClick = { onSaveClick(notesDraft) },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    )
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
                    key = { _, draft -> draft.draftKey },
                ) { index, draft ->
                    val totalCount = uiState.drafts.size

                    draft.analyteKey?.let { analyteKey ->
                        val nextFocusRequester = uiState.drafts
                            .getOrNull(index + 1)
                            ?.let { nextDraft -> analyteFocusRequesters.getValue(nextDraft.draftKey) }
                        CalibrationAnalyteCard(
                            index = index,
                            count = totalCount,
                            analyteKey = analyteKey,
                            valueText = draft.valueText,
                            isError = draft.draftKey in uiState.invalidDraftKeys,
                            unit = checkNotNull(draft.unit),
                            defaultUnit = checkNotNull(draft.defaultUnit),
                            originalUnit = draft.originalUnit,
                            focusRequester = analyteFocusRequesters.getValue(draft.draftKey),
                            nextFocusRequester = nextFocusRequester,
                            imeAction = calibrationEditorAnalyteImeAction(index, totalCount),
                            onValueChange = { value ->
                                onBuiltinAnalyteValueChange(analyteKey, value)
                            },
                            onUnitChange = { unit ->
                                onBuiltinAnalyteUnitChange(analyteKey, unit)
                            },
                            onRemoveClick = { onRemoveBuiltinAnalyteClick(analyteKey) },
                        )
                    } ?: CalibrationCustomAnalyteCard(
                        focusRequester = analyteFocusRequesters.getValue(draft.draftKey),
                        nextFocusRequester = uiState.drafts
                            .getOrNull(index + 1)
                            ?.let { nextDraft -> analyteFocusRequesters.getValue(nextDraft.draftKey) },
                        imeAction = calibrationEditorAnalyteImeAction(index, totalCount),
                        index = index,
                        count = totalCount,
                        abbreviation = checkNotNull(draft.customAnalyteAbbreviation),
                        name = checkNotNull(draft.customAnalyteName),
                        unitLabel = checkNotNull(draft.customUnitLabel),
                        valueText = draft.valueText,
                        isError = draft.draftKey in uiState.invalidDraftKeys,
                        onValueChange = { value ->
                            onCustomAnalyteValueChange(
                                checkNotNull(draft.customAnalyteUuid),
                                value,
                            )
                        },
                        onRemoveClick = {
                            onRemoveCustomAnalyteClick(checkNotNull(draft.customAnalyteUuid))
                        },
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
                        HrtFilledTonalButton(
                            text = stringResource(R.string.settings_calibration_add_analyte),
                            onClick = onAddAnalyteClick,
                            enabled = remainingAnalyteCount > 0,
                            icon = Icons.Rounded.Add,
                            iconModifier = Modifier.size(
                                ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)
                            ),
                            iconSpacing = ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight),
                            compact = true,
                            contentPadding = ButtonDefaults.contentPaddingFor(
                                ButtonDefaults.MinHeight,
                                hasStartIcon = true
                            ),
                        )
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
                        HrtButton(
                            text = stringResource(R.string.delete_entries_confirm),
                            onClick = onDeleteClick,
                            enabled = !uiState.isSaving && !uiState.isDeleting,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Delete,
                            iconModifier = Modifier.size(
                                ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)
                            ),
                            iconSpacing = ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationAddAnalyteSheet(
    availableAnalytes: List<CalibrationAddAnalyteOption>,
    onDismissRequest: () -> Unit,
    onAnalyteClick: (CalibrationAddAnalyteOption) -> Unit,
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
    availableAnalytes: List<CalibrationAddAnalyteOption>,
    onDismissRequest: () -> Unit,
    onAnalyteClick: (CalibrationAddAnalyteOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = dimensionResource(R.dimen.padding_large),
                end = dimensionResource(R.dimen.padding_large),
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
            HrtFilledTonalButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        availableAnalytes.forEachIndexed { index, option ->
            val title = when (option) {
                is CalibrationAddAnalyteOption.Builtin -> {
                    stringResource(calibrationAnalyteFullNameRes(option.analyteKey))
                }

                is CalibrationAddAnalyteOption.Custom -> option.customAnalyte.name
            }
            val supportingText = when (option) {
                is CalibrationAddAnalyteOption.Builtin -> {
                    val unitsLabel = calibrationAllowedUnitsFor(option.analyteKey).joinToString(
                        separator = " · ",
                    ) { unit -> calibrationUnitLabel(unit) }
                    "${calibrationAnalyteLabel(option.analyteKey)} - $unitsLabel"
                }

                is CalibrationAddAnalyteOption.Custom -> buildString {
                    append(option.customAnalyte.abbreviation)
                    append(" - ")
                    append(option.customAnalyte.unitLabel)
                }
            }
            val leadingIconVector = when (option) {
                is CalibrationAddAnalyteOption.Builtin -> Icons.Rounded.WaterDrop
                is CalibrationAddAnalyteOption.Custom -> Icons.Rounded.Edit
            }

            EditorSegmentedListItem(
                index = index,
                count = availableAnalytes.size,
                onClick = { onAnalyteClick(option) },
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                leadingContent = {
                    Icon(
                        imageVector = leadingIconVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = {
                    Text(text = supportingText)
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ) {
                Text(
                    text = title,
                    modifier = Modifier.cjkTextOffset(title)
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
                    CalibrationResultDraftUiState(
                        customAnalyteUuid = UUID.fromString("6e7d06a8-f0a9-46b3-9e75-dd5dbe4bc45c"),
                        customAnalyteAbbreviation = "DHT",
                        customAnalyteName = "DHT",
                        customUnitLabel = "ng/dL",
                        valueText = "18.4",
                    ),
                ),
                customAnalytes = previewCalibrationCustomAnalytes(),
            ),
            dateFormatter = previewCalibrationEditorDateFormatter(),
            timeFormatter = previewCalibrationEditorTimeFormatter(),
            onNavigateBack = { },
            onDateClick = { },
            onTimeClick = { },
            onNotesCommit = { _ -> },
            onBuiltinAnalyteValueChange = { _, _ -> },
            onCustomAnalyteValueChange = { _, _ -> },
            onBuiltinAnalyteUnitChange = { _, _ -> },
            onRemoveBuiltinAnalyteClick = { },
            onRemoveCustomAnalyteClick = { },
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
                        CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.T),
                        CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.PROG),
                        CalibrationAddAnalyteOption.Custom(
                            CustomBloodAnalyte(
                                uuid = UUID.fromString("6e7d06a8-f0a9-46b3-9e75-dd5dbe4bc45c"),
                                abbreviation = "DHT",
                                name = "DHT",
                                unitLabel = "ng/dL",
                                createdAt = java.time.Instant.parse("2026-04-24T00:30:00Z"),
                                updatedAt = java.time.Instant.parse("2026-04-24T00:30:00Z"),
                                archivedAt = null,
                            )
                        ),
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

private fun previewCalibrationCustomAnalytes(): List<CustomBloodAnalyte> {
    return listOf(
        CustomBloodAnalyte(
            uuid = UUID.fromString("6e7d06a8-f0a9-46b3-9e75-dd5dbe4bc45c"),
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            createdAt = java.time.Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = java.time.Instant.parse("2026-04-24T00:30:00Z"),
            archivedAt = null,
        )
    )
}
