package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.ui.settings.DarkModeOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsState(
    val darkModeOption: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM,
    val adaptiveColorEnabled: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val darkModeKey = stringPreferencesKey("dark_mode")
    private val adaptiveColorKey = booleanPreferencesKey("adaptive_color")

    val settingsState: StateFlow<SettingsState> = context.dataStore.data
        .map(::preferencesToSettingsState)
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsState()
        )

    suspend fun getCurrentSettings(): SettingsState {
        return preferencesToSettingsState(context.dataStore.data.first())
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

    private fun preferencesToSettingsState(preferences: Preferences): SettingsState {
        val darkModeOption = preferences[darkModeKey]
            ?.let(DarkModeOption::valueOf)
            ?: DarkModeOption.FOLLOW_SYSTEM

        return SettingsState(
            darkModeOption = darkModeOption,
            adaptiveColorEnabled = preferences[adaptiveColorKey] ?: true
        )
    }
}
