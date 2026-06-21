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
        assertEquals(MilestoneUnit.DAYS, next?.unit)
        assertEquals(90L, next?.value)
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
        assertEquals(MilestoneUnit.YEARS, next?.unit)
    }

    @Test
    fun current_returnsMilestoneOnExactDayMarker() {
        val start = LocalDate.of(2026, 3, 9)
        val today = start.plusDays(100)
        val current = Milestones.current(date = start, today = today)

        assertEquals(100L, current?.dayCount)
        assertEquals(MilestoneUnit.DAYS, current?.unit)
        assertEquals(100L, current?.value)
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

    @Test
    fun next_returnsAnniversary_forAnchorOlderThanFiftyYears() {
        // Anniversaries are generated on demand, so an anchor older than any fixed cap
        // (e.g. a birthday tracked for decades) still has an upcoming yearly goal rather
        // than silently dropping its milestone.
        val start = LocalDate.of(1970, 6, 22)
        val today = start.plusYears(55).plusDays(10)
        val next = Milestones.next(date = start, today = today)
        assertNotNull(next)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(56L, next?.value)
    }

    @Test
    fun current_returnsAnniversary_onLeapDayAnchorAdjustedToFeb28() {
        // 2024-02-29 + 1 year adjusts to 2025-02-28, which IS the first anniversary.
        // Viewing on it must report year 1; ChronoUnit.YEARS.between(start, today) alone
        // returns 0 here (the day-of-month drops 29->28) and would wrongly drop the milestone.
        val start = LocalDate.of(2024, 2, 29)
        val today = LocalDate.of(2025, 2, 28)
        val current = Milestones.current(date = start, today = today)
        assertNotNull(current)
        assertEquals(MilestoneUnit.YEARS, current?.unit)
        assertEquals(1L, current?.value)
    }

    @Test
    fun next_returnsFollowingAnniversary_onLeapDayAnchorAdjustedToFeb28() {
        // On the adjusted first anniversary (2025-02-28) the *next* milestone is year 2
        // (2026-02-28), not year 1 again — next must look strictly past today rather than
        // returning the current anniversary with zero remaining days.
        val start = LocalDate.of(2024, 2, 29)
        val today = LocalDate.of(2025, 2, 28)
        val next = Milestones.next(date = start, today = today)
        assertNotNull(next)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(2L, next?.value)
        assertEquals(ChronoUnit.DAYS.between(start, LocalDate.of(2026, 2, 28)), next?.dayCount)
    }

    @Test
    fun current_returnsAnniversary_forAnchorOlderThanFiftyYears() {
        val start = LocalDate.of(1970, 6, 22)
        val today = start.plusYears(55)
        val current = Milestones.current(date = start, today = today)
        assertNotNull(current)
        assertEquals(MilestoneUnit.YEARS, current?.unit)
        assertEquals(55L, current?.value)
    }
}
