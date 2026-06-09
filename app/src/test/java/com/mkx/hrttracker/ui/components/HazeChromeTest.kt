package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HazeChromeTest {
    @Test
    fun haze_blur_is_supported_on_android_12_and_newer() {
        assertFalse(isHazeBlurSupported(sdkInt = 30))
        assertTrue(isHazeBlurSupported(sdkInt = 31))
        assertTrue(isHazeBlurSupported(sdkInt = 35))
    }

    @Test
    fun effective_haze_blur_requires_supported_platform_and_enabled_preference() {
        assertFalse(effectiveHazeBlurEnabled(preferenceEnabled = true, sdkInt = 30))
        assertFalse(effectiveHazeBlurEnabled(preferenceEnabled = false, sdkInt = 31))
        assertTrue(effectiveHazeBlurEnabled(preferenceEnabled = true, sdkInt = 31))
    }
}
