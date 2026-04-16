package com.mkx.hrttracker.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

@Composable
fun AddEntryScreen(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.consumeSavedState()
            onEntrySaved()
        }
    }

    AddEntryScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRouteSelected = viewModel::updateRoute,
        onMedicineNameChange = viewModel::updateMedicineName,
        onDosageChange = viewModel::updateDosageMg,
        onAppliedAtChange = viewModel::updateAppliedAt,
        onSaveClick = viewModel::saveEntry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryScreenContent(
    uiState: AddEntryUiState,
    onNavigateBack: () -> Unit,
    onRouteSelected: (RouteOfAdministration) -> Unit,
    onMedicineNameChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    onAppliedAtChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRouteMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) R.string.edit_entry else R.string.add_entry
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            ExposedDropdownMenuBox(
                expanded = isRouteMenuExpanded,
                onExpandedChange = { isRouteMenuExpanded = !isRouteMenuExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.routeOfAdministration.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(text = stringResource(R.string.field_route)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRouteMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isRouteMenuExpanded,
                    onDismissRequest = { isRouteMenuExpanded = false }
                ) {
                    RouteOfAdministration.entries.forEach { route ->
                        DropdownMenuItem(
                            text = { Text(route.displayName) },
                            onClick = {
                                onRouteSelected(route)
                                isRouteMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.medicineName,
                onValueChange = onMedicineNameChange,
                label = { Text(text = stringResource(R.string.field_medication_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.dosageMg,
                onValueChange = onDosageChange,
                label = { Text(text = stringResource(R.string.field_dosage_mg)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = uiState.appliedAtInput,
                onValueChange = onAppliedAtChange,
                label = { Text(text = stringResource(R.string.field_time_of_application)) },
                supportingText = { Text(text = stringResource(R.string.field_time_format_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            uiState.errorMessageRes?.let { errorMessageRes ->
                Text(text = stringResource(errorMessageRes))
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                Text(text = stringResource(R.string.save_entry))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddEntryScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        AddEntryScreenContent(
            uiState = AddEntryUiState(
                editingEntryId = "f16ec8a7-5115-410a-b12d-f376fdb6f76b",
                routeOfAdministration = RouteOfAdministration.SUBCUTANEOUS,
                medicineName = "Estradiol cypionate",
                dosageMg = "4.0",
                appliedAtInput = "2026-04-16 21:15",
            ),
            onNavigateBack = { },
            onRouteSelected = { },
            onMedicineNameChange = { },
            onDosageChange = { },
            onAppliedAtChange = { },
            onSaveClick = { }
        )
    }
}
