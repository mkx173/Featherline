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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
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
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.isPastMidnight
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import java.time.DayOfWeek
import java.time.LocalDate
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
    firstDayOfWeek: DayOfWeek,
    time: LocalTime,
    originalTime: LocalTime? = null,
    appLocale: Locale,
    timeFormatter: DateTimeFormatter,
    boundary: MedicationDayBoundary,
    onIntervalChange: (String) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onResetDaysOfWeek: () -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    intervalEnabled: Boolean = true,
    daySelectionEnabled: Boolean = true,
    timeEditEnabled: Boolean = true,
    showLockedTimeNote: Boolean = false,
    shapeLocked: Boolean = false,
    /** A last row, given its segment index and the count. */
    dayEndingItem: (@Composable (index: Int, count: Int) -> Unit)? = null,
) {
    val totalCount = 3 + (if (dayEndingItem != null) 1 else 0)
    var itemIndex = 0
    Column {
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
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 2.dp)
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
                        painterResourceId = R.drawable.ic_restart_alt,
                        enabled = daySelectionEnabled && !isDaySelectionAtDefault,
                    )
                }
                val orderedDaysOfWeek = remember(firstDayOfWeek) {
                    List(DayOfWeek.entries.size) { offset -> firstDayOfWeek.plus(offset.toLong()) }
                }
                ConnectedButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    options = orderedDaysOfWeek,
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
            isPastMidnight = boundary.isPastMidnight(time),
            showLockedTimeNote = showLockedTimeNote,
            index = itemIndex++,
            count = totalCount
        )

        if (dayEndingItem != null) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
            dayEndingItem(itemIndex++, totalCount)
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
    isPastMidnight: Boolean,
    showLockedTimeNote: Boolean,
    index: Int = 0,
    count: Int = 1,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, top = 2.dp)
            )
            ScheduleTimeRow(
                formattedTime = formattedTime,
                formattedOriginalTime = formattedOriginalTime,
                onClick = onClick,
                enabled = enabled,
                isPastMidnight = isPastMidnight,
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
            firstDayOfWeek = DayOfWeek.MONDAY,
            time = LocalTime.of(9, 30),
            appLocale = previewLocale,
            timeFormatter = localizedShortTimeFormatter(
                previewLocale,
                uses24HourFormat = false,
            ),
            boundary = MedicationDayBoundary.Midnight,
            onIntervalChange = {},
            onDayChange = {},
            onResetDaysOfWeek = {},
            onTimeChange = {}
        )
    }
}
