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
    fun appTimeSnapshotTicker_emitsImmediateSnapshotThenMinuteBoundaries() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val utcZone = ZoneId.of("UTC")
            val clock = SchedulerClock(
                scheduler = testScheduler,
                baseInstant = Instant.parse("2026-04-25T12:00:43.500Z"),
                zone = ZoneOffset.UTC
            )
            val emissions = mutableListOf<AppTimeSnapshot>()

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                appTimeSnapshotTicker(clock)
                    .take(3)
                    .toList(emissions)
            }

            assertEquals(
                listOf(AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), utcZone)),
                emissions
            )

            advanceTimeBy(16_499)
            runCurrent()
            assertEquals(1, emissions.size)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(
                listOf(
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), utcZone),
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 1), utcZone),
                ),
                emissions
            )

            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(
                listOf(
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), utcZone),
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 1), utcZone),
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 2), utcZone),
                ),
                emissions
            )

            job.cancel()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun appTimeSnapshotTicker_emitsMinuteSnapshotsWithoutSyntheticGeneration() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val utcZone = ZoneId.of("UTC")
            val clock = SchedulerClock(
                scheduler = testScheduler,
                baseInstant = Instant.parse("2026-04-25T12:00:43.500Z"),
                zone = ZoneOffset.UTC
            )
            val emissions = mutableListOf<AppTimeSnapshot>()

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                appTimeSnapshotTicker(clock)
                    .take(2)
                    .toList(emissions)
            }

            advanceTimeBy(16_500)
            runCurrent()

            assertEquals(
                listOf(
                    AppTimeSnapshot(
                        minute = LocalDateTime.of(2026, 4, 25, 12, 0),
                        zone = utcZone,
                    ),
                    AppTimeSnapshot(
                        minute = LocalDateTime.of(2026, 4, 25, 12, 1),
                        zone = utcZone,
                    ),
                ),
                emissions,
            )

            job.cancel()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun refresh_reReadsZoneAndMinuteForActiveSnapshotSubscribers() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val tokyoZone = ZoneId.of("Asia/Tokyo")
            val clock = MutableInstantClock(
                instant = Instant.parse("2026-04-25T12:00:30Z"),
                zone = ZoneOffset.UTC,
            )
            val source = DefaultAppTimeSource(clock = clock, appScope = backgroundScope)

            val collector = launch { source.currentSnapshot.collect {} }
            runCurrent()
            assertEquals(
                AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC")),
                source.currentSnapshot.value,
            )

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            source.refresh()
            runCurrent()

            val expectedSnapshot = AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 21, 0), tokyoZone)
            assertEquals(expectedSnapshot, source.currentSnapshot.value)
            assertEquals(expectedSnapshot.minute, source.currentMinute.value)
            assertEquals(expectedSnapshot.zone, source.currentZone.value)

            collector.cancel()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun refresh_reEmitsOnlyWhenSnapshotValueChanges() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val clock = MutableInstantClock(
                instant = Instant.parse("2026-04-25T12:00:30Z"),
                zone = ZoneOffset.UTC,
            )
            val source = DefaultAppTimeSource(clock = clock, appScope = backgroundScope)
            val emissions = mutableListOf<AppTimeSnapshot>()

            val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
                source.currentSnapshot
                    .take(2)
                    .toList(emissions)
            }
            runCurrent()
            assertEquals(
                listOf(AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC"))),
                emissions,
            )

            source.refresh()
            runCurrent()
            assertEquals(
                listOf(AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC"))),
                emissions,
            )

            clock.instant = Instant.parse("2026-04-25T13:30:30Z")
            source.refresh()
            runCurrent()

            assertEquals(
                listOf(
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC")),
                    AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 13, 30), ZoneId.of("UTC")),
                ),
                emissions,
            )

            collector.cancel()
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
    fun refresh_realignsTickerToNewWallClockBoundary() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val clock = MutableInstantClock(
                instant = Instant.parse("2026-04-25T12:00:00Z"),
                zone = ZoneOffset.UTC,
            )
            val source = DefaultAppTimeSource(clock = clock, appScope = backgroundScope)

            val collector = launch { source.currentMinute.collect {} }
            runCurrent()
            // Ticker emitted 12:00 and is now sleeping until the 12:01 boundary,
            // i.e. for a full 60s of monotonic time.
            assertEquals(
                LocalDateTime.of(2026, 4, 25, 12, 0),
                source.currentMinute.value,
            )

            // 1s into that sleep the app backgrounds; the wall clock is moved to
            // 13:30:59 and the app is foregrounded -> refresh().
            advanceTimeBy(1_000)
            clock.instant = Instant.parse("2026-04-25T13:30:59Z")
            source.refresh()
            runCurrent()
            assertEquals(
                LocalDateTime.of(2026, 4, 25, 13, 30),
                source.currentMinute.value,
            )

            // 1 real second later the wall clock crosses 13:31:00. The minute
            // label must advance promptly, NOT wait out the original ~59s sleep
            // that was scheduled against the pre-jump clock.
            advanceTimeBy(1_000)
            clock.instant = Instant.parse("2026-04-25T13:31:00Z")
            runCurrent()
            assertEquals(
                LocalDateTime.of(2026, 4, 25, 13, 31),
                source.currentMinute.value,
            )

            collector.cancel()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun currentMinuteProjection_doesNotKeepSnapshotTickerActiveAfterCancellation() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val clock = SchedulerClock(
                scheduler = testScheduler,
                baseInstant = Instant.parse("2026-04-25T12:00:00Z"),
                zone = ZoneOffset.UTC,
            )
            val source = DefaultAppTimeSource(
                clock = clock,
                appScope = backgroundScope,
                stopTimeoutMillis = 0L,
            )

            val collector = launch { source.currentMinute.collect {} }
            runCurrent()
            assertEquals(
                AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC")),
                source.currentSnapshot.value,
            )

            collector.cancel()
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(
                AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC")),
                source.currentSnapshot.value,
            )
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun currentAppTimeSnapshot_readsInstantAndZoneAsOneValue() {
        val originalTimeZone = TimeZone.getDefault()
        val clock = Clock.fixed(
            Instant.parse("2026-04-25T12:00:00Z"),
            ZoneOffset.UTC
        )

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 12, 0), ZoneId.of("UTC")),
                currentAppTimeSnapshot(clock),
            )
            assertEquals(LocalDateTime.of(2026, 4, 25, 12, 0), currentMinute(clock))

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(
                AppTimeSnapshot(LocalDateTime.of(2026, 4, 25, 21, 0), ZoneId.of("Asia/Tokyo")),
                currentAppTimeSnapshot(clock),
            )
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
