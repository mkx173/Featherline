package com.mkx.hrttracker.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
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
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationUnitsScreenContent(
    uiState: CalibrationUnitsUiState,
    onNavigateBack: () -> Unit,
    onUnitChange: (BloodAnalyteKey, BloodUnitKey) -> Unit,
    onSaveCustomAnalyte: suspend (UUID?, String, String) -> Throwable?,
    onArchiveCustomAnalyte: suspend (UUID) -> Throwable?,
    modifier: Modifier = Modifier,
) {
    val listSegmentGap = dimensionResource(R.dimen.list_segment_gap)
    val sectionSpacing = dimensionResource(R.dimen.padding_medium)
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_calibration_settings)) },
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
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
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
                    actionLabel = stringResource(R.string.add),
                    onActionClick = { openCustomAnalyteDialog(customAnalyteId = null) },
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
                            CircularProgressIndicator()
                        }
                    }
                }

                uiState.customAnalytes.isEmpty() -> {
                    item(key = "custom-empty") {
                        Text(
                            text = stringResource(R.string.settings_calibration_custom_analytes_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = dimensionResource(R.dimen.padding_small),
                                    vertical = dimensionResource(R.dimen.padding_xsmall),
                                ),
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
        }
    }

    if (isCustomAnalyteDialogVisible) {
        key(customAnalyteDialogSessionId) {
            CalibrationCustomAnalyteDialog(
                customAnalyte = editingCustomAnalyte,
                onSave = onSaveCustomAnalyte,
                onArchive = onArchiveCustomAnalyte,
                onDismiss = ::dismissCustomAnalyteDialog,
            )
        }
    }
}

@Composable
private fun CalibrationSettingsSectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
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
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = actionLabel)
            }
        }
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
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        overlineContent = {
            Text(text = calibrationAnalyteLabel(analyteKey))
        },
        supportingContent = {
            ConnectedButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                options = calibrationAllowedUnitsFor(analyteKey),
                selectedOption = selectedUnit,
                optionLabel = { unit -> calibrationUnitLabel(unit) },
                onOptionSelected = onUnitChange,
                layout = ConnectedButtonGroupLayout.ROW,
                expandOptions = true,
            )
        }
    ) {
        Text(text = stringResource(calibrationAnalyteFullNameRes(analyteKey)))
    }
}

@Composable
private fun CalibrationCustomAnalyteItem(
    customAnalyte: CustomBloodAnalyte,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        supportingContent = {
            Text(text = customAnalyte.unitLabel)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    ) {
        Text(text = customAnalyte.name)
    }
}

@Composable
private fun CalibrationCustomAnalyteDialog(
    customAnalyte: CustomBloodAnalyte?,
    onSave: suspend (UUID?, String, String) -> Throwable?,
    onArchive: suspend (UUID) -> Throwable?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val duplicateErrorMessage =
        stringResource(R.string.settings_calibration_custom_analyte_error_duplicate)
    val genericSaveErrorMessage =
        stringResource(R.string.settings_calibration_custom_analyte_error_save)
    val archiveErrorMessage =
        stringResource(R.string.settings_calibration_custom_analyte_error_archive)
    var nameText by rememberSaveable { mutableStateOf(customAnalyte?.name.orEmpty()) }
    var unitText by rememberSaveable { mutableStateOf(customAnalyte?.unitLabel.orEmpty()) }
    var isNameErrorVisible by rememberSaveable { mutableStateOf(false) }
    var isUnitErrorVisible by rememberSaveable { mutableStateOf(false) }
    var actionErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var isArchiveConfirmationVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = {
            if (!isWorking) {
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
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = unitText,
                    onValueChange = { value ->
                        unitText = value
                        isUnitErrorVisible = false
                        actionErrorMessage = null
                    },
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
                )
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
            TextButton(
                enabled = !isWorking,
                onClick = {
                    val trimmedName = nameText.trim()
                    val trimmedUnit = unitText.trim()
                    val isNameBlank = trimmedName.isEmpty()
                    val isUnitBlank = trimmedUnit.isEmpty()
                    isNameErrorVisible = isNameBlank
                    isUnitErrorVisible = isUnitBlank
                    actionErrorMessage = null
                    if (isNameBlank || isUnitBlank) {
                        return@TextButton
                    }

                    coroutineScope.launch {
                        isWorking = true
                        val error = onSave(
                            customAnalyte?.uuid,
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
                },
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                customAnalyte?.let { existingAnalyte ->
                    TextButton(
                        enabled = !isWorking,
                        onClick = { isArchiveConfirmationVisible = true },
                    ) {
                        Text(text = stringResource(R.string.archive))
                    }
                }
                TextButton(
                    enabled = !isWorking,
                    onClick = onDismiss,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    )

    if (isArchiveConfirmationVisible && customAnalyte != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isWorking) {
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
                    enabled = !isWorking,
                    onClick = {
                        isArchiveConfirmationVisible = false
                        coroutineScope.launch {
                            isWorking = true
                            val error = onArchive(customAnalyte.uuid)
                            isWorking = false
                            if (error == null) {
                                onDismiss()
                            } else {
                                actionErrorMessage = archiveErrorMessage
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.archive))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isWorking,
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
            onSaveCustomAnalyte = { _, _, _ -> null },
            onArchiveCustomAnalyte = { null },
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
            onSave = { _, _, _ -> null },
            onArchive = { null },
            onDismiss = {},
        )
    }
}

private fun previewCustomAnalytes(): List<CustomBloodAnalyte> {
    return listOf(
        CustomBloodAnalyte(
            uuid = UUID.fromString("c74b52f5-f1e8-4d9a-a971-c140fa91da78"),
            name = "DHT",
            unitLabel = "ng/dL",
            createdAt = Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
            archivedAt = null,
        ),
        CustomBloodAnalyte(
            uuid = UUID.fromString("deeb1c02-bc7e-49af-9d6f-209aa151ca14"),
            name = "SHBG",
            unitLabel = "nmol/L",
            createdAt = Instant.parse("2026-04-12T22:15:00Z"),
            updatedAt = Instant.parse("2026-04-12T22:15:00Z"),
            archivedAt = null,
        ),
    )
}
