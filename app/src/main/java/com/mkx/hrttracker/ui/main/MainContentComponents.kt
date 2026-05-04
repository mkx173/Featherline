package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.ui.medication.applicationTypeBadgeLabel
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val FulfilledStatusColor = Color(0xFF2E7D32)
private val MainPreviewNow = LocalDateTime.of(2026, 5, 5, 10, 30)
private val PreviewEstradiolGroupUuid = UUID.fromString("56c7730e-1273-4de3-8d92-1a77953aa2e4")
private val PreviewPatchGroupUuid = UUID.fromString("4d6ac2b0-1185-4718-91d7-e74d4ec19488")
private val PreviewAntiandrogenGroupUuid = UUID.fromString("f5b5ef28-7364-47b4-9d92-2527e9b7b753")
private val PreviewMorningScheduleUuid = UUID.fromString("f4ea56fc-0856-485e-9af2-d1bc7caa341c")
private val PreviewPatchScheduleUuid = UUID.fromString("99cc2da0-e3ea-4bbd-a3ce-d6532b73f474")
private val PreviewAntiandrogenScheduleUuid = UUID.fromString("35bbf5a3-7ee0-4c93-b8cf-54292964a3d7")

@Composable
internal fun MainE2HeroCard(
    section: MainE2HeroUiState,
    now: LocalDateTime,
    modifier: Modifier = Modifier
) {
    val inRange = section.currentValue in section.targetMin..section.targetMax
    val trendDeltaLabel = mainTrendDeltaLabel(section.changeSinceYesterday)
    val trendIcon = when {
        section.changeSinceYesterday > 0 -> Icons.AutoMirrored.Rounded.TrendingUp
        section.changeSinceYesterday < 0 -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingFlat
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
                        shape = CircleShape,
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
                                    .clip(CircleShape)
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
                        shape = CircleShape,
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
                            imageVector = Icons.Rounded.Schedule,
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
internal fun MainE2ChartCard(
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
                        imageVector = Icons.AutoMirrored.Rounded.ShowChart,
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
                    shape = CircleShape,
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
internal fun MainAntiandrogenCard(
    card: MainAntiandrogenCardUiState,
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
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
                timeFormatter = timeFormatter
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
                        imageVector = Icons.Rounded.Medication,
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
                            imageVector = Icons.Rounded.CheckCircle,
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
internal fun MainTodaySection(
    section: MainTodaySectionUiState,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
    ) {
        SectionHeader(
            title = stringResource(R.string.main_today_title),
            subtitle = dateFormatter(section.date),
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
internal fun MainUpcomingSection(
    section: MainUpcomingSectionUiState,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val title = when (section.title) {
        MainUpcomingSectionTitle.TOMORROW -> stringResource(R.string.main_tomorrow_title)
        MainUpcomingSectionTitle.UPCOMING -> stringResource(R.string.main_upcoming_title)
    }

    val subtitle = section.anchorDate?.let(dateFormatter).orEmpty()

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
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
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
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = FulfilledStatusColor
                        )
                    }
                }
            } else {
                MainTodayActionButton(
                    status = row.status,
                    onClick = {
                        onQuickLogDoseClick(
                            row.groupUuid,
                            row.scheduleTimeUuid,
                            row.scheduledAt,
                            row.medication.details,
                            remainingQuickLogCount(
                                totalCount = row.medication.count,
                                fulfilledCount = row.loggedCount
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun MainUpcomingDoseRow(
    row: MainUpcomingDoseRowUiState,
    dateFormatter: LocalDateFormatter,
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
        includeDate = if (showDate) dateFormatter(row.scheduledAt.toLocalDate()) else null,
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
    val shape = RoundedCornerShape(percent = 50)
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
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter
): String {
    val timeText = target.toLocalTime().format(timeFormatter)
    return when (target.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.main_relative_today_time, timeText)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.main_relative_tomorrow_time, timeText)
        else -> stringResource(
            R.string.main_relative_date_time,
            dateFormatter(target.toLocalDate()),
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

private fun remainingQuickLogCount(
    totalCount: Int,
    fulfilledCount: Int
): Int {
    return totalCount - fulfilledCount
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

@Preview(name = "Main E2 Hero Card", showBackground = true, widthDp = 420)
@Composable
private fun MainE2HeroCardPreview() {
    val uiState = buildMainContentPreviewUiState()

    MainContentComponentPreviewContainer {
        MainE2HeroCard(
            section = uiState.e2Hero,
            now = uiState.now
        )
    }
}

@Preview(name = "Main E2 Chart Card", showBackground = true, widthDp = 420)
@Composable
private fun MainE2ChartCardPreview() {
    val uiState = buildMainContentPreviewUiState()

    MainContentComponentPreviewContainer {
        MainE2ChartCard(
            section = uiState.e2Chart,
            now = uiState.now,
            appLocale = Locale.US
        )
    }
}

@Preview(name = "Main Antiandrogen Card", showBackground = true, widthDp = 420)
@Composable
private fun MainAntiandrogenCardPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainAntiandrogenCard(
            card = uiState.antiandrogenCards.first(),
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter
        )
    }
}

@Preview(name = "Main Today Section", showBackground = true, widthDp = 420, heightDp = 430)
@Composable
private fun MainTodaySectionPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodaySection(
            section = uiState.todaySection,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Main Upcoming Section", showBackground = true, widthDp = 420)
@Composable
private fun MainUpcomingSectionPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainUpcomingSection(
            section = uiState.upcomingSection,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter
        )
    }
}

@Preview(name = "Main Today Dose Row", showBackground = true, widthDp = 420)
@Composable
private fun MainTodayDoseRowPreview() {
    val uiState = buildMainContentPreviewUiState()
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodayDoseRow(
            row = uiState.todaySection.rows.first { it.status == MainTodayDoseStatus.DUE_SOON },
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Main Upcoming Dose Row", showBackground = true, widthDp = 420)
@Composable
private fun MainUpcomingDoseRowPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainUpcomingDoseRow(
            row = uiState.upcomingSection.rows.first(),
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            showDate = true
        )
    }
}

@Preview(name = "Main Application Badge", showBackground = true, widthDp = 180)
@Composable
private fun MainApplicationBadgePreview() {
    MainContentComponentPreviewContainer {
        MainApplicationBadge(
            label = "ORAL",
            groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.ROSE)
        )
    }
}

@Preview(name = "Main Medication Count Badge", showBackground = true, widthDp = 180)
@Composable
private fun MainMedicationCountBadgePreview() {
    MainContentComponentPreviewContainer {
        MainMedicationCountBadge(
            count = 2,
            groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.INDIGO)
        )
    }
}

@Preview(name = "Main Today Action Button", showBackground = true, widthDp = 180)
@Composable
private fun MainTodayActionButtonPreview() {
    MainContentComponentPreviewContainer {
        MainTodayActionButton(
            status = MainTodayDoseStatus.DUE_SOON,
            onClick = { }
        )
    }
}

@Preview(name = "Main Time Column", showBackground = true, widthDp = 120)
@Composable
private fun MainTimeColumnPreview() {
    MainContentComponentPreviewContainer {
        MainTimeColumn(primaryLabel = "10:45 AM")
    }
}

@Preview(name = "Main Section Header", showBackground = true, widthDp = 420)
@Composable
private fun SectionHeaderPreview() {
    MainContentComponentPreviewContainer {
        SectionHeader(
            title = "Today",
            subtitle = "May 5",
            trailing = "1/4 done"
        )
    }
}

@Composable
private fun MainContentComponentPreviewContainer(
    content: @Composable () -> Unit
) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

internal fun buildMainContentPreviewUiState(): MainUiState {
    val now = MainPreviewNow
    val estradiolTablet = previewMedication(
        uuid = "e0d4fc52-8a75-4cf5-9ab3-f28fbd9a5ea7",
        key = MedicationKey.ESTRADIOL,
        applicationType = MedicationApplicationType.ORAL,
        dose = MedicationDose.MgAsMedicine(2.0)
    )
    val estradiolSublingual = previewMedication(
        uuid = "98f22e3e-ef38-4701-9f73-94a6b3430d9b",
        key = MedicationKey.ESTRADIOL_VALERATE,
        applicationType = MedicationApplicationType.SUBLINGUAL,
        dose = MedicationDose.MgAsMedicine(1.0)
    )
    val estradiolPatch = previewMedication(
        uuid = "e483ee93-976d-445a-9b0d-78ee8753d59e",
        key = MedicationKey.ESTRADIOL_PATCH,
        applicationType = MedicationApplicationType.PATCH_ON,
        dose = MedicationDose.PatchReleaseRateMcgPerDay(100.0)
    )
    val spironolactone = previewMedication(
        uuid = "be598126-5d8f-4daa-bdc6-54d45035f3f3",
        key = MedicationKey.SPIRONOLACTONE,
        applicationType = MedicationApplicationType.ORAL,
        dose = MedicationDose.MgAsMedicine(100.0)
    )

    return MainUiState(
        isLoading = false,
        now = now,
        e2Hero = MainE2HeroUiState(
            currentValue = 147,
            changeSinceYesterday = 8,
            targetMin = 100,
            targetMax = 200,
            lastDoseDetails = estradiolTablet.details,
            lastDoseAt = now.minusHours(2).minusMinutes(25)
        ),
        e2Chart = MainE2ChartUiState(
            points = listOf(132f, 148f, 162f, 155f, 176f, 139f, 147f)
        ),
        antiandrogenCards = listOf(
            MainAntiandrogenCardUiState(
                id = "preview-spiro",
                groupUuid = PreviewAntiandrogenGroupUuid,
                groupName = "Antiandrogen",
                groupColorKey = MedicationGroupColorKey.INDIGO,
                medication = spironolactone,
                lastDoseDetails = spironolactone.details,
                lastDoseAt = now.minusHours(1).minusMinutes(10),
                nextDoseAt = now.plusHours(11).plusMinutes(30)
            )
        ),
        todaySection = MainTodaySectionUiState(
            date = now.toLocalDate(),
            doneCount = 1,
            totalCount = 4,
            rows = listOf(
                MainTodayDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Morning estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = PreviewMorningScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(8, 0)),
                    medication = estradiolTablet,
                    status = MainTodayDoseStatus.DONE,
                    loggedAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(8, 3)),
                    loggedCount = 1
                ),
                MainTodayDoseRowUiState(
                    groupUuid = PreviewPatchGroupUuid,
                    groupName = "Patch change",
                    groupColorKey = MedicationGroupColorKey.TEAL,
                    scheduleTimeUuid = PreviewPatchScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(9, 0)),
                    medication = estradiolPatch,
                    status = MainTodayDoseStatus.OVERDUE
                ),
                MainTodayDoseRowUiState(
                    groupUuid = PreviewAntiandrogenGroupUuid,
                    groupName = "Antiandrogen",
                    groupColorKey = MedicationGroupColorKey.INDIGO,
                    scheduleTimeUuid = PreviewAntiandrogenScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(10, 45)),
                    medication = spironolactone,
                    status = MainTodayDoseStatus.DUE_SOON
                ),
                MainTodayDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Afternoon estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = null,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(14, 0)),
                    medication = estradiolSublingual,
                    status = MainTodayDoseStatus.UPCOMING
                ),
            )
        ),
        upcomingSection = MainUpcomingSectionUiState(
            title = MainUpcomingSectionTitle.TOMORROW,
            anchorDate = now.toLocalDate().plusDays(1),
            rows = listOf(
                MainUpcomingDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Morning estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = PreviewMorningScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(8, 0)),
                    medication = estradiolTablet
                ),
                MainUpcomingDoseRowUiState(
                    groupUuid = PreviewAntiandrogenGroupUuid,
                    groupName = "Antiandrogen",
                    groupColorKey = MedicationGroupColorKey.INDIGO,
                    scheduleTimeUuid = PreviewAntiandrogenScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(22, 0)),
                    medication = spironolactone
                ),
            )
        )
    )
}

private fun previewMedication(
    uuid: String,
    key: MedicationKey,
    applicationType: MedicationApplicationType,
    dose: MedicationDose,
    count: Int = 1
): MedicationGroupMedication {
    return MedicationGroupMedication(
        uuid = UUID.fromString(uuid),
        details = MedicationDetails(
            category = key.category,
            applicationType = applicationType,
            selection = MedicationSelection.Catalog(key),
            dose = dose
        ),
        count = count
    )
}
