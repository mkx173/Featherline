package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAppearanceCodecTest {

    @Test
    fun `round-trips all-null appearance`() {
        val a = WidgetAppearance.Default
        assertEquals(a, WidgetAppearanceCodec.decode(WidgetAppearanceCodec.encode(a)))
    }

    @Test
    fun `round-trips fully-populated appearance`() {
        val a = WidgetAppearance(
            seedHue = 212.5f, saturation = 0.8f, vibrancy = 0.8f,
            contentScale = 1.2f, backgroundAlpha = 0.7f, darkMode = DarkModeOption.DARK,
        )
        assertEquals(a, WidgetAppearanceCodec.decode(WidgetAppearanceCodec.encode(a)))
    }

    @Test
    fun `decode sanitizes out-of-range values instead of failing`() {
        // Restored backups / hand-edited stores must not brick the widget: values clamp.
        val encoded = "2|400.0|3.0|3.0|9.0|0.1|DARK"
        val decoded = WidgetAppearanceCodec.decode(encoded)!!
        assertEquals(40f, decoded.seedHue!!, 1e-4f)        // 400 mod 360
        assertEquals(1f, decoded.saturation, 0f)           // 3.0 clamped to 1
        assertEquals(1f, decoded.vibrancy, 0f)
        assertEquals(1.5f, decoded.contentScale, 0f)
        assertEquals(0.5f, decoded.backgroundAlpha, 0f)
    }

    @Test
    fun `decode accepts v1 dropping background hue and anchoring saturation`() {
        // v1-compat: existing stores / old backups must keep decoding. The old
        // slot-2 backgroundHue (180.0) is IGNORED and saturation defaults to the
        // anchor, so a v1 entry reproduces today's output.
        val decoded = WidgetAppearanceCodec.decode("1|212.5|180.0|0.8|1.2|0.7|DARK")!!
        assertEquals(212.5f, decoded.seedHue!!, 1e-4f)
        assertEquals(WidgetAppearance.DEFAULT_SATURATION, decoded.saturation, 0f)
        assertEquals(0.8f, decoded.vibrancy, 0f)
        assertEquals(1.2f, decoded.contentScale, 0f)
        assertEquals(0.7f, decoded.backgroundAlpha, 0f)
        assertEquals(DarkModeOption.DARK, decoded.darkMode)

        // v1 with EMPTY backgroundHue slot (null was stored as empty) must also decode.
        val emptySlot = WidgetAppearanceCodec.decode("1|212.5||0.8|1.2|0.7|DARK")!!
        assertEquals(212.5f, emptySlot.seedHue!!, 1e-4f)
        assertEquals(WidgetAppearance.DEFAULT_SATURATION, emptySlot.saturation, 0f)
    }

    @Test
    fun `decode returns null for garbage, wrong arity, unknown version`() {
        assertNull(WidgetAppearanceCodec.decode(""))
        assertNull(WidgetAppearanceCodec.decode("banana"))
        assertNull(WidgetAppearanceCodec.decode("2|||0.4|1.0|1.0|FOLLOW_SYSTEM"))
        assertNull(WidgetAppearanceCodec.decode("1|||0.4|1.0|1.0"))
        assertNull(WidgetAppearanceCodec.decode("1|||0.4|1.0|1.0|NOT_A_MODE"))
        // Unknown future version with correct arity still rejects.
        assertNull(WidgetAppearanceCodec.decode("3|212.5|0.5|0.8|1.2|0.7|DARK"))
    }

    @Test
    fun `Default is the regression anchor and must not drift`() {
        // The all-default appearance is contractually pixel-equivalent to the
        // pre-customization widget; these exact values are what makes that true.
        assertEquals(0.5f, WidgetAppearance.DEFAULT_SATURATION, 0f)
        assertEquals(0.4f, WidgetAppearance.DEFAULT_VIBRANCY, 0f)
        assertEquals(
            WidgetAppearance(
                seedHue = null, saturation = 0.5f, vibrancy = 0.4f,
                contentScale = 1.0f, backgroundAlpha = 1.0f,
                darkMode = DarkModeOption.FOLLOW_SYSTEM,
            ),
            WidgetAppearance.Default,
        )
    }
}
