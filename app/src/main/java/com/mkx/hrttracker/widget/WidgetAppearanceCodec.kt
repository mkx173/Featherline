package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption

// Versioned pipe-separated string codec for the Preferences DataStore values and the
// backup payload. Empty field = null. Decode is total: any malformed input → null
// (caller falls back to Default), in-range parse → sanitized() clamps.
//
// v3 layout (current): 3|seedHue|saturation|balance|scale|alpha|darkMode. Encode always
// emits v3.
//
// v2-compat: Round-3 entries were 2|seedHue|saturation|vibrancy|scale|alpha|darkMode,
// where vibrancy did both chroma boost and tone depth. Round 4 split those: saturation
// now owns chroma and the depth axis became `balance`, re-anchored at 0. Map the old
// vibrancy onto balance via the v=1 ramp `((v-0.4)/0.6).coerceIn(0,1)` and carry
// saturation as-is — a dev-grade approximation (the feature is unreleased; only dev
// devices hold v2 strings). Note the asymmetry it accepts: v2 vibrancy BELOW 0.4
// used to reduce chroma, but that job moved to saturation, so such entries render
// MORE saturated after the remap (their stored saturation is carried unreduced).
//
// v1-compat: pre-Round-3 entries were 1|seedHue|backgroundHue|vibrancy|scale|alpha|darkMode.
// The slot-2 backgroundHue is IGNORED, saturation defaults to DEFAULT_SATURATION, and the
// slot-3 vibrancy maps onto balance via the same (v-0.4)/0.6 formula (v1's 0.4 anchor → 0).
internal object WidgetAppearanceCodec {
    private const val VERSION = 3
    private const val FIELD_COUNT = 7

    // Round-4 re-anchor: the old vibrancy axis (0.4 anchor, 1 max) collapses onto the
    // balance axis (0 anchor, 1 deepest) by the v=1 ramp the pre-split code already ran.
    private fun vibrancyToBalance(vibrancy: Float): Float =
        ((vibrancy - 0.4f) / 0.6f).coerceIn(0f, 1f)

    fun encode(appearance: WidgetAppearance): String = listOf(
        VERSION.toString(),
        appearance.seedHue?.toString().orEmpty(),
        appearance.saturation.toString(),
        appearance.balance.toString(),
        appearance.contentScale.toString(),
        appearance.backgroundAlpha.toString(),
        appearance.darkMode.name,
    ).joinToString("|")

    fun decode(value: String): WidgetAppearance? = runCatching {
        val parts = value.split("|")
        require(parts.size == FIELD_COUNT)
        when (parts[0].toInt()) {
            3 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = parts[2].toFloat(),
                balance = parts[3].toFloat(),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            // v2: slot 3 was vibrancy (chroma + depth). Carry saturation, map vibrancy
            // onto the re-anchored balance axis.
            2 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = parts[2].toFloat(),
                balance = vibrancyToBalance(parts[3].toFloat()),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            // v1: slot 2 was backgroundHue (dropped in Round 3) — ignored; saturation
            // anchors at the default; slot-3 vibrancy maps onto balance (0.4 → 0).
            1 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                balance = vibrancyToBalance(parts[3].toFloat()),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            else -> return@runCatching null
        }.sanitized()
    }.getOrNull()
}
