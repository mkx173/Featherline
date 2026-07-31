package com.mkx.hrttracker.cloudsync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncDecisionResolverTest {
    @Test
    fun missing_remote_uploads_local_snapshot() {
        assertDecision(CloudSyncDecision.UPLOAD, local = "local", remote = null, last = null)
    }

    @Test
    fun matching_hashes_are_up_to_date() {
        assertDecision(CloudSyncDecision.UP_TO_DATE, local = "same", remote = "same", last = null)
    }

    @Test
    fun only_local_change_uploads() {
        assertDecision(CloudSyncDecision.UPLOAD, local = "local-new", remote = "old", last = "old")
    }

    @Test
    fun only_remote_change_downloads() {
        assertDecision(CloudSyncDecision.DOWNLOAD, local = "old", remote = "remote-new", last = "old")
    }

    @Test
    fun two_sided_changes_stop_for_conflict_resolution() {
        assertDecision(
            CloudSyncDecision.CONFLICT,
            local = "local-new",
            remote = "remote-new",
            last = "old",
        )
    }

    @Test
    fun existing_cloud_on_fresh_device_requires_explicit_choice() {
        assertDecision(
            CloudSyncDecision.CONFLICT,
            local = "fresh-local",
            remote = "existing-cloud",
            last = null,
        )
    }

    @Test
    fun intervals_map_to_requested_day_counts() {
        assertEquals(1L, CloudSyncInterval.DAILY.days)
        assertEquals(3L, CloudSyncInterval.EVERY_THREE_DAYS.days)
        assertEquals(7L, CloudSyncInterval.WEEKLY.days)
        assertEquals(30L, CloudSyncInterval.MONTHLY.days)
    }

    private fun assertDecision(
        expected: CloudSyncDecision,
        local: String,
        remote: String?,
        last: String?,
    ) {
        assertEquals(
            expected,
            CloudSyncDecisionResolver.resolve(
                localHash = local,
                remoteHash = remote,
                lastSyncedHash = last,
            ),
        )
    }
}
