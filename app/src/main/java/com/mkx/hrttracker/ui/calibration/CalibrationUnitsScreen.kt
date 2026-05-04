package com.mkx.hrttracker.ui.calibration

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationUnitsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationUnitsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalibrationUnitsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onUnitChange = viewModel::setCalibrationDefaultUnit,
        onSaveCustomAnalyte = viewModel::saveCustomAnalyte,
        onArchiveCustomAnalyte = viewModel::archiveCustomAnalyte,
        onArchiveCustomAnalyteResultConsumed = viewModel::consumeArchiveCustomAnalyteResult,
        onCheckHasResultsForCustomAnalyte = viewModel::hasResultsForCustomAnalyte,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationUnitsScreenContent(
    uiState: CalibrationUnitsUiState,
    onNavigateBack: () -> Unit,
    onUnitChange: (BloodAnalyteKey, BloodUnitKey) -> Unit,
    onSaveCustomAnalyte: suspend (UUID?, String, String, String) -> Throwable?,
    onArchiveCustomAnalyte: (UUID) -> Unit,
    onArchiveCustomAnalyteResultConsumed: () -> Unit,
    onCheckHasResultsForCustomAnalyte: suspend (UUID) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listSegmentGap = dimensionResource(R.dimen.list_segment_gap)
    val sectionSpacing = dimensionResource(R.dimen.padding_medium)
    val archiveSuccessMessage =
        stringResource(R.string.settings_calibration_custom_analyte_archive_success)
    val archiveFailureMessage =
        stringResource(R.string.settings_calibration_custom_analyte_error_archive)
    var isCustomAnalyteDialogVisible by rememberSaveable { mutableStateOf(false) }
    var customAnalyteDialogAnalyteId by rememberSaveable { mutableStateOf<String?>(null) }
    var customAnalyteDialogSessionId by rememberSaveable { mutableIntStateOf(0) }
    val editingCustomAnalyte = remember(uiState.customAnalytes, customAnalyteDialogAnalyteId) {
        customAnalyteDialogAnalyteId?.let { analyteId ->
            uiState.customAnalytes.firstOrNull { it.uuid.toString() == analyteId }
        }
    }

    fun openCustomAnalyteDialog(customAnalyteId: String?) {
        customAnalyteDialogAnalyteId = customAnalyteId
        customAnalyteDialogSessionId += 1
        isCustomAnalyteDialogVisible = true
    }

    fun dismissCustomAnalyteDialog() {
        isCustomAnalyteDialogVisible = false
        customAnalyteDialogAnalyteId = null
    }

    LaunchedEffect(uiState.archiveCustomAnalyteResult) {
        when (uiState.archiveCustomAnalyteResult) {
            CalibrationArchiveCustomAnalyteResult.SUCCESS -> {
                Toast.makeText(
                    context,
                    archiveSuccessMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                dismissCustomAnalyteDialog()
                onArchiveCustomAnalyteResultConsumed()
            }

            CalibrationArchiveCustomAnalyteResult.FAILURE -> {
                Toast.makeText(
                    context,
                    archiveFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onArchiveCustomAnalyteResultConsumed()
            }

            null -> Unit
        }
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(R.string.settings_calibration_settings)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-2).dp),
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
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
        ) {
            item(key = "builtin-header") {
                CalibrationSettingsSectionHeader(
                    title = stringResource(R.string.settings_calibration_builtin_analytes),
                )
            }

            item(key = "builtin-list") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(listSegmentGap),
                ) {
                    calibrationAnalytes.forEachIndexed { index, analyteKey ->
                        CalibrationUnitPreferenceItem(
                            analyteKey = analyteKey,
                            selectedUnit = defaultCalibrationUnitFor(analyteKey, uiState.settingsState),
                            index = index,
                            count = calibrationAnalytes.size,
                            onUnitChange = { unit -> onUnitChange(analyteKey, unit) },
                        )
                    }
                }
            }

            item(key = "custom-header") {
                Spacer(modifier = Modifier.height(sectionSpacing))
                CalibrationSettingsSectionHeader(
                    title = stringResource(R.string.settings_calibration_custom_analytes),
                )
            }

            when {
                uiState.isLoadingCustomAnalytes -> {
                    item(key = "custom-loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = sectionSpacing),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                uiState.customAnalytes.isEmpty() -> {
                    item(key = "custom-empty") {
                        SupportMessageListItem(
                            text = stringResource(R.string.settings_calibration_custom_analytes_empty),
                            painter = painterResource(R.drawable.ic_info),
                            index = 0,
                            count = 1,
                        )
                    }
                }

                else -> {
                    item(key = "custom-list") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(listSegmentGap),
                        ) {
                            uiState.customAnalytes.forEachIndexed { index, analyte ->
                                CalibrationCustomAnalyteItem(
                                    customAnalyte = analyte,
                                    index = index,
                                    count = uiState.customAnalytes.size,
                                    onClick = {
                                        openCustomAnalyteDialog(
                                            customAnalyteId = analyte.uuid.toString()
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                HrtButton(
                    text = stringResource(R.string.add),
                    onClick = { openCustomAnalyteDialog(customAnalyteId = null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    icon = Icons.Rounded.Add,
                    iconModifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                    iconSpacing = ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight),
                    compact = true,
                )
            }
        }
    }

    if (isCustomAnalyteDialogVisible) {
        key(customAnalyteDialogSessionId) {
            CalibrationCustomAnalyteDialog(
                customAnalyte = editingCustomAnalyte,
                onSave = onSaveCustomAnalyte,
                onArchive = onArchiveCustomAnalyte,
                onCheckHasResults = onCheckHasResultsForCustomAnalyte,
                isArchiving = uiState.isArchivingCustomAnalyte,
                onDismiss = ::dismissCustomAnalyteDialog,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationSettingsSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalibrationUnitPreferenceItem(
    analyteKey: BloodAnalyteKey,
    selectedUnit: BloodUnitKey,
    index: Int,
    count: Int,
    onUnitChange: (BloodUnitKey) -> Unit,
) {
    val title = stringResource(calibrationAnalyteFullNameRes(analyteKey))
    PreferenceSegmentedListItem(
        title = title,
        index = index,
        count = count,
        onClick = {},
        titleTextStyle = MaterialTheme.typography.labelLarge,
        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContent = {
            ConnectedButtonGroup(
                options = calibrationAllowedUnitsFor(analyteKey),
                selectedOption = selectedUnit,
                optionLabel = { unit -> calibrationUnitLabel(unit) },
                onOptionSelected = onUnitChange,
                layout = ConnectedButtonGroupLayout.ROW,
                applyCjkTextOffset = false
            )
        },
    )
}

@Composable
private fun CalibrationCustomAnalyteItem(
    customAnalyte: CustomBloodAnalyte,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val supportingText = buildString {
        append(customAnalyte.abbreviation)
        append(" - ")
        append(customAnalyte.unitLabel)
    }
    PreferenceSegmentedListItem(
        title = customAnalyte.name,
        supportingText = supportingText,
        index = index,
        count = count,
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    )
}

@Composable
private fun CalibrationCustomAnalyteDialog(
    customAnalyte: CustomBloodAnalyte?,
    onSave: suspend (UUID?, String, String, String) -> Throwable?,
    onArchive: (UUID) -> Unit,
    onCheckHasResults: suspend (UUID) -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isArchiving: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val duplicateErrorMessage =
        stringResource(R.string.settings_calibration_custom_analyte_error_duplicate)
    val genericSaveErrorMessage =
        stringResource(R.string.settings_calibration_custom_analyte_error_save)
    val unitLockedToastMessage =
        stringResource(R.string.settings_calibration_custom_analyte_unit_locked_toast)
    var abbreviationText by rememberSaveable { mutableStateOf(customAnalyte?.abbreviation.orEmpty()) }
    var nameText by rememberSaveable { mutableStateOf(customAnalyte?.name.orEmpty()) }
    var unitText by rememberSaveable { mutableStateOf(customAnalyte?.unitLabel.orEmpty()) }
    var isAbbreviationErrorVisible by rememberSaveable { mutableStateOf(false) }
    var isNameErrorVisible by rememberSaveable { mutableStateOf(false) }
    var isUnitErrorVisible by rememberSaveable { mutableStateOf(false) }
    var actionErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var isArchiveConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var isUnitLocked by rememberSaveable { mutableStateOf(customAnalyte != null) }
    val abbreviationFocusRequester = remember { FocusRequester() }
    val unitFocusRequester = remember { FocusRequester() }
    val isBusy = isWorking || isArchiving

    LaunchedEffect(customAnalyte?.uuid) {
        isUnitLocked = customAnalyte?.uuid?.let { onCheckHasResults(it) } ?: false
    }

    val submit = submit@{
        val trimmedAbbreviation = abbreviationText.trim()
        val trimmedName = nameText.trim()
        val trimmedUnit = unitText.trim()
        val isAbbreviationBlank = trimmedAbbreviation.isEmpty()
        val isNameBlank = trimmedName.isEmpty()
        val isUnitBlank = trimmedUnit.isEmpty()
        isAbbreviationErrorVisible = isAbbreviationBlank
        isNameErrorVisible = isNameBlank
        isUnitErrorVisible = isUnitBlank
        actionErrorMessage = null
        if (isAbbreviationBlank || isNameBlank || isUnitBlank) {
            return@submit
        }

        coroutineScope.launch {
            isWorking = true
            val error = onSave(
                customAnalyte?.uuid,
                trimmedAbbreviation,
                trimmedName,
                trimmedUnit,
            )
            isWorking = false
            if (error == null) {
                onDismiss()
            } else {
                actionErrorMessage = resolveCustomAnalyteSaveErrorMessage(
                    error = error,
                    duplicateErrorMessage = duplicateErrorMessage,
                    genericErrorMessage = genericSaveErrorMessage,
                )
            }
        }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = {
            if (!isBusy) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(
                    if (customAnalyte == null) {
                        R.string.settings_calibration_custom_analyte_add_title
                    } else {
                        R.string.settings_calibration_custom_analyte_edit_title
                    }
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.padding_small)
                )
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = nameText,
                    onValueChange = { value ->
                        nameText = value
                        isNameErrorVisible = false
                        actionErrorMessage = null
                    },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.settings_calibration_custom_analyte_name_label
                            )
                        )
                    },
                    singleLine = true,
                    isError = isNameErrorVisible,
                    supportingText = if (isNameErrorVisible) {
                        {
                            Text(
                                text = stringResource(
                                    R.string.settings_calibration_custom_analyte_error_required_name
                                )
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { abbreviationFocusRequester.requestFocus() },
                    ),
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(abbreviationFocusRequester),
                    value = abbreviationText,
                    onValueChange = { value ->
                        abbreviationText = value
                        isAbbreviationErrorVisible = false
                        actionErrorMessage = null
                    },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.settings_calibration_custom_analyte_abbreviation_label
                            )
                        )
                    },
                    singleLine = true,
                    isError = isAbbreviationErrorVisible,
                    supportingText = if (isAbbreviationErrorVisible) {
                        {
                            Text(
                                text = stringResource(
                                    R.string.settings_calibration_custom_analyte_error_required_abbreviation
                                )
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (isUnitLocked) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { unitFocusRequester.requestFocus() },
                        onDone = {
                            if (!isBusy) {
                                submit()
                            }
                        },
                    ),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(unitFocusRequester),
                        value = unitText,
                        onValueChange = { value ->
                            unitText = value
                            isUnitErrorVisible = false
                            actionErrorMessage = null
                        },
                        enabled = !isUnitLocked,
                        label = {
                            Text(text = stringResource(R.string.settings_calibration_unit_label))
                        },
                        singleLine = true,
                        isError = isUnitErrorVisible,
                        supportingText = if (isUnitErrorVisible) {
                            {
                                Text(
                                    text = stringResource(
                                        R.string.settings_calibration_custom_analyte_error_required_unit
                                    )
                                )
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isBusy) {
                                    submit()
                                }
                            },
                        ),
                    )
                    if (isUnitLocked) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    Toast.makeText(
                                        context,
                                        unitLockedToastMessage,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        )
                    }
                }
                actionErrorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                customAnalyte?.let { existingAnalyte ->
                    TextButton(
                        enabled = !isBusy,
                        onClick = { isArchiveConfirmationVisible = true },
                    ) {
                        Text(
                            text = stringResource(R.string.archive),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    enabled = !isBusy,
                    onClick = onDismiss,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
                TextButton(
                    enabled = !isBusy,
                    onClick = { submit() },
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        },
    )

    if (isArchiveConfirmationVisible && customAnalyte != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    isArchiveConfirmationVisible = false
                }
            },
            title = {
                Text(
                    text = stringResource(
                        R.string.settings_calibration_custom_analyte_archive_title
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_calibration_custom_analyte_archive_message,
                        customAnalyte.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        isArchiveConfirmationVisible = false
                        onArchive(customAnalyte.uuid)
                    },
                ) {
                    Text(text = stringResource(R.string.archive))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { isArchiveConfirmationVisible = false },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun resolveCustomAnalyteSaveErrorMessage(
    error: Throwable,
    duplicateErrorMessage: String,
    genericErrorMessage: String,
): String {
    return if (error.message?.contains("same name and unit", ignoreCase = true) == true) {
        duplicateErrorMessage
    } else {
        genericErrorMessage
    }
}

@Preview(
    name = "Calibration Settings",
    showBackground = true,
    widthDp = 420,
    heightDp = 920,
)
@Composable
private fun CalibrationUnitsScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationUnitsScreenContent(
            uiState = CalibrationUnitsUiState(
                settingsState = SettingsState(
                    calibrationDefaultUnits = mapOf(
                        BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                        BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
                        BloodAnalyteKey.PRL to BloodUnitKey.MIU_L,
                    )
                ),
                customAnalytes = previewCustomAnalytes(),
            ),
            onNavigateBack = {},
            onUnitChange = { _, _ -> },
            onSaveCustomAnalyte = { _, _, _, _ -> null },
            onArchiveCustomAnalyte = { },
            onArchiveCustomAnalyteResultConsumed = { },
            onCheckHasResultsForCustomAnalyte = { false },
        )
    }
}

@Preview(
    name = "Custom Analyte Dialog",
    showBackground = true,
    widthDp = 420,
    heightDp = 420,
)
@Composable
private fun CalibrationCustomAnalyteDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationCustomAnalyteDialog(
            customAnalyte = previewCustomAnalytes().first(),
            onSave = { _, _, _, _ -> null },
            onArchive = { },
            onCheckHasResults = { false },
            onDismiss = {},
        )
    }
}

private fun previewCustomAnalytes(): List<CustomBloodAnalyte> {
    return listOf(
        CustomBloodAnalyte(
            uuid = UUID.fromString("c74b52f5-f1e8-4d9a-a971-c140fa91da78"),
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            createdAt = Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
            archivedAt = null,
        ),
        CustomBloodAnalyte(
            uuid = UUID.fromString("deeb1c02-bc7e-49af-9d6f-209aa151ca14"),
            abbreviation = "SHBG",
            name = "Sex hormone-binding globulin",
            unitLabel = "nmol/L",
            createdAt = Instant.parse("2026-04-12T22:15:00Z"),
            updatedAt = Instant.parse("2026-04-12T22:15:00Z"),
            archivedAt = null,
        ),
    )
}
