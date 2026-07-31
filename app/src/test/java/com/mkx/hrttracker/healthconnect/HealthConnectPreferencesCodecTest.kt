package com.mkx.hrttracker.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectPreferencesCodecTest {
    @Test
    fun fingerprintsRoundTripWithoutChangingIdsOrHashes() {
        val fingerprints = linkedMapOf(
            "78038bbf-01d9-4c6a-9f2f-46b72850d811" to "abc123",
            "5e5ae3c2-bb72-45ab-8f65-bbd6af5d3a9e" to "def456",
        )

        assertEquals(fingerprints, decodeFingerprints(encodeFingerprints(fingerprints)))
    }

    @Test
    fun malformedFingerprintRowsAreIgnored() {
        assertEquals(
            mapOf("valid-id" to "valid-hash"),
            decodeFingerprints(
                setOf("", "missing-separator", "|missing-id", "valid-id|valid-hash")
            ),
        )
    }
}
