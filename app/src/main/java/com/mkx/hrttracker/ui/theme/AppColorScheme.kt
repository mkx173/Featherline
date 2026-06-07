package com.mkx.hrttracker.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Baked default seed (issue #21). Used when adaptive color is off, or on API 26-30
 * where the framework has no wallpaper-derived system palette to read.
 * Value = the emulator's default system_accent1_500; matches the widget's seed convention.
 */
val DefaultSeedColor = Color(0xFF6476A5)

/**
 * The live system light & dark schemes, read straight from the framework's dynamic-color
 * resources — the exact same source AndroidX's dynamicLight/DarkColorScheme draw from. This
 * mirrors the launcher's Material You faithfully (all three accent ramps + neutrals), rather
 * than reconstructing the whole palette from a single seed.
 *
 * API 31+ only; callers must gate on [Build.VERSION_CODES.S] and fall back to a
 * [DefaultSeedColor]-seeded MaterialKolor scheme below that.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun systemColorSchemes(context: Context): Pair<ColorScheme, ColorScheme> =
    dynamicLightColorScheme(context) to dynamicDarkColorScheme(context)
