package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenPlatformTest {
    @Test
    fun haze_blur_toggle_is_visible_only_on_android_12_and_newer() {
        assertFalse(shouldShowHazeBlurToggle(sdkInt = 30))
        assertTrue(shouldShowHazeBlurToggle(sdkInt = 31))
    }
}
