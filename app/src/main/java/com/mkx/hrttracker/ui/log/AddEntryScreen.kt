package com.mkx.hrttracker.ui.log

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationLogEntryEditorSheet
import com.mkx.hrttracker.ui.medication.changeApplicationType
import com.mkx.hrttracker.ui.medication.changeCategory
import com.mkx.hrttracker.ui.medication.changeCustomDoseUnit
import com.mkx.hrttracker.ui.medication.changeDoseKind
import com.mkx.hrttracker.ui.medication.changeMedicationKey
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    entryIds: List<String>,
    quickLogRequest: AddEntryQuickLogRequest? = null,
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

    LaunchedEffect(entryIds, quickLogRequest) {
        if (quickLogRequest == null) {
            viewModel.initialize(entryIds)
        } else {
            viewModel.initializeQuickLog(
                groupId = quickLogRequest.groupId,
                scheduleTimeUuid = quickLogRequest.scheduleTimeUuid,
                scheduledFor = quickLogRequest.scheduledFor,
                medicationDetails = quickLogRequest.medicationDetails,
                medicationCount = quickLogRequest.medicationCount,
            )
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            hideBottomSheet(scope, sheetState) {
                viewModel.consumeSavedState()
                onEntrySaved()
            }
        }
    }

    val context = LocalContext.current
    val saveEntryFailureMessage = stringResource(R.string.save_entry_failure)
    val deleteEntryFailureMessage = stringResource(R.string.delete_entry_failure)
    LaunchedEffect(uiState.saveEntryResult) {
        when (uiState.saveEntryResult) {
            SaveEntryResult.FAILURE -> {
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
            DeleteEntryResult.FAILURE -> {
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

    AddEntryScreenContent(
        uiState = uiState,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        onCloseClick = {
            hideBottomSheet(scope, sheetState, onDismissRequest)
        },
        onMedicationDraftChange = viewModel::updateMedicationDraft,
        onCountTextChange = viewModel::updateCountText,
        onDecreaseCountClick = viewModel::decreaseCount,
        onIncreaseCountClick = viewModel::increaseCount,
        onAppliedDateChange = viewModel::updateAppliedDate,
        onAppliedTimeChange = viewModel::updateAppliedTime,
        appliedZoneId = uiState.appliedZoneId,
        onDeleteClick = viewModel::deleteEntry,
        onSaveClick = viewModel::saveEntry,
        onSaveAfterFulfillmentWarningClick = viewModel::saveEntryAfterFulfillmentWarning,
        onScheduleFulfillmentWarningDismiss = viewModel::dismissScheduleFulfillmentWarning,
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
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    appliedZoneId: ZoneId = ZoneId.systemDefault(),
    onDeleteClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAfterFulfillmentWarningClick: () -> Unit,
    onScheduleFulfillmentWarningDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDeleteConfirmationVisible by remember(uiState.canDelete) { mutableStateOf(false) }

    MedicationLogEntryEditorSheet(
        modifier = modifier,
        sheetState = sheetState,
        title = stringResource(if (uiState.isEditing) R.string.edit_entry else R.string.add_entry),
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        draft = uiState.medicationDraft,
        canEditMedicationIdentity = uiState.canEditMedicationIdentity,
        sourceGroupName = uiState.sourceGroupName,
        sourceGroupColorKey = uiState.sourceGroupColorKey,
        sourceGroupScheduledFor = uiState.scheduledFor,
        sourceGroupScheduleOffsetOutsideFulfillmentWindow = uiState.shouldWarnScheduleWillNotBeFulfilled(
            LocalDateTime.of(uiState.appliedDate, uiState.appliedTime)
        ),
        onCategoryChange = { category ->
            onMedicationDraftChange { draft -> draft.changeCategory(category) }
        },
        onApplicationTypeChange = { applicationType ->
            onMedicationDraftChange { draft -> draft.changeApplicationType(applicationType) }
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
        onCustomDoseUnitChange = { customDoseUnit ->
            onMedicationDraftChange { draft -> draft.changeCustomDoseUnit(customDoseUnit) }
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
        countText = uiState.countText,
        onCountTextChange = onCountTextChange,
        onDecreaseCountClick = onDecreaseCountClick,
        onIncreaseCountClick = onIncreaseCountClick,
        appliedDate = uiState.appliedDate,
        appliedTime = uiState.appliedTime,
        appliedZoneId = appliedZoneId,
        onAppliedDateChange = onAppliedDateChange,
        onAppliedTimeChange = onAppliedTimeChange,
        errorMessageRes = uiState.errorMessageRes,
        isSaving = isAddEntryBusy(uiState),
        destructiveButtonText = if (uiState.canDelete) {
            stringResource(R.string.delete_entries_confirm)
        } else {
            null
        },
        onDestructiveAction = if (uiState.canDelete) {
            { isDeleteConfirmationVisible = true }
        } else {
            null
        },
        onConfirm = onSaveClick
    )

    if (isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) {
                    isDeleteConfirmationVisible = false
                }
            },
            title = { Text(text = stringResource(R.string.delete_entry_title)) },
            text = {
                Text(
                    text = stringResource(R.string.delete_editing_entry_confirmation)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDeleting,
                    onClick = {
                        isDeleteConfirmationVisible = false
                        onDeleteClick()
                    }
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isDeleting,
                    onClick = { isDeleteConfirmationVisible = false },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.isScheduleFulfillmentWarningVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSaving) {
                    onScheduleFulfillmentWarningDismiss()
                }
            },
            title = {
                Text(text = stringResource(R.string.schedule_fulfillment_warning_title))
            },
            text = {
                Text(text = stringResource(R.string.schedule_fulfillment_warning_message))
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSaving,
                    onClick = onSaveAfterFulfillmentWarningClick
                ) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSaving,
                    onClick = onScheduleFulfillmentWarningDismiss,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        AddEntryScreenContent(
            uiState = AddEntryUiState(
                editingEntryIds = listOf("f16ec8a7-5115-410a-b12d-f376fdb6f76b"),
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
            onCountTextChange = { },
            onDecreaseCountClick = { },
            onIncreaseCountClick = { },
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onDeleteClick = { },
            onSaveClick = { },
            onSaveAfterFulfillmentWarningClick = { },
            onScheduleFulfillmentWarningDismiss = { }
        )
    }
}
