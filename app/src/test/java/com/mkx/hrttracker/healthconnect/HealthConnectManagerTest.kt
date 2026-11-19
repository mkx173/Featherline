package com.mkx.hrttracker.healthconnect

import android.annotation.SuppressLint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HealthConnectManagerTest {
    // The fakes model this end, so only this sees the real read's filter.
    @SuppressLint("RestrictedApi")
    @Test
    fun latestWeightRequest_endsAtNow() {
        val now = Instant.parse("2026-10-16T09:00:00Z")

        // Never open-ended (Android 14+ would end it at now + 1 day) and never
        // beyond now: a record dated after now makes the whole read throw there.
        assertEquals(now, latestWeightRequest(now).timeRangeFilter.endTime)
    }
}
