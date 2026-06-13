package com.mkx.hrttracker.widget

import com.mkx.hrttracker.data.backup.CURRENT_BACKUP_SNAPSHOT_VERSION
import com.mkx.hrttracker.model.settings.DarkModeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAppearanceCodecTest {

    @Test
    fun `codec format version stays coupled to the backup snapshot version`() {
        // The encoded appearance travels inside backups (settings.widgetAppearance), and
        // BackupRestoreService hard-fails the ENTIRE restore when decode() returns null —
        // which it does for any codec format version an older app does not recognize. So a
        // codec format bump MUST be paired with a CURRENT_BACKUP_SNAPSHOT_VERSION bump, so
        // an older app rejects the newer backup cleanly at the version gate instead of
        // throwing mid-restore. This tripwire pins the current known-good pairing: if you
        // change either constant, change both deliberately and update the expectations here.
        val codecFormatVersion = WidgetAppearanceCodec.encode(WidgetAppearance.Default)
            .substringBefore('|')
            .toInt()
        assertEquals(4, codecFormatVersion)
        assertEquals(4, CURRENT_BACKUP_SNAPSHOT_VERSION)
    }

    @Test
    fun `round-trips all-null appearance`() {
        val a = WidgetAppearance.Default
        assertEquals(a, WidgetAppearanceCodec.decode(WidgetAppearanceCodec.encode(a)))
    }

    @Test
    fun `round-trips fully-populated appearance`() {
        val a = WidgetAppearance(
            seedHue = 212.5f, saturation = 0.8f, balance = 0.8f,
            contentScale = 1.2f, backgroundAlpha = 0.7f, darkMode = DarkModeOption.DARK,
        )
        assertEquals(a, WidgetAppearanceCodec.decode(WidgetAppearanceCodec.encode(a)))
    }

    @Test
    fun `decode sanitizes out-of-range values instead of failing`() {
        // Restored backups / hand-edited stores must not brick the widget: values clamp.
        val encoded = "3|400.0|3.0|3.0|9.0|0.1|DARK"
        val decoded = WidgetAppearanceCodec.decode(encoded)!!
        assertEquals(40f, decoded.seedHue!!, 1e-4f)        // 400 mod 360
        assertEquals(1f, decoded.saturation, 0f)           // 3.0 clamped to 1
        assertEquals(1f, decoded.balance, 0f)
        assertEquals(1.5f, decoded.contentScale, 0f)
        assertEquals(0.5f, decoded.backgroundAlpha, 0f)
    }

    @Test
    fun `decode accepts v2 mapping vibrancy onto balance and carrying saturation`() {
        // v2-compat: Round-3 strings carried saturation + a combined vibrancy. Round 4
        // splits them — saturation carries over as-is, vibrancy re-anchors onto balance
        // via ((v-0.4)/0.6).coerceIn(0,1).
        val decoded = WidgetAppearanceCodec.decode("2|212.5|0.8|1.0|1.2|0.7|DARK")!!
        assertEquals(212.5f, decoded.seedHue!!, 1e-4f)
        assertEquals(0.8f, decoded.saturation, 0f)
        assertEquals(1.0f, decoded.balance, 1e-4f)  // vibrancy 1.0 → balance 1.0
        assertEquals(1.2f, decoded.contentScale, 0f)
        assertEquals(0.7f, decoded.backgroundAlpha, 0f)
        assertEquals(DarkModeOption.DARK, decoded.darkMode)

        // v2 at the old vibrancy anchor (0.4) maps to depth 0, then to balance 0.5 — the
        // new bidirectional anchor (today's tones).
        val anchor = WidgetAppearanceCodec.decode("2||0.5|0.4|1.0|1.0|FOLLOW_SYSTEM")!!
        assertNull(anchor.seedHue)
        assertEquals(0.5f, anchor.saturation, 0f)
        assertEquals(0.5f, anchor.balance, 1e-4f)
    }

    @Test
    fun `decode accepts v1 dropping background hue and anchoring saturation`() {
        // v1-compat: existing stores / old backups must keep decoding. The old
        // slot-2 backgroundHue (180.0) is IGNORED, saturation defaults to the anchor,
        // and the slot-3 vibrancy (0.4 anchor) maps onto depth 0 then balance 0.5 — today's
        // output at the new bidirectional anchor.
        val decoded = WidgetAppearanceCodec.decode("1|212.5|180.0|0.4|1.2|0.7|DARK")!!
        assertEquals(212.5f, decoded.seedHue!!, 1e-4f)
        assertEquals(WidgetAppearance.DEFAULT_SATURATION, decoded.saturation, 0f)
        assertEquals(0.5f, decoded.balance, 1e-4f)
        assertEquals(1.2f, decoded.contentScale, 0f)
        assertEquals(0.7f, decoded.backgroundAlpha, 0f)
        assertEquals(DarkModeOption.DARK, decoded.darkMode)

        // v1 with EMPTY backgroundHue slot (null was stored as empty) must also decode.
        val emptySlot = WidgetAppearanceCodec.decode("1|212.5||0.4|1.2|0.7|DARK")!!
        assertEquals(212.5f, emptySlot.seedHue!!, 1e-4f)
        assertEquals(WidgetAppearance.DEFAULT_SATURATION, emptySlot.saturation, 0f)
        assertEquals(0.5f, emptySlot.balance, 1e-4f)
    }

    @Test
    fun `decode returns null for garbage, wrong arity, unknown version`() {
        assertNull(WidgetAppearanceCodec.decode(""))
        assertNull(WidgetAppearanceCodec.decode("banana"))
        assertNull(WidgetAppearanceCodec.decode("3|||x|1.0|1.0|FOLLOW_SYSTEM"))
        assertNull(WidgetAppearanceCodec.decode("1|||0.4|1.0|1.0"))
        assertNull(WidgetAppearanceCodec.decode("1|||0.4|1.0|1.0|NOT_A_MODE"))
        // Unknown future version with correct arity still rejects.
        assertNull(WidgetAppearanceCodec.decode("5|212.5|0.5|0.8|1.2|0.7|DARK"))
    }

    @Test
    fun `decode remaps v3 one-directional balance onto the bidirectional anchor`() {
        // v3 stored balance with 0 = anchor (today's tones) / 1 = deepest. Round 6 moved the
        // anchor to 0.5, so old depth maps onto the deepen half via 0.5 + 0.5·balance — a
        // stored v3 anchor (0) must re-emerge as 0.5, and the deepest (1) stays 1.
        assertEquals(0.5f, WidgetAppearanceCodec.decode("3||0.5|0.0|1.0|1.0|FOLLOW_SYSTEM")!!.balance, 1e-4f)
        assertEquals(0.75f, WidgetAppearanceCodec.decode("3||0.5|0.5|1.0|1.0|FOLLOW_SYSTEM")!!.balance, 1e-4f)
        assertEquals(1.0f, WidgetAppearanceCodec.decode("3||0.5|1.0|1.0|1.0|FOLLOW_SYSTEM")!!.balance, 1e-4f)
    }

    @Test
    fun `Default is the regression anchor and must not drift`() {
        // The all-default appearance is contractually pixel-equivalent to the
        // pre-customization widget; these exact values are what makes that true.
        assertEquals(0.5f, WidgetAppearance.DEFAULT_SATURATION, 0f)
        assertEquals(0.5f, WidgetAppearance.DEFAULT_BALANCE, 0f)
        assertEquals(
            WidgetAppearance(
                seedHue = null, saturation = 0.5f, balance = 0.5f,
                contentScale = 1.0f, backgroundAlpha = 1.0f,
                darkMode = DarkModeOption.FOLLOW_SYSTEM,
            ),
            WidgetAppearance.Default,
        )
    }
}
