package com.mkx.hrttracker.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mkx.hrttracker.data.repository.LegacyWidgetAppearance
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

// Behavioral coverage of WidgetAppearanceRepository.migrateFromLegacySettingsIfNeeded
// over real Preferences DataStores: the one-time seed must never clobber an existing
// default (e.g. a restored backup), must clear the legacy keys, and must still report
// true when the clear fails so the caller repaints and the next start retries.
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAppearanceRepositoryMigrationTest {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val widgetContentScaleKey = floatPreferencesKey("widget_content_scale")
    private val widgetBackgroundAlphaKey = floatPreferencesKey("widget_background_alpha")
    private val widgetDarkModeKey = stringPreferencesKey("widget_dark_mode")

    private lateinit var settingsDataStore: DataStore<Preferences>
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var store: WidgetAppearanceStore
    private lateinit var repository: WidgetAppearanceRepository

    @Before
    fun setUp() {
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
        val context: Context = mockk()

        settingsDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("migration_settings_test.preferences_pb") },
        )
        settingsRepository = SettingsRepository(context, settingsDataStore)

        store = WidgetAppearanceStore(widgetContext)
        repository = WidgetAppearanceRepository(store, settingsRepository, mockk(relaxed = true))

        // The widget DataStore singleton survives across tests; start each empty.
        runBlocking { widgetDataStore().edit { it.clear() } }
    }

    @After
    fun tearDown() {
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun `legacy keys seed the empty store default, get cleared, and report true`() =
        runTest(testDispatcher) {
            settingsDataStore.edit {
                it[widgetContentScaleKey] = 1.3f
                it[widgetBackgroundAlphaKey] = 0.8f
                it[widgetDarkModeKey] = "DARK"
            }

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())

            assertEquals(
                legacyWidgetAppearance(1.3f, 0.8f, DarkModeOption.DARK),
                store.observeEntry(null).first(),
            )
            assertNull(settingsRepository.readLegacyWidgetAppearance())
        }

    @Test
    fun `existing store default is not clobbered but legacy keys are still cleared`() =
        runTest(testDispatcher) {
            // E.g. a backup restore already wrote a default before migration ran.
            val restored = WidgetAppearance.Default.copy(seedHue = 200f)
            store.setEntry(null, restored)
            settingsDataStore.edit { it[widgetDarkModeKey] = "DARK" }

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())

            assertEquals(restored, store.observeEntry(null).first())
            assertNull(settingsRepository.readLegacyWidgetAppearance())
        }

    @Test
    fun `no legacy keys reports false and leaves the store untouched`() =
        runTest(testDispatcher) {
            assertFalse(repository.migrateFromLegacySettingsIfNeeded())

            assertNull(store.observeEntry(null).first())
        }

    @Test
    fun `failed legacy clear still reports true, keys survive, next start retries`() =
        runTest(testDispatcher) {
            settingsDataStore.edit { it[widgetDarkModeKey] = "DARK" }
            val failingSettings = spyk(settingsRepository)
            coEvery {
                failingSettings.clearLegacyWidgetAppearanceKeys()
            } throws IOException("injected clear failure")
            val firstStart =
                WidgetAppearanceRepository(store, failingSettings, mockk(relaxed = true))

            // The seed landed, so the caller must still repaint (true) even though
            // the clear failed; the surviving keys make the next start retry.
            assertTrue(firstStart.migrateFromLegacySettingsIfNeeded())
            val seeded = store.observeEntry(null).first()
            assertEquals(legacyWidgetAppearance(1.0f, 1.0f, DarkModeOption.DARK), seeded)
            assertEquals(
                LegacyWidgetAppearance(1.0f, 1.0f, DarkModeOption.DARK),
                settingsRepository.readLegacyWidgetAppearance(),
            )

            // Second start: seed no-ops, clear succeeds, migration goes quiet.
            assertTrue(repository.migrateFromLegacySettingsIfNeeded())
            assertEquals(seeded, store.observeEntry(null).first())
            assertNull(settingsRepository.readLegacyWidgetAppearance())
            assertFalse(repository.migrateFromLegacySettingsIfNeeded())
        }

    // The store's DataStore is created by a process-wide singleton delegate keyed to
    // the first Context's filesDir, so every test must share one stable directory
    // (a per-test TemporaryFolder would leave the cached singleton pointing at a
    // deleted path) and reset contents in setUp instead.
    private fun widgetDataStore(): DataStore<Preferences> {
        val getter = Class.forName("com.mkx.hrttracker.widget.WidgetAppearanceStoreKt")
            .getDeclaredMethod("getWidgetAppearanceDataStore", Context::class.java)
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return getter.invoke(null, widgetContext) as DataStore<Preferences>
    }

    private companion object {
        val widgetFilesDir = Files.createTempDirectory("widget_appearance_test").toFile()
        val widgetContext: Context = mockk<Context>().also {
            every { it.applicationContext } returns it
            every { it.filesDir } returns widgetFilesDir
        }
    }
}
