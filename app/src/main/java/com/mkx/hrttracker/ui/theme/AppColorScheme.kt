package com.mkx.hrttracker.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color

/**
 * Baked default seed (issue #21). Used when adaptive color is off, or on API 26-30
 * where the framework has no wallpaper-derived system palette to read.
 * Value = the emulator's default system_accent1_500; matches the widget's seed convention.
 */
val DefaultSeedColor = Color(0xFF6476A5)

/**
 * Single source of the Material 3 seed for both the app and the widget.
 * On API >= 31 with adaptive color on, mirror the launcher's Material You by seeding
 * from system_accent1_500. Otherwise fall back to [DefaultSeedColor].
 *
 * @param sdkInt injectable for testing; defaults to the runtime SDK level.
 */
fun resolveSeedColor(
    context: Context,
    adaptiveEnabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Color {
    val canUseSystemSeed = adaptiveEnabled && sdkInt >= Build.VERSION_CODES.S
    return if (canUseSystemSeed) {
        systemAccentSeed(context)
    } else {
        DefaultSeedColor
    }
}

@SuppressLint("InlinedApi")
private fun systemAccentSeed(context: Context): Color {
    return Color(context.getColor(android.R.color.system_accent1_500))
}
