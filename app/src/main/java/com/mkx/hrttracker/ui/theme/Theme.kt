package com.mkx.hrttracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun HrtTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // When true on API 31+, mirror AndroidX and read the live system Material You palette.
    // Otherwise (adaptive off, or API 26-30 with no system palette) regenerate the scheme
    // from DefaultSeedColor via MaterialKolor.
    dynamicColor: Boolean = true,
    // AMOLED pure-black surfaces; only meaningful in dark mode.
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // API 31+ with adaptive on: read the live system palette directly (mirrors AndroidX).
        // Unremembered on purpose so a wallpaper/accent change flows through on recomposition;
        // only the active mode is built, not both.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && amoled) base.amoled() else base
        }

        // Adaptive off, or API 26-30 with no system palette: regenerate from DefaultSeedColor.
        else -> rememberDynamicColorScheme(
            seedColor = DefaultSeedColor,
            isDark = darkTheme,
            isAmoled = darkTheme && amoled,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            modifyColorScheme = { scheme ->
                val base = scheme.dimErrorContainer()
                if (darkTheme && amoled) base.amoledContainers() else base
            },
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
