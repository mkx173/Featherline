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
    private val securityErrorMessageRes = MutableStateFlow<Int?>(null)
    private val pendingPreparedBackupExport = MutableStateFlow<PendingPreparedBackupExport?>(null)
    private val pendingRestoreRequest = MutableStateFlow<PendingBackupRestoreRequest?>(null)
    private val isBackupExportInProgress = MutableStateFlow(false)
    private val isBackupRestoreInProgress = MutableStateFlow(false)
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
            settingsRepository.setHideScreenContentEnabled(true)
            securityErrorMessageRes.value = null
        }
    }

    fun onScreenLockProtectionPromptError(errorCode: Int) {
        pendingPrompt.value = null
        securityErrorMessageRes.value = appLockSecurityManager.promptErrorMessageRes(errorCode)
    }

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

    suspend fun validateBackupFile(
        fileUri: Uri,
    ) {
        backupRestoreService.validateBackupFile(fileUri)
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
    ) {
        pendingRestoreRequest.value = PendingBackupRestoreRequest(
            uri = fileUri,
            displayName = displayName,
        )
    }

    fun clearPendingRestoreRequest() {
        pendingRestoreRequest.value = null
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

data class PendingBackupRestoreRequest(
    val uri: Uri,
    val displayName: String?,
)
