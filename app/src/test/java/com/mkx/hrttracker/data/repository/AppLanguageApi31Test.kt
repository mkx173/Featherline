package com.mkx.hrttracker.data.repository

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.followAndroidAppLanguageSetting
import com.mkx.hrttracker.followAppLanguage
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.appLocales
import com.mkx.hrttracker.util.initAppLocales
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** A bare AppCompat activity: attaching one makes AppCompat load and write its stored list. */
class AppCompatLocalesTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(savedInstanceState)
    }
}

/**
 * Below API 33, in a process no activity has attached to (a widget refresh or an alarm woke it):
 * AppCompat has not loaded its stored list yet, so the app language is read from AppCompat's
 * file, as an activity attaching would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class AppLanguageApi31Test {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private val tagKey = stringPreferencesKey("app_language_tag")

    @Before
    fun setUp() {
        newProcess()
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() = forgetAppCompatsList()

    // AppCompat's in-memory list is static: forget it, as a new process would.
    private fun forgetAppCompatsList() {
        AppCompatDelegate::class.java.getDeclaredMethod("resetStaticRequestedAndStoredLocales")
            .apply { isAccessible = true }
            .invoke(null)
    }

    private fun newProcess() {
        forgetAppCompatsList()
        initAppLocales(context)
    }

    private fun appCompatsFile() = File(context.filesDir, APP_COMPAT_LOCALES_FILE)

    /** What AppCompat's autoStoreLocales leaves for [tags]. */
    private fun storeInAppCompatsFile(tags: String) {
        context.openFileOutput(APP_COMPAT_LOCALES_FILE, 0).use { output ->
            output.write(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><locales application_locales=\"$tags\" />"
                    .toByteArray()
            )
        }
    }

    private suspend fun awaitStoredTag(expected: String) = withTimeout(30_000) {
        while (dataStore.data.first()[tagKey] != expected) delay(10)
    }

    private class Run(val job: Job, val reposts: () -> Int)

    /** [followAppLanguage] over [settings], as the application starts it; count its reposts. */
    private suspend fun follow(settings: SettingsRepository): Run {
        settings.lastRunAppLanguageTag()
        var reposts = 0
        val scheduler = mockk<MedicationReminderScheduler> {
            coEvery { refreshDoseReminderNotifications() } answers { reposts++ }
        }
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            followAppLanguage(
                languages = settings.shownAppLanguage,
                lastLanguageTag = settings::lastRunAppLanguageTag,
                reminderNotificationManager = mockk(relaxed = true),
                medicationReminderScheduler = { scheduler },
                diagnosticsLogger = mockk(relaxed = true),
            )
        }
        return Run(job) { reposts }
    }

    @Test
    fun aChosenLanguage_reachesWidgetsAndReminders_withNoActivity() = runBlocking {
        storeInAppCompatsFile("zh-Hans")
        dataStore.edit { it[tagKey] = "zh-Hans" }

        val woken = SettingsRepository(dataStore)

        // The widgets build from these, the reminders from the tag.
        val settings = woken.getCurrentSettings()
        assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE, settings.appLanguageOption)
        assertEquals(false, settings.appLanguageFollowsSystem)
        assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE, woken.settingsState.value.appLanguageOption)
        assertEquals("zh-Hans", woken.appLanguageTag())
    }

    @Test
    fun afterAnUpgradeFromV1_4_2_aProcessAnAlarmWokePostsInTheChosenLanguage_andStoresIt() = runBlocking {
        // v1.4.2 kept the choice only in AppCompat's file.
        storeInAppCompatsFile("zh-Hant")

        val woken = SettingsRepository(dataStore)
        val wokenRun = follow(woken)

        assertEquals("zh-Hant", woken.appLanguageTag())
        assertEquals(0, wokenRun.reposts())
        awaitStoredTag("zh-Hant")
        // The activity attaches in the same process: what was posted is already in it.
        Robolectric.buildActivity(AppCompatLocalesTestActivity::class.java).setup()
        woken.refreshAppLanguageOption()
        assertEquals(0, wokenRun.reposts())
        assertEquals("zh-Hant", woken.appLanguageTag())
        wokenRun.job.cancel()
    }

    @Test
    fun followSystemChosenInThisProcess_isNotUndoneByAppCompatsFile() = runBlocking {
        // AppCompat writes (or deletes) its file later; until then it still holds the old choice.
        storeInAppCompatsFile("zh-Hans")
        val settings = SettingsRepository(dataStore)

        settings.setAppLanguageOption(null)

        val state = settings.getCurrentSettings()
        assertEquals(AppLanguageOption.ENGLISH to true, state.appLanguageOption to state.appLanguageFollowsSystem)
        assertEquals("en", settings.appLanguageTag())
        settings.refreshAppLanguageOption()
        assertEquals(true, settings.getCurrentSettings().appLanguageFollowsSystem)
    }

    @Test
    fun onceAnActivityAttached_AppCompatsInMemoryListIsTheChoice() = runBlocking {
        storeInAppCompatsFile("zh-Hans")
        // As AppCompat holds it once loaded or set, before its file is rewritten.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("yue-Hant-HK"))

        val settings = SettingsRepository(dataStore)

        assertEquals(AppLanguageOption.CANTONESE, settings.getCurrentSettings().appLanguageOption)
        assertEquals("yue-Hant-HK", settings.appLanguageTag())
    }

    @Test
    fun aRestartWithNothingChanged_repostsNothing_chosenOrFollowingTheSystem() = runBlocking {
        // A chosen language, read from AppCompat's file.
        storeInAppCompatsFile("zh-Hans")
        dataStore.edit { it[tagKey] = "zh-Hans" }
        val chosen = follow(SettingsRepository(dataStore))
        assertEquals(0, chosen.reposts())
        chosen.job.cancel()

        // Following the system.
        newProcess()
        appCompatsFile().delete()
        dataStore.edit { it[tagKey] = "en" }
        val settings = SettingsRepository(dataStore)
        val following = follow(settings)
        // The activity's own read, and the foreground's.
        settings.refreshAppLanguageOption()
        followAndroidAppLanguageSetting(settings)
        assertEquals(0, following.reposts())
        following.job.cancel()
    }

    @Test
    fun followingTheSystem_aPhoneLanguageChangedWhileClosed_repostsOnce_withNoActivity() = runBlocking {
        // The last run showed Simplified Chinese; the phone is in English now.
        dataStore.edit { it[tagKey] = "zh-Hans" }
        val woken = follow(SettingsRepository(dataStore))
        assertEquals(1, woken.reposts())
        woken.job.cancel()
        awaitStoredTag("en")

        newProcess()
        val next = SettingsRepository(dataStore)
        val nextRun = follow(next)
        next.refreshAppLanguageOption()
        assertEquals(0, nextRun.reposts())
        nextRun.job.cancel()
    }

    @Test
    fun followingTheSystem_aPhoneLanguageChangeWithNoActivity_isFollowed() = runBlocking {
        dataStore.edit { it[tagKey] = "en" }
        val settings = SettingsRepository(dataStore)
        val run = follow(settings)
        assertEquals(0, run.reposts())

        RuntimeEnvironment.setQualifiers("zh-rCN")
        followAndroidAppLanguageSetting(settings)

        assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE, settings.getCurrentSettings().appLanguageOption)
        assertEquals(1, run.reposts())
        run.job.cancel()
    }

    /** Guards the file this relies on: an AppCompat upgrade that changed it would break it. */
    @Test
    fun aPick_isWrittenByAppCompat_andReadByTheNextProcess() = runBlocking {
        Robolectric.buildActivity(AppCompatLocalesTestActivity::class.java).setup()
        val settings = SettingsRepository(dataStore)

        settings.setAppLanguageOption(AppLanguageOption.SIMPLIFIED_CHINESE)
        assertEquals("zh-Hans", settings.appLanguageTag())
        // AppCompat writes on a worker thread.
        assertEquals(true, withTimeoutOrNull(5_000) { while (!appCompatsFile().exists()) delay(1); true })

        newProcess()
        val next = SettingsRepository(dataStore).getCurrentSettings()
        assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE to false, next.appLanguageOption to next.appLanguageFollowsSystem)

        // The next run's activity attaches; Follow system is picked, and the file is deleted.
        Robolectric.buildActivity(AppCompatLocalesTestActivity::class.java).setup()
        val second = SettingsRepository(dataStore)
        second.setAppLanguageOption(null)
        assertTrue(appLocales().isEmpty)
        assertEquals("en", second.appLanguageTag())
        assertEquals(true, withTimeoutOrNull(5_000) { while (appCompatsFile().exists()) delay(1); true })

        newProcess()
        val third = SettingsRepository(dataStore).getCurrentSettings()
        assertEquals(AppLanguageOption.ENGLISH to true, third.appLanguageOption to third.appLanguageFollowsSystem)
    }

    private companion object {
        const val APP_COMPAT_LOCALES_FILE =
            "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"
    }
}
