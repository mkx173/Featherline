package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AddChip
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun DailyScheduleEditor(
    sinceDate: LocalDate,
    intervalDays: String,
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    onRemoveTime: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = sinceDate.format(dateFormatter),
            icon = Icons.Rounded.Event,
            onClick = { onSinceDateChange(sinceDate) },
            index = 0,
            count = 3
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
            index = 1,
            count = 3
        )

        DailyTimesCard(
            times = dailyTimes,
            timeFormatter = timeFormatter,
            onAddTime = onAddTime,
            onTimeClick = onTimeClick,
            onRemoveTime = onRemoveTime,
            index = 2,
            count = 3
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyTimesCard(
    times: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    onRemoveTime: (String) -> Unit,
    index: Int = 0,
    count: Int = 1
) {
    SegmentedListItem (
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        onClick = {}
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.group_schedule_times_with_count,
                        times.size
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AddChip(onClick = onAddTime)
            }
            val removeEnabled = canRemoveDailyTime(times.size)
            times.forEachIndexed { index, dailyTime ->
                DailyTimeRow(
                    formattedTime = dailyTime.time.format(timeFormatter),
                    onClick = { onTimeClick(dailyTime.localId, dailyTime.time) },
                    removeEnabled = removeEnabled,
                    onRemoveClick = { onRemoveTime(dailyTime.localId) },
                    index = index,
                    count = times.size
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DailyTimeRow(
    formattedTime: String,
    onClick: () -> Unit,
    removeEnabled: Boolean,
    onRemoveClick: () -> Unit,
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
        trailingContent = {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
            ) {
                IconButton(
                    onClick = onRemoveClick,
                    enabled = removeEnabled,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove_time),
                        tint = if (removeEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        onClick = onClick,
        shapes = segmentedListItemShapes(index = index, count = count),
    ) {
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

internal fun canRemoveDailyTime(totalTimeCount: Int): Boolean = totalTimeCount > 1

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
            dateFormatter = DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(previewLocale),
            timeFormatter = DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(previewLocale),
            onSinceDateChange = {},
            onIntervalChange = {},
            onAddTime = {},
            onTimeClick = { _, _ -> },
            onRemoveTime = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedListItemShapes(
    index: Int,
    count: Int,
): ListItemShapes {
    val defaultShapes = ListItemDefaults.shapes()
    val mediumShape = MaterialTheme.shapes.medium

    return remember(index, count, defaultShapes, mediumShape) {
        val defaultBaseShape = defaultShapes.shape

        if (defaultBaseShape !is CornerBasedShape || mediumShape !is CornerBasedShape) {
            return@remember defaultShapes
        }

        when {
            count <= 1 -> {
                defaultShapes.copy(shape = mediumShape)
            }

            index == 0 -> {
                defaultShapes.copy(
                    shape = defaultBaseShape.copy(
                        topStart = mediumShape.topStart,
                        topEnd = mediumShape.topEnd,
                    )
                )
            }

            index == count - 1 -> {
                defaultShapes.copy(
                    shape = defaultBaseShape.copy(
                        bottomStart = mediumShape.bottomStart,
                        bottomEnd = mediumShape.bottomEnd,
                    )
                )
            }

            else -> {
                defaultShapes
            }
        }
    }
}