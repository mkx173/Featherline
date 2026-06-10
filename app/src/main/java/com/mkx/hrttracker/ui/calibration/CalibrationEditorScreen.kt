package com.mkx.hrttracker.ui.calibration

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeBottomSheetSurface
import com.mkx.hrttracker.ui.components.HazeTopAppBarColorReset
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.hazeBottomSheetContainerColor
import com.mkx.hrttracker.ui.components.hazeBottomSheetContentWindowInsets
import com.mkx.hrttracker.ui.components.hazeTopAppBarColors
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.hazeTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.dismissInputAndRun
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.calibrationUnitLabel
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.formatEditorZoneLabel
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberUses24HourTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val is24Hour = rememberUses24HourTimeFormat()
    var isDatePickerVisible by rememberSaveable { mutableStateOf(false) }
    var isTimePickerVisible by rememberSaveable { mutableStateOf(false) }
    var addAnalyteSheetOptions by remember {
        mutableStateOf<List<CalibrationAddAnalyteOption>?>(null)
    }
    var isDeleteDialogVisible by rememberSaveable { mutableStateOf(false) }
    val saveEntryFailureMessage =
        stringResource(R.string.settings_calibration_save_entry_failure)
    val deleteEntryFailureMessage =
        stringResource(R.string.settings_calibration_delete_entry_failure)
    val crossZoneSavedFormat = stringResource(R.string.cross_timezone_saved_toast)

    LaunchedEffect(uiState.savedCrossZoneZoneText) {
        val zoneText = uiState.savedCrossZoneZoneText ?: return@LaunchedEffect
        Toast.makeText(
            context,
            crossZoneSavedFormat.format(zoneText),
            Toast.LENGTH_SHORT,
        ).show()
        viewModel.consumeCrossZoneToast()
    }

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

    LaunchedEffect(uiState.saveEntryResult) {
        when (uiState.saveEntryResult) {
            CalibrationSaveEntryResult.FAILURE -> {
                Toast.makeText(
                    context,
                    saveEntryFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeSaveEntryResult()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.deleteEntryResult) {
        when (uiState.deleteEntryResult) {
            CalibrationDeleteEntryResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteEntryFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeDeleteEntryResult()
            }

            null -> Unit
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
        HazeAlertDialog(
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
                        if (uiState.isDeleting) return@TextButton
                        isDeleteDialogVisible = false
                        viewModel.delete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete_entries_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isDeleteDialogVisible = false },
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
        onAddAnalyteClick = {
            dismissInputAndRun(
                focusManager = focusManager,
                keyboardController = keyboardController,
            ) {
                addAnalyteSheetOptions = calibrationAddAnalyteOptions(uiState)
            }
        },
        onDeleteClick = {
            if (isCalibrationEditorBusy(uiState)) return@CalibrationEditorScreenContent
            isDeleteDialogVisible = true
        },
        onSaveClick = { notes ->
            viewModel.updateNotes(notes)
            viewModel.save()
        },
        modifier = modifier,
    )

    addAnalyteSheetOptions?.let { availableAnalytes ->
        CalibrationAddAnalyteSheet(
            availableAnalytes = availableAnalytes,
            onDismissRequest = { addAnalyteSheetOptions = null },
            onAnalyteClick = { option ->
                when (option) {
                    is CalibrationAddAnalyteOption.Builtin -> {
                        viewModel.addAnalyte(option.analyteKey)
                    }

                    is CalibrationAddAnalyteOption.Custom -> {
                        viewModel.addCustomAnalyte(option.customAnalyte)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationEditorScreenContent(
    uiState: CalibrationEditorUiState,
    dateFormatter: LocalDateFormatter,
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
    // Form-validity gate only. Busy state keeps the save button visually
    // normal; the click handler no-ops while ROOM is writing.
    val canSave = canSaveCalibrationEditorState(uiState)
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

    val scrollState = rememberScrollState()
    val contentFocusManager = LocalFocusManager.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        scrollState = scrollState,
        state = topAppBarState
    )
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBarColorReset {
                TopAppBar(
                    modifier = Modifier
                        .topAppBarScrollToTop(scrollBehavior, scrollState)
                        .hazeTopAppBar(scrollBehavior),
                    title = {
                        val title = stringResource(
                            if (uiState.isEditing) {
                                R.string.settings_calibration_edit_result
                            } else {
                                R.string.settings_calibration_add_result
                            }
                        )
                        Text(
                            text = title,
                            modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                        )
                    },
                    colors = hazeTopAppBarColors(),
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
                            onClick = {
                                if (isCalibrationEditorBusy(uiState)) return@HrtButton
                                onSaveClick(notesDraft)
                            },
                            enabled = canSave,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(scrollState)
                        .padding(appContentPaddingValuesBehindTopAppBar(innerPadding)),
                ) {
                    val deviceZone = remember { ZoneId.systemDefault() }
                    val itemLocale = rememberAppLocale()
                    val crossZoneLabel = remember(
                        uiState.collectedDate,
                        uiState.collectedTime,
                        uiState.collectedZoneId,
                        deviceZone,
                        itemLocale,
                    ) {
                        val pickerInstant = LocalDateTime
                            .of(uiState.collectedDate, uiState.collectedTime)
                            .atZone(uiState.collectedZoneId)
                            .toInstant()
                        formatEditorZoneLabel(
                            appliedZoneId = uiState.collectedZoneId,
                            appliedAtInstant = pickerInstant,
                            deviceZone = deviceZone,
                            locale = itemLocale,
                        )
                    }
                    CalibrationDateTimeCard(
                        dateLabel = dateFormatter(uiState.collectedDate),
                        timeLabel = uiState.collectedTime.format(timeFormatter),
                        timeSinceLastEstradiolDoseMillis = uiState.timeSinceLastEstradiolDoseMillis,
                        onDateClick = onDateClick,
                        onTimeClick = onTimeClick,
                        crossZoneLabel = crossZoneLabel,
                    )

                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                    val totalCount = uiState.drafts.size
                    HrtSection(title = stringResource(R.string.settings_calibration_results)) {
                        uiState.drafts.forEachIndexed { index, draft ->
                            val nextDraft = uiState.drafts.getOrNull(index + 1)
                            val nextFocusRequester = nextDraft
                                ?.let { analyteFocusRequesters.getValue(it.draftKey) }
                            // All drafts stay composed in a regular Column, so the next
                            // field's FocusRequester is always attached when IME Next
                            // fires. Direct requestFocus is sufficient.
                            val onImeNext: () -> Unit = if (nextFocusRequester != null) {
                                { nextFocusRequester.requestFocus() }
                            } else {
                                { contentFocusManager.clearFocus() }
                            }

                            item {
                                draft.analyteKey?.let { analyteKey ->
                                    CalibrationAnalyteCard(
                                        analyteKey = analyteKey,
                                        valueText = draft.valueText,
                                        isError = draft.draftKey in uiState.invalidDraftKeys,
                                        unit = checkNotNull(draft.unit),
                                        defaultUnit = checkNotNull(draft.defaultUnit),
                                        originalUnit = draft.originalUnit,
                                        focusRequester = analyteFocusRequesters.getValue(draft.draftKey),
                                        nextFocusRequester = nextFocusRequester,
                                        imeAction = calibrationEditorAnalyteImeAction(
                                            index,
                                            totalCount
                                        ),
                                        onImeNext = onImeNext,
                                        onValueChange = { value ->
                                            onBuiltinAnalyteValueChange(analyteKey, value)
                                        },
                                        onUnitChange = { unit ->
                                            onBuiltinAnalyteUnitChange(analyteKey, unit)
                                        },
                                        onRemoveClick = { onRemoveBuiltinAnalyteClick(analyteKey) },
                                        hideReferenceRanges = uiState.hideReferenceRanges,
                                    )
                                } ?: CalibrationCustomAnalyteCard(
                                    focusRequester = analyteFocusRequesters.getValue(draft.draftKey),
                                    nextFocusRequester = nextFocusRequester,
                                    imeAction = calibrationEditorAnalyteImeAction(
                                        index,
                                        totalCount
                                    ),
                                    onImeNext = onImeNext,
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
                                        onRemoveCustomAnalyteClick(
                                            checkNotNull(draft.customAnalyteUuid)
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimensionResource(R.dimen.padding_small))
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
                        )
                    }

                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                    HrtSection(title = stringResource(R.string.settings_calibration_notes_label)) {
                        item {
                            CalibrationNotesCard(
                                notes = notesDraft,
                                // Commit live (not just on focus loss) so the ViewModel's
                                // persisted draft snapshot captures in-progress notes for
                                // process-death restore, matching the analyte value fields.
                                onNotesChange = {
                                    notesDraft = it
                                    onNotesCommit(it)
                                },
                                onNotesCommit = { onNotesCommit(notesDraft) },
                            )
                        }
                    }

                    if (uiState.isEditing) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                        HrtFilledTonalButton(
                            text = stringResource(R.string.delete_entries_confirm),
                            onClick = onDeleteClick,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Delete,
                            iconModifier = Modifier.size(
                                ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)
                            ),
                            iconSpacing = ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight),
                        )
                    }

                    if (!uiState.hideReferenceRanges) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                        MedicalDisclaimerText(kinds = MedicalDisclaimerSets.calibrationEditor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalibrationAddAnalyteSheet(
    availableAnalytes: List<CalibrationAddAnalyteOption>,
    onDismissRequest: () -> Unit,
    onAnalyteClick: (CalibrationAddAnalyteOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = hazeBottomSheetContainerColor(),
        dragHandle = null,
        contentWindowInsets = { hazeBottomSheetContentWindowInsets() },
    ) {
        HazeBottomSheetSurface {
            CalibrationAddAnalyteSheetContent(
                availableAnalytes = availableAnalytes,
                onDismissRequest = {
                    hideBottomSheet(scope, sheetState, onDismissRequest)
                },
                onAnalyteClick = { option ->
                    onAnalyteClick(option)
                    hideBottomSheet(scope, sheetState, onDismissRequest)
                },
            )
        }
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
                bottom = dimensionResource(R.dimen.padding_large),
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
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
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

            PreferenceSegmentedListItem(
                title = title,
                supportingText = supportingText,
                index = index,
                count = availableAnalytes.size,
                onClick = { onAnalyteClick(option) },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleColor = MaterialTheme.colorScheme.onSurface,
                leadingContent = {
                    Icon(
                        imageVector = leadingIconVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
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
                                createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                                updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
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

private fun previewCalibrationEditorDateFormatter(): LocalDateFormatter {
    return dateLabelFormatter(
        locale = Locale.ENGLISH,
        today = LocalDate.of(2026, 4, 24),
    )
}

private fun previewCalibrationEditorTimeFormatter(): DateTimeFormatter {
    return localizedShortTimeFormatter(Locale.ENGLISH, uses24HourFormat = false)
}

private fun previewCalibrationCustomAnalytes(): List<CustomBloodAnalyte> {
    return listOf(
        CustomBloodAnalyte(
            uuid = UUID.fromString("6e7d06a8-f0a9-46b3-9e75-dd5dbe4bc45c"),
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            createdAt = Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
            archivedAt = null,
        )
    )
}
