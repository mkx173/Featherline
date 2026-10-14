package com.mkx.hrttracker.widget

import android.content.Context
import android.content.SharedPreferences
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
        baselineValues.clear()
        baselineCommitSucceeds = true
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

    @Test
    fun `legacy baseline ratio follows the placed widget types`() {
        // A stale medium height (that widget was removed) must not override the placed large.
        assertEquals(204f / 276f, legacyBaselineScaleRatio(138f, 204f, false, true)!!, 1e-6f)
        assertEquals(138f / 276f, legacyBaselineScaleRatio(138f, 204f, true, false)!!, 1e-6f)
        // Both placed but different: the smaller factor, so neither grows past its old size.
        assertEquals(138f / 276f, legacyBaselineScaleRatio(204f, 138f, true, true)!!, 1e-6f)
        // None placed: still fold the old-meaning scale with this device's captured height.
        assertEquals(138f / 276f, legacyBaselineScaleRatio(204f, 138f, false, false)!!, 1e-6f)
        // A placed type with no usable height falls back to the captured one.
        assertEquals(204f / 276f, legacyBaselineScaleRatio(0f, 204f, true, false)!!, 1e-6f)
        // The old ratio floor.
        assertEquals(0.35f, legacyBaselineScaleRatio(60f, 0f, true, false)!!, 1e-6f)
        // Never captured, or outside the range the old code would persist.
        assertNull(legacyBaselineScaleRatio(0f, 0f, true, true))
        assertNull(legacyBaselineScaleRatio(20f, 500f, true, true))
    }

    @Test
    fun `baseline folds into the default scale once and is cleared`() =
        runTest(testDispatcher) {
            // One UI 2-row cell: v1.3.x drew the medium widget at 100% x 204/276.
            baselineValues["medium_height_dp_v3"] = 204f

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())

            val folded = store.observeEntry(null).first()
            assertEquals(WidgetAppearance.Default.copy(contentScale = 204f / 276f), folded)
            assertTrue(baselineValues.isEmpty())

            // Next start (fresh flags): nothing left to fold, scale not shrunk again.
            val nextStart = WidgetAppearanceRepository(store, settingsRepository, mockk(relaxed = true))
            assertFalse(nextStart.migrateFromLegacySettingsIfNeeded())
            assertEquals(folded, store.observeEntry(null).first())
        }

    @Test
    fun `baseline rescales an existing default and keeps its other fields`() =
        runTest(testDispatcher) {
            store.setEntry(null, WidgetAppearance.Default.copy(contentScale = 1.5f, seedHue = 200f))
            baselineValues["medium_height_dp_v3"] = 138f

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())
            assertEquals(
                WidgetAppearance.Default.copy(contentScale = 0.75f, seedHue = 200f),
                store.observeEntry(null).first(),
            )
        }

    @Test
    fun `baseline below the slider range lands on its 50 percent floor`() =
        runTest(testDispatcher) {
            // 100% x 100/276 = 36%.
            baselineValues["medium_height_dp_v3"] = 100f

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())
            assertEquals(0.5f, store.observeEntry(null).first()!!.contentScale)
        }

    @Test
    fun `baseline rescales the value the legacy seed just wrote`() =
        runTest(testDispatcher) {
            settingsDataStore.edit { it[widgetContentScaleKey] = 1.3f }
            baselineValues["medium_height_dp_v3"] = 138f

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())

            assertEquals(0.65f, store.observeEntry(null).first()!!.contentScale, 1e-6f)
        }

    @Test
    fun `failed fold write keeps the baseline so the next start folds it`() =
        runTest(testDispatcher) {
            baselineValues["medium_height_dp_v3"] = 138f
            val failingStore = spyk(store)
            coEvery {
                failingStore.foldLegacyBaselineScale(any())
            } throws IOException("injected write failure")
            val firstStart =
                WidgetAppearanceRepository(failingStore, settingsRepository, mockk(relaxed = true))

            val failure = runCatching { firstStart.migrateFromLegacySettingsIfNeeded() }
            assertTrue(failure.exceptionOrNull() is IOException)
            assertNull(store.observeEntry(null).first())
            assertEquals(138f, baselineValues["medium_height_dp_v3"])

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())
            assertEquals(0.5f, store.observeEntry(null).first()!!.contentScale)
            assertTrue(baselineValues.isEmpty())
        }

    @Test
    fun `failed baseline clear still folds once and the marker stops a refold`() =
        runTest(testDispatcher) {
            baselineValues["medium_height_dp_v3"] = 204f
            baselineCommitSucceeds = false

            assertTrue(repository.migrateFromLegacySettingsIfNeeded())
            assertEquals(204f / 276f, store.observeEntry(null).first()!!.contentScale)
            assertEquals(204f, baselineValues["medium_height_dp_v3"])

            // Next start: the clear is retried, the scale is not folded again.
            baselineCommitSucceeds = true
            val nextStart = WidgetAppearanceRepository(store, settingsRepository, mockk(relaxed = true))
            assertFalse(nextStart.migrateFromLegacySettingsIfNeeded())
            assertEquals(204f / 276f, store.observeEntry(null).first()!!.contentScale)
            assertTrue(baselineValues.isEmpty())
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

        // In-memory stand-in for the v1.3.x hrt_widget_baseline file; commit() applies clear().
        val baselineValues = mutableMapOf<String, Float>()
        var baselineCommitSucceeds = true
        val baselineEditor: SharedPreferences.Editor = mockk {
            every { clear() } returns this
            every { commit() } answers {
                baselineCommitSucceeds.also { if (it) baselineValues.clear() }
            }
        }
        val baselinePrefs: SharedPreferences = mockk {
            every { getFloat(any(), any()) } answers {
                baselineValues[firstArg()] ?: secondArg()
            }
            every { edit() } returns baselineEditor
        }

        val widgetContext: Context = mockk<Context>().also {
            every { it.applicationContext } returns it
            every { it.filesDir } returns widgetFilesDir
            every { it.getSharedPreferences("hrt_widget_baseline", Context.MODE_PRIVATE) } returns
                baselinePrefs
        }
    }
}
