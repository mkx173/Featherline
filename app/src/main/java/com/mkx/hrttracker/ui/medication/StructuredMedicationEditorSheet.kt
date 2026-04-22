package com.mkx.hrttracker.ui.medication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredMedicationEditorSheet(
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    draft: MedicationDraftUiState,
    onCategoryChange: (MedicationCategory) -> Unit,
    onApplicationTypeChange: (MedicationApplicationType) -> Unit,
    onSelectionKindChange: (MedicationSelectionKind) -> Unit,
    onMedicationKeyChange: (MedicationKey) -> Unit,
    onCustomMedicationNameChange: (String) -> Unit,
    onDoseKindChange: (MedicationDoseKind) -> Unit,
    onDoseMgChange: (String) -> Unit,
    onGelPercentChange: (String) -> Unit,
    onGelWeightChange: (String) -> Unit,
    onPatchReleaseRateChange: (String) -> Unit,
    isMedicationIdentityEditable: Boolean = true,
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
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val catalog = remember(draft.category, draft.applicationType) {
        MedicationCatalog.catalogFor(draft.category, draft.applicationType)
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

            DropdownField(
                label = stringResource(R.string.field_medication_category),
                value = stringResource(draft.category.labelRes),
                options = editorMedicationCategories(),
                optionLabel = { category -> stringResource(category.labelRes) },
                onOptionSelected = onCategoryChange,
                enabled = isMedicationIdentityEditable
            )

            DropdownField(
                label = stringResource(R.string.field_medication_application),
                value = stringResource(draft.applicationType.labelRes),
                options = MedicationCatalog.applicationTypesFor(draft.category),
                optionLabel = { applicationType -> stringResource(applicationType.labelRes) },
                onOptionSelected = onApplicationTypeChange,
                enabled = isMedicationIdentityEditable
            )

            if (draft.supportsCatalogSelection() && draft.supportsCustomName()) {
                DropdownField(
                    label = stringResource(R.string.field_medication_source),
                    value = stringResource(
                        when (draft.selectionKind) {
                            MedicationSelectionKind.CATALOG -> R.string.medication_source_catalog
                            MedicationSelectionKind.CUSTOM -> R.string.medication_source_custom
                        }
                    ),
                    options = MedicationSelectionKind.entries,
                    optionLabel = { selectionKind ->
                        stringResource(
                            when (selectionKind) {
                                MedicationSelectionKind.CATALOG -> R.string.medication_source_catalog
                                MedicationSelectionKind.CUSTOM -> R.string.medication_source_custom
                            }
                        )
                    },
                    onOptionSelected = onSelectionKindChange,
                    enabled = isMedicationIdentityEditable
                )
            }

            if (draft.supportsCatalogSelection() && draft.selectionKind == MedicationSelectionKind.CATALOG) {
                DropdownField(
                    label = stringResource(R.string.field_medication),
                    value = draft.selectedCatalogEntry().medicationKey?.let { key ->
                        stringResource(key.labelRes)
                    }.orEmpty(),
                    options = catalog.entries.mapNotNull { it.medicationKey },
                    optionLabel = { medicationKey -> stringResource(medicationKey.labelRes) },
                    onOptionSelected = onMedicationKeyChange,
                    enabled = isMedicationIdentityEditable
                )
            }

            if (draft.requiresCustomName()) {
                OutlinedTextField(
                    value = draft.customMedicationName,
                    onValueChange = onCustomMedicationNameChange,
                    enabled = isMedicationIdentityEditable,
                    label = {
                        Text(text = stringResource(R.string.field_medication_name))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (draft.availableDoseKinds().size > 1) {
                DropdownField(
                    label = stringResource(R.string.field_dose_type),
                    value = stringResource(doseKindLabelRes(draft.doseKind)),
                    options = draft.availableDoseKinds(),
                    optionLabel = { doseKind -> stringResource(doseKindLabelRes(doseKind)) },
                    onOptionSelected = onDoseKindChange
                )
            }

            when (draft.doseKind) {
                MedicationDoseKind.MG_AS_MEDICINE -> {
                    DoseTextField(
                        value = draft.doseMg,
                        onValueChange = onDoseMgChange,
                        label = stringResource(R.string.field_dosage_mg)
                    )
                }

                MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> {
                    DoseTextField(
                        value = draft.doseMg,
                        onValueChange = onDoseMgChange,
                        label = stringResource(R.string.field_equivalent_estradiol_mg)
                    )
                }

                MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> {
                    DoseTextField(
                        value = draft.gelPercent,
                        onValueChange = onGelPercentChange,
                        label = stringResource(R.string.field_gel_percent)
                    )
                    DoseTextField(
                        value = draft.gelWeightGrams,
                        onValueChange = onGelWeightChange,
                        label = stringResource(R.string.field_gel_weight_grams)
                    )
                }

                MedicationDoseKind.PATCH_TOTAL_MG -> {
                    DoseTextField(
                        value = draft.doseMg,
                        onValueChange = onDoseMgChange,
                        label = stringResource(R.string.field_patch_total_mg)
                    )
                }

                MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> {
                    DoseTextField(
                        value = draft.patchReleaseRateMcgPerDay,
                        onValueChange = onPatchReleaseRateChange,
                        label = stringResource(R.string.field_patch_release_rate_mcg_day)
                    )
                }

                MedicationDoseKind.NONE -> {
                    Text(
                        text = stringResource(R.string.medication_editor_patch_off_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showAppliedAtFields && appliedDate != null && appliedTime != null) {
                PickerField(
                    value = appliedDate.format(dateFormatter),
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
                    value = appliedTime.format(timeFormatter),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) {
                expanded = !expanded
            }
        }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = label) },
            trailingIcon = if (enabled) {
                {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            } else {
                null
            },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = enabled && expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DoseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { onClick() }
        )
    }
}

private fun doseKindLabelRes(doseKind: MedicationDoseKind): Int {
    return when (doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE -> R.string.dose_type_mg_as_medicine
        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> R.string.dose_type_gel_equivalent_estradiol_mg
        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> R.string.dose_type_gel_percent_and_weight
        MedicationDoseKind.PATCH_TOTAL_MG -> R.string.dose_type_patch_total_mg
        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> R.string.dose_type_patch_release_rate_mcg_day
        MedicationDoseKind.NONE -> R.string.medication_application_patch_off
    }
}
