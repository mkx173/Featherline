package com.mkx.hrttracker.ui.log

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        onRouteSelected = viewModel::updateRoute,
        onMedicineNameChange = viewModel::updateMedicineName,
        onDosageChange = viewModel::updateDosageMg,
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
    onRouteSelected: (RouteOfAdministration) -> Unit,
    onMedicineNameChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MedicationEditorSheet(
        modifier = modifier,
        sheetState = sheetState,
        title = stringResource(if (uiState.isEditing) R.string.edit_entry else R.string.add_entry),
        confirmButtonText = stringResource(R.string.save_entry),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        routeOfAdministration = uiState.routeOfAdministration,
        medicineName = uiState.medicineName,
        dosageMg = uiState.dosageMg,
        onRouteSelected = onRouteSelected,
        onMedicineNameChange = onMedicineNameChange,
        onDosageChange = onDosageChange,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditorSheet(
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    routeOfAdministration: RouteOfAdministration,
    medicineName: String,
    dosageMg: String,
    onRouteSelected: (RouteOfAdministration) -> Unit,
    onMedicineNameChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    appliedDate: LocalDate? = null,
    appliedTime: LocalTime? = null,
    onAppliedDateChange: ((LocalDate) -> Unit)? = null,
    onAppliedTimeChange: ((LocalTime) -> Unit)? = null,
    showAppliedAtFields: Boolean = false,
    errorMessageRes: Int? = null,
    isSaving: Boolean = false,
    onConfirm: () -> Unit,
) {
    var isRouteMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val formattedDate = remember(appliedDate, dateFormatter) {
        appliedDate?.format(dateFormatter).orEmpty()
    }
    val formattedTime = remember(appliedTime, timeFormatter) {
        appliedTime?.format(timeFormatter).orEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_medium)
                ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onCloseClick) {
                    Text(text = stringResource(R.string.cancel))
                }
            }

            ExposedDropdownMenuBox(
                expanded = isRouteMenuExpanded,
                onExpandedChange = { isRouteMenuExpanded = !isRouteMenuExpanded }
            ) {
                OutlinedTextField(
                    value = stringResource(routeOfAdministration.labelRes),
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
                            text = { Text(text = stringResource(route.labelRes)) },
                            onClick = {
                                onRouteSelected(route)
                                isRouteMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = medicineName,
                onValueChange = onMedicineNameChange,
                label = { Text(text = stringResource(R.string.field_medication_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosageMg,
                onValueChange = onDosageChange,
                label = { Text(text = stringResource(R.string.field_dosage_mg)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            if (showAppliedAtFields && appliedDate != null && appliedTime != null) {
                PickerField(
                    value = formattedDate,
                    label = stringResource(R.string.field_date_of_application),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                onAppliedDateChange?.invoke(LocalDate.of(year, month + 1, dayOfMonth))
                            },
                            appliedDate.year,
                            appliedDate.monthValue - 1,
                            appliedDate.dayOfMonth
                        ).show()
                    }
                )

                PickerField(
                    value = formattedTime,
                    label = stringResource(R.string.field_time_of_application),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                onAppliedTimeChange?.invoke(LocalTime.of(hourOfDay, minute))
                            },
                            appliedTime.hour,
                            appliedTime.minute,
                            DateFormat.is24HourFormat(context)
                        ).show()
                    }
                )
            }

            errorMessageRes?.let { messageRes ->
                Text(text = stringResource(messageRes))
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(text = confirmButtonText)
            }
        }
    }
}

@Composable
private fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            singleLine = true
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
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
                editingEntryId = "f16ec8a7-5115-410a-b12d-f376fdb6f76b",
                routeOfAdministration = RouteOfAdministration.SUBCUTANEOUS,
                medicineName = "Estradiol cypionate",
                dosageMg = "4.0",
                appliedDate = LocalDate.of(2026, 4, 16),
                appliedTime = LocalTime.of(21, 15),
            ),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { },
            onCloseClick = { },
            onRouteSelected = { },
            onMedicineNameChange = { },
            onDosageChange = { },
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onSaveClick = { }
        )
    }
}
