package com.mkx.hrttracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val (light, dark) = systemColorSchemes(context)
        val base = if (darkTheme) dark else light
        if (darkTheme && amoled) base.amoled() else base
    } else {
        rememberDynamicColorScheme(
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
