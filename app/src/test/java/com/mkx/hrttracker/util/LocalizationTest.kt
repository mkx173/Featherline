package com.mkx.hrttracker.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

// Pinned below API 33 on purpose: this is the regime withAppLanguage() exists for.
// Below TIRAMISU, AppCompat applies the chosen app language to activities only, so an
// @ApplicationContext keeps the system locale; withAppLanguage() must override it from
// AppCompatDelegate. Tests pinned at sdk 34 never exercise this branch, leaving the
// feature's whole reason for existing uncovered.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LocalizationTest {
    private val appContext: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @After
    fun clearAppLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun withAppLanguage_belowApi33_appliesChosenAppLanguageOverStaleSystemLocale() {
        // Simulate the stale application context: its configuration stays on the system
        // locale (US) even though the user has chosen Simplified Chinese as the app language.
        val systemLocaleContext = appContext.createConfigurationContext(
            Configuration(appContext.resources.configuration).apply { setLocale(Locale.US) },
        )
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(SIMPLIFIED_CHINESE))

        val localized = systemLocaleContext.withAppLanguage()

        // The locale must come from AppCompatDelegate, not the stale context configuration,
        // so numbers and strings resolve in the chosen app language below API 33.
        assertEquals(SIMPLIFIED_CHINESE, localized.currentAppLocale())
    }

    @Test
    fun withAppLanguage_returnsSameContextWhenConfigurationAlreadyMatchesAppLanguage() {
        // When the context configuration already carries the app language there is nothing
        // to override, so the same context is returned rather than a needless wrapper.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(SIMPLIFIED_CHINESE))
        val chineseContext = appContext.createConfigurationContext(
            Configuration(appContext.resources.configuration).apply { setLocale(SIMPLIFIED_CHINESE) },
        )

        assertSame(chineseContext, chineseContext.withAppLanguage())
    }

    // As the screens show it: a language the app can't show (only from an older state) is English.
    @Test
    fun withAppLanguage_mapsAChosenLanguageTheAppCantShowToEnglish() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale.GERMANY))

        assertEquals(Locale.ENGLISH, appContext.withAppLanguage().currentAppLocale())
    }

    private companion object {
        val SIMPLIFIED_CHINESE: Locale = Locale.forLanguageTag("zh-Hans")
    }
}
