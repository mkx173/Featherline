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
    // HCT hue 0..360; null = follow the seed scheme's secondaryContainer hue.
    val backgroundHue: Float?,
    // 0 = neutral black/white, DEFAULT_VIBRANCY = today's tint (regression anchor),
    // 1 = max tint. Drives chroma, tone depth, and the text-tone lift.
    val vibrancy: Float,
    val contentScale: Float,
    val backgroundAlpha: Float, // backgrounds only; controls stay opaque (existing design)
    val darkMode: DarkModeOption,
) {
    fun sanitized(): WidgetAppearance = copy(
        seedHue = seedHue?.takeIf { it.isFinite() }?.mod(360f),
        backgroundHue = backgroundHue?.takeIf { it.isFinite() }?.mod(360f),
        vibrancy = if (vibrancy.isFinite()) vibrancy.coerceIn(0f, 1f) else DEFAULT_VIBRANCY,
        contentScale = if (contentScale.isFinite()) contentScale.coerceIn(0.5f, 1.5f) else 1f,
        backgroundAlpha = if (backgroundAlpha.isFinite()) backgroundAlpha.coerceIn(0.5f, 1f) else 1f,
    )

    companion object {
        const val DEFAULT_VIBRANCY = 0.4f
        val Default = WidgetAppearance(
            seedHue = null,
            backgroundHue = null,
            vibrancy = DEFAULT_VIBRANCY,
            contentScale = 1.0f,
            backgroundAlpha = 1.0f,
            darkMode = DarkModeOption.FOLLOW_SYSTEM,
        )
    }
}
