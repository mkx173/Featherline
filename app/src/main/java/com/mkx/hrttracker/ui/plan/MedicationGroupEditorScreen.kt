package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.log.MedicationEditorSheet
import com.mkx.hrttracker.util.rememberAppLocale

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
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

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
