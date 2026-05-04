package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AddChip
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.segmentedListItemShapes
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DailyScheduleEditor(
    sinceDate: LocalDate,
    intervalDays: String,
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    previewOccurrences: List<LocalDateTime>,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    sinceEnabled: Boolean = true,
    intervalEnabled: Boolean = true,
    addRemoveTimeEnabled: Boolean = true,
    timeEditEnabled: Boolean = true,
    shapeLocked: Boolean = false,
) {
    val totalCount = if (previewOccurrences.isNotEmpty()) 4 else 3
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = dateFormatter(sinceDate),
            icon = painterResource(R.drawable.ic_event),
            onClick = { onSinceDateChange(sinceDate) },
            enabled = sinceEnabled,
            locked = shapeLocked,
            index = 0,
            count = totalCount
        )

        IntervalStepperCard(
            label = stringResource(R.string.group_schedule_repeat_every),
            value = parseScheduleInterval(intervalDays),
            unit = pluralStringResource(
                R.plurals.group_schedule_days_unit,
                parseScheduleInterval(intervalDays),
                parseScheduleInterval(intervalDays)
            ),
            onDecreaseClick = { onIntervalChange(decrementScheduleInterval(intervalDays)) },
            onIncreaseClick = { onIntervalChange(incrementScheduleInterval(intervalDays)) },
            enabled = intervalEnabled,
            locked = shapeLocked,
            index = 1,
            count = totalCount
        )

        DailyTimesCard(
            times = dailyTimes,
            timeFormatter = timeFormatter,
            onAddTime = onAddTime,
            onTimeClick = onTimeClick,
            addRemoveTimeEnabled = addRemoveTimeEnabled,
            timeEditEnabled = timeEditEnabled,
            index = 2,
            count = totalCount
        )

        if (previewOccurrences.isNotEmpty()) {
            ScheduleOccurrencesCard(
                title = stringResource(R.string.group_schedule_preview),
                occurrences = previewOccurrences,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                index = 3,
                count = totalCount
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyTimesCard(
    times: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    addRemoveTimeEnabled: Boolean,
    timeEditEnabled: Boolean,
    index: Int = 0,
    count: Int = 1
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {},
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.group_schedule_times_with_count,
                        times.size
                    ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (addRemoveTimeEnabled) {
                    AddChip(onClick = onAddTime)
                }
            }
            val sortedTimes = sortDailyTimesByOriginalTime(times)
            sortedTimes.forEachIndexed { index, dailyTime ->
                DailyTimeRow(
                    formattedTime = dailyTime.time.format(timeFormatter),
                    formattedOriginalTime = dailyTime.originalTime?.format(timeFormatter),
                    onClick = { onTimeClick(dailyTime.localId, dailyTime.time) },
                    enabled = timeEditEnabled,
                    index = index,
                    count = sortedTimes.size
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DailyTimeRow(
    formattedTime: String,
    formattedOriginalTime: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    index: Int = 0,
    count: Int = 1
) {
    SegmentedListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingContent = if (enabled) {
            {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            null
        },
        onClick = if (enabled) onClick else {
            {}
        },
        enabled = true,
        shapes = segmentedListItemShapes(
            index = index,
            count = count,
            cornerShape = MaterialTheme.shapes.medium,
            pressedShape = MaterialTheme.shapes.medium
        ),
    ) {
        ChangedScheduleTimeText(
            value = formattedTime,
            originalValue = formattedOriginalTime,
        )
    }
}

internal fun canRemoveDailyTime(totalTimeCount: Int): Boolean = totalTimeCount > 1

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduleOccurrencesCard(
    title: String,
    occurrences: List<LocalDateTime>,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    index: Int = 0,
    count: Int = 1
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {},
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            occurrences.forEachIndexed { occurrenceIndex, occurrence ->
                ScheduleOccurrenceRow(
                    formattedTime = occurrence.toLocalTime().format(timeFormatter),
                    formattedDate = dateFormatter(occurrence.toLocalDate()),
                    index = occurrenceIndex,
                    count = occurrences.size
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ScheduleOccurrenceRow(
    formattedTime: String,
    formattedDate: String,
    index: Int = 0,
    count: Int = 1
) {
    SegmentedListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_event_upcoming),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = {},
        supportingContent = {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shapes = segmentedListItemShapes(
            index = index,
            count = count,
            cornerShape = MaterialTheme.shapes.medium,
            pressedShape = MaterialTheme.shapes.medium
        ),
    ) {
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun DailyScheduleEditorPreview() {
    val previewLocale = Locale.US
    HrtTrackerTheme(dynamicColor = false) {
        DailyScheduleEditor(
            sinceDate = LocalDate.of(2026, 4, 22),
            intervalDays = "3",
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)),
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(21, 30))
            ),
            previewOccurrences = listOf(
                LocalDateTime.of(2026, 4, 25, 21, 30),
                LocalDateTime.of(2026, 4, 28, 9, 0),
                LocalDateTime.of(2026, 4, 28, 21, 30)
            ),
            dateFormatter = medicationGroupScheduleDateFormatter(
                locale = previewLocale,
                today = LocalDate.of(2026, 4, 22),
            ),
            timeFormatter = localizedShortTimeFormatter(
                previewLocale,
                uses24HourFormat = false,
            ),
            onSinceDateChange = {},
            onIntervalChange = {},
            onAddTime = {},
            onTimeClick = { _, _ -> },
        )
    }
}
