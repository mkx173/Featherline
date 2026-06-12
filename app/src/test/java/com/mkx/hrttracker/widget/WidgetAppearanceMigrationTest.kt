package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAppearanceMigrationTest {

    @Test
    fun `effective appearance resolves override then default then built-in`() {
        val default = WidgetAppearance.Default.copy(vibrancy = 0.9f)
        val override = WidgetAppearance.Default.copy(seedHue = 200f)
        assertEquals(override, resolveEffectiveAppearance(override, default))
        assertEquals(default, resolveEffectiveAppearance(null, default))
        assertEquals(WidgetAppearance.Default, resolveEffectiveAppearance(null, null))
    }

    @Test
    fun `legacy settings map onto Default with new params untouched`() {
        val migrated = legacyWidgetAppearance(
            contentScale = 1.3f, backgroundAlpha = 0.6f, darkMode = DarkModeOption.DARK,
        )
        assertEquals(
            WidgetAppearance.Default.copy(
                contentScale = 1.3f, backgroundAlpha = 0.6f, darkMode = DarkModeOption.DARK,
            ),
            migrated,
        )
        assertNull(migrated.seedHue)
        assertNull(migrated.backgroundHue)
        assertEquals(WidgetAppearance.DEFAULT_VIBRANCY, migrated.vibrancy, 0f)
    }
}
