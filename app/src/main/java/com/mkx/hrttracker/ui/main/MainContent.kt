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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.ui.medication.applicationTypeBadgeLabel
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
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
        item(key = "e2-hero") {
            MainE2HeroCard(
                section = uiState.e2Hero,
                now = uiState.now
            )
        }

        item(key = "e2-chart") {
            MainE2ChartCard(
                section = uiState.e2Chart,
                now = uiState.now,
                appLocale = appLocale
            )
        }

        uiState.antiandrogenCards.forEach { card ->
            item(key = "antiandrogen-${card.id}") {
                MainAntiandrogenCard(
                    card = card,
                    now = uiState.now,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter,
                    appLocale = appLocale
                )
            }
        }

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
private fun MainE2HeroCard(
    section: MainE2HeroUiState,
    now: LocalDateTime,
    modifier: Modifier = Modifier
) {
    val inRange = section.currentValue in section.targetMin..section.targetMax
    val trendDeltaLabel = mainTrendDeltaLabel(section.changeSinceYesterday)
    val trendIcon = when {
        section.changeSinceYesterday > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        section.changeSinceYesterday < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val lastDoseSummary = mainE2LastDoseSummary(
        section = section,
        now = now
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "E2",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .alpha(0.1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.main_e2_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (inRange) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (inRange) FulfilledStatusColor else MaterialTheme.colorScheme.error
                                    )
                            )
                            Text(
                                text = stringResource(
                                    if (inRange) {
                                        R.string.main_e2_status_in_range
                                    } else {
                                        R.string.main_e2_status_below_range
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (inRange) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = section.currentValue.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = section.unit,
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = trendIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(
                                    R.string.main_e2_change_since_yesterday,
                                    trendDeltaLabel
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                        )
                        Text(
                            text = lastDoseSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainE2ChartCard(
    section: MainE2ChartUiState,
    now: LocalDateTime,
    appLocale: Locale,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val xLabels = remember(section.points, now, appLocale) {
        section.points.indices.map { index ->
            now.toLocalDate()
                .minusDays((section.points.lastIndex - index).toLong())
                .dayOfWeek
                .getDisplayName(TextStyle.NARROW, appLocale)
        }
    }

    LaunchedEffect(section.points) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = section.points.indices.toList(),
                    y = section.points
                )
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.main_e2_chart_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = stringResource(R.string.main_e2_chart_target, 100, 200),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            value.toInt().toString()
                        }
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            xLabels.getOrElse(value.toInt()) { "" }
                        }
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp)
            )
        }
    }
}

