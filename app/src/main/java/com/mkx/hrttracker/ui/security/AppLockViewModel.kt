package com.mkx.hrttracker.ui.security

import androidx.biometric.BiometricPrompt
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
    // A stop came while a prompt was in flight, and the prompt was kept for its result.
    private var promptKeptAcrossStop = false
    // That kept prompt was cancelled. On API 29 the PIN screen's cancel can land before the start
    // that follows it, and the start must not ask again: the Unlock button stays.
    private var keptPromptCancelled = false
    // The Settings lock toggle's prompt is up, and a stop came while it was (below API 30 its
    // PIN screen stops this activity). Its outcome settles the relock: onOtherPromptEnded.
    private var otherPromptInFlight = false
    private var stoppedForOtherPrompt = false
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
        if (!state.isReady || !state.screenLockProtectionEnabled || stoppedForOtherPrompt) {
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
            // Activity recreated without a prior onBackgrounded (e.g. a font-size
            // change; language and dark mode apply in place). The app is still
            // unlocked, so don't re-prompt.
            return
        } else if (pendingPrompt.value != null) {
            // Its result or cancel is still to come.
            return
        } else if (keptPromptCancelled) {
            keptPromptCancelled = false
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

        keptPromptCancelled = false
        if (!state.isUnlocked) {
            backgroundedAtElapsedRealtime = null
            errorMessageRes.value = null
            // Before API 30 the PIN screen is an activity that stops this one, and its result
            // lands around the start. If the user left from the fingerprint dialog instead, the
            // system cancels it and onAuthenticationError asks again; the PIN screen's own cancel,
            // Back or a leave, is a user cancel and leaves the Unlock button.
            promptKeptAcrossStop = pendingPrompt.value != null &&
                    appLockSecurityManager.credentialScreenStopsActivity()
            if (!promptKeptAcrossStop) {
                pendingPrompt.value = null
            }
            return
        }

        isUnlocked.value = false
        stoppedForOtherPrompt = otherPromptInFlight &&
                appLockSecurityManager.credentialScreenStopsActivity()

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
        promptKeptAcrossStop = false
        backgroundedAtElapsedRealtime = null
        isUnlocked.value = true
        errorMessageRes.value = null
    }

    fun onAuthenticationError(errorCode: Int) {
        pendingPrompt.value = null
        val keptAcrossStop = promptKeptAcrossStop
        promptKeptAcrossStop = false
        if (isUnlocked.value) {
            return
        }

        if (keptAcrossStop && errorCode == BiometricPrompt.ERROR_CANCELED) {
            // The system closed the prompt when the user left the app: ask again.
            requestUnlock()
            return
        }
        keptPromptCancelled = keptAcrossStop
        errorMessageRes.value = appLockSecurityManager.promptErrorMessageRes(errorCode)
    }

    fun onOtherPromptStarted() {
        otherPromptInFlight = true
    }

    fun onOtherPromptEnded(succeeded: Boolean) {
        otherPromptInFlight = false
        if (!stoppedForOtherPrompt) {
            return
        }
        stoppedForOtherPrompt = false
        if (succeeded) {
            onAuthenticationSucceeded()
        } else {
            // Back can't be told from leaving and coming back to cancel it: settle the stop now.
            onForegrounded()
        }
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
