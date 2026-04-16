package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
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

    fun setAppLanguageOption(option: AppLanguageOption) {
        settingsRepository.setAppLanguageOption(option)
    }

    fun refreshAppLanguageOption() {
        settingsRepository.refreshAppLanguageOption()
    }
}
