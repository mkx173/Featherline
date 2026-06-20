package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MilestonesTest {
    @Test
    fun next_picksFirstDayMarkerExceedingCurrentCount() {
        val start = LocalDate.of(2024, 4, 1)
        val today = start.plusDays(65)
        val next = Milestones.next(date = start, today = today)
        assertEquals(90L, next?.dayCount)
        assertEquals("90 days", next?.label)
    }

    @Test
    fun next_switchesToYearlyAnniversaryAfterLastDayMarker() {
        // Past the 180-day marker but before the first anniversary: the next
        // milestone must be the 1-year anniversary, not another day marker.
        val start = LocalDate.of(2024, 6, 1)
        val today = start.plusDays(200)
        val next = Milestones.next(date = start, today = today)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(1L, next?.value)
    }

    @Test
    fun next_yearAnniversary_usesCalendarDayCount() {
        // Leap-year start: the anniversary lands on the real calendar date, so the
        // day count comes from the calendar, not a fixed 365.
        val start = LocalDate.of(2024, 2, 29)
        val anniversary = start.plusYears(1)
        val today = start.plusDays(200)
        val next = Milestones.next(date = start, today = today)
        assertEquals(LocalDate.of(2025, 2, 28), anniversary)
        assertEquals(ChronoUnit.DAYS.between(start, anniversary), next?.dayCount)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(1L, next?.value)
    }

    @Test
    fun next_picksLaterAnniversary_whenPastEarlierOnes() {
        val start = LocalDate.of(2020, 4, 1)
        val today = start.plusDays(400) // past the 1-year anniversary
        val next = Milestones.next(date = start, today = today)
        assertEquals(2L, next?.value)
        assertEquals("2 years", next?.label)
    }

    @Test
    fun current_returnsMilestoneOnExactDayMarker() {
        val start = LocalDate.of(2026, 3, 9)
        val today = start.plusDays(100)
        val current = Milestones.current(date = start, today = today)

        assertEquals(100L, current?.dayCount)
        assertEquals("100 days", current?.label)
    }

    @Test
    fun current_returnsAnniversaryOnExactYear() {
        val start = LocalDate.of(2024, 4, 1)
        val today = start.plusYears(1)
        val current = Milestones.current(date = start, today = today)
        assertEquals(MilestoneUnit.YEARS, current?.unit)
        assertEquals(1L, current?.value)
    }

    @Test
    fun futureAnchor_hasNoMilestone() {
        val today = LocalDate.of(2026, 6, 16)
        assertNull(Milestones.next(date = today.plusDays(10), today = today))
    }

    @Test
    fun anniversariesAreUnbounded_veryOldAnchorStillHasNextMilestone() {
        // A 16-year-old anchor still has an upcoming yearly anniversary (year 17),
        // so the chip never runs out of goals.
        val start = LocalDate.of(2010, 1, 1)
        val today = LocalDate.of(2026, 6, 16)
        val next = Milestones.next(date = start, today = today)
        assertNotNull(next)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(17L, next?.value)
    }
}
