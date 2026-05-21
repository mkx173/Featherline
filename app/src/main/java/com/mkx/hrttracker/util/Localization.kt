package com.mkx.hrttracker.util

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

fun Context.currentAppLocale(): Locale {
    return ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
}

// Returns the device-level locale, ignoring any per-app language override
// (AppCompatDelegate.setApplicationLocales). Used by settings that follow the
// OS region (e.g. first-day-of-week) rather than the chosen UI language.
// Resources.getSystem() is null in pure-JVM unit tests, so we fall back to
// Locale.getDefault() there.
fun systemLocale(): Locale {
    val systemResources: Resources? = Resources.getSystem()
    val configurationLocale = systemResources
        ?.let { ConfigurationCompat.getLocales(it.configuration)[0] }
    return configurationLocale ?: Locale.getDefault()
}

@Composable
fun rememberAppLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    }
}
