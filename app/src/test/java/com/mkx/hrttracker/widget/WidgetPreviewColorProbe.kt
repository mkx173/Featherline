package com.mkx.hrttracker.widget

import org.junit.Ignore
import org.junit.Test

// Manual probe: prints the Default-appearance surface hexes that res/values{,-night}/
// colors.xml widget_preview_* tokens must mirror (the static launcher previews bake
// these because previewLayout XML can't run the dynamic derivation). Re-run whenever
// the widget derivation changes, paste the output, and bump GENERATED_PREVIEW_VERSION.
// WidgetPreviewColorsSyncTest guards the paste against drift using the same derivation.
@Ignore("manual probe - run by hand when the widget derivation changes")
class WidgetPreviewColorProbe {
    @Test
    fun printStaticPreviewHexes() {
        for ((mode, tokens) in widgetPreviewDerivedHexes()) {
            for ((token, hex) in tokens) {
                println("[$mode] $token=$hex")
            }
        }
    }
}
