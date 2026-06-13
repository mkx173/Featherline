package com.mkx.hrttracker.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mkx.hrttracker.ui.theme.DefaultSeedColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

// The Default-appearance hexes the static launcher previews bake into
// res/values{,-night}/colors.xml (previewLayout XML can't run the dynamic
// derivation). Shared by this sync test and the manual WidgetPreviewColorProbe so
// the probe's paste-me output and the guard can never diverge. Mode -> token -> hex.
internal fun widgetPreviewDerivedHexes(): Map<String, Map<String, String>> {
    fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
    val (light, dark) = seededWidgetColorSchemes(DefaultSeedColor)
    return listOf(
        Triple("light", light, false), Triple("night", dark, true),
    ).associate { (mode, scheme, isDark) ->
        val s = deriveWidgetSurfaces(
            scheme.secondaryContainer, scheme.onSurface, scheme.onSurfaceVariant,
            scheme.outlineVariant,
            saturation = WidgetAppearance.DEFAULT_SATURATION,
            balance = WidgetAppearance.DEFAULT_BALANCE, dark = isDark,
        )
        mode to mapOf(
            "widget_preview_background" to hex(s.shell),
            "widget_preview_card" to hex(s.card),
            "widget_preview_control" to hex(s.control),
            "widget_preview_on_surface" to hex(s.onSurface),
            "widget_preview_on_surface_variant" to hex(s.onSurfaceVariant),
            // s.outlineVariant at the anchor (u==0) is the short-circuited scheme
            // color, so this is exactly scheme.outlineVariant.
            "widget_preview_outline_variant" to hex(s.outlineVariant),
            "widget_preview_primary" to hex(scheme.primary),
            "widget_preview_on_primary" to hex(scheme.onPrimary),
            "widget_preview_primary_container" to hex(scheme.primaryContainer),
            "widget_preview_on_primary_container" to hex(scheme.onPrimaryContainer),
        )
    }
}

// Always-on guard: fails when the widget derivation is retuned without re-pasting
// the probe output into colors.xml. Only the derived tokens are covered — the
// hand-picked widget_preview_accent_* constants are not derivation output.
class WidgetPreviewColorsSyncTest {

    @Test
    fun `colors xml widget_preview tokens match the widget derivation`() {
        val derived = widgetPreviewDerivedHexes()
        val xmlByMode = mapOf(
            "light" to resColorsFile("values"),
            "night" to resColorsFile("values-night"),
        )
        for ((mode, file) in xmlByMode) {
            val declared = parseColorTokens(file)
            for ((token, expectedHex) in derived.getValue(mode)) {
                val declaredHex = declared[token]
                assertNotNull("[$mode] $token missing from ${file.path}", declaredHex)
                assertEquals(
                    "[$mode] $token in ${file.path} is out of sync with the widget " +
                        "derivation - re-run WidgetPreviewColorProbe and paste its output",
                    expectedHex,
                    normalizeHex(declaredHex!!),
                )
            }
        }
    }

    // JVM unit tests run with the app module as the working dir; fall back to the
    // repo root in case a different runner working dir is used.
    private fun resColorsFile(valuesDir: String): File {
        val relative = "src/main/res/$valuesDir/colors.xml"
        return listOf(File(relative), File("app", relative)).firstOrNull(File::exists)
            ?: error("colors.xml not found relative to ${File(".").absolutePath}")
    }

    private fun parseColorTokens(file: File): Map<String, String> =
        Regex("""<color name="(\w+)">(#[0-9A-Fa-f]{6,8})</color>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    // Compare on the RGB channels: drop a leading alpha pair if present.
    private fun normalizeHex(hex: String): String = "#" + hex.removePrefix("#").takeLast(6).uppercase()
}
