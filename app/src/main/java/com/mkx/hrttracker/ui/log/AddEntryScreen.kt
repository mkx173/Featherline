package com.mkx.hrttracker.ui.log

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.StructuredMedicationEditorSheet
import com.mkx.hrttracker.ui.medication.changeApplicationType
import com.mkx.hrttracker.ui.medication.changeCategory
import com.mkx.hrttracker.ui.medication.changeDoseKind
import com.mkx.hrttracker.ui.medication.changeMedicationKey
import com.mkx.hrttracker.ui.medication.changeSelectionKind
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    entryId: String?,
    onDismissRequest: () -> Unit,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(entryId) {
        viewModel.initialize(entryId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            hideBottomSheet(scope, sheetState) {
                viewModel.consumeSavedState()
                onEntrySaved()
            }
        }
    }

    AddEntryScreenContent(
        uiState = uiState,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        onCloseClick = {
            hideBottomSheet(scope, sheetState, onDismissRequest)
        },
        onMedicationDraftChange = viewModel::updateMedicationDraft,
        onAppliedDateChange = viewModel::updateAppliedDate,
        onAppliedTimeChange = viewModel::updateAppliedTime,
        onSaveClick = viewModel::saveEntry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryScreenContent(
    uiState: AddEntryUiState,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onMedicationDraftChange: ((MedicationDraftUiState) -> MedicationDraftUiState) -> Unit,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StructuredMedicationEditorSheet(
        modifier = modifier,
        sheetState = sheetState,
        title = stringResource(if (uiState.isEditing) R.string.edit_entry else R.string.add_entry),
        confirmButtonText = stringResource(R.string.save_entry),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        draft = uiState.medicationDraft,
        onCategoryChange = { category ->
            onMedicationDraftChange { draft -> draft.changeCategory(category) }
        },
        onApplicationTypeChange = { applicationType ->
            onMedicationDraftChange { draft -> draft.changeApplicationType(applicationType) }
        },
        onSelectionKindChange = { selectionKind ->
            onMedicationDraftChange { draft -> draft.changeSelectionKind(selectionKind) }
        },
        onMedicationKeyChange = { medicationKey ->
            onMedicationDraftChange { draft -> draft.changeMedicationKey(medicationKey) }
        },
        onCustomMedicationNameChange = { medicationName ->
            onMedicationDraftChange { draft -> draft.copy(customMedicationName = medicationName) }
        },
        onDoseKindChange = { doseKind ->
            onMedicationDraftChange { draft -> draft.changeDoseKind(doseKind) }
        },
        onDoseMgChange = { doseMg ->
            onMedicationDraftChange { draft -> draft.copy(doseMg = doseMg) }
        },
        onGelPercentChange = { gelPercent ->
            onMedicationDraftChange { draft -> draft.copy(gelPercent = gelPercent) }
        },
        onGelWeightChange = { gelWeight ->
            onMedicationDraftChange { draft -> draft.copy(gelWeightGrams = gelWeight) }
        },
        onPatchReleaseRateChange = { releaseRate ->
            onMedicationDraftChange { draft ->
                draft.copy(patchReleaseRateMcgPerDay = releaseRate)
            }
        },
        appliedDate = uiState.appliedDate,
        appliedTime = uiState.appliedTime,
        onAppliedDateChange = onAppliedDateChange,
        onAppliedTimeChange = onAppliedTimeChange,
        showAppliedAtFields = true,
        errorMessageRes = uiState.errorMessageRes,
        isSaving = uiState.isSaving,
        onConfirm = onSaveClick
    )
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        AddEntryScreenContent(
            uiState = AddEntryUiState(
                editingEntryId = "f16ec8a7-5115-410a-b12d-f376fdb6f76b",
                medicationDraft = defaultMedicationDraft().changeApplicationType(
                    com.mkx.hrttracker.model.medication.MedicationApplicationType.INJECTION
                ).changeMedicationKey(
                    com.mkx.hrttracker.model.medication.MedicationKey.ESTRADIOL_CYPIONATE
                ).copy(doseMg = "4.0"),
                appliedDate = LocalDate.of(2026, 4, 16),
                appliedTime = LocalTime.of(21, 15),
            ),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { },
            onCloseClick = { },
            onMedicationDraftChange = { },
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onSaveClick = { }
        )
    }
}
