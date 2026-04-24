package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.util.rememberAppLocale
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLocale = rememberAppLocale()
    val dateTimeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(appLocale)
    }
    val scope = rememberCoroutineScope()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var draftCollectedAtEpochMillis by rememberSaveable { mutableLongStateOf(0L) }
    var draftTimeZoneId by rememberSaveable { mutableStateOf(ZoneId.systemDefault().id) }
    var draftE2Value by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_personalization_calibration)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    draftCollectedAtEpochMillis = System.currentTimeMillis()
                    draftTimeZoneId = ZoneId.systemDefault().id
                    draftE2Value = ""
                    showAddSheet = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.settings_calibration_add_result)
                )
            }
        }
    ) { innerPadding ->
        if (uiState.panels.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(dimensionResource(R.dimen.padding_medium)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_calibration_empty_state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
            ) {
                items(
                    items = uiState.panels,
                    key = { panel -> panel.uuid }
                ) { panel ->
                    CalibrationPanelRow(
                        panel = panel,
                        dateTimeFormatter = dateTimeFormatter
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val draftCollectedAt = remember(draftCollectedAtEpochMillis) {
            Instant.ofEpochMilli(draftCollectedAtEpochMillis)
        }
        val draftCollectedAtLabel = remember(draftCollectedAtEpochMillis, draftTimeZoneId, dateTimeFormatter) {
            draftCollectedAt.atZone(ZoneId.of(draftTimeZoneId)).format(dateTimeFormatter)
        }

        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState
        ) {
            CalibrationEntrySheetContent(
                e2Value = draftE2Value,
                collectedAtLabel = draftCollectedAtLabel,
                timeZoneId = draftTimeZoneId,
                isSaving = uiState.isSaving,
                onE2ValueChange = { value -> draftE2Value = value },
                onCloseClick = {
                    hideBottomSheet(scope, sheetState) {
                        showAddSheet = false
                    }
                },
                onSaveClick = {
                    scope.launch {
                        val saved = viewModel.addE2Result(
                            e2ValueText = draftE2Value,
                            collectedAt = draftCollectedAt,
                            collectedAtTimeZoneId = draftTimeZoneId
                        )
                        if (saved) {
                            hideBottomSheet(scope, sheetState) {
                                showAddSheet = false
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CalibrationPanelRow(
    panel: BloodTestPanel,
    dateTimeFormatter: DateTimeFormatter,
) {
    val zoneId = remember(panel.collectedAtTimeZoneId) {
        ZoneId.of(panel.collectedAtTimeZoneId)
    }
    val collectedAtLabel = remember(panel.collectedAt, panel.collectedAtTimeZoneId, dateTimeFormatter) {
        panel.collectedAt.atZone(zoneId).format(dateTimeFormatter)
    }
    val valueSummary = remember(panel.results) {
        formatCalibrationPanelValueSummary(panel)
    }

    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = {
            Text(text = collectedAtLabel)
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.settings_calibration_row_meta,
                    valueSummary,
                    panel.collectedAtTimeZoneId
                )
            )
        }
    )
}

@Composable
private fun CalibrationEntrySheetContent(
    e2Value: String,
    collectedAtLabel: String,
    timeZoneId: String,
    isSaving: Boolean,
    onE2ValueChange: (String) -> Unit,
    onCloseClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    val canSave = parseCalibrationNumericInput(e2Value) != null && !isSaving

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = dimensionResource(R.dimen.padding_medium)),
        contentPadding = PaddingValues(
            top = dimensionResource(R.dimen.padding_small),
            bottom = dimensionResource(R.dimen.padding_medium)
        ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        item {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_calibration_entry_title),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onCloseClick) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.settings_calibration_entry_collected_at, collectedAtLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_calibration_entry_timezone, timeZoneId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(
                value = e2Value,
                onValueChange = onE2ValueChange,
                label = {
                    Text(text = stringResource(R.string.settings_calibration_e2_field))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Button(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Text(text = stringResource(R.string.settings_calibration_save_result))
            }
        }
    }
}

private fun formatCalibrationPanelValueSummary(panel: BloodTestPanel): String {
    val e2Result = panel.results.firstOrNull { result ->
        val analyte = result.analyte as? BloodTestResultAnalyte.Builtin
        analyte?.key == BloodAnalyteKey.E2
    }
    if (e2Result != null) {
        return "E2 ${formatCalibrationNumericValue(e2Result.value)} ${formatCalibrationUnitLabel(e2Result.unitSnapshot)}"
    }

    return panel.results.joinToString(separator = " · ") { result ->
        when (val analyte = result.analyte) {
            is BloodTestResultAnalyte.Builtin -> {
                "${analyte.key.storageValue.uppercase()} ${formatCalibrationNumericValue(result.value)} ${formatCalibrationUnitLabel(result.unitSnapshot)}"
            }

            is BloodTestResultAnalyte.Custom -> {
                "${analyte.name ?: "Custom"} ${formatCalibrationNumericValue(result.value)} ${formatCalibrationUnitLabel(result.unitSnapshot)}"
            }
        }
    }
}

private fun formatCalibrationUnitLabel(unitSnapshot: String): String {
    return when (BloodUnitKey.fromStorageValue(unitSnapshot)) {
        BloodUnitKey.PG_ML -> "pg/mL"
        BloodUnitKey.PMOL_L -> "pmol/L"
        BloodUnitKey.NG_DL -> "ng/dL"
        BloodUnitKey.NMOL_L -> "nmol/L"
        BloodUnitKey.NG_ML -> "ng/mL"
        BloodUnitKey.MIU_ML -> "mIU/mL"
        BloodUnitKey.IU_L -> "IU/L"
        null -> unitSnapshot
    }
}

private fun formatCalibrationNumericValue(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
