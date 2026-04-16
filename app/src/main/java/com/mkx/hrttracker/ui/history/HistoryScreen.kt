package com.mkx.hrttracker.ui.history

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
fun HistoryScreen(
    onEntryClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreenContent(
        uiState = uiState,
        onEntryClick = { entryId ->
            if (uiState.isSelectionMode) {
                viewModel.toggleEntrySelection(entryId)
            } else {
                onEntryClick(entryId)
            }
        },
        onEntryLongClick = viewModel::toggleEntrySelection,
        onDeleteSelectedClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteSelectedEntries,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    uiState: HistoryUiState,
    onEntryClick: (UUID) -> Unit,
    onEntryLongClick: (UUID) -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val groupedEntries = uiState.entries.groupBy { entry ->
        entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    if (uiState.isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(text = stringResource(R.string.delete_entries_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_entries_confirmation,
                        uiState.selectedEntryIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (uiState.isSelectionMode) {
                FloatingActionButton(onClick = onDeleteSelectedClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_entries_fab)
                    )
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_history)) }
            )
        }
    ) { innerPadding ->
        if (uiState.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.history_empty_state))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(dimensionResource(R.dimen.padding_small)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
            ) {
                groupedEntries.forEach { (date, dateEntries) ->
                    item(key = "header-$date") {
                        Text(
                            text = date.format(dateFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(
                                top = dimensionResource(R.dimen.padding_small),
                                bottom = dimensionResource(R.dimen.padding_xsmall)
                            )
                        )
                    }

                    items(
                        items = dateEntries,
                        key = { it.uuid }
                    ) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            appLocale = appLocale,
                            timeFormatter = timeFormatter,
                            isSelected = entry.uuid in uiState.selectedEntryIds,
                            onClick = { onEntryClick(entry.uuid) },
                            onLongClick = { onEntryLongClick(entry.uuid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: MedicationLogEntry,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        overlineContent = {
            Text(text = stringResource(entry.routeOfAdministration.labelRes))
        },
        headlineContent = {
            Text(text = entry.medicineName)
        },
        trailingContent = {
            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))) {
                Text(
                    text = stringResource(
                        R.string.entry_medicine_dose,
                        entry.dosageMgAsMedicine.formatDose(appLocale)
                    )
                )
                Text(
                    text = entry.dosageMgAsEstradiol?.let {
                        stringResource(R.string.entry_estradiol_dose, it.formatDose(appLocale))
                    } ?: stringResource(R.string.history_unknown_estradiol_dose)
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        HistoryScreenContent(
            uiState = HistoryUiState(
                entries = listOf(
                    MedicationLogEntry(
                        uuid = UUID.fromString("f16ec8a7-5115-410a-b12d-f376fdb6f76b"),
                        routeOfAdministration = RouteOfAdministration.INTRAMUSCULAR,
                        medicineName = "Estradiol valerate",
                        dosageMgAsMedicine = 5.0,
                        dosageMgAsEstradiol = 3.82,
                        appliedAt = Instant.parse("2026-04-16T08:30:00Z")
                    ),
                    MedicationLogEntry(
                        uuid = UUID.fromString("9b9a1efe-6df3-43da-871d-9584370fbca8"),
                        routeOfAdministration = RouteOfAdministration.ORAL,
                        medicineName = "Estradiol",
                        dosageMgAsMedicine = 2.0,
                        dosageMgAsEstradiol = 2.0,
                        appliedAt = Instant.parse("2026-04-15T22:00:00Z")
                    ),
                    MedicationLogEntry(
                        uuid = UUID.fromString("611d7af2-6108-45ab-a320-4064e0dd1233"),
                        routeOfAdministration = RouteOfAdministration.SUBLINGUAL,
                        medicineName = "Estradiol",
                        dosageMgAsMedicine = 1.0,
                        dosageMgAsEstradiol = 1.0,
                        appliedAt = Instant.parse("2026-04-16T19:00:00Z")
                    )
                ),
                selectedEntryIds = setOf(UUID.fromString("611d7af2-6108-45ab-a320-4064e0dd1233"))
            ),
            onEntryClick = { },
            onEntryLongClick = { },
            onDeleteSelectedClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { }
        )
    }
}

private fun Double.formatDose(locale: Locale): String {
    return if (this % 1.0 == 0.0) {
        String.format(locale, "%.0f", this)
    } else {
        String.format(locale, "%.2f", this)
    }
}
