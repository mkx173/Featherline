package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.os.LocaleListCompat
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.currentAppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val darkModeKey = stringPreferencesKey("dark_mode")
    private val adaptiveColorKey = booleanPreferencesKey("adaptive_color")
    private val appLanguageOption = MutableStateFlow(resolveCurrentAppLanguage())

    val settingsState: StateFlow<SettingsState> = combine(
        context.dataStore.data.map(::preferencesToStoredSettingsState),
        appLanguageOption
    ) { storedSettingsState, currentAppLanguageOption ->
        storedSettingsState.copy(appLanguageOption = currentAppLanguageOption)
    }
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsState(appLanguageOption = appLanguageOption.value)
        )

    suspend fun getCurrentSettings(): SettingsState {
        return preferencesToStoredSettingsState(context.dataStore.data.first())
            .copy(appLanguageOption = appLanguageOption.value)
    }

    suspend fun setDarkModeOption(option: DarkModeOption) {
        context.dataStore.edit { preferences ->
            preferences[darkModeKey] = option.name
        }
    }

    suspend fun setAdaptiveColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[adaptiveColorKey] = enabled
        }
    }

    fun setAppLanguageOption(option: AppLanguageOption) {
        appLanguageOption.value = option
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.languageTag))
    }

    fun refreshAppLanguageOption(sourceContext: Context = context) {
        appLanguageOption.value = AppLanguageOption.fromLocale(sourceContext.currentAppLocale())
    }

    private fun preferencesToStoredSettingsState(preferences: Preferences): SettingsState {
        return SettingsState(
            darkModeOption = DarkModeOption.fromStorageValue(preferences[darkModeKey]),
            adaptiveColorEnabled = preferences[adaptiveColorKey] ?: true,
        )
    }

    private fun resolveCurrentAppLanguage(): AppLanguageOption {
        return AppLanguageOption.fromLocale(context.currentAppLocale())
    }
}
