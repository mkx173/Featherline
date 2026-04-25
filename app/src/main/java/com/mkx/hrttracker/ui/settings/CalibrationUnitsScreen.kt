package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

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
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationUnitsScreenContent(
    uiState: CalibrationUnitsUiState,
    onNavigateBack: () -> Unit,
    onUnitChange: (BloodAnalyteKey, BloodUnitKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_calibration_default_units)) },
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
            itemsIndexed(
                items = calibrationAnalytes,
                key = { _, analyteKey -> analyteKey.storageValue },
            ) { index, analyteKey ->
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

@Preview(
    name = "Calibration Default Units",
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
                )
            ),
            onNavigateBack = {},
            onUnitChange = { _, _ -> },
        )
    }
}
