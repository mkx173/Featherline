package com.mkx.hrttracker.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.util.AppLockSecurityManager
import com.mkx.hrttracker.util.ElapsedRealtimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockSecurityManager: AppLockSecurityManager,
    private val elapsedRealtimeProvider: ElapsedRealtimeProvider,
) : ViewModel() {
    private val isReady = MutableStateFlow(false)
    private val isUnlocked = MutableStateFlow(true)
    private val pendingPrompt = MutableStateFlow<AuthenticationPromptRequest?>(null)
    private val errorMessageRes = MutableStateFlow<Int?>(null)
    private var backgroundedAtElapsedRealtime: Long? = null
    private var nextPromptId = 0L

    val uiState: StateFlow<AppLockUiState> = combine(
        settingsRepository.settingsState,
        isReady,
        isUnlocked,
        pendingPrompt,
        errorMessageRes
    ) { settingsState, ready, unlocked, prompt, errorRes ->
        AppLockUiState(
            isReady = ready,
            screenLockProtectionEnabled = settingsState.screenLockProtectionEnabled,
            appLockGracePeriodOption = settingsState.appLockGracePeriodOption,
            isUnlocked = if (settingsState.screenLockProtectionEnabled) unlocked else true,
            pendingPrompt = prompt,
            errorMessageRes = errorRes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLockUiState()
    )

    init {
        viewModelScope.launch {
            val currentSettings = settingsRepository.getCurrentSettings()
            isUnlocked.value = !currentSettings.screenLockProtectionEnabled
            isReady.value = true
            if (currentSettings.screenLockProtectionEnabled) {
                requestUnlock()
            }

            settingsRepository.settingsState.collect { settingsState ->
                if (!settingsState.screenLockProtectionEnabled) {
                    backgroundedAtElapsedRealtime = null
                    isUnlocked.value = true
                    pendingPrompt.value = null
                    errorMessageRes.value = null
                }
            }
        }
    }

    fun onForegrounded() {
        val state = uiState.value
        if (!state.isReady || !state.screenLockProtectionEnabled) {
            return
        }

        if (state.isUnlocked) {
            val backgroundedAt = backgroundedAtElapsedRealtime
            if (backgroundedAt == null) {
                return
            }

            val elapsedSinceLeaving = (elapsedRealtimeProvider.now() - backgroundedAt)
                .coerceAtLeast(0L)

            backgroundedAtElapsedRealtime = null
            if (!state.appLockGracePeriodOption.shouldRelock(elapsedSinceLeaving)) {
                return
            }

            appLockSecurityManager.clearUnlockedSession()
            isUnlocked.value = false
        }

        requestUnlock()
    }

    fun onBackgrounded() {
        val state = uiState.value
        if (!state.screenLockProtectionEnabled) {
            return
        }

        if (!state.isUnlocked) {
            backgroundedAtElapsedRealtime = null
            pendingPrompt.value = null
            errorMessageRes.value = null
            return
        }

        if (state.appLockGracePeriodOption.shouldRelock(0L)) {
            appLockSecurityManager.clearUnlockedSession()
            isUnlocked.value = false
            backgroundedAtElapsedRealtime = null
        } else {
            backgroundedAtElapsedRealtime = elapsedRealtimeProvider.now()
        }

        pendingPrompt.value = null
        errorMessageRes.value = null
    }

    fun requestUnlock() {
        val availabilityError = appLockSecurityManager.availabilityErrorMessageRes()
        if (availabilityError != null) {
            errorMessageRes.value = availabilityError
            pendingPrompt.value = null
            return
        }

        errorMessageRes.value = null
        pendingPrompt.value = AuthenticationPromptRequest(
            id = ++nextPromptId,
            titleRes = R.string.unlock_app_prompt_title,
            subtitleRes = R.string.unlock_app_prompt_subtitle,
            descriptionRes = R.string.unlock_app_prompt_description
        )
    }

    fun onAuthenticationSucceeded() {
        pendingPrompt.value = null

        runCatching { appLockSecurityManager.unlockApp() }
            .onSuccess {
                backgroundedAtElapsedRealtime = null
                isUnlocked.value = true
                errorMessageRes.value = null
            }
            .onFailure {
                backgroundedAtElapsedRealtime = null
                isUnlocked.value = false
                errorMessageRes.value = R.string.screen_lock_auth_failed
            }
    }

    fun onAuthenticationError(errorCode: Int) {
        pendingPrompt.value = null
        if (isUnlocked.value) {
            return
        }

        errorMessageRes.value = appLockSecurityManager.promptErrorMessageRes(errorCode)
    }
}

data class AppLockUiState(
    val isReady: Boolean = false,
    val screenLockProtectionEnabled: Boolean = false,
    val appLockGracePeriodOption: com.mkx.hrttracker.model.settings.AppLockGracePeriodOption =
        com.mkx.hrttracker.model.settings.AppLockGracePeriodOption.IMMEDIATELY,
    val isUnlocked: Boolean = false,
    val pendingPrompt: AuthenticationPromptRequest? = null,
    val errorMessageRes: Int? = null,
) {
    val shouldShowLockScreen: Boolean
        get() = isReady && screenLockProtectionEnabled && !isUnlocked
}
