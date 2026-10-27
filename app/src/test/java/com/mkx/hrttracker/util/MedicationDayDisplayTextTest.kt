package com.mkx.hrttracker.util

import android.content.Context
import com.mkx.hrttracker.R
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationDayDisplayTextTest {
    private val context: Context = mockk()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")
    private val day = LocalDate.of(2026, 10, 31)

    init {
        every {
            context.getString(R.string.plan_schedule_entry_logged_day_offset_later, 1)
        } returns "+1"
        every {
            context.getString(R.string.plan_schedule_entry_logged_day_offset_earlier, 1)
        } returns "-1"
    }

    @Test
    fun sameDayTimeHasNoSuffix() {
        assertEquals(
            "20:00",
            medicationDayTimeText(
                context = context,
                dateTime = LocalDateTime.of(2026, 10, 31, 20, 0),
                dayDate = day,
                timeFormatter = formatter,
            ),
        )
    }

    @Test
    fun laterAndEarlierCrossDayTimesUseTheSignedOffset() {
        assertEquals(
            "02:00 (+1)",
            medicationDayTimeText(
                context = context,
                dateTime = LocalDateTime.of(2026, 11, 1, 2, 0),
                dayDate = day,
                timeFormatter = formatter,
            ),
        )
        assertEquals(
            "23:00 (-1)",
            medicationDayTimeText(
                context = context,
                dateTime = LocalDateTime.of(2026, 10, 30, 23, 0),
                dayDate = day,
                timeFormatter = formatter,
            ),
        )
    }

    @Test
    fun datedDoseTimeNamesItsMedicationDayAndOnlyMarksBeforeFourAm() {
        val dawn = LocalDateTime.of(2026, 11, 1, 1, 0)
        assertEquals(
            day to "01:00 (+1)",
            medicationDayDateAndTimeText(context, dawn, MedicationDayBoundary.FourAm, formatter),
        )
        assertEquals(
            day.plusDays(1) to "01:00",
            medicationDayDateAndTimeText(context, dawn, MedicationDayBoundary.Midnight, formatter),
        )
        assertEquals(
            day.plusDays(1) to "04:00",
            medicationDayDateAndTimeText(context, dawn.withHour(4), MedicationDayBoundary.FourAm, formatter),
        )
    }
}
