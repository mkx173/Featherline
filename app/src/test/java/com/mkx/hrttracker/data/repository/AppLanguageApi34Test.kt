package com.mkx.hrttracker.data.repository

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.followAndroidAppLanguageSetting
import com.mkx.hrttracker.followAppLanguage
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.initAppLocales
import com.mkx.hrttracker.util.withAppLanguage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * From API 33, in a process no activity has attached to (an alarm woke it, or the activity is
 * gone): the app language is the framework's, read without AppCompat, which would report none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class AppLanguageApi34Test {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        initAppLocales(context)
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
    }

    private fun chooseInAndroidSetting(tags: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
    }

    @Test
    fun aChosenLanguage_isKnownWithNoActivity() = runBlocking {
        chooseInAndroidSetting("zh-Hant")
        val settings = SettingsRepository(dataStore)

        val state = settings.getCurrentSettings()
        assertEquals(AppLanguageOption.TRADITIONAL_CHINESE, state.appLanguageOption)
        assertEquals(false, state.appLanguageFollowsSystem)
        assertEquals("zh-Hant", settings.appLanguageTag())
    }

    @Test
    fun aConfigurationChangeWithNoActivity_keepsTheChosenLanguage() = runBlocking {
        chooseInAndroidSetting("yue-Hant-HK")
        val settings = SettingsRepository(dataStore)

        followAndroidAppLanguageSetting(settings)

        val state = settings.getCurrentSettings()
        assertEquals(AppLanguageOption.CANTONESE, state.appLanguageOption)
        assertEquals(false, state.appLanguageFollowsSystem)
    }

    @Test
    fun aProcessAnAlarmWokeAfterAChange_storesIt_soOnlyThatStartReposts() = runBlocking {
        // The last run showed English; the language was changed in Android's setting since.
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("app_language_tag")] = "en"
            preferences[booleanPreferencesKey("app_language_follows_system")] = false
        }
        chooseInAndroidSetting("zh-Hant")

        val woken = SettingsRepository(dataStore)
        assertEquals(1, repostsAtStart(woken))
        withTimeout(30_000) {
            while (dataStore.data.first()[stringPreferencesKey("app_language_tag")] != "zh-Hant") delay(10)
        }
        assertEquals(false, dataStore.data.first()[booleanPreferencesKey("app_language_follows_system")])

        assertEquals(0, repostsAtStart(SettingsRepository(dataStore)))
    }

    @Test
    @Config(qualifiers = "ja")
    fun followingASystemLanguageTheAppCantShow_textOutsideAnActivityIsInEnglish_asTheScreens() = runBlocking {
        chooseInAndroidSetting("")
        val settings = SettingsRepository(dataStore)
        settings.getCurrentSettings()

        assertEquals("en", settings.appLanguageTag())
        assertEquals(english(), context.withAppLanguage().getString(R.string.reminder_notification_single_title))
    }

    @Test
    fun aChosenLanguageTheAppCantShow_textOutsideAnActivityIsInEnglish_asTheScreens() = runBlocking {
        // Only from an older state: Android's setting now offers only the app's languages.
        chooseInAndroidSetting("ja")
        val settings = SettingsRepository(dataStore)
        settings.getCurrentSettings()

        assertEquals("en", settings.appLanguageTag())
        assertEquals(english(), context.withAppLanguage().getString(R.string.reminder_notification_single_title))
    }

    private fun english(): String {
        val configuration = android.content.res.Configuration(context.resources.configuration)
            .apply { setLocale(Locale.ENGLISH) }
        return context.createConfigurationContext(configuration).getString(R.string.reminder_notification_single_title)
    }

    /** The reminders [followAppLanguage] reposts as a run of [settings] starts. */
    private suspend fun repostsAtStart(settings: SettingsRepository): Int {
        settings.lastRunAppLanguageTag()
        var reposts = 0
        val scheduler = mockk<MedicationReminderScheduler> {
            coEvery { refreshDoseReminderNotifications() } answers { reposts++ }
        }
        val run = CoroutineScope(Dispatchers.Unconfined).launch {
            followAppLanguage(
                languages = settings.knownAppLanguage,
                lastLanguageTag = settings::lastRunAppLanguageTag,
                reminderNotificationManager = mockk(relaxed = true),
                medicationReminderScheduler = { scheduler },
                diagnosticsLogger = mockk(relaxed = true),
            )
        }
        run.cancel()
        return reposts
    }
}
