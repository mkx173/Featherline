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
    // 0..1; OWNS chroma outright (Round 4). 0 = neutral black/white,
    // DEFAULT_SATURATION (0.5) = the scheme's standard chroma (regression anchor,
    // bit-identical to today's output), 1 = the old maximum (scChroma + CHROMA_BOOST).
    val saturation: Float,
    // 0..1; "light balance" — pure tone depth. Bidirectional (Round 6): 0.5 = today's
    // tones (regression anchor); 0.5→1 DEEPENS (light darker / dark brighter, the
    // Round-2 card/text/outline ramps run on this half); 0.5→0 LIGHTENS (light brighter
    // / dark darker, tones-only — lifts/outline stay at the anchor). Chroma is independent.
    val balance: Float,
    val contentScale: Float,
    val backgroundAlpha: Float, // backgrounds only; controls stay opaque (existing design)
    val darkMode: DarkModeOption,
) {
    fun sanitized(): WidgetAppearance = copy(
        seedHue = seedHue?.takeIf { it.isFinite() }?.mod(360f),
        saturation = if (saturation.isFinite()) saturation.coerceIn(0f, 1f) else DEFAULT_SATURATION,
        balance = if (balance.isFinite()) balance.coerceIn(0f, 1f) else DEFAULT_BALANCE,
        contentScale = if (contentScale.isFinite()) contentScale.coerceIn(0.5f, 1.5f) else 1f,
        backgroundAlpha = if (backgroundAlpha.isFinite()) backgroundAlpha.coerceIn(0.5f, 1f) else 1f,
    )

    companion object {
        const val DEFAULT_SATURATION = 0.5f
        // Round 6: the anchor (today's tones) is the slider's MIDPOINT so balance can move
        // both ways. The deepen half (0.5→1) reproduces the old 0→1 bit-exact.
        const val DEFAULT_BALANCE = 0.5f
        val Default = WidgetAppearance(
            seedHue = null,
            saturation = DEFAULT_SATURATION,
            balance = DEFAULT_BALANCE,
            contentScale = 1.0f,
            backgroundAlpha = 1.0f,
            darkMode = DarkModeOption.FOLLOW_SYSTEM,
        )
    }
}
