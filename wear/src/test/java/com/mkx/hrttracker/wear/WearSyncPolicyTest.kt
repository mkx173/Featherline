package com.mkx.hrttracker.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSyncPolicyTest {
    @Test
    fun freshSnapshotDoesNotRequestPhone() {
        assertFalse(
            shouldRequestWearSnapshot(
                snapshotGeneratedAtEpochMillis = 900_000L,
                lastRequestedAtEpochMillis = 0L,
                nowEpochMillis = 1_000_000L,
            )
        )
    }

    @Test
    fun missingSnapshotRetriesAtMostEveryThirtySeconds() {
        assertFalse(
            shouldRequestWearSnapshot(
                snapshotGeneratedAtEpochMillis = null,
                lastRequestedAtEpochMillis = 990_000L,
                nowEpochMillis = 1_000_000L,
            )
        )
        assertTrue(
            shouldRequestWearSnapshot(
                snapshotGeneratedAtEpochMillis = null,
                lastRequestedAtEpochMillis = 900_000L,
                nowEpochMillis = 1_000_000L,
            )
        )
    }

    @Test
    fun staleSnapshotRequestIsThrottledForFiveMinutes() {
        assertFalse(
            shouldRequestWearSnapshot(
                snapshotGeneratedAtEpochMillis = 0L,
                lastRequestedAtEpochMillis = 1_900_000L,
                nowEpochMillis = 2_000_000L,
            )
        )
        assertTrue(
            shouldRequestWearSnapshot(
                snapshotGeneratedAtEpochMillis = 0L,
                lastRequestedAtEpochMillis = 0L,
                nowEpochMillis = 2_000_000L,
            )
        )
    }
}
