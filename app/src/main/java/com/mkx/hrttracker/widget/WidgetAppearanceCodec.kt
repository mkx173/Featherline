package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption

// Versioned pipe-separated string codec for the Preferences DataStore values and the
// backup payload. Empty field = null. Decode is total: any malformed input → null
// (caller falls back to Default), in-range parse → sanitized() clamps.
//
// v1-compat contract: existing DataStore entries and old backups were written as
//   1|seedHue|backgroundHue|vibrancy|scale|alpha|darkMode
// before Round 3 dropped backgroundHue for a user-facing saturation control. Decode
// MUST keep accepting v1 so those stores/backups migrate transparently: the slot-2
// backgroundHue value is IGNORED and saturation defaults to DEFAULT_SATURATION (the
// anchor), reproducing today's output. Encode always emits v2.
internal object WidgetAppearanceCodec {
    private const val VERSION = 2
    private const val FIELD_COUNT = 7

    fun encode(appearance: WidgetAppearance): String = listOf(
        VERSION.toString(),
        appearance.seedHue?.toString().orEmpty(),
        appearance.saturation.toString(),
        appearance.vibrancy.toString(),
        appearance.contentScale.toString(),
        appearance.backgroundAlpha.toString(),
        appearance.darkMode.name,
    ).joinToString("|")

    fun decode(value: String): WidgetAppearance? = runCatching {
        val parts = value.split("|")
        require(parts.size == FIELD_COUNT)
        when (parts[0].toInt()) {
            2 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = parts[2].toFloat(),
                vibrancy = parts[3].toFloat(),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            // v1: slot 2 was backgroundHue — dropped in Round 3. Ignore it and
            // anchor saturation at the default so v1 stores decode to today's output.
            1 -> WidgetAppearance(
                seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                vibrancy = parts[3].toFloat(),
                contentScale = parts[4].toFloat(),
                backgroundAlpha = parts[5].toFloat(),
                darkMode = DarkModeOption.entries.first { it.name == parts[6] },
            )
            else -> return@runCatching null
        }.sanitized()
    }.getOrNull()
}
