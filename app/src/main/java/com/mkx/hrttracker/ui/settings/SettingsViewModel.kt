package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.security.AuthenticationPromptRequest
import com.mkx.hrttracker.util.AppLockSecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockSecurityManager: AppLockSecurityManager,
) : ViewModel() {
    private val pendingPrompt = MutableStateFlow<AuthenticationPromptRequest?>(null)
    private val securityErrorMessageRes = MutableStateFlow<Int?>(null)
    private var nextPromptId = 0L

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsState,
        pendingPrompt,
        securityErrorMessageRes
    ) { settingsState, prompt, errorRes ->
        SettingsUiState(
            settingsState = settingsState,
            pendingPrompt = prompt,
            securityErrorMessageRes = errorRes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

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

    fun setAppLockGracePeriodOption(option: AppLockGracePeriodOption) {
        viewModelScope.launch {
            settingsRepository.setAppLockGracePeriodOption(option)
        }
    }

    fun setHideScreenContentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideScreenContentEnabled(enabled)
        }
    }

    fun refreshAppLanguageOption() {
        settingsRepository.refreshAppLanguageOption()
    }

    fun onScreenLockProtectionToggle(enabled: Boolean) {
        if (enabled == uiState.value.settingsState.screenLockProtectionEnabled) {
            return
        }

        if (enabled) {
            val availabilityError = appLockSecurityManager.availabilityErrorMessageRes()
            if (availabilityError != null) {
                securityErrorMessageRes.value = availabilityError
                pendingPrompt.value = null
                return
            }

            securityErrorMessageRes.value = null
            pendingPrompt.value = AuthenticationPromptRequest(
                id = ++nextPromptId,
                titleRes = R.string.enable_screen_lock_prompt_title,
                subtitleRes = R.string.enable_screen_lock_prompt_subtitle,
                descriptionRes = R.string.enable_screen_lock_prompt_description
            )
            return
        }

        viewModelScope.launch {
            settingsRepository.setScreenLockProtectionEnabled(false)
            securityErrorMessageRes.value = null
            pendingPrompt.value = null
        }
    }

    fun onScreenLockProtectionAuthenticated() {
        pendingPrompt.value = null
        viewModelScope.launch {
            settingsRepository.setScreenLockProtectionEnabled(true)
            securityErrorMessageRes.value = null
        }
    }

    fun onScreenLockProtectionPromptError(errorCode: Int) {
        pendingPrompt.value = null
        securityErrorMessageRes.value = appLockSecurityManager.promptErrorMessageRes(errorCode)
    }
}

data class SettingsUiState(
    val settingsState: SettingsState = SettingsState(),
    val pendingPrompt: AuthenticationPromptRequest? = null,
    val securityErrorMessageRes: Int? = null,
)
