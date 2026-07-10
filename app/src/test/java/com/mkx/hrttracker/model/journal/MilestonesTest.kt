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
    fun next_picksHundredDayMultiple_whenItPrecedesTheAnniversary() {
        // Past the 180-day marker at day 200: day 300 comes before the 1-year
        // anniversary, so the every-100-days schedule supplies the next goal.
        val start = LocalDate.of(2024, 6, 1)
        val today = start.plusDays(200)
        val next = Milestones.next(date = start, today = today)
        assertEquals(MilestoneUnit.DAYS, next?.unit)
        assertEquals(300L, next?.value)
    }

    @Test
    fun next_picksAnniversary_whenItPrecedesTheNextHundred() {
        // At day 350 the 1-year anniversary (~day 365) lands before day 400, so the
        // yearly schedule wins even though 100-day multiples continue past it.
        val start = LocalDate.of(2024, 6, 1)
        val today = start.plusDays(350)
        val next = Milestones.next(date = start, today = today)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(1L, next?.value)
    }

    @Test
    fun next_earlyDayMarkerBeatsTheNextHundred() {
        // At day 150 the 180-day marker precedes day 200.
        val start = LocalDate.of(2024, 6, 1)
        val today = start.plusDays(150)
        val next = Milestones.next(date = start, today = today)
        assertEquals(MilestoneUnit.DAYS, next?.unit)
        assertEquals(180L, next?.value)
    }

    @Test
    fun next_anniversaryWinsAnExactTieWithAHundredMultiple() {
        // 2001-01-01 → 2024-01-01 spans exactly 8400 days (23 years, 5 leap days), so
        // the year-23 anniversary and day 8400 coincide; the anniversary outranks it.
        val start = LocalDate.of(2001, 1, 1)
        val today = start.plusDays(8350)
        val next = Milestones.next(date = start, today = today)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(23L, next?.value)
        assertEquals(8400L, next?.dayCount)
    }

    @Test
    fun next_yearAnniversary_usesCalendarDayCount() {
        // Leap-year start: the anniversary lands on the real calendar date, so the
        // day count comes from the calendar, not a fixed 365. Day 350 sits past the
        // last hundred-multiple before the anniversary, so the anniversary is next.
        val start = LocalDate.of(2024, 2, 29)
        val anniversary = start.plusYears(1)
        val today = start.plusDays(350)
        val next = Milestones.next(date = start, today = today)
        assertEquals(LocalDate.of(2025, 2, 28), anniversary)
        assertEquals(ChronoUnit.DAYS.between(start, anniversary), next?.dayCount)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(1L, next?.value)
    }

    @Test
    fun next_continuesHundredDaySchedulePastTheFirstAnniversary() {
        // Past the 1-year anniversary at day 400: day 500 precedes the 2-year
        // anniversary (~day 730), so day counts keep interleaving with years.
        val start = LocalDate.of(2020, 4, 1)
        val today = start.plusDays(400)
        val next = Milestones.next(date = start, today = today)
        assertEquals(500L, next?.value)
        assertEquals(MilestoneUnit.DAYS, next?.unit)
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
    fun current_returnsMilestoneOnEveryHundredDayMultiple() {
        val start = LocalDate.of(2023, 3, 9)
        for (days in listOf(200L, 400L, 1000L)) {
            val current = Milestones.current(date = start, today = start.plusDays(days))
            assertEquals(days, current?.dayCount)
            assertEquals(MilestoneUnit.DAYS, current?.unit)
            assertEquals(days, current?.value)
        }
    }

    @Test
    fun current_anniversaryWinsAnExactCollisionWithAHundredMultiple() {
        // Day 8400 is both a 100-multiple and the 23-year anniversary; report years.
        val start = LocalDate.of(2001, 1, 1)
        val current = Milestones.current(date = start, today = start.plusDays(8400))
        assertEquals(MilestoneUnit.YEARS, current?.unit)
        assertEquals(23L, current?.value)
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
        // so the chip never runs out of goals. Today sits between the last
        // hundred-multiple and the anniversary so the yearly schedule supplies it.
        val start = LocalDate.of(2010, 1, 1)
        val today = LocalDate.of(2026, 12, 24)
        val next = Milestones.next(date = start, today = today)
        assertNotNull(next)
        assertEquals(MilestoneUnit.YEARS, next?.unit)
        assertEquals(17L, next?.value)
    }

    @Test
    fun next_returnsAnniversary_forAnchorOlderThanFiftyYears() {
        // Anniversaries are generated on demand, so an anchor older than any fixed cap
        // (e.g. a birthday tracked for decades) still has an upcoming yearly goal rather
        // than silently dropping its milestone. Ten days before the 56th anniversary no
        // hundred-multiple intervenes, so the yearly schedule supplies it.
        val start = LocalDate.of(1970, 6, 22)
        val today = start.plusYears(56).minusDays(10)
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
    fun next_looksStrictlyPastToday_onLeapDayAnchorAdjustedToFeb28() {
        // On the adjusted first anniversary (2025-02-28, day 365) the *next* milestone
        // is day 400, not year 1 again — next must look strictly past today rather than
        // returning the current anniversary with zero remaining days.
        val start = LocalDate.of(2024, 2, 29)
        val today = LocalDate.of(2025, 2, 28)
        val next = Milestones.next(date = start, today = today)
        assertNotNull(next)
        assertEquals(MilestoneUnit.DAYS, next?.unit)
        assertEquals(400L, next?.value)
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
