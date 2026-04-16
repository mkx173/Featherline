package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        return SettingsState(
            darkModeOption = DarkModeOption.fromStorageValue(preferences[darkModeKey]),
            adaptiveColorEnabled = preferences[adaptiveColorKey] ?: true
        )
    }
}
