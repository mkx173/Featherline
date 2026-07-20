package com.mkx.hrttracker.model.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageOptionTest {
    @Test
    fun fromLocale_mapsTraditionalChinese() {
        assertEquals(
            AppLanguageOption.TRADITIONAL_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-Hant-HK")),
        )
        assertEquals(
            AppLanguageOption.TRADITIONAL_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-HK")),
        )
        assertEquals(
            AppLanguageOption.TRADITIONAL_CHINESE,
            AppLanguageOption.fromLocale(Locale.TRADITIONAL_CHINESE),
        )
    }

    @Test
    fun fromLocale_mapsOtherChineseScripts() {
        assertEquals(
            AppLanguageOption.SIMPLIFIED_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-Hans")),
        )
        assertEquals(
            AppLanguageOption.SIMPLIFIED_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-Hans-HK")),
        )
        assertEquals(
            AppLanguageOption.SIMPLIFIED_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-Hans-MO")),
        )
        assertEquals(
            AppLanguageOption.SIMPLIFIED_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-Hans-TW")),
        )
        assertEquals(
            AppLanguageOption.TRADITIONAL_CHINESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("zh-Hant")),
        )
    }

    @Test
    fun fromLocale_mapsCantonese() {
        assertEquals(
            AppLanguageOption.CANTONESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("yue-Hant-HK")),
        )
        assertEquals(
            AppLanguageOption.CANTONESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("yue-Hant-MO")),
        )
        assertEquals(
            AppLanguageOption.CANTONESE,
            AppLanguageOption.fromLocale(Locale.forLanguageTag("yue")),
        )
    }

    @Test
    fun fromLocale_defaultsToEnglish() {
        assertEquals(
            AppLanguageOption.ENGLISH,
            AppLanguageOption.fromLocale(Locale.FRENCH),
        )
    }
}
