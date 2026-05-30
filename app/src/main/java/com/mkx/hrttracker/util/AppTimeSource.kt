package com.mkx.hrttracker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

interface AppTimeSource {
    val currentMinute: StateFlow<LocalDateTime>
    fun now(): Instant

    /**
     * Forces [currentMinute] to re-read the wall clock immediately for active
     * subscribers. Called when the app returns to the foreground so the home
     * screens reflect any date/time change that happened while backgrounded,
     * instead of waiting up to a minute for the next tick.
     */
    fun refresh()
}

class DefaultAppTimeSource(
    private val clock: Clock,
    appScope: CoroutineScope,
    stopTimeoutMillis: Long = APP_TIME_SOURCE_STOP_TIMEOUT_MILLIS
) : AppTimeSource {
    // Out-of-band re-read trigger (e.g. app foregrounding). extraBufferCapacity
    // lets refresh() emit without suspending even when no collector is active.
    private val refreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val currentMinute: StateFlow<LocalDateTime> = merge(
        currentMinuteTicker(clock),
        // The ticker only re-reads the wall clock when its monotonic delay
        // fires (up to a minute away, and paused while the process is frozen).
        // A refresh re-reads it on demand so foregrounding after a background
        // date/time change reflects immediately.
        refreshSignals.map { currentMinute(clock) },
    )
        .distinctUntilChanged()
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis),
            initialValue = currentMinute(clock)
        )

    override fun now(): Instant = Instant.now(clock)

    override fun refresh() {
        refreshSignals.tryEmit(Unit)
    }
}

internal fun currentMinuteTicker(clock: Clock): Flow<LocalDateTime> {
    return flow {
        while (true) {
            emit(currentMinute(clock))
            delay(millisUntilNextMinuteBoundary(clock))
        }
    }.distinctUntilChanged()
}

internal fun currentMinute(clock: Clock): LocalDateTime {
    return LocalDateTime
        .ofInstant(clock.instant(), ZoneId.systemDefault())
        .truncatedTo(ChronoUnit.MINUTES)
}

internal fun millisUntilNextMinuteBoundary(clock: Clock): Long {
    val now = Instant.now(clock)
    val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
    return Duration.between(now, nextMinute).toMillis().coerceAtLeast(1L)
}

private const val APP_TIME_SOURCE_STOP_TIMEOUT_MILLIS = 5_000L
