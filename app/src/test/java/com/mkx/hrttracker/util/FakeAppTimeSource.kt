package com.mkx.hrttracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class FakeAppTimeSource(
    initialMinute: LocalDateTime,
    initialZone: ZoneId = ZoneId.systemDefault(),
) : AppTimeSource {
    private val mutableCurrentSnapshot = MutableStateFlow(
        AppTimeSnapshot(
            minute = initialMinute.truncatedTo(ChronoUnit.MINUTES),
            zone = initialZone,
        )
    )
    private val mutableCurrentMinute = MutableStateFlow(mutableCurrentSnapshot.value.minute)
    private val mutableCurrentZone = MutableStateFlow(mutableCurrentSnapshot.value.zone)
    private var currentInstant = mutableCurrentSnapshot.value.minute
        .atZone(mutableCurrentSnapshot.value.zone)
        .toInstant()
    private var refreshGeneration = mutableCurrentSnapshot.value.refreshGeneration

    override val currentSnapshot: StateFlow<AppTimeSnapshot> = mutableCurrentSnapshot
    override val currentMinute: StateFlow<LocalDateTime> = mutableCurrentMinute
    override val currentZone: StateFlow<ZoneId> = mutableCurrentZone

    override fun now(): Instant = currentInstant

    override fun refresh() {
        setCurrentSnapshot(
            currentMinute = mutableCurrentSnapshot.value.minute,
            zoneId = mutableCurrentSnapshot.value.zone,
            refresh = true,
        )
    }

    fun setCurrentSnapshot(
        currentMinute: LocalDateTime,
        zoneId: ZoneId,
        refresh: Boolean = false,
    ) {
        val normalizedCurrentMinute = currentMinute.truncatedTo(ChronoUnit.MINUTES)
        if (refresh) {
            refreshGeneration += 1L
        }
        val snapshot = AppTimeSnapshot(
            minute = normalizedCurrentMinute,
            zone = zoneId,
            refreshGeneration = refreshGeneration,
        )
        mutableCurrentSnapshot.value = snapshot
        mutableCurrentMinute.value = snapshot.minute
        mutableCurrentZone.value = snapshot.zone
        currentInstant = snapshot.minute.atZone(snapshot.zone).toInstant()
    }

    fun setCurrentZone(zoneId: ZoneId) {
        setCurrentSnapshot(
            currentMinute = mutableCurrentSnapshot.value.minute,
            zoneId = zoneId,
        )
    }

    fun setCurrentMinute(currentMinute: LocalDateTime) {
        setCurrentSnapshot(
            currentMinute = currentMinute,
            zoneId = mutableCurrentSnapshot.value.zone,
        )
    }

    fun refreshToSnapshot(currentMinute: LocalDateTime, zoneId: ZoneId) {
        setCurrentSnapshot(
            currentMinute = currentMinute,
            zoneId = zoneId,
            refresh = true,
        )
    }

    fun setCurrentInstant(currentInstant: Instant) {
        this.currentInstant = currentInstant
        val currentZone = mutableCurrentSnapshot.value.zone
        val currentMinute = LocalDateTime
            .ofInstant(currentInstant, currentZone)
            .truncatedTo(ChronoUnit.MINUTES)
        mutableCurrentSnapshot.value = AppTimeSnapshot(
            minute = currentMinute,
            zone = currentZone,
            refreshGeneration = refreshGeneration,
        )
        mutableCurrentMinute.value = currentMinute
        mutableCurrentZone.value = currentZone
    }
}
