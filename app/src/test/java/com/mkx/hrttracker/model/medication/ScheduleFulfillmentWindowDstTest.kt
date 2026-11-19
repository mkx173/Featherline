package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The fulfillment window counts real minutes, as the offset badge does. New York: spring
 * forward 2026-03-08 (02:00 EST becomes 03:00 EDT), fall back 2026-11-01 (01:00-02:00 twice).
 */
class ScheduleFulfillmentWindowDstTest {
    private val newYork = ZoneId.of("America/New_York")

    // 01:30 and 03:30 doses are 60 real minutes apart, so 30 are allowed; 03:10 EDT is 40 late.
    @Test
    fun springForward_0130And0330Doses_loggedAt0310EDT_isOutside() {
        val slot = LocalDateTime.of(2026, 3, 8, 1, 30)
        val next = LocalDateTime.of(2026, 3, 8, 3, 30)
        val applied = Instant.parse("2026-03-08T07:10:00Z")

        assertEquals(40L, scheduleOffsetMinutes(slot, applied, newYork))
        assertEquals(Duration.ofMinutes(30), scheduleFulfillmentAllowedOffset(slot, next, newYork))
        assertFalse(window(slot, applied, next))
    }

    // A once-daily 01:30 dose logged at 13:40 EDT: 11 h 10 real of 11 h 30 allowed (the wall
    // clock says 12 h 10 of 12 h).
    @Test
    fun springForward_onceDaily0130_loggedAt1340EDT_isInside() {
        val slot = LocalDateTime.of(2026, 3, 8, 1, 30)
        val next = slot.plusDays(1)

        assertEquals(Duration.parse("PT11H30M"), scheduleFulfillmentAllowedOffset(slot, next, newYork))
        assertTrue(window(slot, Instant.parse("2026-03-08T17:40:00Z"), next))
    }

    // A once-daily 00:30 dose logged at 12:20 EST: 12 h 50 real of 12 h 30 allowed (the wall
    // clock says 11 h 50 of 12 h).
    @Test
    fun fallBack_onceDaily0030_loggedAt1220EST_isOutside() {
        val slot = LocalDateTime.of(2026, 11, 1, 0, 30)
        val next = slot.plusDays(1)

        assertEquals(Duration.parse("PT12H30M"), scheduleFulfillmentAllowedOffset(slot, next, newYork))
        assertFalse(window(slot, Instant.parse("2026-11-01T17:20:00Z"), next))
    }

    // 02:30 falls in the gap and resolves to 03:30 EDT, the next slot's instant: the window
    // keeps the wall-clock gap rather than widening to a day.
    @Test
    fun springForward_slotsTheGapPutsOnOneInstant_keepTheirWallClockGap() {
        val slot = LocalDateTime.of(2026, 3, 8, 2, 30)
        val next = LocalDateTime.of(2026, 3, 8, 3, 30)

        assertEquals(Duration.ofMinutes(30), scheduleFulfillmentAllowedOffset(slot, next, newYork))
        assertFalse(window(slot, Instant.parse("2026-03-09T03:00:00Z"), next))
    }

    private fun window(slot: LocalDateTime, applied: Instant, next: LocalDateTime) =
        isWithinScheduleFulfillmentWindow(
            scheduledFor = slot,
            appliedAt = applied.atZone(newYork),
            previousScheduledFor = null,
            nextScheduledFor = next,
        )
}
