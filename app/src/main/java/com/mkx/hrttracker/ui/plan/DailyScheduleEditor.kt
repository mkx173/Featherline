package com.mkx.hrttracker.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AddChip
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.MedicationDayTimeLabel
import com.mkx.hrttracker.util.isPastMidnight
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DailyScheduleEditor(
    intervalDays: String,
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    boundary: MedicationDayBoundary,
    onIntervalChange: (String) -> Unit,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    intervalEnabled: Boolean = true,
    addRemoveTimeEnabled: Boolean = true,
    timeEditEnabled: Boolean = true,
    showLockedTimeNote: Boolean = false,
    shapeLocked: Boolean = false,
    /** A last row, given its segment index and the count. */
    dayEndingItem: (@Composable (index: Int, count: Int) -> Unit)? = null,
) {
    val totalCount = 2 + (if (dayEndingItem != null) 1 else 0)
    var itemIndex = 0
    Column {
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
            boundary = boundary,
            onAddTime = onAddTime,
            onTimeClick = onTimeClick,
            addRemoveTimeEnabled = addRemoveTimeEnabled,
            timeEditEnabled = timeEditEnabled,
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
private fun DailyTimesCard(
    times: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    boundary: MedicationDayBoundary,
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
    ) {
        Column(
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 8.dp),
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
            val sortedTimes = sortDailyTimesByOriginalTime(times, boundary)
            sortedTimes.forEachIndexed { index, dailyTime ->
                DailyTimeRow(
                    formattedTime = dailyTime.time.format(timeFormatter),
                    formattedOriginalTime = dailyTime.originalTime?.format(timeFormatter),
                    onClick = { onTimeClick(dailyTime.localId, dailyTime.time) },
                    enabled = timeEditEnabled,
                    isPastMidnight = boundary.isPastMidnight(dailyTime.time),
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
    isPastMidnight: Boolean,
    index: Int = 0,
    count: Int = 1
) {
    ScheduleTimeRow(
        formattedTime = formattedTime,
        formattedOriginalTime = formattedOriginalTime,
        onClick = onClick,
        enabled = enabled,
        isPastMidnight = isPastMidnight,
        index = index,
        count = count,
    )
}

internal fun canRemoveDailyTime(totalTimeCount: Int): Boolean = totalTimeCount > 1

/**
 * An upcoming dose's day as its plan counts it, near or far: with the day ending at
 * 04:00 a 03:00 dose is on the evening before, and its time leads with the moon.
 */
internal fun upcomingDoseDayLabel(
    occurrence: LocalDateTime,
    now: LocalDateTime,
    boundary: MedicationDayBoundary,
    todayLabel: String,
    tomorrowLabel: String,
    dateFormatter: LocalDateFormatter,
): String {
    val day = medicationDay(occurrence, boundary)
    return schedulePreviewRelativeDateLabel(
        date = day,
        currentDate = medicationDay(now, boundary),
        todayLabel = todayLabel,
        tomorrowLabel = tomorrowLabel,
    ) ?: dateFormatter(day)
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

/**
 * The plan editor's Preview: its upcoming doses in one row that scrolls sideways,
 * each a standalone section row (same corners, color and type) sized to its text.
 */
@Composable
internal fun SchedulePreviewRow(
    occurrences: List<LocalDateTime>,
    now: LocalDateTime,
    boundary: MedicationDayBoundary,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
) {
    val todayLabel = stringResource(R.string.plan_group_upcoming_today)
    val tomorrowLabel = stringResource(R.string.plan_group_upcoming_tomorrow)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        occurrences.forEach { occurrence ->
            EditorSegmentedListItem(
                index = 0,
                count = 1,
                modifier = Modifier.width(IntrinsicSize.Max),
                supportingContent = {
                    Text(
                        text = upcomingDoseDayLabel(
                            occurrence = occurrence,
                            now = now,
                            boundary = boundary,
                            todayLabel = todayLabel,
                            tomorrowLabel = tomorrowLabel,
                            dateFormatter = dateFormatter,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                MedicationDayTimeLabel(
                    text = occurrence.toLocalTime().format(timeFormatter),
                    isPastMidnight = boundary.isPastMidnight(occurrence.toLocalTime()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
            intervalDays = "3",
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)),
                MedicationGroupScheduleTimeUiState(time = LocalTime.of(21, 30))
            ),
            timeFormatter = localizedShortTimeFormatter(
                previewLocale,
                uses24HourFormat = false,
            ),
            boundary = MedicationDayBoundary.Midnight,
            onIntervalChange = {},
            onAddTime = {},
            onTimeClick = { _, _ -> },
        )
    }
}
