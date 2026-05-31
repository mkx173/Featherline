package com.mkx.hrttracker.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

fun Context.currentAppLocale(): Locale {
    return ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
}

// The per-app UI language chosen via AppCompatDelegate.setApplicationLocales.
// This is authoritative on every API level, unlike a Context's Resources
// configuration: below API 33 AppCompat applies the locale to AppCompat
// activities only, never to the application context, so reading it back from
// an @ApplicationContext would yield the stale system locale.
fun appLanguageLocale(): Locale {
    return AppCompatDelegate.getApplicationLocales()[0] ?: systemLocale()
}

// Returns a Context whose resources resolve strings in the current app language.
// Required when localizing user-facing strings off an @ApplicationContext below
// API 33, where the application context otherwise stays on the system locale.
fun Context.withAppLanguage(): Context {
    val locale = appLanguageLocale()
    if (locale == ConfigurationCompat.getLocales(resources.configuration)[0]) return this
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
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
