package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MilestonesTest {
    @Test
    fun nextRoundDayMilestone_picksFirstExceedingCurrentCount() {
        val start = LocalDate.of(2024, 4, 1)
        val today = start.plusDays(794)
        val next = Milestones.next(date = start, today = today)
        assertEquals(800L, next?.dayCount)
        assertEquals("800 days", next?.label)
    }

    @Test
    fun monthAnniversary_competesByDayCount() {
        val start = LocalDate.of(2024, 1, 1)
        val today = start.plusMonths(24).plusDays(1)
        val next = Milestones.next(date = start, today = today)
        assertEquals(800L, next?.dayCount)
    }

    @Test
    fun monthAnniversary_canBeNextMilestone_usesCalendarMonthDayCount() {
        val start = LocalDate.of(2025, 8, 31)
        val anniversary = start.plusMonths(6)
        val today = start.plusDays(150)
        val next = Milestones.next(date = start, today = today)
        assertEquals(LocalDate.of(2026, 2, 28), anniversary)
        assertEquals(ChronoUnit.DAYS.between(start, anniversary), next?.dayCount)
        assertEquals("6 months", next?.label)
    }

    @Test
    fun futureAnchor_hasNoMilestone() {
        val today = LocalDate.of(2026, 6, 16)
        assertNull(Milestones.next(date = today.plusDays(10), today = today))
    }

    @Test
    fun pastAllMilestones_returnsNull() {
        val start = LocalDate.of(2010, 1, 1)
        val today = LocalDate.of(2026, 6, 16)
        assertNull(Milestones.next(date = start, today = today))
    }
}
