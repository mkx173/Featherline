package com.mkx.hrttracker.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AddChip
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
    onResetDaysOfWeek: () -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    pastScheduleSelectorState: PastScheduleSelectorUiState? = null,
    onPastScheduleOptionSelected: (PastScheduleOption) -> Unit = {},
    sinceEnabled: Boolean = true,
    intervalEnabled: Boolean = true,
    daySelectionEnabled: Boolean = true,
    timeEditEnabled: Boolean = true,
    showLockedTimeNote: Boolean = false,
    shapeLocked: Boolean = false,
) {
    val totalCount = 4 +
        (if (previewOccurrences.isNotEmpty()) 1 else 0) +
        (if (pastScheduleSelectorState != null) 1 else 0)
    var itemIndex = 0
    Column {
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

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
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

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
        EditorSegmentedListItem(
            index = itemIndex++,
            count = totalCount,
            onClick = {},
            enabled = true,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isDaySelectionAtDefault =
                    selectedDaysOfWeek == setOf(sinceDate.dayOfWeek)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.group_schedule_days_of_week).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AddChip(
                        onClick = onResetDaysOfWeek,
                        label = stringResource(R.string.group_schedule_reset_days_to_start),
                        icon = Icons.Rounded.RestartAlt,
                        enabled = daySelectionEnabled && !isDaySelectionAtDefault,
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

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
        WeeklyTimeCard(
            label = stringResource(R.string.group_schedule_time),
            formattedTime = time.format(timeFormatter),
            formattedOriginalTime = originalTime?.format(timeFormatter),
            onClick = { onTimeChange(time) },
            enabled = timeEditEnabled,
            showLockedTimeNote = showLockedTimeNote,
            index = itemIndex++,
            count = totalCount
        )

        if (previewOccurrences.isNotEmpty()) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
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
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
            PastScheduleSelectorCard(
                state = selectorState,
                onOptionSelected = onPastScheduleOptionSelected,
                index = itemIndex,
                count = totalCount,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeeklyTimeCard(
    label: String,
    formattedTime: String,
    formattedOriginalTime: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    showLockedTimeNote: Boolean,
    index: Int = 0,
    count: Int = 1,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {},
        enabled = true,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            ScheduleTimeRow(
                formattedTime = formattedTime,
                formattedOriginalTime = formattedOriginalTime,
                onClick = onClick,
                enabled = enabled,
            )

            AnimatedVisibility(
                visible = showLockedTimeNote,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                label = "weekly-schedule-locked-time-note",
            ) {
                LockedScheduleTimeNoteRow()
            }
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
            onResetDaysOfWeek = {},
            onTimeChange = {}
        )
    }
}
