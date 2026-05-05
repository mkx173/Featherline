package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseSupportingText
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberAppLocale
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
import java.time.temporal.ChronoUnit
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
private const val MainScheduleGraceMinutes = 60L

private enum class MainTodayTimeRange(
    val labelRes: Int,
    val startHour: Int,
    val endHour: Int,
) {
    NIGHT(
        labelRes = R.string.main_today_range_night,
        startHour = 0,
        endHour = 6
    ),
    MORNING(
        labelRes = R.string.main_today_range_morning,
        startHour = 6,
        endHour = 12
    ),
    AFTERNOON(
        labelRes = R.string.main_today_range_afternoon,
        startHour = 12,
        endHour = 18
    ),
    EVENING(
        labelRes = R.string.main_today_range_evening,
        startHour = 18,
        endHour = 24
    );
}

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
    val medicationName = medicationDisplayName(displayedDetails)
    val summaryText = medicationDoseSupportingText(
        details = displayedDetails,
        medicationCount = card.medication.count,
    )
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
                color = groupColorScheme.secondaryContainer,
                contentColor = groupColorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedicationApplicationIcon(
                        applicationType = displayedDetails.applicationType,
                        contentDescription = stringResource(displayedDetails.applicationType.labelRes),
                        modifier = Modifier.size(22.dp),
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
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = medicationName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedRows = remember(section.rows) {
        section.rows
            .groupBy { row -> mainTodayTimeRange(row.scheduledAt.toLocalTime()) }
            .entries
            .sortedBy { (timeRange, _) -> timeRange.ordinal }
    }
    val sectionSummary = listOf(
        dateFormatter(section.date),
        stringResource(
            R.string.main_today_progress,
            section.doneCount,
            section.totalCount
        )
    ).joinToString(separator = " · ")

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        MainSectionHeader(
            title = stringResource(R.string.main_today_title),
            summary = sectionSummary,
            emphasize = true
        )

        if (section.rows.isEmpty()) {
            SupportMessageListItem(
                text = stringResource(R.string.main_today_empty_state),
                painter = painterResource(R.drawable.ic_info),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                groupedRows.forEachIndexed { groupIndex, (timeRange, rows) ->
                    MainTodayTimeRangeHeader(
                        timeRange = timeRange,
                        isCurrent = timeRange == mainTodayTimeRange(now.toLocalTime()),
                        doneCount = rows.count { row -> row.status == MainTodayDoseStatus.DONE },
                        totalCount = rows.size,
                        timeFormatter = timeFormatter,
                        modifier = Modifier.padding(
                            top = if (groupIndex == 0) 0.dp else 2.dp
                        )
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                    ) {
                        rows.forEachIndexed { index, row ->
                            MainTodayDoseRow(
                                row = row,
                                index = index,
                                itemCount = rows.size,
                                now = now,
                                timeFormatter = timeFormatter,
                                onQuickLogDoseClick = onQuickLogDoseClick,
                                onEntryClick = onEntryClick
                            )
                        }
                    }
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
    ) {
        MainSectionHeader(
            title = title,
            summary = subtitle
        )

        if (section.rows.isEmpty()) {
            SupportMessageListItem(
                text = stringResource(R.string.main_upcoming_empty_state),
                painter = painterResource(R.drawable.ic_info),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
            ) {
                section.rows.forEachIndexed { index, row ->
                    MainUpcomingDoseRow(
                        row = row,
                        index = index,
                        itemCount = section.rows.size,
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
    index: Int,
    itemCount: Int,
    now: LocalDateTime,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    modifier: Modifier = Modifier
) {
    val details = row.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(row.groupColorKey)
    val headline = medicationDisplayName(details)
    val routeLabel = stringResource(details.applicationType.labelRes)
    val doseText = medicationDoseText(details)
    val supportingText = listOfNotNull(
        routeLabel,
        doseText,
    ).joinToString(separator = " · ")
    val entryEditorIds = mainTodayEntryEditorIds(row)
    val hasOnlyOutsideScheduleWindowLog = row.loggedAt == null &&
        row.outsideScheduleWindowLoggedAt != null
    val routeIconOutlined = hasOnlyOutsideScheduleWindowLog ||
        (row.loggedAt == null && !mainTodayIsInGracePeriod(
            scheduledFor = row.scheduledAt,
            now = now
        ))
    val onQuickLogClick = {
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
    val onStatusClick = {
        if (entryEditorIds.isNotEmpty()) {
            onEntryClick(entryEditorIds)
        } else {
            onQuickLogClick()
        }
    }

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = onStatusClick,
        modifier = modifier.fillMaxWidth(),
        trailingContent = {
            MainTodayTrailingContent(
                row = row,
                now = now,
                timeFormatter = timeFormatter,
                onStatusClick = onStatusClick
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MainRouteIconSurface(
                applicationType = details.applicationType,
                groupColorScheme = groupColorScheme,
                outlinedIcon = routeIconOutlined
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).alignByBaseline().cjkTextOffset(headline),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).alignByBaseline().cjkTextOffset(supportingText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MainUpcomingDoseRow(
    row: MainUpcomingDoseRowUiState,
    index: Int,
    itemCount: Int,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val details = row.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(row.groupColorKey)
    val headline = medicationDisplayName(details)
    val doseText = medicationDoseText(details)
    val headlinePlainText = listOfNotNull(
        headline,
        doseText
    ).joinToString(separator = " · ")
    val headlineText = buildAnnotatedString {
        append(headline)
        if (doseText != null) {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(" · ")
                append(doseText)
            }
        }
    }
    val extraSupportingText = if (showDate) {
        dateFormatter(row.scheduledAt.toLocalDate())
    } else {
        null
    }
    val supportingText = listOfNotNull(
        medicationCountIndicatorText(row.medication.count).takeIf { row.medication.count > 1 },
        extraSupportingText
    ).joinToString(separator = " · ")
    val timeLabel = row.scheduledAt.toLocalTime().format(timeFormatter)

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = { },
        modifier = modifier.fillMaxWidth(),
        trailingContent = {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MainRoutePill(
                applicationType = details.applicationType,
                groupColorScheme = groupColorScheme,
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.cjkTextOffset(headlinePlainText),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.cjkTextOffset(supportingText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTodayTrailingContent(
    row: MainTodayDoseRowUiState,
    now: LocalDateTime,
    timeFormatter: DateTimeFormatter,
    onStatusClick: () -> Unit,
) {
    val loggedAt = row.loggedAt ?: row.outsideScheduleWindowLoggedAt
    val isLogged = loggedAt != null
    val isMutedLogged = row.loggedAt == null && row.outsideScheduleWindowLoggedAt != null
    val isDueSoon = !isLogged && mainTodayIsInGracePeriod(
        scheduledFor = row.scheduledAt,
        now = now
    )
    val textLabel = when (loggedAt) {
        null -> when {
            mainTodayIsBeforeOrInGracePeriod(
                scheduledFor = row.scheduledAt,
                now = now
            ) -> MainTodayTrailingText(
                text = row.scheduledAt.toLocalTime().format(timeFormatter),
                isDelta = false
            )

            else -> mainTodayScheduleOffsetText(
                scheduledFor = row.scheduledAt,
                comparedAt = now
            )?.let { text ->
                MainTodayTrailingText(
                    text = text,
                    isDelta = true
                )
            }
        }

        else -> {
            mainTodayScheduleOffsetText(
                scheduledFor = row.scheduledAt,
                comparedAt = loggedAt
            )?.let { text ->
                MainTodayTrailingText(
                    text = text,
                    isDelta = true
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (textLabel != null && textLabel.text.isNotBlank()) {
            Text(
                text = textLabel.text,
                style = if (textLabel.isDelta) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (textLabel.isDelta) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (textLabel.isDelta) {
                    FontWeight.Normal
                } else {
                    FontWeight.Medium
                },
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
        MainTodayTrailingStatusButton(
            isLogged = isLogged,
            isMutedLogged = isMutedLogged,
            isDueSoon = isDueSoon,
            onClick = onStatusClick
        )
    }
}

private data class MainTodayTrailingText(
    val text: String,
    val isDelta: Boolean,
)

@Composable
private fun MainTodayTrailingStatusButton(
    isLogged: Boolean,
    isMutedLogged: Boolean,
    isDueSoon: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val actionDescription = stringResource(
        if (isLogged) R.string.edit_entry else R.string.main_today_quick_log
    )
    val containerColor = when {
        isMutedLogged -> colorScheme.surfaceContainerHighest
        isLogged -> colorScheme.primaryContainer
        isDueSoon -> colorScheme.tertiaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isLogged -> colorScheme.onPrimaryContainer
        isDueSoon -> colorScheme.onTertiaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    val border = if (!isLogged && !isDueSoon) {
        BorderStroke(1.dp, colorScheme.outlineVariant)
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = actionDescription
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLogged -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                isDueSoon -> Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun mainTodayScheduleOffsetText(
    scheduledFor: LocalDateTime,
    comparedAt: LocalDateTime,
): String? {
    val deltaMinutes = ChronoUnit.MINUTES.between(scheduledFor, comparedAt)
    val absoluteMinutes = kotlin.math.abs(deltaMinutes)
    if (absoluteMinutes < MainScheduleGraceMinutes) {
        return null
    }
    val isEarly = deltaMinutes < 0
    val absoluteHours = (absoluteMinutes / 60L).coerceAtLeast(1L)
    val quantity = absoluteHours.toInt()
    return pluralStringResource(
        if (isEarly) {
            R.plurals.main_today_schedule_offset_hours_earlier
        } else {
            R.plurals.main_today_schedule_offset_hours_later
        },
        quantity,
        quantity
    )
}

private fun mainTodayIsBeforeOrInGracePeriod(
    scheduledFor: LocalDateTime,
    now: LocalDateTime,
): Boolean {
    return !scheduledFor.isBefore(now.minusMinutes(MainScheduleGraceMinutes))
}

private fun mainTodayIsInGracePeriod(
    scheduledFor: LocalDateTime,
    now: LocalDateTime,
): Boolean {
    return kotlin.math.abs(
        ChronoUnit.MINUTES.between(scheduledFor, now)
    ) <= MainScheduleGraceMinutes
}

private fun mainTodayTimeRangeTimeLabel(
    timeRange: MainTodayTimeRange,
    timeFormatter: DateTimeFormatter,
): String {
    return listOf(
        mainTodayTimeRangeBoundaryLabel(timeRange.startHour, timeFormatter),
        mainTodayTimeRangeBoundaryLabel(timeRange.endHour, timeFormatter)
    ).joinToString(separator = "–")
}

private fun mainTodayTimeRangeBoundaryLabel(
    hour: Int,
    timeFormatter: DateTimeFormatter,
): String {
    return LocalTime.of(hour % 24, 0).format(timeFormatter)
}

@Composable
private fun MainRouteIconSurface(
    applicationType: MedicationApplicationType,
    groupColorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    surfaceSize: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    outlinedIcon: Boolean = false,
) {
    val applicationTypeLabel = stringResource(applicationType.labelRes)

    Box(
        modifier = modifier.size(surfaceSize),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.small,
            color = groupColorScheme.secondaryContainer,
            contentColor = groupColorScheme.onSecondaryContainer
        ) {
            Box(
                modifier = Modifier.size(surfaceSize),
                contentAlignment = Alignment.Center
            ) {
                MedicationApplicationIcon(
                    applicationType = applicationType,
                    contentDescription = applicationTypeLabel,
                    modifier = Modifier.size(iconSize),
                    outlined = outlinedIcon,
                )
            }
        }
    }
}

@Composable
private fun MainRoutePill(
    applicationType: MedicationApplicationType,
    groupColorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val routeLabel = stringResource(applicationType.labelRes)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = groupColorScheme.secondaryContainer.copy(alpha = 0.68f),
        contentColor = groupColorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MedicationApplicationIcon(
                applicationType = applicationType,
                contentDescription = routeLabel,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = routeLabel,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.cjkTextOffset(routeLabel),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MainSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    emphasize: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = if (emphasize) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.alignByBaseline().cjkTextOffset(title),
            maxLines = 1,
        )
        if (!summary.isNullOrEmpty()) {
            Text(
                text = summary.uppercase(),
                style = if (emphasize) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline().cjkTextOffset(summary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun MainTodayTimeRangeHeader(
    timeRange: MainTodayTimeRange,
    isCurrent: Boolean,
    doneCount: Int,
    totalCount: Int,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val timeRangeLabel = stringResource(timeRange.labelRes)
    val timeRangeTimeLabel = mainTodayTimeRangeTimeLabel(
        timeRange = timeRange,
        timeFormatter = timeFormatter
    )
    val countLabel = "$doneCount/$totalCount"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = if (isCurrent) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = timeRangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.alignByBaseline().cjkTextOffset(timeRangeLabel)
                )
                Text(
                    text = timeRangeTimeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.alignByBaseline().cjkTextOffset(appLocale)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun mainTodayTimeRange(time: LocalTime): MainTodayTimeRange {
    return when (time.hour) {
        in 0 until 6 -> MainTodayTimeRange.NIGHT
        in 6 until 12 -> MainTodayTimeRange.MORNING
        in 12 until 18 -> MainTodayTimeRange.AFTERNOON
        else -> MainTodayTimeRange.EVENING
    }
}

private fun mainTodayEntryEditorIds(row: MainTodayDoseRowUiState): Set<UUID> {
    return (row.fulfillingEntryUuids + row.outsideScheduleWindowEntryUuids).toSet()
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
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { _, _, _, _, _ -> },
            onEntryClick = { }
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
            index = 0,
            itemCount = 2,
            now = uiState.now,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { _, _, _, _, _ -> },
            onEntryClick = { }
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
            index = 0,
            itemCount = 3,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            showDate = true
        )
    }
}

@Preview(name = "Main Today Trailing Content", showBackground = true, widthDp = 260)
@Composable
private fun MainTodayTrailingContentPreview() {
    val uiState = buildMainContentPreviewUiState()
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            uiState.todaySection.rows.forEach { row ->
                MainTodayTrailingContent(
                    row = row,
                    now = uiState.now,
                    timeFormatter = timeFormatter,
                    onStatusClick = { }
                )
            }
        }
    }
}

@Preview(name = "Main Route Icon Surface", showBackground = true, widthDp = 180)
@Composable
private fun MainRouteIconSurfacePreview() {
    MainContentComponentPreviewContainer {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.ORAL,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.ROSE),
            )
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.PATCH_ON,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.TEAL),
            )
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.INJECTION,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.INDIGO),
                iconSize = 18.dp,
                surfaceSize = 30.dp,
                outlinedIcon = true
            )
        }
    }
}

@Preview(name = "Main Route Pill", showBackground = true, widthDp = 220)
@Composable
private fun MainRoutePillPreview() {
    MainContentComponentPreviewContainer {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainRoutePill(
                applicationType = MedicationApplicationType.ORAL,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.ROSE),
            )
            MainRoutePill(
                applicationType = MedicationApplicationType.PATCH_ON,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.TEAL),
            )
            MainRoutePill(
                applicationType = MedicationApplicationType.INJECTION,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.INDIGO),
            )
        }
    }
}

@Preview(name = "Main Section Header", showBackground = true, widthDp = 420)
@Composable
private fun MainSectionHeaderPreview() {
    MainContentComponentPreviewContainer {
        MainSectionHeader(
            title = "Today",
            summary = "May 5 · 1/4 done",
            emphasize = true
        )
    }
}

@Preview(name = "Main Today Time Range Header", showBackground = true, widthDp = 420)
@Composable
private fun MainTodayTimeRangeHeaderPreview() {
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodayTimeRangeHeader(
            timeRange = MainTodayTimeRange.MORNING,
            isCurrent = true,
            doneCount = 1,
            totalCount = 3,
            timeFormatter = timeFormatter
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
