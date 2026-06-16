package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayCountTest {
    private val today = LocalDate.of(2026, 6, 16)

    @Test
    fun pastDate_countsUp_notFuture() {
        val result = dayCount(date = LocalDate.of(2024, 4, 1), today = today)
        assertEquals(806L, result.magnitude)
        assertFalse(result.isFuture)
    }

    @Test
    fun today_isZero_notFuture() {
        val result = dayCount(date = today, today = today)
        assertEquals(0L, result.magnitude)
        assertFalse(result.isFuture)
    }

    @Test
    fun futureDate_countsDown_isFuture() {
        val result = dayCount(date = LocalDate.of(2026, 9, 15), today = today)
        assertEquals(91L, result.magnitude)
        assertTrue(result.isFuture)
    }
}
