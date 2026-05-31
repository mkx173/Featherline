package com.mkx.hrttracker.ui.log

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationLogEntryEditorSheet
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.stockMutationPreviewDoseMagnitude
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    entryIds: List<String>,
    modifier: Modifier = Modifier,
    quickLogRequest: AddEntryQuickLogRequest? = null,
    editSnapshot: AddEntryEditSnapshot? = null,
    onDismissRequest: () -> Unit,
    onEntrySaved: () -> Unit,
    viewModel: AddEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSheetLockedState = rememberUpdatedState(
        uiState.isSaving || uiState.isDeleting
    )
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            value != SheetValue.Hidden || !isSheetLockedState.value
        },
    )
    BackHandler(enabled = isSheetLockedState.value) { }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entryIds, quickLogRequest, editSnapshot) {
        when {
            quickLogRequest != null -> viewModel.initializeQuickLog(
                groupId = quickLogRequest.groupId,
                scheduleTimeUuid = quickLogRequest.scheduleTimeUuid,
                scheduledFor = quickLogRequest.scheduledFor,
                medicine = quickLogRequest.medicine,
                applicationType = quickLogRequest.applicationType,
                doseInstruction = quickLogRequest.doseInstruction,
                medicationCount = quickLogRequest.medicationCount,
                sourceGroupName = quickLogRequest.sourceGroupName,
                sourceGroupColorKey = quickLogRequest.sourceGroupColorKey,
                sourceGroupPreviousScheduledFor = quickLogRequest.sourceGroupPreviousScheduledFor,
                sourceGroupNextScheduledFor = quickLogRequest.sourceGroupNextScheduledFor,
            )
            else -> viewModel.initialize(
                entryIds = entryIds,
                editSnapshot = editSnapshot,
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

    AddEntryScreenBody(
        modifier = modifier,
        uiState = uiState,
        sheetState = sheetState,
        isSheetLocked = isSheetLockedState.value,
        onDismissRequest = onDismissRequest,
        onCloseClick = {
            hideBottomSheet(scope, sheetState, onDismissRequest)
        },
        onAppliedDateChange = viewModel::updateAppliedDate,
        onAppliedTimeChange = viewModel::updateAppliedTime,
        onDeleteClick = viewModel::deleteEntry,
        onSaveClick = viewModel::saveEntry,
        onSaveAfterFulfillmentWarningClick = viewModel::saveEntryAfterFulfillmentWarning,
        onScheduleFulfillmentWarningDismiss = viewModel::dismissScheduleFulfillmentWarning,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryScreenBody(
    modifier: Modifier = Modifier,
    uiState: AddEntryUiState,
    sheetState: SheetState,
    isSheetLocked: Boolean,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    onDeleteClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAfterFulfillmentWarningClick: () -> Unit,
    onScheduleFulfillmentWarningDismiss: () -> Unit,
) {
    var isDeleteConfirmationVisible by remember(uiState.canDelete) { mutableStateOf(false) }
    val previewDoseMagnitude = remember(
        uiState.isEditing,
        uiState.resolvedMedicine,
        uiState.doseInstructionDraft,
        uiState.countText,
    ) {
        if (uiState.isEditing) {
            null
        } else {
            stockMutationPreviewDoseMagnitude(
                medicine = uiState.resolvedMedicine,
                doseInstructionDraft = uiState.doseInstructionDraft,
                countText = uiState.countText,
            )
        }
    }

    MedicationLogEntryEditorSheet(
        modifier = modifier,
        sheetState = sheetState,
        title = stringResource(if (uiState.isEditing) R.string.edit_entry else R.string.add_entry),
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = {
            if (!isSheetLocked) {
                onDismissRequest()
            }
        },
        onCloseClick = {
            if (!isSheetLocked) {
                onCloseClick()
            }
        },
        medicineDraft = uiState.medicineDraft,
        doseInstructionDraft = uiState.doseInstructionDraft,
        lockedMedicine = uiState.resolvedMedicine,
        selectedStockProjection = uiState.selectedStockProjection,
        stockMutationPreviewDoseMagnitude = previewDoseMagnitude,
        sourceGroupName = uiState.sourceGroupName,
        sourceGroupColorKey = uiState.sourceGroupColorKey,
        sourceGroupScheduledFor = uiState.scheduledFor,
        sourceGroupScheduleOffsetOutsideFulfillmentWindow = uiState.shouldWarnScheduleWillNotBeFulfilled(
            LocalDateTime.of(uiState.appliedDate, uiState.appliedTime)
        ),
        countText = uiState.countText,
        appliedDate = uiState.appliedDate,
        appliedTime = uiState.appliedTime,
        appliedZoneId = uiState.appliedZoneId,
        onAppliedDateChange = onAppliedDateChange,
        onAppliedTimeChange = onAppliedTimeChange,
        isSaving = uiState.isSaving || uiState.isDeleting || uiState.isSaved,
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
                    onClick = {
                        if (uiState.isDeleting) return@TextButton
                        isDeleteConfirmationVisible = false
                        onDeleteClick()
                    }
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeleting) return@TextButton
                        isDeleteConfirmationVisible = false
                    },
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
                    onClick = {
                        if (!uiState.isSaving) onSaveAfterFulfillmentWarningClick()
                    },
                ) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isSaving) onScheduleFulfillmentWarningDismiss()
                    },
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
        AddEntryScreenBody(
            uiState = AddEntryUiState(
                editingEntryIds = listOf("f16ec8a7-5115-410a-b12d-f376fdb6f76b"),
                medicineDraft = defaultMedicineDraft(),
                appliedDate = LocalDate.of(2026, 4, 16),
                appliedTime = LocalTime.of(21, 15),
            ),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            isSheetLocked = false,
            onDismissRequest = { },
            onCloseClick = { },
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onDeleteClick = { },
            onSaveClick = { },
            onSaveAfterFulfillmentWarningClick = { },
            onScheduleFulfillmentWarningDismiss = { },
        )
    }
}
