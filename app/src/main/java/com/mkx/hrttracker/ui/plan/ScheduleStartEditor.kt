package com.mkx.hrttracker.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.LocalMedicationDayBoundary
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ScheduleStartEditor(
    sinceDate: LocalDate,
    startsNow: Boolean,
    start: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    pastScheduleSelectorState: PastScheduleSelectorUiState? = null,
    onPastScheduleOptionSelected: (PastScheduleOption) -> Unit = {},
    enabled: Boolean = true,
    shapeLocked: Boolean = false,
) {
    // Keep the last selector state rendered so the card can animate out when the
    // start leaves no past doses — visibility, not the state's presence, drives it.
    var retainedPastScheduleSelectorState by remember {
        mutableStateOf(pastScheduleSelectorState)
    }
    if (pastScheduleSelectorState != null) {
        retainedPastScheduleSelectorState = pastScheduleSelectorState
    }
    HrtSection(title = null) {
        item {
            EditorFieldRow(
                label = stringResource(R.string.group_schedule_since),
                value = dateFormatter(sinceDate),
                icon = painterResource(R.drawable.ic_event),
                onClick = onDateClick,
                enabled = enabled,
                locked = shapeLocked,
            )
        }
        item {
            EditorFieldRow(
                label = stringResource(R.string.group_schedule_start_time),
                value = when {
                    startsNow -> stringResource(R.string.group_schedule_start_now)
                    // A saved start past the start date's own day: a plan that hid its
                    // past from a later save, or one a night owl switch shifted.
                    medicationDay(start, LocalMedicationDayBoundary.current) != sinceDate ->
                        stringResource(
                            R.string.plan_group_upcoming_format,
                            dateFormatter(start.toLocalDate()),
                            start.format(timeFormatter),
                        )

                    else -> start.format(timeFormatter)
                },
                icon = painterResource(R.drawable.ic_calendar_clock),
                onClick = onTimeClick,
                enabled = enabled,
                locked = shapeLocked,
            )
        }
        animatedItem(visible = pastScheduleSelectorState != null) {
            retainedPastScheduleSelectorState?.let { selectorState ->
                PastScheduleSelectorCard(
                    state = selectorState,
                    // The editor drops taps once the live state is gone, so the
                    // retained card can't change the past choice mid-exit.
                    onOptionSelected = onPastScheduleOptionSelected,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun ScheduleStartEditorPreview() {
    val previewLocale = Locale.US
    HrtTrackerTheme(dynamicColor = false) {
        ScheduleStartEditor(
            sinceDate = LocalDate.of(2026, 4, 20),
            startsNow = false,
            start = LocalDate.of(2026, 4, 20).atStartOfDay(),
            dateFormatter = medicationGroupScheduleDateFormatter(
                locale = previewLocale,
                today = LocalDate.of(2026, 4, 22),
            ),
            timeFormatter = localizedShortTimeFormatter(
                previewLocale,
                uses24HourFormat = false,
            ),
            onDateClick = {},
            onTimeClick = {},
            pastScheduleSelectorState = PastScheduleSelectorUiState(
                selectedOption = PastScheduleOption.SHOW,
                showGeneratePastRecordsOption = true,
                enabled = true,
            ),
        )
    }
}
