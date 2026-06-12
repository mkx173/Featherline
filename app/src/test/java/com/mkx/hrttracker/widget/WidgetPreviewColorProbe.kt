package com.mkx.hrttracker.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mkx.hrttracker.ui.theme.DefaultSeedColor
import org.junit.Ignore
import org.junit.Test

// Manual probe: prints the Default-appearance surface hexes that res/values{,-night}/
// colors.xml widget_preview_* tokens must mirror (the static launcher previews bake
// these because previewLayout XML can't run the dynamic derivation). Re-run whenever
// the widget derivation changes, paste the output, and bump GENERATED_PREVIEW_VERSION.
@Ignore("manual probe - run by hand when the widget derivation changes")
class WidgetPreviewColorProbe {
    @Test
    fun printStaticPreviewHexes() {
        fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
        val (light, dark) = seededWidgetColorSchemes(DefaultSeedColor)
        for ((mode, scheme, isDark) in listOf(
            Triple("light", light, false), Triple("night", dark, true),
        )) {
            val s = deriveWidgetSurfaces(
                scheme.secondaryContainer, scheme.onSurface, scheme.onSurfaceVariant,
                backgroundHue = null, vibrancy = WidgetAppearance.DEFAULT_VIBRANCY, dark = isDark,
            )
            println("[$mode] widget_preview_background=${hex(s.shell)}")
            println("[$mode] widget_preview_card=${hex(s.card)}")
            println("[$mode] widget_preview_control=${hex(s.control)}")
            println("[$mode] widget_preview_on_surface=${hex(s.onSurface)}")
            println("[$mode] widget_preview_on_surface_variant=${hex(s.onSurfaceVariant)}")
            println("[$mode] widget_preview_outline_variant=${hex(scheme.outlineVariant)}")
            println("[$mode] widget_preview_primary=${hex(scheme.primary)}")
            println("[$mode] widget_preview_on_primary=${hex(scheme.onPrimary)}")
            println("[$mode] widget_preview_primary_container=${hex(scheme.primaryContainer)}")
            println("[$mode] widget_preview_on_primary_container=${hex(scheme.onPrimaryContainer)}")
        }
    }
}
