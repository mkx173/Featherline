package com.mkx.hrttracker.ui.log

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddMedicationGroupSheet(
    onDismissRequest: () -> Unit,
    onEntriesSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickAddMedicationGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            hideBottomSheet(scope, sheetState) {
                viewModel.reset()
                onEntriesSaved()
            }
        }
    }

    QuickAddMedicationGroupSheetContent(
        uiState = uiState,
        sheetState = sheetState,
        onDismissRequest = {
            viewModel.reset()
            onDismissRequest()
        },
        onCloseClick = {
            hideBottomSheet(scope, sheetState) {
                viewModel.reset()
                onDismissRequest()
            }
        },
        onGroupSelected = viewModel::selectGroup,
        onChangeGroupClick = viewModel::clearSelectedGroup,
        onItemTimeChange = viewModel::updateItemTime,
        onSaveClick = viewModel::saveEntries,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddMedicationGroupSheetContent(
    uiState: QuickAddMedicationGroupUiState,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onGroupSelected: (UUID) -> Unit,
    onChangeGroupClick: () -> Unit,
    onItemTimeChange: (String, LocalTime) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxSize()
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
            QuickAddMedicationGroupSheetHeader(
                selectedGroup = uiState.selectedGroup,
                onCloseClick = onCloseClick,
                onChangeGroupClick = onChangeGroupClick
            )

            if (uiState.selectedGroup == null) {
                Text(
                    text = stringResource(R.string.quick_add_group_pick_group),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (uiState.groups.isEmpty()) {
                    Text(text = stringResource(R.string.quick_add_group_empty_state))
                } else {
                    uiState.groups.forEach { group ->
                        MedicationGroupSelectionRow(
                            group = group,
                            appLocale = appLocale,
                            onClick = { onGroupSelected(group.uuid) }
                        )
                    }
                }
            } else {
                uiState.draftEntries.forEach { entry ->
                    QuickAddMedicationGroupEntryRow(
                        entry = entry,
                        appLocale = appLocale,
                        timeFormatter = timeFormatter,
                        onTimeClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    onItemTimeChange(
                                        entry.localId,
                                        LocalTime.of(hourOfDay, minute)
                                    )
                                },
                                entry.appliedTime.hour,
                                entry.appliedTime.minute,
                                DateFormat.is24HourFormat(context)
                            ).show()
                        }
                    )
                }

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.draftEntries.isNotEmpty() && !uiState.isSaving
                ) {
                    Text(text = stringResource(R.string.quick_add_group_save_entries))
                }
            }
        }
    }
}

@Composable
private fun QuickAddMedicationGroupSheetHeader(
    selectedGroup: MedicationGroup?,
    onCloseClick: () -> Unit,
    onChangeGroupClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (selectedGroup == null) {
            Text(
                text = stringResource(R.string.quick_add_group_title),
                style = MaterialTheme.typography.titleLarge
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))) {
                IconButton(onClick = onChangeGroupClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.quick_add_group_change_group)
                    )
                }
                Text(
                    text = selectedGroup.name,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        TextButton(onClick = onCloseClick) {
            Text(text = stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun MedicationGroupSelectionRow(
    group: MedicationGroup,
    appLocale: Locale,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        overlineContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.plan_group_medication_count,
                    group.medications.size,
                    group.medications.size
                )
            )
        },
        headlineContent = {
            Text(text = group.name)
        },
        supportingContent = {
            group.medications.firstOrNull()?.let { medication ->
                Text(
                    text = stringResource(
                        R.string.plan_group_medication_summary,
                        medication.medicineName,
                        medication.dosageMgAsMedicine.formatDose(appLocale),
                        stringResource(medication.routeOfAdministration.labelRes)
                    )
                )
            }
        }
    )
}

@Composable
private fun QuickAddMedicationGroupEntryRow(
    entry: QuickAddMedicationGroupItemUiState,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    onTimeClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        overlineContent = {
            Text(text = stringResource(entry.routeOfAdministration.labelRes))
        },
        headlineContent = {
            Text(text = entry.medicineName)
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.entry_medicine_dose,
                    entry.dosageMgAsMedicine.formatDose(appLocale)
                )
            )
        },
        trailingContent = {
            TextButton(onClick = onTimeClick) {
                Text(
                    text = entry.appliedTime.format(timeFormatter)
                )
            }
        }
    )
}
