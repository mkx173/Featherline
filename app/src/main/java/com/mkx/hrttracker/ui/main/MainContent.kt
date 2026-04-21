package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.ui.medication.applicationTypeBadgeLabel
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

private val FulfilledStatusColor = Color(0xFF2E7D32)

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
        contentPadding = PaddingValues(
            start = dimensionResource(R.dimen.padding_medium),
            top = dimensionResource(R.dimen.padding_medium),
            end = dimensionResource(R.dimen.padding_medium),
            bottom = dimensionResource(R.dimen.padding_large),
        ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_large))
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
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
            ) {
                section.rows.forEach { row ->
                    MainTodayDoseRow(
                        row = row,
                        timeFormatter = timeFormatter,
                        onQuickLogDoseClick = onQuickLogDoseClick
                    )
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
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                section.rows.forEach { row ->
                    MainUpcomingDoseRow(
                        row = row,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        showDate = section.title == MainUpcomingSectionTitle.UPCOMING
                    )
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
    val details = row.primaryMedicationDetails()
    val accentColors = accentColors(mainMedicationAccent(details))
    val rowColors = todayRowColors(mainTodayRowTone(row.status))
    val headline = details?.let { medicationDisplayName(it) } ?: row.groupName
    val supportingText = supportingText(
        details = details,
        medications = row.medications,
        groupName = row.groupName,
        includeDate = null,
    )
    val statusText = when (row.status) {
        MainTodayDoseStatus.DONE -> row.loggedAt?.let {
            stringResource(R.string.main_today_status_logged_at, it.toLocalTime().format(timeFormatter))
        } ?: stringResource(R.string.main_today_status_done)
        MainTodayDoseStatus.DUE_SOON -> stringResource(R.string.main_today_status_due_soon)
        MainTodayDoseStatus.UPCOMING -> stringResource(R.string.main_today_status_upcoming)
        MainTodayDoseStatus.OVERDUE -> stringResource(R.string.main_today_status_overdue)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = rowColors.containerColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(rowColors.borderWidth, rowColors.borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTimeColumn(
                primaryLabel = row.scheduledAt.toLocalTime().format(timeFormatter),
                modifier = Modifier.width(52.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accentColors.barColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    details?.let {
                        MainApplicationBadge(
                            label = applicationTypeBadgeLabel(it.applicationType),
                            colors = accentColors
                        )
                    }

                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (supportingText.isNotEmpty()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = statusText,
                    color = rowColors.statusColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (row.status == MainTodayDoseStatus.DONE) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = FulfilledStatusColor
                        )
                    }
                }
            } else {
                MainTodayActionButton(
                    status = row.status,
                    onClick = {
                        onQuickLogDoseClick(row.groupUuid, row.scheduledAt)
                    }
                )
            }
        }
    }
}

@Composable
private fun MainUpcomingDoseRow(
    row: MainUpcomingDoseRowUiState,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val details = row.primaryMedicationDetails()
    val accentColors = accentColors(mainMedicationAccent(details))
    val headline = details?.let { medicationDisplayName(it) } ?: row.groupName
    val supportingText = supportingText(
        details = details,
        medications = row.medications,
        groupName = row.groupName,
        includeDate = if (showDate) row.scheduledAt.toLocalDate().format(dateFormatter) else null,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainTimeColumn(
            primaryLabel = row.scheduledAt.toLocalTime().format(timeFormatter),
            modifier = Modifier.width(52.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                details?.let {
                    MainApplicationBadge(
                        label = applicationTypeBadgeLabel(it.applicationType),
                        colors = accentColors
                    )
                }

                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (supportingText.isNotEmpty()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MainApplicationBadge(
    label: String,
    colors: MainAccentColors,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = colors.badgeContainerColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.badgeContentColor,
            letterSpacing = 0.6.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MainTodayActionButton(
    status: MainTodayDoseStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (status) {
        MainTodayDoseStatus.DUE_SOON -> colorScheme.primary
        MainTodayDoseStatus.OVERDUE -> colorScheme.errorContainer
        MainTodayDoseStatus.UPCOMING -> Color.Transparent
        MainTodayDoseStatus.DONE -> Color.Transparent
    }
    val contentColor = when (status) {
        MainTodayDoseStatus.DUE_SOON -> colorScheme.onPrimary
        MainTodayDoseStatus.OVERDUE -> colorScheme.onErrorContainer
        MainTodayDoseStatus.UPCOMING -> colorScheme.primary
        MainTodayDoseStatus.DONE -> colorScheme.onSurface
    }
    val border = when (status) {
        MainTodayDoseStatus.UPCOMING -> BorderStroke(1.dp, colorScheme.outline)
        else -> null
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        border = border
    ) {
        Text(
            text = stringResource(R.string.main_today_quick_log),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun MainTimeColumn(
    primaryLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = primaryLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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

@Composable
private fun supportingText(
    details: MedicationDetails?,
    medications: List<MedicationGroupMedication>,
    groupName: String,
    includeDate: String?,
): String {
    val parts = buildList {
        includeDate?.let(::add)
        details?.let { medicationDoseText(it) }?.let(::add)
        val hiddenCount = medications.size - 1
        if (hiddenCount > 0) {
            add(stringResource(R.string.plan_group_more_medications, hiddenCount))
        }
        val headline = details?.let { medicationDisplayName(it) }
        if (groupName != headline) {
            add(groupName)
        }
    }
    return parts.joinToString(separator = " · ")
}

private fun MainTodayDoseRowUiState.primaryMedicationDetails(): MedicationDetails? {
    return medications.firstOrNull()?.details
}

private fun MainUpcomingDoseRowUiState.primaryMedicationDetails(): MedicationDetails? {
    return medications.firstOrNull()?.details
}

@Composable
private fun accentColors(accent: MainMedicationAccent): MainAccentColors {
    val colorScheme = MaterialTheme.colorScheme
    return when (accent) {
        MainMedicationAccent.PRIMARY -> MainAccentColors(
            badgeContainerColor = colorScheme.primaryContainer,
            badgeContentColor = colorScheme.onPrimaryContainer,
            barColor = colorScheme.primary
        )

        MainMedicationAccent.SECONDARY -> MainAccentColors(
            badgeContainerColor = colorScheme.secondaryContainer,
            badgeContentColor = colorScheme.onSecondaryContainer,
            barColor = colorScheme.secondary
        )

        MainMedicationAccent.TERTIARY -> MainAccentColors(
            badgeContainerColor = colorScheme.tertiaryContainer,
            badgeContentColor = colorScheme.onTertiaryContainer,
            barColor = colorScheme.tertiary
        )

        MainMedicationAccent.NEUTRAL -> MainAccentColors(
            badgeContainerColor = colorScheme.surfaceContainerHighest,
            badgeContentColor = colorScheme.onSurfaceVariant,
            barColor = colorScheme.outline
        )
    }
}

@Composable
private fun todayRowColors(tone: MainTodayRowTone): MainTodayRowColors {
    val colorScheme = MaterialTheme.colorScheme
    return when (tone) {
        MainTodayRowTone.DEFAULT -> MainTodayRowColors(
            containerColor = colorScheme.surfaceContainerLow,
            borderColor = colorScheme.outlineVariant,
            borderWidth = 1.dp,
            statusColor = colorScheme.onSurfaceVariant
        )

        MainTodayRowTone.DUE_SOON -> MainTodayRowColors(
            containerColor = colorScheme.surfaceContainerHigh,
            borderColor = colorScheme.primary,
            borderWidth = 1.5.dp,
            statusColor = colorScheme.primary
        )

        MainTodayRowTone.OVERDUE -> MainTodayRowColors(
            containerColor = colorScheme.errorContainer,
            borderColor = colorScheme.error,
            borderWidth = 1.5.dp,
            statusColor = colorScheme.error
        )

        MainTodayRowTone.DONE -> MainTodayRowColors(
            containerColor = colorScheme.surfaceContainerLow,
            borderColor = colorScheme.outlineVariant,
            borderWidth = 1.dp,
            statusColor = FulfilledStatusColor
        )
    }
}

private data class MainAccentColors(
    val badgeContainerColor: Color,
    val badgeContentColor: Color,
    val barColor: Color,
)

private data class MainTodayRowColors(
    val containerColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val statusColor: Color,
)
