package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WeeklyScheduleEditor(
    sinceDate: LocalDate,
    intervalWeeks: String,
    selectedDaysOfWeek: Set<DayOfWeek>,
    time: LocalTime,
    originalTime: LocalTime? = null,
    previewOccurrences: List<LocalDateTime>,
    currentDate: LocalDate,
    appLocale: Locale,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    pastScheduleSelectorState: PastScheduleSelectorUiState? = null,
    onPastScheduleOptionSelected: (PastScheduleOption) -> Unit = {},
    sinceEnabled: Boolean = true,
    intervalEnabled: Boolean = true,
    daySelectionEnabled: Boolean = true,
    timeEditEnabled: Boolean = true,
    shapeLocked: Boolean = false,
) {
    val totalCount = 4 +
        (if (previewOccurrences.isNotEmpty()) 1 else 0) +
        (if (pastScheduleSelectorState != null) 1 else 0)
    var itemIndex = 0
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
            index = itemIndex++,
            count = totalCount
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
            enabled = intervalEnabled,
            locked = shapeLocked,
            index = itemIndex++,
            count = totalCount
        )

        EditorSegmentedListItem(
            index = itemIndex++,
            count = totalCount,
            onClick = {},
            enabled = true,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.group_schedule_days_of_week).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConnectedButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    options = DayOfWeek.entries.toList(),
                    selectedOptions = selectedDaysOfWeek,
                    optionLabel = { weekday ->
                        weekday.getDisplayName(TextStyle.NARROW, appLocale)
                    },
                    onOptionToggled = onDayChange,
                    enabled = daySelectionEnabled,
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
            originalValue = originalTime?.format(timeFormatter),
            icon = painterResource(R.drawable.ic_schedule),
            onClick = { onTimeChange(time) },
            enabled = timeEditEnabled,
            locked = !timeEditEnabled,
            index = itemIndex++,
            count = totalCount
        )

        if (previewOccurrences.isNotEmpty()) {
            ScheduleOccurrencesCard(
                title = stringResource(R.string.group_schedule_preview),
                occurrences = previewOccurrences,
                currentDate = currentDate,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                index = itemIndex++,
                count = totalCount
            )
        }

        pastScheduleSelectorState?.let { selectorState ->
            PastScheduleSelectorCard(
                state = selectorState,
                onOptionSelected = onPastScheduleOptionSelected,
                index = itemIndex,
                count = totalCount,
            )
        }
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
            previewOccurrences = listOf(
                LocalDateTime.of(2026, 4, 22, 9, 30),
                LocalDateTime.of(2026, 4, 23, 9, 30),
                LocalDateTime.of(2026, 4, 27, 9, 30),
                LocalDateTime.of(2026, 4, 29, 9, 30),
                LocalDateTime.of(2026, 5, 1, 9, 30)
            ),
            currentDate = LocalDate.of(2026, 4, 22),
            appLocale = previewLocale,
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
            onDayChange = {},
            onTimeChange = {}
        )
    }
}
