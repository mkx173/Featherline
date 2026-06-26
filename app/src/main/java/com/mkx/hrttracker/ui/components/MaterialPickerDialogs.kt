package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.datepicker.HazeDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    initialSelectedDate: LocalDate,
    minimumDate: LocalDate? = null,
    maximumDate: LocalDate? = null,
    // When provided, a left-aligned reset button (e.g. "Reset to now") clears the
    // caller's selection and dismisses, matching the WeightDialog clear pattern.
    onReset: (() -> Unit)? = null,
    resetButtonText: String? = null,
) {
    val selectableDates = remember(minimumDate, maximumDate) {
        datePickerSelectableDates(
            minimumDate = minimumDate,
            maximumDate = maximumDate,
        )
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDate = initialSelectedDate,
        selectableDates = selectableDates,
    )

    HazeDatePickerDialog(
        onDismissRequest = onDismiss,
        colors = hazeDatePickerDialogColors(),
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onReset != null && resetButtonText != null) {
                    TextButton(
                        onClick = {
                            onReset()
                            onDismiss()
                        },
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(resetButtonText)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            // Material 3 returns selectedDateMillis as UTC-midnight. Decoding it
                            // with the system zone in non-UTC offsets would shift to the previous
                            // (or next) calendar day on confirm.
                            onDateSelected(
                                materialPickerDateMillisToLocalDate(
                                    selectedDateMillis,
                                    ZoneOffset.UTC
                                )
                            )
                        }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
    ) {
        HazeDatePicker(
            state = datePickerState,
            colors = hazeDatePickerColors(),
            modifier = Modifier.verticalScroll(
                rememberScrollState()
            ),
            hazeState = LocalChromeHazeState.current,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun datePickerSelectableDates(
    minimumDate: LocalDate? = null,
    maximumDate: LocalDate? = null,
): SelectableDates {
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val date = materialPickerDateMillisToLocalDate(utcTimeMillis, ZoneOffset.UTC)
            return (minimumDate == null || !date.isBefore(minimumDate)) &&
                    (maximumDate == null || !date.isAfter(maximumDate))
        }

        override fun isSelectableYear(year: Int): Boolean {
            return (minimumDate == null || year >= minimumDate.year) &&
                    (maximumDate == null || year <= maximumDate.year)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(
    onTimeSelected: (LocalTime) -> Boolean,
    onDismiss: () -> Unit,
    initialTime: LocalTime,
    is24Hour: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = is24Hour
    )

    HazeTimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val confirmTime = {
                val shouldDismiss = onTimeSelected(
                    LocalTime.of(timePickerState.hour, timePickerState.minute)
                )
                if (shouldDismiss) {
                    onDismiss()
                }
            }
            if (onRemove != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            onRemove()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    ) {
                        Text(stringResource(R.string.remove_time))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = confirmTime) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            } else {
                TextButton(
                    onClick = confirmTime
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
        dismissButton = if (onRemove == null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        } else {
            null
        },
        title = {
            TimePickerDialogDefaults.Title(
                displayMode = TimePickerDisplayMode.Picker
            )
        }
    ) {
        TimePicker(state = timePickerState)
    }
}

internal fun materialPickerDateMillisToLocalDate(
    selectedDateMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate {
    return Instant.ofEpochMilli(selectedDateMillis)
        .atZone(zoneId)
        .toLocalDate()
}
