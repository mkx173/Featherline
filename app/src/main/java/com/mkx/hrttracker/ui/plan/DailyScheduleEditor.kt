package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = sinceDate.format(dateFormatter),
            icon = Icons.Rounded.Event,
            onClick = { onSinceDateChange(sinceDate) }
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
        )

        DailyTimesCard(
            times = dailyTimes,
            timeFormatter = timeFormatter,
            onAddTime = onAddTime,
            onTimeClick = onTimeClick,
            onRemoveTime = onRemoveTime
        )
    }
}

@Composable
private fun DailyTimesCard(
    times: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    onRemoveTime: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp),
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
                AddTimeChip(onClick = onAddTime)
            }
            if (times.isEmpty()) {
                Text(
                    text = stringResource(R.string.add_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            } else {
                times.forEach { dailyTime ->
                    DailyTimeRow(
                        formattedTime = dailyTime.time.format(timeFormatter),
                        onClick = { onTimeClick(dailyTime.localId, dailyTime.time) },
                        onRemoveClick = { onRemoveTime(dailyTime.localId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTimeChip(onClick: () -> Unit) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.add_time),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DailyTimeRow(
    formattedTime: String,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.remove_time),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
