package com.mkx.hrttracker.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.zone.ZoneOffsetTransition

class PassedAsOfTest {
    /**
     * Around every clock change of every zone from 2000 to 2040, a clock read maps to the latest
     * wall time whose first occurrence has passed: that time's first instant is at or before the
     * clock, and the next wall time's is after it. A change that isn't a whole hour, or that has
     * another change within 6 hours, is read each minute for 3 hours either side; the others
     * every 29 minutes and at their edges.
     */
    @Test
    fun passedAsOf_isTheLatestWallTimeWhoseFirstOccurrenceHasPassed() {
        var reads = 0
        ZoneId.getAvailableZoneIds().sorted().map(ZoneId::of).forEach { zone ->
            val changes = transitionsIn(zone)
            changes.forEachIndexed { index, change ->
                val near = listOfNotNull(changes.getOrNull(index - 1), changes.getOrNull(index + 1))
                    .any { other -> Duration.between(other.instant, change.instant).abs() <= Duration.ofHours(6) }
                val step = if (near || change.duration.abs() != Duration.ofHours(1)) 1 else 29
                val edges = listOf(Duration.ZERO, change.duration.abs()).flatMap { at ->
                    listOf(change.instant.plus(at).minusNanos(1), change.instant.plus(at))
                }
                ((-180..180 step step).map { minute -> change.instant.plusSeconds(minute * 60L) } + edges)
                    .forEach { clock ->
                        assertLatestPassed(zone, clock)
                        reads++
                    }
            }
        }
        assertTrue("no clock change found", reads > 0)
    }

    @Test
    fun passedAsOf_onTheSecondPassOfNewYorksRepeatedHour_isThatHoursEnd() {
        val newYork = ZoneId.of("America/New_York")
        val tenPastOne = LocalDateTime.of(2026, 11, 1, 1, 10)

        assertEquals(tenPastOne, tenPastOne.passedAsOf(Instant.parse("2026-11-01T05:10:00Z"), newYork))
        assertEquals(
            LocalDateTime.of(2026, 11, 1, 1, 59, 59, 999_999_999),
            tenPastOne.passedAsOf(Instant.parse("2026-11-01T06:10:00Z"), newYork),
        )
    }

    private fun assertLatestPassed(zone: ZoneId, clock: Instant) {
        val passed = LocalDateTime.ofInstant(clock, zone).passedAsOf(clock, zone)
        assertTrue("$zone at $clock: $passed hasn't passed", !passed.atZone(zone).toInstant().isAfter(clock))
        assertTrue(
            "$zone at $clock: $passed isn't the latest passed",
            passed.plusNanos(1).atZone(zone).toInstant().isAfter(clock),
        )
    }

    private fun transitionsIn(zone: ZoneId): List<ZoneOffsetTransition> {
        val end = Instant.parse("2041-01-01T00:00:00Z")
        return generateSequence(zone.rules.nextTransition(Instant.parse("2000-01-01T00:00:00Z"))) { previous ->
            zone.rules.nextTransition(previous.instant)
        }.takeWhile { change -> change.instant.isBefore(end) }.toList()
    }
}
