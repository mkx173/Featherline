package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryLegacyWidgetAppearanceTest {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val widgetContentScaleKey = floatPreferencesKey("widget_content_scale")
    private val widgetBackgroundAlphaKey = floatPreferencesKey("widget_background_alpha")
    private val widgetDarkModeKey = stringPreferencesKey("widget_dark_mode")

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        val context: Context = mockk()
        every { appLanguageLocale() } returns Locale.ENGLISH

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("legacy_widget_test.preferences_pb") },
        )

        settingsRepository = SettingsRepository(context, dataStore)
    }

    @After
    fun tearDown() {
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun `readLegacyWidgetAppearance returns null when all three keys are absent`() =
        runTest(testDispatcher) {
            assertNull(settingsRepository.readLegacyWidgetAppearance())
        }

    @Test
    fun `readLegacyWidgetAppearance defaults scale and alpha when only dark mode is stored`() =
        runTest(testDispatcher) {
            dataStore.edit { it[widgetDarkModeKey] = "DARK" }

            val result = settingsRepository.readLegacyWidgetAppearance()

            assertEquals(
                LegacyWidgetAppearance(1.0f, 1.0f, DarkModeOption.DARK),
                result,
            )
        }

    @Test
    fun `readLegacyWidgetAppearance clamps alpha and falls back to follow-system enum`() =
        runTest(testDispatcher) {
            dataStore.edit {
                it[widgetContentScaleKey] = 1.3f
                it[widgetBackgroundAlphaKey] = 0.2f
            }

            val result = settingsRepository.readLegacyWidgetAppearance()

            assertEquals(
                LegacyWidgetAppearance(1.3f, 0.5f, DarkModeOption.FOLLOW_SYSTEM),
                result,
            )
        }

    @Test
    fun `readLegacyWidgetAppearance returns null after clearLegacyWidgetAppearanceKeys`() =
        runTest(testDispatcher) {
            dataStore.edit {
                it[widgetContentScaleKey] = 1.3f
                it[widgetBackgroundAlphaKey] = 0.8f
                it[widgetDarkModeKey] = "DARK"
            }

            settingsRepository.clearLegacyWidgetAppearanceKeys()

            assertNull(settingsRepository.readLegacyWidgetAppearance())
        }

    @Test
    fun `readLegacyWidgetAppearance maps unknown dark string to follow-system`() =
        runTest(testDispatcher) {
            dataStore.edit { it[widgetDarkModeKey] = "PURPLE" }

            val result = settingsRepository.readLegacyWidgetAppearance()

            assertEquals(
                LegacyWidgetAppearance(1.0f, 1.0f, DarkModeOption.FOLLOW_SYSTEM),
                result,
            )
        }
}
