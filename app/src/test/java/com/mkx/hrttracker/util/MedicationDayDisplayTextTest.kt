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
                boundary = MedicationDayBoundary.Midnight,
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
                boundary = MedicationDayBoundary.Midnight,
                timeFormatter = formatter,
            ),
        )
        assertEquals(
            "23:00 (-1)",
            medicationDayTimeText(
                context = context,
                dateTime = LocalDateTime.of(2026, 10, 30, 23, 0),
                dayDate = day,
                boundary = MedicationDayBoundary.Midnight,
                timeFormatter = formatter,
            ),
        )
    }

    @Test
    fun fourAmOnlyMarksTimesOnAnotherMedicationDay() {
        fun text(dateTime: LocalDateTime) = medicationDayTimeText(
            context, dateTime, day, MedicationDayBoundary.FourAm, formatter,
        )
        assertEquals("01:00", text(LocalDateTime.of(2026, 11, 1, 1, 0)))
        assertEquals("09:00 (+1)", text(LocalDateTime.of(2026, 11, 1, 9, 0)))
        assertEquals("02:00 (-1)", text(LocalDateTime.of(2026, 10, 31, 2, 0)))
    }
}
