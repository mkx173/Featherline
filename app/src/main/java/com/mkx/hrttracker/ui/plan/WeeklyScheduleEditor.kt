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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
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
        modifier = Modifier.padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = sinceDate.format(dateFormatter),
            icon = Icons.Default.Event,
            onClick = { onSinceDateChange(sinceDate) }
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
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                )
            }
        }

        EditorFieldRow(
            label = stringResource(R.string.group_schedule_time),
            value = time.format(timeFormatter),
            icon = Icons.Default.Schedule,
            onClick = { onTimeChange(time) }
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
