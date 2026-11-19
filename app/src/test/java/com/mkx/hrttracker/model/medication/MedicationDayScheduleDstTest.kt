package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Due soon and past due are measured between instants, so neither goes back across a DST change. */
class MedicationDayScheduleDstTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun inTheRepeatedHour_aMissedDoseStaysPastDue_onTheSecondPass() {
        // 2026-11-01: 02:00 EDT becomes 01:00 EST. A 00:30 dose, at 01:10 on each pass.
        val dose = LocalDateTime.of(2026, 11, 1, 0, 30)
        val wallClock = LocalDateTime.of(2026, 11, 1, 1, 10)
        val firstPass = wallClock.atOffset(ZoneOffset.ofHours(-4)).toInstant()
        val secondPass = wallClock.atOffset(ZoneOffset.ofHours(-5)).toInstant()

        assertTrue(isDueSoon(dose, wallClock, newYork, firstPass))
        assertFalse(isPastDue(dose, wallClock, newYork, firstPass))
        // 100 minutes after the dose, not the 40 the wall clock shows.
        assertFalse(isDueSoon(dose, wallClock, newYork, secondPass))
        assertTrue(isPastDue(dose, wallClock, newYork, secondPass))
    }

    @Test
    fun acrossTheSkippedHour_aDoseIsDueSoonAnHourBefore_inRealTime() {
        // 2026-03-08: 02:00 EST becomes 03:00 EDT. A 03:30 dose is 45 minutes after 01:45 EST.
        val dose = LocalDateTime.of(2026, 3, 8, 3, 30)
        val now = LocalDateTime.of(2026, 3, 8, 1, 45)
        val clockNow = now.atZone(newYork).toInstant()

        assertTrue(isDueSoon(dose, now, newYork, clockNow))
        // And past due at 04:30 EDT, an hour after it.
        val later = LocalDateTime.of(2026, 3, 8, 4, 30)
        assertFalse(isPastDue(dose, later.minusMinutes(1), newYork, later.minusMinutes(1).atZone(newYork).toInstant()))
        assertTrue(isPastDue(dose, later, newYork, later.atZone(newYork).toInstant()))
    }
}
