package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.formatSummary
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
fun PlanScreen(
    onGroupClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanScreenContent(
        uiState = uiState,
        onGroupClick = onGroupClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanScreenContent(
    uiState: PlanUiState,
    onGroupClick: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_plan)) }
            )
        }
    ) { innerPadding ->
        if (uiState.medicationGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.plan_empty_state))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(dimensionResource(R.dimen.padding_small)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
            ) {
                items(
                    items = uiState.medicationGroups,
                    key = { it.uuid }
                ) { group ->
                    MedicationGroupCard(
                        group = group,
                        appLocale = appLocale,
                        timeFormatter = timeFormatter,
                        onClick = { onGroupClick(group.uuid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationGroupCard(
    group: MedicationGroup,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                Text(
                    text = group.schedule.formatSummary(
                        locale = appLocale,
                        timeFormatter = timeFormatter,
                        dailyLabel = stringResource(
                            R.string.group_schedule_daily_summary,
                            group.schedule.interval
                        ),
                        weeklyLabel = stringResource(
                            R.string.group_schedule_weekly_summary,
                            group.schedule.interval
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                group.medications.take(3).forEach { medication ->
                    Text(
                        text = stringResource(
                            R.string.plan_group_medication_summary,
                            medication.medicineName,
                            medication.dosageMgAsMedicine.formatDose(appLocale),
                            stringResource(medication.routeOfAdministration.labelRes)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val hiddenMedicationCount = group.medications.size - 3
                if (hiddenMedicationCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.plan_group_more_medications,
                            hiddenMedicationCount
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}
