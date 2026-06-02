package com.mkx.hrttracker.ui.settings

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsAppearanceSectionLayoutTest {
    @Test
    fun resolveSettingsAppearanceSectionLayout_belowS_hidesAdaptiveColor() {
        val layout = resolveSettingsAppearanceSectionLayout(
            sdkInt = Build.VERSION_CODES.R,
            showCjkTextOffset = false,
        )

        assertEquals(4, layout.itemCount)
        assertEquals(0, layout.widgetAppearanceIndex)
        assertEquals(1, layout.appLanguageIndex)
        assertEquals(2, layout.darkModeIndex)
        assertNull(layout.cjkTextOffsetIndex)
        assertNull(layout.adaptiveColorIndex)
        assertEquals(3, layout.pureBlackIndex)
    }

    @Test
    fun resolveSettingsAppearanceSectionLayout_fromS_showsAdaptiveColor() {
        val layout = resolveSettingsAppearanceSectionLayout(
            sdkInt = Build.VERSION_CODES.S,
            showCjkTextOffset = false,
        )

        assertEquals(5, layout.itemCount)
        assertEquals(0, layout.widgetAppearanceIndex)
        assertEquals(1, layout.appLanguageIndex)
        assertEquals(2, layout.darkModeIndex)
        assertNull(layout.cjkTextOffsetIndex)
        assertEquals(3, layout.pureBlackIndex)
        assertEquals(4, layout.adaptiveColorIndex)
    }

    @Test
    fun resolveSettingsAppearanceSectionLayout_belowS_showsCjkTextOffsetWhenChinese() {
        val layout = resolveSettingsAppearanceSectionLayout(
            sdkInt = Build.VERSION_CODES.R,
            showCjkTextOffset = true,
        )

        assertEquals(5, layout.itemCount)
        assertEquals(0, layout.widgetAppearanceIndex)
        assertEquals(1, layout.appLanguageIndex)
        assertEquals(2, layout.darkModeIndex)
        assertNull(layout.adaptiveColorIndex)
        assertEquals(3, layout.pureBlackIndex)
        assertEquals(4, layout.cjkTextOffsetIndex)
    }

    @Test
    fun resolveSettingsAppearanceSectionLayout_fromS_showsCjkTextOffsetWhenChinese() {
        val layout = resolveSettingsAppearanceSectionLayout(
            sdkInt = Build.VERSION_CODES.S,
            showCjkTextOffset = true,
        )

        assertEquals(6, layout.itemCount)
        assertEquals(0, layout.widgetAppearanceIndex)
        assertEquals(1, layout.appLanguageIndex)
        assertEquals(2, layout.darkModeIndex)
        assertEquals(3, layout.pureBlackIndex)
        assertEquals(4, layout.adaptiveColorIndex)
        assertEquals(5, layout.cjkTextOffsetIndex)
    }
}
