package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WeeklyScheduleEditor(
    sinceDate: LocalDate,
    intervalWeeks: String,
    selectedDaysOfWeek: Set<DayOfWeek>,
    time: LocalTime,
    appLocale: Locale,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onTimeChange: (LocalTime) -> Unit
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
            count = 4
        )

        IntervalStepperCard(
            label = stringResource(R.string.group_schedule_repeat_every),
            value = parseScheduleInterval(intervalWeeks),
            unit = pluralStringResource(
                R.plurals.group_schedule_weeks_unit,
                parseScheduleInterval(intervalWeeks),
                parseScheduleInterval(intervalWeeks)
            ),
            onDecreaseClick = { onIntervalChange(decrementScheduleInterval(intervalWeeks)) },
            onIncreaseClick = { onIntervalChange(incrementScheduleInterval(intervalWeeks)) },
            index = 1,
            count = 4
        )

        SegmentedListItem(
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shapes = ListItemDefaults.segmentedShapes(
                index = 2,
                count = 4
            ),
            onClick = {}
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_schedule_days_of_week).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ConnectedButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    options = DayOfWeek.entries.toList(),
                    selectedOptions = selectedDaysOfWeek,
                    optionLabel = { weekday ->
                        weekday.getDisplayName(TextStyle.NARROW, appLocale)
                    },
                    onOptionToggled = onDayChange,
                    layout = ConnectedButtonGroupLayout.ROW,
                    expandOptions = true,
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        EditorFieldRow(
            label = stringResource(R.string.group_schedule_time),
            value = time.format(timeFormatter),
            icon = Icons.Rounded.Schedule,
            onClick = { onTimeChange(time) },
            index = 3,
            count = 4
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun WeeklyScheduleEditorPreview() {
    val previewLocale = Locale.US
    HrtTrackerTheme(dynamicColor = false) {
        WeeklyScheduleEditor(
            sinceDate = LocalDate.of(2026, 4, 22),
            intervalWeeks = "2",
            selectedDaysOfWeek = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
            ),
            time = LocalTime.of(9, 30),
            appLocale = previewLocale,
            dateFormatter = DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(previewLocale),
            timeFormatter = DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(previewLocale),
            onSinceDateChange = {},
            onIntervalChange = {},
            onDayChange = {},
            onTimeChange = {}
        )
    }
}
