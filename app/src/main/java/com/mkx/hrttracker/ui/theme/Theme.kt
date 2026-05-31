package com.mkx.hrttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

@Composable
fun HrtTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // When true, derive the seed from the system Material You palette (API 31+);
    // otherwise use the baked DefaultSeedColor. MaterialKolor regenerates the scheme
    // either way, so there is a single tonal path across all API levels.
    dynamicColor: Boolean = true,
    // AMOLED pure-black surfaces; only meaningful in dark mode.
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val seed = resolveSeedColor(context, adaptiveEnabled = dynamicColor)
    val colorScheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        isAmoled = darkTheme && amoled,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