@Composable
private fun MainAntiandrogenCard(
    card: MainAntiandrogenCardUiState,
    now: LocalDateTime,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    appLocale: Locale,
    modifier: Modifier = Modifier
) {
    val displayedDetails = card.lastDoseDetails ?: card.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(card.groupColorKey)
    val doseText = medicationDoseText(displayedDetails)
    val lastDoseText = card.lastDoseAt?.let { doseAt ->
        stringResource(
            R.string.main_antiandrogen_last_dose_elapsed,
            mainElapsedDurationLabel(from = doseAt, to = now)
        )
    } ?: stringResource(R.string.main_antiandrogen_no_last_dose)
    val nextDoseText = card.nextDoseAt?.let { nextDoseAt ->
        stringResource(
            R.string.main_antiandrogen_next_dose,
            mainRelativeDateTimeLabel(
                target = nextDoseAt,
                now = now,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                appLocale = appLocale
            )
        )
    } ?: stringResource(R.string.main_antiandrogen_no_next_dose)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = groupColorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = groupColorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.main_antiandrogen_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    MainMedicationCountBadge(
                        count = card.medication.count,
                        groupColorScheme = groupColorScheme
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = listOfNotNull(
                            medicationDisplayName(displayedDetails),
                            doseText
                        ).joinToString(separator = " · "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = FulfilledStatusColor
                        )
                        Text(
                            text = lastDoseText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = nextDoseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
    val details = row.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(row.groupColorKey)
    val rowColors = todayRowColors(mainTodayRowTone(row.status))
    val headline = medicationDisplayName(details)
    val supportingText = supportingText(
        details = details,
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
                    .background(groupColorScheme.primary)
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
                    MainApplicationBadge(
                        label = applicationTypeBadgeLabel(details.applicationType),
                        groupColorScheme = groupColorScheme
                    )

                    MainMedicationCountBadge(
                        count = row.medication.count,
                        groupColorScheme = groupColorScheme
                    )

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
    val details = row.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(row.groupColorKey)
    val headline = medicationDisplayName(details)
    val supportingText = supportingText(
        details = details,
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
                MainApplicationBadge(
                    label = applicationTypeBadgeLabel(details.applicationType),
                    groupColorScheme = groupColorScheme
                )

                MainMedicationCountBadge(
                    count = row.medication.count,
                    groupColorScheme = groupColorScheme
                )

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
    groupColorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = groupColorScheme.primaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = groupColorScheme.onPrimaryContainer,
            letterSpacing = 0.6.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MainMedicationCountBadge(
    count: Int,
    groupColorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = groupColorScheme.secondaryContainer
    ) {
        Text(
            text = medicationCountIndicatorText(count),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = groupColorScheme.onSecondaryContainer,
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
    details: MedicationDetails,
    groupName: String,
    includeDate: String?,
): String {
    val parts = buildList {
        includeDate?.let(::add)
        medicationDoseText(details)?.let(::add)
        val headline = medicationDisplayName(details)
        if (groupName != headline) {
            add(groupName)
        }
    }
    return parts.joinToString(separator = " · ")
}

@Composable
private fun mainE2LastDoseSummary(
    section: MainE2HeroUiState,
    now: LocalDateTime
): String {
    val lastDoseAt = section.lastDoseAt ?: return stringResource(R.string.main_e2_no_last_dose)
    val elapsedLabel = mainElapsedDurationLabel(
        from = lastDoseAt,
        to = now
    )
    val doseText = section.lastDoseDetails?.let { details -> medicationDoseText(details) }
    return if (doseText != null) {
        stringResource(
            R.string.main_e2_last_dose_with_amount_and_time,
            doseText,
            elapsedLabel
        )
    } else {
        stringResource(
            R.string.main_e2_last_dose_with_time,
            elapsedLabel
        )
    }
}

@Composable
private fun mainElapsedDurationLabel(
    from: LocalDateTime,
    to: LocalDateTime
): String {
    val duration = Duration.between(from, to)
    val clampedSeconds = duration.seconds.coerceAtLeast(0)
    val totalMinutes = clampedSeconds / 60
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> stringResource(
            R.string.main_duration_days_hours,
            days,
            hours
        )

        hours > 0 -> stringResource(
            R.string.main_duration_hours_minutes,
            hours,
            minutes
        )

        else -> stringResource(
            R.string.main_duration_minutes,
            minutes.coerceAtLeast(1)
        )
    }
}

@Composable
private fun mainRelativeDateTimeLabel(
    target: LocalDateTime,
    now: LocalDateTime,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    appLocale: Locale
): String {
    val timeText = target.toLocalTime().format(timeFormatter)
    return when (target.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.main_relative_today_time, timeText)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.main_relative_tomorrow_time, timeText)
        else -> stringResource(
            R.string.main_relative_date_time,
            target.toLocalDate().format(dateFormatter.withLocale(appLocale)),
            timeText
        )
    }
}

private fun mainTrendDeltaLabel(changeSinceYesterday: Int): String {
    return when {
        changeSinceYesterday > 0 -> "+$changeSinceYesterday"
        changeSinceYesterday < 0 -> changeSinceYesterday.toString()
        else -> "0"
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

private data class MainTodayRowColors(
    val containerColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val statusColor: Color,
)
