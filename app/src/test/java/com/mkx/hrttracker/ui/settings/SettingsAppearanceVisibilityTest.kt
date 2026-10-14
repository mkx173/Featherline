package com.mkx.hrttracker.ui.settings

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAppearanceVisibilityTest {
    @Test
    fun adaptiveColor_hiddenBelowS() {
        assertFalse(shouldShowAdaptiveColor(Build.VERSION_CODES.R))
    }

    @Test
    fun adaptiveColor_shownFromS() {
        assertTrue(shouldShowAdaptiveColor(Build.VERSION_CODES.S))
    }
}
