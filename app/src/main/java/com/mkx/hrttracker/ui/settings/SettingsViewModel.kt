package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mkx.hrttracker.HrtTrackerApplication
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settingsState: StateFlow<SettingsState> = settingsRepository.settingsState

    fun setDarkModeOption(option: DarkModeOption) {
        viewModelScope.launch {
            settingsRepository.setDarkModeOption(option)
        }
    }

    fun setAdaptiveColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdaptiveColorEnabled(enabled)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as HrtTrackerApplication
                SettingsViewModel(application.appContainer.settingsRepository)
            }
        }
    }
}
