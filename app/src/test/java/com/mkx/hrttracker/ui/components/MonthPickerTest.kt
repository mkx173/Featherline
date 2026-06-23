package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthPickerTest {

    // Notes only in Dec 2025, May 2026, Jun 2026 — note the gap (no Jan–Apr 2026).
    private val available = listOf(
        YearMonth.of(2025, 12),
        YearMonth.of(2026, 5),
        YearMonth.of(2026, 6),
    )

    @Test
    fun yearOptions_listsOnlyYearsWithRecords_sorted() {
        assertEquals(listOf(2025, 2026), monthPickerYearOptions(available))
    }

    @Test
    fun monthOptions_listsOnlyMonthsWithRecordsForThatYear() {
        // 2026 has records in May and June only — not the full 1..12 range.
        assertEquals(listOf(5, 6), monthPickerMonthOptions(available, 2026))
        assertEquals(listOf(12), monthPickerMonthOptions(available, 2025))
    }

    @Test
    fun coerceSelection_keepsExactMatch() {
        val target = YearMonth.of(2026, 5)
        assertEquals(target, coerceMonthPickerSelection(available, target))
    }

    @Test
    fun coerceSelection_snapsToNearestAvailableWhenAbsent() {
        // Mar 2026 has no records; the nearest available month is May 2026.
        assertEquals(YearMonth.of(2026, 5), coerceMonthPickerSelection(available, YearMonth.of(2026, 3)))
    }

    @Test
    fun wheelState_indexesPointAtTheSelectedYearAndMonth() {
        val state = monthPickerWheelState(available, YearMonth.of(2026, 6))
        assertEquals(listOf(2025, 2026), state.yearOptions)
        assertEquals(1, state.selectedYearIndex)
        assertEquals(listOf(5, 6), state.monthOptions)
        assertEquals(1, state.selectedMonthIndex)
    }

    @Test
    fun selectionForYearIndex_snapsMonthIntoTheNewYearsAvailableMonths() {
        // Currently on month 6; switching to 2025 (only Dec available) snaps to December.
        val result = monthPickerSelectionForYearIndex(
            available = available,
            selectedYearIndex = 0,
            currentMonthValue = 6,
        )
        assertEquals(YearMonth.of(2025, 12), result)
    }

    @Test
    fun selectionForMonthIndex_picksTheMonthAtThatIndexWithinTheYear() {
        val result = monthPickerSelectionForMonthIndex(
            available = available,
            selectedYear = 2026,
            selectedMonthIndex = 0,
        )
        assertEquals(YearMonth.of(2026, 5), result)
    }
}
