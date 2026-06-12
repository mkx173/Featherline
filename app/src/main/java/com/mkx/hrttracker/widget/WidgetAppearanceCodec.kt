package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.settings.DarkModeOption

// Versioned pipe-separated string codec for the Preferences DataStore values and the
// backup payload. Empty field = null. Decode is total: any malformed input → null
// (caller falls back to Default), in-range parse → sanitized() clamps.
internal object WidgetAppearanceCodec {
    private const val VERSION = 1
    private const val FIELD_COUNT = 7

    fun encode(appearance: WidgetAppearance): String = listOf(
        VERSION.toString(),
        appearance.seedHue?.toString().orEmpty(),
        appearance.backgroundHue?.toString().orEmpty(),
        appearance.vibrancy.toString(),
        appearance.contentScale.toString(),
        appearance.backgroundAlpha.toString(),
        appearance.darkMode.name,
    ).joinToString("|")

    fun decode(value: String): WidgetAppearance? = runCatching {
        val parts = value.split("|")
        require(parts.size == FIELD_COUNT && parts[0].toInt() == VERSION)
        WidgetAppearance(
            seedHue = parts[1].takeIf { it.isNotEmpty() }?.toFloat(),
            backgroundHue = parts[2].takeIf { it.isNotEmpty() }?.toFloat(),
            vibrancy = parts[3].toFloat(),
            contentScale = parts[4].toFloat(),
            backgroundAlpha = parts[5].toFloat(),
            darkMode = DarkModeOption.entries.first { it.name == parts[6] },
        ).sanitized()
    }.getOrNull()
}
