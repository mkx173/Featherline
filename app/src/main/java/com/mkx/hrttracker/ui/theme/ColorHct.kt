package com.mkx.hrttracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

internal fun Color.atTone(tone: Double): Color {
    val hct = Hct.fromInt(toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, tone).toInt())
}

fun Color.desaturateHct(factor: Double = 0.7): Color {
    val source = Hct.fromInt(toArgb())
    val safeFactor = factor.coerceIn(0.0, 1.0)

    return Color(
        Hct.from(
            source.hue,
            source.chroma * safeFactor,
            source.tone,
        ).toInt()
    )
}

private const val AMOLED_TOP_TONE = 10.0   // lower = darker overall

// surface / background / onSurface are already handled by isAmoled;
// this only compresses the elevated container ramp toward black.
internal fun ColorScheme.amoledContainers(): ColorScheme = copy(
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = surfaceContainerLow.atTone(AMOLED_TOP_TONE * 0.30),
    surfaceContainer = surfaceContainer.atTone(AMOLED_TOP_TONE * 0.45),
    surfaceContainerHigh = surfaceContainerHigh.atTone(AMOLED_TOP_TONE * 0.70),
    surfaceContainerHighest = surfaceContainerHighest.atTone(AMOLED_TOP_TONE),
    surfaceBright = surfaceBright.atTone(AMOLED_TOP_TONE * 1.2),
)

// Full AMOLED transform for a dark scheme that isn't already amoled (e.g. the framework
// dynamic scheme). The first copy mirrors MaterialKolor's isAmoled exactly — it blackens
// background/surface and whitens their on-colors, and nothing else — then amoledContainers()
// compresses the elevated ramp, matching the in-app modifyColorScheme path one-for-one.
internal fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
).amoledContainers()
