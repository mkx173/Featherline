package com.mkx.hrttracker.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class AppTimeSourceTest {
    @Test
    fun defaultAppTimeSource_nowReturnsUntruncatedClockInstant() = runTest {
        val instant = Instant.parse("2026-04-25T12:00:43.500Z")
        val source = DefaultAppTimeSource(
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            appScope = backgroundScope,
        )

        assertEquals(instant, source.now())
    }

    @Test
    fun currentMinuteTicker_emitsImmediatelyThenAtMinuteBoundaries() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val clock = SchedulerClock(
                scheduler = testScheduler,
                baseInstant = Instant.parse("2026-04-25T12:00:43.500Z"),
                zone = ZoneOffset.UTC
            )
            val emissions = mutableListOf<LocalDateTime>()

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                currentMinuteTicker(clock)
                    .take(3)
                    .toList(emissions)
            }

            assertEquals(
                listOf(LocalDateTime.of(2026, 4, 25, 12, 0)),
                emissions
            )

            advanceTimeBy(16_499)
            runCurrent()
            assertEquals(1, emissions.size)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(
                listOf(
                    LocalDateTime.of(2026, 4, 25, 12, 0),
                    LocalDateTime.of(2026, 4, 25, 12, 1),
                ),
                emissions
            )

            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(
                listOf(
                    LocalDateTime.of(2026, 4, 25, 12, 0),
                    LocalDateTime.of(2026, 4, 25, 12, 1),
                    LocalDateTime.of(2026, 4, 25, 12, 2),
                ),
                emissions
            )

            job.cancel()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun refresh_reReadsWallClockForActiveSubscribers() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            // A clock whose wall time is set independently of the test
            // scheduler, so we can advance virtual time (driving delay /
            // WhileSubscribed) without moving the wall clock, and vice versa.
            val clock = MutableInstantClock(
                instant = Instant.parse("2026-04-25T12:00:30Z"),
                zone = ZoneOffset.UTC,
            )
            val source = DefaultAppTimeSource(clock = clock, appScope = backgroundScope)

            // Keep the StateFlow hot for the whole scenario — this mirrors a
            // warm/frozen process whose WhileSubscribed window never elapsed
            // across a brief backgrounding.
            val collector = launch { source.currentMinute.collect {} }
            runCurrent()
            assertEquals(
                LocalDateTime.of(2026, 4, 25, 12, 0),
                source.currentMinute.value,
            )

            // The wall clock jumps forward while the app is backgrounded (user
            // changes date/time, or a midnight crossing). The monotonic
            // minute-ticker delay has NOT yet reached the next boundary, so the
            // cached value is stale: without a forced refresh the home screens
            // would keep showing the old time until the next tick.
            clock.instant = Instant.parse("2026-04-25T13:30:30Z")
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(
                LocalDateTime.of(2026, 4, 25, 12, 0),
                source.currentMinute.value,
            )

            // Foregrounding nudges the source to re-read the wall clock
            // immediately, without waiting up to a minute for the next tick.
            source.refresh()
            runCurrent()
            assertEquals(
                LocalDateTime.of(2026, 4, 25, 13, 30),
                source.currentMinute.value,
            )

            collector.cancel()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun currentMinute_usesUpdatedSystemDefaultZone() {
        val originalTimeZone = TimeZone.getDefault()
        val clock = Clock.fixed(
            Instant.parse("2026-04-25T12:00:00Z"),
            ZoneOffset.UTC
        )

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(LocalDateTime.of(2026, 4, 25, 12, 0), currentMinute(clock))

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(LocalDateTime.of(2026, 4, 25, 21, 0), currentMinute(clock))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun millisUntilNextMinuteBoundary_usesStrictlyFutureBoundaryAtExactMinute() {
        val clock = Clock.fixed(
            Instant.parse("2026-04-25T12:00:00Z"),
            ZoneOffset.UTC
        )

        assertEquals(60_000L, millisUntilNextMinuteBoundary(clock))
    }
}

private class MutableInstantClock(
    @Volatile var instant: Instant,
    private val zone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock =
        MutableInstantClock(instant = instant, zone = zone)

    override fun instant(): Instant = instant
}

@OptIn(ExperimentalCoroutinesApi::class)
private class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val baseInstant: Instant,
    private val zone: ZoneId
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock {
        return SchedulerClock(
            scheduler = scheduler,
            baseInstant = baseInstant,
            zone = zone
        )
    }

    override fun instant(): Instant {
        return baseInstant.plusMillis(scheduler.currentTime)
    }
}
