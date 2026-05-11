package com.mkx.hrttracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupExportedFile
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.ui.security.AuthenticationPromptRequest
import com.mkx.hrttracker.util.AppDiagnosticsExportService
import com.mkx.hrttracker.util.AppDiagnosticsExportedFile
import com.mkx.hrttracker.util.AppLockSecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val backupExportService: BackupExportService,
    private val backupRestoreService: BackupRestoreService,
    private val diagnosticsExportService: AppDiagnosticsExportService,
) : ViewModel() {
    private val pendingPrompt = MutableStateFlow<AuthenticationPromptRequest?>(null)
    // Tracks the intent of pendingPrompt so the success handler knows whether the
    // user authenticated to enable or to disable screen-lock protection.
    private var pendingScreenLockIntent: ScreenLockPromptIntent? = null
    private val securityErrorMessageRes = MutableStateFlow<Int?>(null)
    private val pendingPreparedBackupExport = MutableStateFlow<PendingPreparedBackupExport?>(null)
    private val pendingRestoreRequest = MutableStateFlow<PendingBackupRestoreRequest?>(null)
    private val isBackupExportInProgress = MutableStateFlow(false)
    private val isBackupRestoreInProgress = MutableStateFlow(false)
    // Replay the most recent result so the UI still sees it after a config
    // change recreates the collector. The restore itself sets the app locale,
    // which triggers an activity recreate; without replay the success/failure
    // toast would be lost in the gap.
    private val restoreEvents = MutableSharedFlow<BackupRestoreEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    private var nextPromptId = 0L

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsState,
        userProfileRepository.observeProfile(),
        pendingPrompt,
        securityErrorMessageRes,
        pendingPreparedBackupExport,
        pendingRestoreRequest,
        isBackupExportInProgress,
        isBackupRestoreInProgress,
    ) { values ->
        val settingsState = values[0] as SettingsState
        val profile = values[1] as UserProfile?
        val prompt = values[2] as AuthenticationPromptRequest?
        val errorRes = values[3] as Int?
        val preparedBackupExport = values[4] as PendingPreparedBackupExport?
        val restoreRequest = values[5] as PendingBackupRestoreRequest?
        val exportInProgress = values[6] as Boolean
        val restoreInProgress = values[7] as Boolean
        SettingsUiState(
            settingsState = settingsState,
            userProfile = profile ?: UserProfile(),
            pendingPrompt = prompt,
            securityErrorMessageRes = errorRes,
            pendingPreparedBackupExport = preparedBackupExport,
            pendingRestoreRequest = restoreRequest,
            isBackupExportInProgress = exportInProgress,
            isBackupRestoreInProgress = restoreInProgress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    init {
        preloadCalibrationData()
    }

    fun setWeight(value: Double, unit: WeightUnit) {
        viewModelScope.launch {
            userProfileRepository.setWeight(value, unit)
        }
    }

    fun clearWeight() {
        viewModelScope.launch {
            userProfileRepository.clearWeight()
        }
    }

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

    fun setShowArchivedGroupRecords(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowArchivedGroupRecords(enabled)
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRemindersEnabled(enabled)
            if (!enabled) {
                medicationReminderSnoozeScheduler.clearAllSnoozes()
            }
            medicationReminderScheduler.rescheduleAll()
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
            viewModelScope.launch {
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
        viewModelScope.launch {
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

    private enum class ScreenLockPromptIntent { ENABLE, DISABLE }

    suspend fun prepareBackupExport(
        password: String,
    ): PreparedBackupExport {
        return backupExportService.prepareBackupExport(
            password = password,
        )
    }

    suspend fun exportPreparedBackup(
        directoryUri: Uri,
        preparedBackupExport: PreparedBackupExport,
    ): BackupExportedFile {
        return backupExportService.exportPreparedBackup(
            directoryUri = directoryUri,
            preparedBackupExport = preparedBackupExport,
        )
    }

    suspend fun discardPreparedBackup(
        preparedBackupExport: PreparedBackupExport,
    ) {
        backupExportService.discardPreparedBackup(preparedBackupExport)
    }

    fun restorePreparedBackupExport(
        displayName: String,
        tempFilePath: String,
    ): PreparedBackupExport {
        return backupExportService.restorePreparedBackupExport(
            displayName = displayName,
            tempFilePath = tempFilePath,
        )
    }

    suspend fun restoreBackup(
        fileUri: Uri,
        password: String,
    ) {
        backupRestoreService.restoreBackup(
            fileUri = fileUri,
            password = password,
        )
    }

    suspend fun restoreBackupBytes(
        encryptedBytes: ByteArray,
        password: String,
    ) {
        backupRestoreService.restoreBackupBytes(
            encryptedBytes = encryptedBytes,
            password = password,
        )
    }

    /**
     * Restore handler that survives configuration change. The restored
     * settings include `appLanguageOption`, which triggers an activity
     * recreate; if we launched this in `rememberCoroutineScope()` the
     * coroutine would be cancelled mid-flight and the success toast would
     * be lost. Running on `viewModelScope` and emitting events via a
     * replaying SharedFlow lets the new composition pick up the result.
     */
    fun requestBackupRestore(password: String) {
        // Guard against a rapid double-tap / IME re-submit on the
        // password dialog: this method is only invoked from the main
        // thread, so a sequential check-and-set is enough to prevent a
        // second launch from starting while the first is in flight.
        if (isBackupRestoreInProgress.value) return
        val request = pendingRestoreRequest.value ?: return
        val ownedBytes = request.encryptedBytes
        // The sentinel empty-bytes case means a previous call already
        // took ownership of the real bytes — bail rather than launch a
        // second coroutine that would fail-fast and emit a false failure
        // toast while flipping the in-progress flag off mid-restore.
        if (ownedBytes.isEmpty()) return
        // Flip the in-progress flag *synchronously* before launching so
        // a second click that lands before the coroutine runs sees it
        // and short-circuits at the guard above.
        setBackupRestoreInProgress(true)
        // Transfer ownership of the encrypted bytes out of the pending
        // request so a mid-flight dialog dismiss (which still calls
        // clearPendingRestoreRequest) can't zero the array we're actively
        // decrypting from. Leave a sentinel empty array in its place so
        // the rest of the UI flow still sees a live request.
        pendingRestoreRequest.value = PendingBackupRestoreRequest(
            uri = request.uri,
            displayName = request.displayName,
            encryptedBytes = ByteArray(0),
        )
        viewModelScope.launch {
            try {
                backupRestoreService.restoreBackupBytes(
                    encryptedBytes = ownedBytes,
                    password = password,
                )
                restoreEvents.tryEmit(BackupRestoreEvent.Success)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                restoreEvents.tryEmit(BackupRestoreEvent.Failure(error))
            } finally {
                ownedBytes.fill(0)
                setBackupRestoreInProgress(false)
                clearPendingRestoreRequest()
            }
        }
    }

    val backupRestoreEvents: SharedFlow<BackupRestoreEvent> = restoreEvents.asSharedFlow()

    fun consumeBackupRestoreEvent() {
        restoreEvents.resetReplayCache()
    }

    suspend fun validateBackupFile(
        fileUri: Uri,
    ) {
        backupRestoreService.validateBackupFile(fileUri)
    }

    /**
     * Reads the backup file once and validates its header. Returning the
     * loaded bytes lets the caller decrypt later (after the password
     * dialog) without re-opening the URI, which can fail when the SAF
     * temporary read grant lapses while the dialog is up.
     */
    suspend fun loadAndValidateBackupBytes(
        fileUri: Uri,
    ): ByteArray {
        val encryptedBytes = backupRestoreService.loadEncryptedBackupBytes(fileUri)
        var keepBytes = false
        try {
            backupRestoreService.validateBackupBytes(encryptedBytes)
            keepBytes = true
            return encryptedBytes
        } finally {
            if (!keepBytes) {
                encryptedBytes.fill(0)
            }
        }
    }

    fun diagnosticsExportFileName(): String {
        return diagnosticsExportService.buildExportFileName()
    }

    suspend fun exportDiagnosticLogs(
        destinationUri: Uri,
    ): AppDiagnosticsExportedFile {
        return diagnosticsExportService.exportLogs(destinationUri = destinationUri)
    }

    fun setPendingRestoreRequest(
        fileUri: Uri,
        displayName: String?,
        encryptedBytes: ByteArray,
    ) {
        val previous = pendingRestoreRequest.value
        pendingRestoreRequest.value = PendingBackupRestoreRequest(
            uri = fileUri,
            displayName = displayName,
            encryptedBytes = encryptedBytes,
        )
        previous?.encryptedBytes?.fill(0)
    }

    fun clearPendingRestoreRequest() {
        val previous = pendingRestoreRequest.value
        pendingRestoreRequest.value = null
        previous?.encryptedBytes?.fill(0)
    }

    fun setPendingPreparedBackupExport(
        displayName: String,
        tempFilePath: String,
    ) {
        pendingPreparedBackupExport.value = PendingPreparedBackupExport(
            displayName = displayName,
            tempFilePath = tempFilePath,
        )
    }

    fun clearPendingPreparedBackupExport() {
        pendingPreparedBackupExport.value = null
    }

    fun setBackupExportInProgress(
        inProgress: Boolean,
    ) {
        isBackupExportInProgress.value = inProgress
    }

    fun setBackupRestoreInProgress(
        inProgress: Boolean,
    ) {
        isBackupRestoreInProgress.value = inProgress
    }

    private fun preloadCalibrationData() {
        viewModelScope.launch {
            runCatching {
                bloodTestRepository.getPanels()
                bloodTestRepository.preloadActiveCustomAnalytes()
            }
        }
    }
}

data class SettingsUiState(
    val settingsState: SettingsState = SettingsState(),
    val userProfile: UserProfile = UserProfile(),
    val pendingPrompt: AuthenticationPromptRequest? = null,
    val securityErrorMessageRes: Int? = null,
    val pendingPreparedBackupExport: PendingPreparedBackupExport? = null,
    val pendingRestoreRequest: PendingBackupRestoreRequest? = null,
    val isBackupExportInProgress: Boolean = false,
    val isBackupRestoreInProgress: Boolean = false,
)

data class PendingPreparedBackupExport(
    val displayName: String,
    val tempFilePath: String,
)

sealed class BackupRestoreEvent {
    data object Success : BackupRestoreEvent()
    data class Failure(val error: Throwable) : BackupRestoreEvent()
}

class PendingBackupRestoreRequest(
    val uri: Uri,
    val displayName: String?,
    /**
     * Pre-loaded encrypted backup contents. Held until the password dialog
     * is confirmed or dismissed so that decryption never has to re-open the
     * URI (whose SAF temporary read grant can expire while the dialog is
     * up). Reference-only — equality on this class deliberately ignores
     * the contents so two separate restore attempts compare as distinct
     * even if they wrap the same file.
     */
    val encryptedBytes: ByteArray,
)
