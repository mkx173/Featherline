package com.mkx.hrttracker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

interface AppTimeSource {
    val currentMinute: StateFlow<LocalDateTime>
}

class DefaultAppTimeSource(
    clock: Clock,
    appScope: CoroutineScope,
    stopTimeoutMillis: Long = APP_TIME_SOURCE_STOP_TIMEOUT_MILLIS
) : AppTimeSource {
    override val currentMinute: StateFlow<LocalDateTime> = currentMinuteTicker(clock)
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis),
            initialValue = currentMinute(clock)
        )
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
