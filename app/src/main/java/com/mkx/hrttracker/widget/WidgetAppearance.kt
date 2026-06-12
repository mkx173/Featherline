package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption

// Per-widget-instance appearance. v1 UI writes only the `default` store entry;
// per-instance overrides are dormant until a later per-widget editor ships.
// The all-default value must render identical to the pre-customization output
// (enforced by WidgetThemeDerivationTest once the derivation task lands) — except
// the dark control pill, an approved change.
data class WidgetAppearance(
    // HCT hue 0..360, materialized as Hct(hue, 36, 50); null = system dynamic
    // palette (API 31+ with adaptive color) / DefaultSeedColor fallback.
    val seedHue: Float?,
    // 0..1; scales the BASE tint chroma. 0 = neutral black/white,
    // DEFAULT_SATURATION (0.5) = the anchor (bit-identical to today's output at
    // every vibrancy), 1 = double the base chroma. Vibrancy's depth/lift/boost
    // stack ON TOP of this base.
    val saturation: Float,
    // 0 = neutral black/white, DEFAULT_VIBRANCY = today's tint (regression anchor),
    // 1 = max tint. Adds tone depth, the text-tone lift, and the +18 chroma boost
    // on top of saturation's base chroma.
    val vibrancy: Float,
    val contentScale: Float,
    val backgroundAlpha: Float, // backgrounds only; controls stay opaque (existing design)
    val darkMode: DarkModeOption,
) {
    fun sanitized(): WidgetAppearance = copy(
        seedHue = seedHue?.takeIf { it.isFinite() }?.mod(360f),
        saturation = if (saturation.isFinite()) saturation.coerceIn(0f, 1f) else DEFAULT_SATURATION,
        vibrancy = if (vibrancy.isFinite()) vibrancy.coerceIn(0f, 1f) else DEFAULT_VIBRANCY,
        contentScale = if (contentScale.isFinite()) contentScale.coerceIn(0.5f, 1.5f) else 1f,
        backgroundAlpha = if (backgroundAlpha.isFinite()) backgroundAlpha.coerceIn(0.5f, 1f) else 1f,
    )

    companion object {
        const val DEFAULT_SATURATION = 0.5f
        const val DEFAULT_VIBRANCY = 0.4f
        val Default = WidgetAppearance(
            seedHue = null,
            saturation = DEFAULT_SATURATION,
            vibrancy = DEFAULT_VIBRANCY,
            contentScale = 1.0f,
            backgroundAlpha = 1.0f,
            darkMode = DarkModeOption.FOLLOW_SYSTEM,
        )
    }
}
