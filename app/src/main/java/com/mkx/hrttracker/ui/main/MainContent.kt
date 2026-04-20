package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun MainContent(
    uiState: MainUiState,
    onQuickLogDoseClick: (UUID, LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium))
    ) {
        item(key = "today") {
            MainTodaySection(
                section = uiState.todaySection,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                onQuickLogDoseClick = onQuickLogDoseClick
            )
        }

        item(key = "upcoming") {
            MainUpcomingSection(
                section = uiState.upcomingSection,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter
            )
        }
    }
}

@Composable
private fun MainTodaySection(
    section: MainTodaySectionUiState,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (UUID, LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        SectionHeader(
            title = stringResource(R.string.main_today_title),
            subtitle = section.date.format(dateFormatter),
            trailing = stringResource(
                R.string.main_today_progress,
                section.doneCount,
                section.totalCount
            )
        )

        if (section.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.main_today_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card {
                Column {
                    section.rows.forEachIndexed { index, row ->
                        MainTodayDoseRow(
                            row = row,
                            timeFormatter = timeFormatter,
                            onQuickLogDoseClick = onQuickLogDoseClick
                        )
                        if (index < section.rows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainUpcomingSection(
    section: MainUpcomingSectionUiState,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val title = when (section.title) {
        MainUpcomingSectionTitle.TOMORROW -> stringResource(R.string.main_tomorrow_title)
        MainUpcomingSectionTitle.UPCOMING -> stringResource(R.string.main_upcoming_title)
    }

    val subtitle = section.anchorDate?.format(dateFormatter).orEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        SectionHeader(
            title = title,
            subtitle = subtitle
        )

        if (section.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.main_upcoming_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card {
                Column {
                    section.rows.forEachIndexed { index, row ->
                        MainUpcomingDoseRow(
                            row = row,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            showDate = section.title == MainUpcomingSectionTitle.UPCOMING
                        )
                        if (index < section.rows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTodayDoseRow(
    row: MainTodayDoseRowUiState,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (UUID, LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (row.status) {
        MainTodayDoseStatus.DONE -> row.loggedAt?.let {
            stringResource(R.string.main_today_status_logged_at, it.toLocalTime().format(timeFormatter))
        } ?: stringResource(R.string.main_today_status_done)
        MainTodayDoseStatus.DUE_SOON -> stringResource(R.string.main_today_status_due_soon)
        MainTodayDoseStatus.UPCOMING -> stringResource(R.string.main_today_status_upcoming)
        MainTodayDoseStatus.OVERDUE -> stringResource(R.string.main_today_status_overdue)
    }
    val statusColor = when (row.status) {
        MainTodayDoseStatus.DONE -> MaterialTheme.colorScheme.tertiary
        MainTodayDoseStatus.DUE_SOON -> MaterialTheme.colorScheme.primary
        MainTodayDoseStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
        MainTodayDoseStatus.OVERDUE -> MaterialTheme.colorScheme.error
    }

    ListItem(
        modifier = modifier.fillMaxWidth(),
        overlineContent = {
            Text(text = row.scheduledAt.toLocalTime().format(timeFormatter))
        },
        headlineContent = {
            Text(text = row.groupName)
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                MainMedicationsText(medications = row.medications)
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        trailingContent = {
            if (row.status != MainTodayDoseStatus.DONE) {
                TextButton(
                    onClick = {
                        onQuickLogDoseClick(row.groupUuid, row.scheduledAt)
                    }
                ) {
                    Text(text = stringResource(R.string.main_today_quick_log))
                }
            }
        }
    )
}

@Composable
private fun MainUpcomingDoseRow(
    row: MainUpcomingDoseRowUiState,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val overline = if (showDate) {
        stringResource(
            R.string.main_upcoming_datetime_format,
            row.scheduledAt.toLocalDate().format(dateFormatter),
            row.scheduledAt.toLocalTime().format(timeFormatter)
        )
    } else {
        row.scheduledAt.toLocalTime().format(timeFormatter)
    }

    ListItem(
        modifier = modifier.fillMaxWidth(),
        overlineContent = {
            Text(text = overline)
        },
        headlineContent = {
            Text(text = row.groupName)
        },
        supportingContent = {
            MainMedicationsText(medications = row.medications)
        }
    )
}

@Composable
private fun MainMedicationsText(
    medications: List<MedicationGroupMedication>,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
    ) {
        medications.take(3).forEach { medication ->
            Text(
                text = stringResource(
                    R.string.plan_group_medication_summary,
                    medication.medicineName,
                    medication.dosageMgAsMedicine.formatDose(appLocale),
                    stringResource(medication.routeOfAdministration.labelRes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        val hiddenCount = medications.size - 3
        if (hiddenCount > 0) {
            Text(
                text = stringResource(R.string.plan_group_more_medications, hiddenCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!trailing.isNullOrEmpty()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
