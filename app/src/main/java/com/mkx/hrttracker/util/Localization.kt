package com.mkx.hrttracker.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.mkx.hrttracker.model.settings.AppLanguageOption
import java.util.Locale

fun Context.currentAppLocale(): Locale {
    return ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
}

// ponytail: process-wide, set first thing in Application.onCreate; pass a Context to
// appLocales() instead if a caller ever needs it before then.
@Volatile
private var localesContext: Context? = null

// Below API 33: whether this process has set AppCompat's list, so its in-memory value is the
// choice even when empty (its stored file is written later, and deleted for an empty list).
@Volatile
private var appLocalesSetInProcess = false

fun initAppLocales(context: Context) {
    localesContext = context.applicationContext
    appLocalesSetInProcess = false
}

/** Sets the app's locales (empty follows the system), on the main thread. */
fun setAppLocales(locales: LocaleListCompat) {
    appLocalesSetInProcess = true
    AppCompatDelegate.setApplicationLocales(locales)
}

// The app's chosen locales, empty when it follows the system. From API 33 the framework's, read
// through the application context: AppCompatDelegate reaches the framework only through an
// attached activity, so with none (a process an alarm woke) it would report none chosen. Below
// 33 AppCompat's: it loads its stored list only when an activity attaches, so until then, unless
// this process set it, the stored list is read here (LocaleManagerCompat reads AppCompat's file).
fun appLocales(): LocaleListCompat {
    val context = localesContext
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context != null) {
        return LocaleManagerCompat.getApplicationLocales(context)
    }
    val inMemory = AppCompatDelegate.getApplicationLocales()
    return if (!inMemory.isEmpty || appLocalesSetInProcess || context == null) {
        inMemory
    } else {
        LocaleManagerCompat.getApplicationLocales(context)
    }
}

// The per-app UI language chosen via AppCompatDelegate.setApplicationLocales.
// This is authoritative on every API level, unlike a Context's Resources
// configuration: below API 33 AppCompat applies the locale to AppCompat
// activities only, never to the application context, so reading it back from
// an @ApplicationContext would yield the stale system locale. Mapped to a supported one, as the
// screens show it: the chosen one, or with none the phone's.
fun appLanguageLocale(): Locale {
    val language = appLocales()[0]?.let(AppLanguageOption::fromLocale) ?: systemAppLanguage()
    return Locale.forLanguageTag(language.languageTag)
}

// The phone's language mapped to a supported one: the app language when following the system.
fun systemAppLanguage(): AppLanguageOption = AppLanguageOption.fromLocale(systemLocale())

// Returns a Context whose resources resolve strings in the current app language.
// Required when localizing user-facing strings off an @ApplicationContext below
// API 33, where the application context otherwise stays on the system locale.
fun Context.withAppLanguage(): Context {
    val locale = appLanguageLocale()
    if (locale == ConfigurationCompat.getLocales(resources.configuration)[0]) return this
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
}

// The phone's languages, ignoring any per-app language override. From API 33
// Resources.getSystem() carries the per-app language first, so they are read from the framework.
// Resources.getSystem() is null in pure-JVM unit tests: empty there.
fun systemLocales(): LocaleListCompat {
    val context = localesContext
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context != null) {
        LocaleManagerCompat.getSystemLocales(context)
    } else {
        Resources.getSystem()?.let { ConfigurationCompat.getLocales(it.configuration) }
            ?: LocaleListCompat.getEmptyLocaleList()
    }
}

// The device-level locale, for settings that follow the OS region (e.g. first-day-of-week)
// rather than the chosen UI language.
fun systemLocale(): Locale = systemLocales()[0] ?: Locale.getDefault()

// Chinese-family UI languages: Mandarin ("zh") and Cantonese ("yue"). Gates the
// Chinese-specific date/time patterns.
fun Locale.isChineseLanguage(): Boolean {
    return language == Locale.CHINESE.language || language == "yue"
}

@Composable
fun rememberAppLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    }
}

/**
 * The composition's context with its resources in [languageTag], for an activity's content to
 * provide as LocalContext and, from its resources, LocalConfiguration: a language change then
 * re-localizes the whole UI, date/time formatters that read LocalConfiguration included, without
 * recreating the activity (which would flash a blank screen). It wraps the activity (rather than
 * returning the config context) so the context still unwraps to the Activity, as hiltViewModel()
 * needs; LocalActivity, which walks the context chain, has to be provided alongside.
 */
@Composable
fun rememberAppLanguageContext(languageTag: String): Context {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    return remember(baseContext, baseConfiguration, languageTag) {
        val configuration = Configuration(baseConfiguration)
            .apply { setLocale(Locale.forLanguageTag(languageTag)) }
        val localizedResources = baseContext.createConfigurationContext(configuration).resources
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = localizedResources
        }
    }
}
