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
