package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption

// Versioned pipe-separated string codec for the Preferences DataStore values and the
// backup payload. Empty field = null. Decode is total: any malformed input → null
// (caller falls back to Default), in-range parse → sanitized() clamps.
//
// v4 layout (current): 4|seedHue|saturation|balance|scale|alpha|darkMode, where `balance` is
// the Round-6 BIDIRECTIONAL axis (0.5 = anchor, 0 = lightest, 1 = deepest). Encode emits v4.
//
// v3-compat: v3 stored a one-directional balance (0 = anchor, 1 = deepest). Round 6 moved the
// anchor to 0.5, so old depth maps onto the deepen half via `0.5 + 0.5·balance` (0→0.5, 1→1);
// the dev devices that hold v3 strings keep their look.
//
// v2-compat: Round-3 entries were 2|seedHue|saturation|vibrancy|scale|alpha|darkMode,
// where vibrancy did both chroma boost and tone depth. Round 4 split those: saturation
// now owns chroma and the depth axis became `balance`. Map the old vibrancy onto the depth
// axis via `((v-0.4)/0.6).coerceIn(0,1)`, then onto the bidirectional balance via the v3
// remap above, and carry saturation as-is — a dev-grade approximation (the feature is
// unreleased; only dev devices hold v2 strings). Note the asymmetry it accepts: v2 vibrancy
// BELOW 0.4 used to reduce chroma, but that job moved to saturation, so such entries render
// MORE saturated after the remap (their stored saturation is carried unreduced).
//
// v1-compat: pre-Round-3 entries were 1|seedHue|backgroundHue|vibrancy|scale|alpha|darkMode.
// The slot-2 backgroundHue is IGNORED, saturation defaults to DEFAULT_SATURATION, and the
// slot-3 vibrancy maps onto depth then the bidirectional balance via the same chain (v1's 0.4
// anchor → depth 0 → balance 0.5).
internal object WidgetAppearanceCodec {
    // WARNING: this wire version also travels inside backups (settings.widgetAppearance),
    // and BackupRestoreService.toValidatedSettings hard-fails the ENTIRE restore when
    // decode returns null — which it does for unknown future versions. Bumping this
    // therefore makes new backups unrestorable on older apps despite their valid legacy
    // mirror fields, so a codec version bump MUST ship with a
    // CURRENT_BACKUP_SNAPSHOT_VERSION bump (see BackupSnapshot.kt's bump policy).
    private const val VERSION = 4
    private const val FIELD_COUNT = 7

    // Round-4 re-anchor: the old vibrancy axis (0.4 anchor, 1 max) collapses onto the
    // one-directional balance axis (0 anchor, 1 deepest) by the v=1 ramp the pre-split code ran.
    private fun vibrancyToBalance(vibrancy: Float): Float =
        ((vibrancy - 0.4f) / 0.6f).coerceIn(0f, 1f)

    // Round-6 re-anchor: pre-v4 `balance` was one-directional (0 = anchor, 1 = deepest); the
    // bidirectional axis puts the anchor at 0.5, so old depth maps onto the deepen half.
    private fun deepenToBidirectional(balance: Float): Float = 0.5f + 0.5f * balance

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
            4 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = parts[2].toFloat(),
                balance = parts[3].toFloat(),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            // v3: slot 3 was the one-directional balance (0 = anchor). Remap onto the
            // bidirectional axis (anchor 0.5).
            3 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = parts[2].toFloat(),
                balance = deepenToBidirectional(parts[3].toFloat()),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            // v2: slot 3 was vibrancy (chroma + depth). Carry saturation, map vibrancy onto
            // the one-directional depth, then onto the bidirectional balance.
            2 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = parts[2].toFloat(),
                balance = deepenToBidirectional(vibrancyToBalance(parts[3].toFloat())),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            // v1: slot 2 was backgroundHue (dropped in Round 3) — ignored; saturation anchors
            // at the default; slot-3 vibrancy maps onto depth then the bidirectional balance.
            1 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                balance = deepenToBidirectional(vibrancyToBalance(parts[3].toFloat())),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            else -> return@runCatching null
        }.sanitized()
    }.getOrNull()
}
