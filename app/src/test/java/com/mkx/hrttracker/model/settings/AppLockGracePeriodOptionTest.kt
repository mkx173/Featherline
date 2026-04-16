package com.mkx.hrttracker.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockGracePeriodOptionTest {
    @Test
    fun fromStorageValueFallsBackToImmediateForUnknownValues() {
        assertEquals(
            AppLockGracePeriodOption.IMMEDIATELY,
            AppLockGracePeriodOption.fromStorageValue("not_a_real_value")
        )
    }

    @Test
    fun immediateOptionRelocksWithoutDelay() {
        assertTrue(AppLockGracePeriodOption.IMMEDIATELY.shouldRelock(0L))
        assertTrue(AppLockGracePeriodOption.IMMEDIATELY.shouldRelock(1L))
    }

    @Test
    fun delayedOptionRelocksOnlyAfterConfiguredTimeout() {
        assertFalse(AppLockGracePeriodOption.FIVE_MINUTES.shouldRelock(299_999L))
        assertTrue(AppLockGracePeriodOption.FIVE_MINUTES.shouldRelock(300_000L))
    }
}
