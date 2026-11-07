package com.mkx.hrttracker.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.SecuritySettingsState
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
    private val securitySettings = settingsRepository.securitySettingsState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SecuritySettingsState.Loading,
    )
    private val isReady = MutableStateFlow(false)
    private val isUnlocked = MutableStateFlow(false)
    private val pendingPrompt = MutableStateFlow<AuthenticationPromptRequest?>(null)
    private val errorMessageRes = MutableStateFlow<Int?>(null)
    private var backgroundedAtElapsedRealtime: Long? = null
    private var nextPromptId = 0L

    val uiState: StateFlow<AppLockUiState> = combine(
        securitySettings,
        isReady,
        isUnlocked,
        pendingPrompt,
        errorMessageRes
    ) { security, ready, unlocked, prompt, errorRes ->
        val loaded = security as? SecuritySettingsState.Loaded
        AppLockUiState(
            isReady = ready,
            screenLockProtectionEnabled = security.lockEnabled,
            appLockGracePeriodOption = loaded?.appLockGracePeriodOption
                ?: AppLockGracePeriodOption.IMMEDIATELY,
            isUnlocked = if (security.lockEnabled) unlocked else true,
            pendingPrompt = prompt,
            errorMessageRes = errorRes,
            hideScreenContentEnabled = loaded?.hideScreenContentEnabled ?: true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLockUiState()
    )

    init {
        viewModelScope.launch {
            securitySettings.collect { security ->
                when {
                    security is SecuritySettingsState.Loading -> Unit

                    // The first read decides, a failed one as locked.
                    !isReady.value -> {
                        isUnlocked.value = !security.lockEnabled
                        isReady.value = true
                        if (security.lockEnabled) {
                            requestUnlock()
                        }
                    }

                    // Only a successful read can turn the lock off.
                    !security.lockEnabled -> {
                        backgroundedAtElapsedRealtime = null
                        isUnlocked.value = true
                        pendingPrompt.value = null
                        errorMessageRes.value = null
                    }
                }
            }
        }
    }

    fun onForegrounded() {
        val state = uiState.value
        if (!state.isReady || !state.screenLockProtectionEnabled) {
            return
        }

        val backgroundedAt = backgroundedAtElapsedRealtime
        backgroundedAtElapsedRealtime = null
        if (backgroundedAt != null) {
            val elapsedSinceLeaving = (elapsedRealtimeProvider.now() - backgroundedAt)
                .coerceAtLeast(0L)
            if (!state.appLockGracePeriodOption.shouldRelock(elapsedSinceLeaving)) {
                isUnlocked.value = true
                return
            }
        } else if (state.isUnlocked) {
            // Activity recreated without a prior onBackgrounded (e.g. language / dark
            // mode change triggers AppCompatDelegate recreation). The app is still
            // unlocked, so don't re-prompt.
            return
        }

        requestUnlock()
    }

    fun onBackgrounded() {
        val state = uiState.value
        // Before the lock decision there is no unlocked session for a grace period to resume.
        if (!state.isReady || !state.screenLockProtectionEnabled) {
            return
        }

        if (!state.isUnlocked) {
            backgroundedAtElapsedRealtime = null
            pendingPrompt.value = null
            errorMessageRes.value = null
            return
        }

        isUnlocked.value = false

        if (state.appLockGracePeriodOption.shouldRelock(0L)) {
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
        )
    }

    fun onAuthenticationSucceeded() {
        pendingPrompt.value = null
        backgroundedAtElapsedRealtime = null
        isUnlocked.value = true
        errorMessageRes.value = null
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
        com.mkx.hrttracker.model.settings.AppLockGracePeriodOption.ONE_MINUTE,
    val isUnlocked: Boolean = false,
    val pendingPrompt: AuthenticationPromptRequest? = null,
    val errorMessageRes: Int? = null,
    // Drives FLAG_SECURE. On until the stored setting has been read.
    val hideScreenContentEnabled: Boolean = true,
) {
    val shouldShowLockScreen: Boolean
        get() = isReady && screenLockProtectionEnabled && !isUnlocked
}

// Until a read succeeds, or after one fails, the stored setting is unknown: fail closed.
private val SecuritySettingsState.lockEnabled: Boolean
    get() = (this as? SecuritySettingsState.Loaded)?.screenLockProtectionEnabled ?: true
