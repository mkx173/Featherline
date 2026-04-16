package com.mkx.hrttracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun HistoryScreen(
    onEntryClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    HistoryScreenContent(
        entries = entries,
        onEntryClick = onEntryClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    entries: List<MedicationLogEntry>,
    onEntryClick: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedEntries = entries.groupBy { entry ->
        entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_history)) }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
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
                            text = date.format(HISTORY_DATE_FORMATTER),
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
                            onClick = { onEntryClick(entry.uuid) }
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
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        overlineContent = {
            Text(text = entry.routeOfAdministration.displayName)
        },
        headlineContent = {
            Text(text = entry.medicineName)
        },
        trailingContent = {
            Text(
                text = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .format(HISTORY_TIME_FORMATTER)
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))) {
                Text(
                    text = stringResource(
                        R.string.entry_medicine_dose,
                        entry.dosageMgAsMedicine.formatDose()
                    )
                )
                Text(
                    text = entry.dosageMgAsEstradiol?.let {
                        stringResource(R.string.entry_estradiol_dose, it.formatDose())
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
            onEntryClick = { }
        )
    }
}

private fun Double.formatDose(): String {
    return if (this % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", this)
    } else {
        String.format(Locale.US, "%.2f", this)
    }
}

private val HISTORY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val HISTORY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
