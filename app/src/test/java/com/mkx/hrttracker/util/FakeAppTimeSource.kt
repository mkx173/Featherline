package com.mkx.hrttracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class FakeAppTimeSource(initialMinute: LocalDateTime) : AppTimeSource {
    private val mutableCurrentMinute = MutableStateFlow(initialMinute.withSecond(0).withNano(0))
    private var currentInstant = mutableCurrentMinute.value.atZone(ZoneId.systemDefault()).toInstant()

    override val currentMinute: StateFlow<LocalDateTime> = mutableCurrentMinute

    override fun now(): Instant = currentInstant

    override fun refresh() {
        // No-op: tests drive this fake's clock explicitly via setCurrentMinute /
        // setCurrentInstant, so there is no lazily-cached value to refresh.
    }

    fun setCurrentMinute(currentMinute: LocalDateTime) {
        val normalizedCurrentMinute = currentMinute.withSecond(0).withNano(0)
        mutableCurrentMinute.value = normalizedCurrentMinute
        currentInstant = normalizedCurrentMinute.atZone(ZoneId.systemDefault()).toInstant()
    }

    fun setCurrentInstant(currentInstant: Instant) {
        this.currentInstant = currentInstant
        mutableCurrentMinute.value = LocalDateTime
            .ofInstant(currentInstant, ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.MINUTES)
    }
}
