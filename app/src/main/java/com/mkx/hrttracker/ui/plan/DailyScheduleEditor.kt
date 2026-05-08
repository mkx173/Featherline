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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
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
    currentDate: LocalDate,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    pastScheduleSelectorState: PastScheduleSelectorUiState? = null,
    onPastScheduleOptionSelected: (PastScheduleOption) -> Unit = {},
    sinceEnabled: Boolean = true,
    intervalEnabled: Boolean = true,
    addRemoveTimeEnabled: Boolean = true,
    timeEditEnabled: Boolean = true,
    showLockedTimeNote: Boolean = false,
    shapeLocked: Boolean = false,
) {
    val totalCount = 3 +
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
            index = itemIndex++,
            count = totalCount
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
        DailyTimesCard(
            times = dailyTimes,
            timeFormatter = timeFormatter,
            onAddTime = onAddTime,
            onTimeClick = onTimeClick,
            addRemoveTimeEnabled = addRemoveTimeEnabled,
            timeEditEnabled = timeEditEnabled,
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
private fun DailyTimesCard(
    times: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    addRemoveTimeEnabled: Boolean,
    timeEditEnabled: Boolean,
    showLockedTimeNote: Boolean,
    index: Int = 0,
    count: Int = 1
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {},
    ) {
        Column(
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                if (index != sortedTimes.size - 1) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
                }
            }

            AnimatedVisibility(
                visible = showLockedTimeNote,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                label = "daily-schedule-locked-time-note",
            ) {
                LockedScheduleTimeNoteRow()
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
    ScheduleTimeRow(
        formattedTime = formattedTime,
        formattedOriginalTime = formattedOriginalTime,
        onClick = onClick,
        enabled = enabled,
        index = index,
        count = count,
    )
}

internal fun canRemoveDailyTime(totalTimeCount: Int): Boolean = totalTimeCount > 1

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduleOccurrencesCard(
    title: String,
    occurrences: List<LocalDateTime>,
    currentDate: LocalDate,
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
            val todayLabel = stringResource(R.string.plan_group_upcoming_today)
            val tomorrowLabel = stringResource(R.string.plan_group_upcoming_tomorrow)
            occurrences.forEachIndexed { occurrenceIndex, occurrence ->
                val occurrenceDate = occurrence.toLocalDate()
                val relativeDateLabel = schedulePreviewRelativeDateLabel(
                    date = occurrenceDate,
                    currentDate = currentDate,
                    todayLabel = todayLabel,
                    tomorrowLabel = tomorrowLabel
                )
                val formattedDate = dateFormatter(occurrenceDate)
                ScheduleOccurrenceRow(
                    formattedTime = occurrence.toLocalTime().format(timeFormatter),
                    formattedDate = relativeDateLabel?.let { label ->
                        stringResource(R.string.plan_group_upcoming_format, label, formattedDate)
                    } ?: formattedDate,
                    index = occurrenceIndex,
                    count = occurrences.size
                )
            }
        }
    }
}

internal fun schedulePreviewRelativeDateLabel(
    date: LocalDate,
    currentDate: LocalDate,
    todayLabel: String,
    tomorrowLabel: String,
): String? = when (date) {
    currentDate -> todayLabel
    currentDate.plusDays(1) -> tomorrowLabel
    else -> null
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
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
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
                LocalDateTime.of(2026, 4, 22, 9, 0),
                LocalDateTime.of(2026, 4, 23, 21, 30),
                LocalDateTime.of(2026, 4, 25, 21, 30),
                LocalDateTime.of(2026, 4, 28, 9, 0),
                LocalDateTime.of(2026, 4, 28, 21, 30)
            ),
            currentDate = LocalDate.of(2026, 4, 22),
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
