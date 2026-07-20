package com.mkx.hrttracker.ui.settings

import android.os.Build
import com.mkx.hrttracker.model.settings.AppLanguageOption
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

    @Test
    fun cjkOffset_shownInChineseLanguages() {
        assertTrue(shouldShowCjkTextOffset(AppLanguageOption.SIMPLIFIED_CHINESE))
        assertTrue(
            shouldShowCjkTextOffset(AppLanguageOption.TRADITIONAL_CHINESE),
        )
        assertTrue(shouldShowCjkTextOffset(AppLanguageOption.CANTONESE))
    }

    @Test
    fun cjkOffset_hiddenForOtherLanguages() {
        AppLanguageOption.entries
            .filter {
                it != AppLanguageOption.SIMPLIFIED_CHINESE &&
                    it != AppLanguageOption.TRADITIONAL_CHINESE &&
                    it != AppLanguageOption.CANTONESE
            }
            .forEach { option ->
                assertFalse(shouldShowCjkTextOffset(option))
            }
    }
}
