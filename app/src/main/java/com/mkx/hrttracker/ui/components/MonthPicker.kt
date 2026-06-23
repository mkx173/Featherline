package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.swmansion.kmpwheelpicker.WheelPicker
import com.swmansion.kmpwheelpicker.WheelPickerState
import com.swmansion.kmpwheelpicker.rememberWheelPickerState
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * A month + year wheel picker dialog whose options are restricted to [availableMonths] — i.e. only
 * months (and years) that actually carry data are offered, so the wheels never show an empty
 * period. The year wheel lists the distinct years present; selecting a year narrows the month wheel
 * to that year's available months, snapping the selection to the closest one.
 *
 * [availableMonths] may be in any order and need not be contiguous; an empty list renders nothing.
 */
@Composable
fun MonthPickerDialog(
    availableMonths: List<YearMonth>,
    selectedMonth: YearMonth,
    title: String,
    appLocale: Locale,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    if (availableMonths.isEmpty()) return

    var pickerMonth by remember(selectedMonth, availableMonths) {
        mutableStateOf(coerceMonthPickerSelection(availableMonths, selectedMonth))
    }
    val wheelState = remember(pickerMonth, availableMonths) {
        monthPickerWheelState(available = availableMonths, selectedMonth = pickerMonth)
    }
    val yearWheelState = rememberWheelPickerState(
        itemCount = wheelState.yearOptions.size,
        initialIndex = wheelState.selectedYearIndex,
    )

    LaunchedEffect(wheelState.selectedYearIndex) {
        if (yearWheelState.index != wheelState.selectedYearIndex) {
            yearWheelState.scrollTo(wheelState.selectedYearIndex)
        }
    }

    LaunchedEffect(yearWheelState, pickerMonth, availableMonths) {
        snapshotFlow { yearWheelState.index }
            .distinctUntilChanged()
            .collect { selectedYearIndex ->
                val updatedMonth = monthPickerSelectionForYearIndex(
                    available = availableMonths,
                    selectedYearIndex = selectedYearIndex,
                    currentMonthValue = pickerMonth.monthValue,
                )
                if (updatedMonth != pickerMonth) {
                    pickerMonth = updatedMonth
                }
            }
    }

    HazeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.month_picker_month),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.month_picker_year),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    key(wheelState.monthOptions) {
                        val monthWheelState = rememberWheelPickerState(
                            itemCount = wheelState.monthOptions.size,
                            initialIndex = wheelState.selectedMonthIndex,
                        )

                        LaunchedEffect(wheelState.selectedMonthIndex) {
                            if (monthWheelState.index != wheelState.selectedMonthIndex) {
                                monthWheelState.scrollTo(wheelState.selectedMonthIndex)
                            }
                        }

                        LaunchedEffect(monthWheelState, wheelState.selectedMonth.year, availableMonths) {
                            snapshotFlow { monthWheelState.index }
                                .distinctUntilChanged()
                                .collect { selectedMonthIndex ->
                                    val updatedMonth = monthPickerSelectionForMonthIndex(
                                        available = availableMonths,
                                        selectedYear = wheelState.selectedMonth.year,
                                        selectedMonthIndex = selectedMonthIndex,
                                    )
                                    if (updatedMonth != pickerMonth) {
                                        pickerMonth = updatedMonth
                                    }
                                }
                        }

                        MonthPickerWheel(
                            options = wheelState.monthOptions.map { monthValue ->
                                monthPickerMonthLabel(monthValue, appLocale)
                            },
                            state = monthWheelState,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MonthPickerWheel(
                        options = wheelState.yearOptions.map(Int::toString),
                        state = yearWheelState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerMonth) }) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MonthPickerWheel(
    options: List<String>,
    state: WheelPickerState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        WheelPicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            bufferSize = 1,
            window = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            },
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        ) { index ->
            val isSelected = state.index == index
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = options[index],
                    style = if (isSelected) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(if (isSelected) 1f else 0.72f)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

private fun monthPickerMonthLabel(
    monthValue: Int,
    locale: Locale,
): String {
    return Month.of(monthValue).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
}

// ---- Wheel option model (pure, unit-tested in MonthPickerTest) ----

internal data class MonthPickerWheelState(
    val selectedMonth: YearMonth,
    val yearOptions: List<Int>,
    val selectedYearIndex: Int,
    val monthOptions: List<Int>,
    val selectedMonthIndex: Int,
)

private fun YearMonth.monthOrdinal(): Int = year * 12 + (monthValue - 1)

internal fun monthPickerYearOptions(available: List<YearMonth>): List<Int> {
    return available.map { it.year }.distinct().sorted()
}

internal fun monthPickerMonthOptions(
    available: List<YearMonth>,
    year: Int,
): List<Int> {
    return available.filter { it.year == year }.map { it.monthValue }.distinct().sorted()
}

// Snap an arbitrary target onto the closest available month so a selection always lands on data.
internal fun coerceMonthPickerSelection(
    available: List<YearMonth>,
    target: YearMonth,
): YearMonth {
    if (target in available) return target
    return available.minByOrNull { abs(it.monthOrdinal() - target.monthOrdinal()) } ?: target
}

internal fun monthPickerWheelState(
    available: List<YearMonth>,
    selectedMonth: YearMonth,
): MonthPickerWheelState {
    val coerced = coerceMonthPickerSelection(available, selectedMonth)
    val yearOptions = monthPickerYearOptions(available)
    val monthOptions = monthPickerMonthOptions(available, coerced.year)
    return MonthPickerWheelState(
        selectedMonth = coerced,
        yearOptions = yearOptions,
        selectedYearIndex = yearOptions.indexOf(coerced.year).coerceAtLeast(0),
        monthOptions = monthOptions,
        selectedMonthIndex = monthOptions.indexOf(coerced.monthValue).coerceAtLeast(0),
    )
}

internal fun monthPickerSelectionForYearIndex(
    available: List<YearMonth>,
    selectedYearIndex: Int,
    currentMonthValue: Int,
): YearMonth {
    val yearOptions = monthPickerYearOptions(available)
    val year = yearOptions.getOrElse(selectedYearIndex) { yearOptions.first() }
    val monthsInYear = monthPickerMonthOptions(available, year)
    // Keep the same month if that year has it, otherwise snap to the nearest available one.
    val month = monthsInYear.minByOrNull { abs(it - currentMonthValue) } ?: monthsInYear.first()
    return YearMonth.of(year, month)
}

internal fun monthPickerSelectionForMonthIndex(
    available: List<YearMonth>,
    selectedYear: Int,
    selectedMonthIndex: Int,
): YearMonth {
    val monthsInYear = monthPickerMonthOptions(available, selectedYear)
    val month = monthsInYear.getOrElse(selectedMonthIndex) { monthsInYear.first() }
    return YearMonth.of(selectedYear, month)
}
