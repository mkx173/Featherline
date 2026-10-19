package com.mkx.hrttracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityReconciler
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityState
import com.mkx.hrttracker.healthconnect.ReadingProblem
import com.mkx.hrttracker.healthconnect.WeightSyncCoordinator
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.ui.security.AuthenticationPromptRequest
import com.mkx.hrttracker.util.AppDiagnosticsExportService
import com.mkx.hrttracker.util.AppDiagnosticsExportedFile
import com.mkx.hrttracker.util.AppLockSecurityManager
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val bloodTestRepository: BloodTestRepository,
    private val appLockSecurityManager: AppLockSecurityManager,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val diagnosticsExportService: AppDiagnosticsExportService,
    private val widgetAppearanceRepository: WidgetAppearanceRepository,
    e2EstimateRepository: E2EstimateRepository,
    private val weightSyncCoordinator: WeightSyncCoordinator,
    private val healthConnectCapabilityReconciler: HealthConnectCapabilityReconciler,
) : ViewModel() {
    private val pendingPrompt = MutableStateFlow<AuthenticationPromptRequest?>(null)

    // Tracks the intent of pendingPrompt so the success handler knows whether the
    // user authenticated to enable or to disable screen-lock protection.
    private var pendingScreenLockIntent: ScreenLockPromptIntent? = null
    private val securityErrorMessageRes = MutableStateFlow<Int?>(null)
    private val isWeightMutationInProgress = MutableStateFlow(false)
    private val weightEvents = MutableSharedFlow<WeightMutationEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    private val settingsEvents = MutableSharedFlow<SettingsMutationEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    private var nextPromptId = 0L

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsState,
        userProfileRepository.observeProfile(),
        pendingPrompt,
        securityErrorMessageRes,
        isWeightMutationInProgress,
        // The basis Home's hero title names: the status of the newest Home
        // estimate, kept until the next one lands and updated in place. The
        // first value is the current one (no start-with-null: that would
        // reach the combine after the seed and blank the row for a frame).
        e2EstimateRepository.latestHome
            .map { estimate -> estimate?.calibration?.adjusted }
            .distinctUntilChanged(),
    ) { values ->
        val settingsState = values[0] as SettingsState
        val profile = values[1] as UserProfile?
        val prompt = values[2] as AuthenticationPromptRequest?
        val errorRes = values[3] as Int?
        val weightInProgress = values[4] as Boolean
        val calibrationAdjusted = values[5] as Boolean?
        SettingsUiState(
            settingsState = settingsState,
            userProfile = profile ?: UserProfile(),
            pendingPrompt = prompt,
            securityErrorMessageRes = errorRes,
            isWeightMutationInProgress = weightInProgress,
            calibrationAdjusted = calibrationAdjusted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Seeded from the estimate Home already has (stored or derived) so
        // the calibration row has its support text on the first frame; the
        // combine above waits on a Room read before its first value.
        initialValue = SettingsUiState(
            calibrationAdjusted = e2EstimateRepository.latestHome.value?.calibration?.adjusted,
        ),
    )

    init {
        preloadCalibrationData()
    }

    val healthConnectState: StateFlow<HealthConnectCapabilityState> =
        healthConnectCapabilityReconciler.state
    val isWeightSyncing: StateFlow<Boolean> = weightSyncCoordinator.isSyncing
    val healthConnectReadingProblem: StateFlow<ReadingProblem?> = weightSyncCoordinator.readingProblem

    fun setWeight(value: Double, unit: WeightUnit) {
        launchWeightMutation {
            userProfileRepository.setWeight(value, unit)
        }
    }

    fun clearWeight() {
        launchWeightMutation {
            userProfileRepository.clearWeight()
        }
    }

    /** Called once READ weight is granted. */
    fun enableHealthConnectWeightSync() {
        launchSettingsMutation {
            healthConnectCapabilityReconciler.enableSync()
        }
    }

    fun disableHealthConnectWeightSync() {
        launchSettingsMutation {
            // Also clears the reading's date: turning sync back on shouldn't show
            // it before the new sync lands.
            healthConnectCapabilityReconciler.disableSync()
        }
    }

    fun syncWeightNow() {
        healthConnectCapabilityReconciler.requestReconcile("weight_sync_now", sync = true)
    }

    private fun launchWeightMutation(block: suspend () -> Unit) {
        if (isWeightMutationInProgress.value) return
        isWeightMutationInProgress.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                weightEvents.tryEmit(WeightMutationEvent.Failure(error))
            } finally {
                isWeightMutationInProgress.value = false
            }
        }
    }

    fun setDarkModeOption(option: DarkModeOption) {
        launchSettingsMutation {
            settingsRepository.setDarkModeOption(option)
        }
    }

    fun setAdaptiveColorEnabled(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setAdaptiveColorEnabled(enabled)
        }
    }

    fun setPureBlackEnabled(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setPureBlackEnabled(enabled)
        }
    }

    fun setHazeBlurEnabled(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setHazeBlurEnabled(enabled)
        }
    }

    fun setShowArchivedGroupRecords(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setShowArchivedGroupRecords(enabled)
        }
    }

    fun setHideReferenceRanges(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setHideReferenceRanges(enabled)
        }
    }

    fun setHideMedicationDetails(hidden: Boolean) {
        launchSettingsMutation {
            settingsRepository.setHideMedicationDetails(hidden)
        }
    }

    val widgetAppearance: StateFlow<WidgetAppearance> = widgetAppearanceRepository
        .effectiveFor(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetAppearance.Default)

    fun setWidgetAppearance(appearance: WidgetAppearance) {
        launchSettingsMutation {
            // The legacy→store migration may still be in flight on a fresh upgrade (it runs
            // fire-and-forget from HomeWidgetManager.start). Await it first — it is idempotent,
            // so this is safe — so the seed lands before this write rather than after it. A
            // migration failure surfaces as a Save failure rather than a silent clobber.
            widgetAppearanceRepository.migrateFromLegacySettingsIfNeeded()
            // The in-app dialog now edits every appearance field (parity with the launcher
            // editor for pre-Android-12 users), so write the whole value.
            widgetAppearanceRepository.setDefault(appearance)
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setRemindersEnabled(enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                settingsEvents.tryEmit(SettingsMutationEvent.Failure(error))
                return@launch
            }
            if (!enabled) {
                runReminderSideEffectBestEffort {
                    medicationReminderSnoozeScheduler.clearAllSnoozes()
                }
            }
            runReminderSideEffectBestEffort {
                medicationReminderScheduler.rescheduleAll()
            }
        }
    }

    fun setAppLanguageOption(option: AppLanguageOption) {
        settingsRepository.setAppLanguageOption(option)
    }

    fun setFirstDayOfWeekOption(option: FirstDayOfWeekOption) {
        launchSettingsMutation {
            settingsRepository.setFirstDayOfWeekOption(option)
        }
    }

    fun setAppLockGracePeriodOption(option: AppLockGracePeriodOption) {
        launchSettingsMutation {
            settingsRepository.setAppLockGracePeriodOption(option)
        }
    }

    fun setHideScreenContentEnabled(enabled: Boolean) {
        launchSettingsMutation {
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

        val availabilityError = appLockSecurityManager.availabilityErrorMessageRes()
        if (availabilityError != null) {
            // If biometrics are unavailable, gracefully fall back: enable cannot
            // proceed (surface the error). Disable can still proceed without auth
            // because the device cannot prompt — preserves the recovery path.
            if (enabled) {
                securityErrorMessageRes.value = availabilityError
                pendingPrompt.value = null
                pendingScreenLockIntent = null
                return
            }
            launchSettingsMutation {
                settingsRepository.setScreenLockProtectionEnabled(false)
                securityErrorMessageRes.value = null
                pendingPrompt.value = null
                pendingScreenLockIntent = null
            }
            return
        }

        securityErrorMessageRes.value = null
        pendingScreenLockIntent = if (enabled) {
            ScreenLockPromptIntent.ENABLE
        } else {
            ScreenLockPromptIntent.DISABLE
        }
        pendingPrompt.value = AuthenticationPromptRequest(
            id = ++nextPromptId,
            titleRes = if (enabled) {
                R.string.enable_screen_lock_prompt_title
            } else {
                R.string.disable_screen_lock_prompt_title
            },
            subtitleRes = if (enabled) {
                R.string.enable_screen_lock_prompt_subtitle
            } else {
                R.string.disable_screen_lock_prompt_subtitle
            },
        )
    }

    fun onScreenLockProtectionAuthenticated() {
        val intent = pendingScreenLockIntent ?: ScreenLockPromptIntent.ENABLE
        pendingPrompt.value = null
        pendingScreenLockIntent = null
        launchSettingsMutation {
            when (intent) {
                ScreenLockPromptIntent.ENABLE -> {
                    settingsRepository.setScreenLockProtectionEnabled(true)
                    settingsRepository.setHideScreenContentEnabled(true)
                }

                ScreenLockPromptIntent.DISABLE -> {
                    settingsRepository.setScreenLockProtectionEnabled(false)
                }
            }
            securityErrorMessageRes.value = null
        }
    }

    fun onScreenLockProtectionPromptError(errorCode: Int) {
        pendingPrompt.value = null
        pendingScreenLockIntent = null
        securityErrorMessageRes.value = appLockSecurityManager.promptErrorMessageRes(errorCode)
    }

    // Clears the security error after it has been surfaced as a toast so it
    // doesn't re-trigger on recomposition or configuration change.
    fun consumeSecurityError() {
        securityErrorMessageRes.value = null
    }

    private enum class ScreenLockPromptIntent { ENABLE, DISABLE }

    val weightMutationEvents: SharedFlow<WeightMutationEvent> = weightEvents.asSharedFlow()
    val settingsMutationEvents: SharedFlow<SettingsMutationEvent> = settingsEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeWeightMutationEvent() {
        weightEvents.resetReplayCache()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeSettingsMutationEvent() {
        settingsEvents.resetReplayCache()
    }

    fun diagnosticsExportFileName(): String {
        return diagnosticsExportService.buildExportFileName()
    }

    suspend fun exportDiagnosticLogs(
        destinationUri: Uri,
    ): AppDiagnosticsExportedFile {
        return diagnosticsExportService.exportLogs(destinationUri = destinationUri)
    }

    private fun preloadCalibrationData() {
        viewModelScope.launch {
            runCatching {
                bloodTestRepository.getPanels()
                bloodTestRepository.preloadActiveCustomAnalytes()
            }
        }
    }

    private fun launchSettingsMutation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                settingsEvents.tryEmit(SettingsMutationEvent.Failure(error))
            }
        }
    }

    private suspend fun runReminderSideEffectBestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Reminder alarms and snoozes are reconciled again by lifecycle
            // hooks; the settings DataStore write above is the user-visible change.
        }
    }
}

data class SettingsUiState(
    val settingsState: SettingsState = SettingsState(),
    val userProfile: UserProfile = UserProfile(),
    val pendingPrompt: AuthenticationPromptRequest? = null,
    val securityErrorMessageRes: Int? = null,
    val isWeightMutationInProgress: Boolean = false,
    /** The basis Home's hero title names: null until a Home estimate with a calibration status exists. */
    val calibrationAdjusted: Boolean? = null,
)

sealed class WeightMutationEvent {
    data class Failure(val error: Throwable) : WeightMutationEvent()
}

sealed class SettingsMutationEvent {
    data class Failure(val error: Throwable) : SettingsMutationEvent()
}
