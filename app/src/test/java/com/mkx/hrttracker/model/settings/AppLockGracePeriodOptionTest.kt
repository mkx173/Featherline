package com.mkx.hrttracker.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockGracePeriodOptionTest {
    @Test
    fun fromStorageValueFallsBackToOneMinuteForUnknownValues() {
        assertEquals(
            AppLockGracePeriodOption.ONE_MINUTE,
            AppLockGracePeriodOption.fromStorageValue("not_a_real_value")
        )
        assertEquals(
            AppLockGracePeriodOption.ONE_MINUTE,
            AppLockGracePeriodOption.fromStorageValue(null)
        )
    }

    @Test
    fun settingsStateDefaultsToOneMinuteGracePeriod() {
        assertEquals(
            AppLockGracePeriodOption.ONE_MINUTE,
            SettingsState().appLockGracePeriodOption
        )
    }

    @Test
    fun immediateOptionRelocksWithoutDelay() {
        assertTrue(AppLockGracePeriodOption.IMMEDIATELY.shouldRelock(0L))
        assertTrue(AppLockGracePeriodOption.IMMEDIATELY.shouldRelock(1L))
    }

    @Test
    fun delayedOptionRelocksOnlyAfterConfiguredTimeout() {
        assertFalse(AppLockGracePeriodOption.FIFTEEN_SECONDS.shouldRelock(14_999L))
        assertTrue(AppLockGracePeriodOption.FIFTEEN_SECONDS.shouldRelock(15_000L))
        assertFalse(AppLockGracePeriodOption.THIRTY_SECONDS.shouldRelock(29_999L))
        assertTrue(AppLockGracePeriodOption.THIRTY_SECONDS.shouldRelock(30_000L))
        assertFalse(AppLockGracePeriodOption.ONE_MINUTE.shouldRelock(59_999L))
        assertTrue(AppLockGracePeriodOption.ONE_MINUTE.shouldRelock(60_000L))
        assertFalse(AppLockGracePeriodOption.TWO_MINUTES.shouldRelock(119_999L))
        assertTrue(AppLockGracePeriodOption.TWO_MINUTES.shouldRelock(120_000L))
    }
}
