package com.mkx.hrttracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupExportedFile
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.healthconnect.FeatherlineHealthConnect
import com.mkx.hrttracker.healthconnect.HealthConnectIntegrationState
import com.mkx.hrttracker.healthconnect.HealthConnectSyncCoordinator
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val bloodTestRepository: BloodTestRepository,
    private val appLockSecurityManager: AppLockSecurityManager,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val backupExportService: BackupExportService,
    private val backupRestoreService: BackupRestoreService,
    private val externalImportService: ExternalImportService,
    private val diagnosticsExportService: AppDiagnosticsExportService,
    private val widgetAppearanceRepository: WidgetAppearanceRepository,
    private val healthConnect: FeatherlineHealthConnect,
    private val healthConnectSyncCoordinator: HealthConnectSyncCoordinator,
) : ViewModel() {
    private val pendingPrompt = MutableStateFlow<AuthenticationPromptRequest?>(null)

    // Tracks the intent of pendingPrompt so the success handler knows whether the
    // user authenticated to enable or to disable screen-lock protection.
    private var pendingScreenLockIntent: ScreenLockPromptIntent? = null
    private val securityErrorMessageRes = MutableStateFlow<Int?>(null)
    private val pendingPreparedBackupExport = MutableStateFlow<PendingPreparedBackupExport?>(null)
    private val pendingRestoreRequest = MutableStateFlow<PendingBackupRestoreRequest?>(null)
    private val pendingExternalImportPreview = MutableStateFlow<ExternalImportPreview?>(null)
    private val isBackupExportInProgress = MutableStateFlow(false)
    private val isBackupRestoreInProgress = MutableStateFlow(false)
    private val isExternalImportInProgress = MutableStateFlow(false)
    private val isWeightMutationInProgress = MutableStateFlow(false)
    private val weightEvents = MutableSharedFlow<WeightMutationEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    private val settingsEvents = MutableSharedFlow<SettingsMutationEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    private val healthConnectEvents = MutableSharedFlow<HealthConnectSyncEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    private val externalImportEventsInternal = MutableSharedFlow<ExternalImportEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )

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
        pendingExternalImportPreview,
        isBackupExportInProgress,
        isBackupRestoreInProgress,
        isExternalImportInProgress,
        isWeightMutationInProgress,
    ) { values ->
        val settingsState = values[0] as SettingsState
        val profile = values[1] as UserProfile?
        val prompt = values[2] as AuthenticationPromptRequest?
        val errorRes = values[3] as Int?
        val preparedBackupExport = values[4] as PendingPreparedBackupExport?
        val restoreRequest = values[5] as PendingBackupRestoreRequest?
        val externalImportPreview = values[6] as ExternalImportPreview?
        val exportInProgress = values[7] as Boolean
        val restoreInProgress = values[8] as Boolean
        val externalImportInProgress = values[9] as Boolean
        val weightInProgress = values[10] as Boolean
        SettingsUiState(
            settingsState = settingsState,
            userProfile = profile ?: UserProfile(),
            pendingPrompt = prompt,
            securityErrorMessageRes = errorRes,
            pendingPreparedBackupExport = preparedBackupExport,
            pendingRestoreRequest = restoreRequest,
            pendingExternalImportPreview = externalImportPreview,
            isBackupExportInProgress = exportInProgress,
            isBackupRestoreInProgress = restoreInProgress,
            isExternalImportInProgress = externalImportInProgress,
            isWeightMutationInProgress = weightInProgress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    init {
        preloadCalibrationData()
        refreshHealthConnectState()
    }

    val healthConnectState: StateFlow<HealthConnectIntegrationState> = healthConnect.state
    val healthConnectSyncEvents: SharedFlow<HealthConnectSyncEvent> =
        healthConnectEvents.asSharedFlow()

    fun refreshHealthConnectState() {
        viewModelScope.launch { healthConnect.refreshCapabilities() }
    }

    fun setHealthConnectWeightEnabled(enabled: Boolean) {
        viewModelScope.launch { healthConnect.setWeightSyncEnabled(enabled) }
    }

    fun setHealthConnectMedicationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            healthConnectSyncCoordinator.setMedicationSyncEnabled(enabled)
        }
    }

    fun syncHealthConnectNow() {
        viewModelScope.launch {
            val succeeded = healthConnectSyncCoordinator.syncNow()
            healthConnectEvents.emit(
                if (succeeded) HealthConnectSyncEvent.Success else HealthConnectSyncEvent.Failure
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeHealthConnectSyncEvent() {
        healthConnectEvents.resetReplayCache()
    }

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

    fun setCjkTextOffsetEnabled(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setCjkTextOffsetEnabled(enabled)
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
        if (ownedBytes.isEmpty()) return
        // Flip the in-progress flag *synchronously* before launching so
        // a second click that lands before the coroutine runs sees it
        // and short-circuits at the guard above.
        setBackupRestoreInProgress(true)
        // Keep the pending request in state while the restore is active so
        // the password dialog stays rendered and locked across a slow Room
        // open or the activity recreate caused by restored settings.
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
    val weightMutationEvents: SharedFlow<WeightMutationEvent> = weightEvents.asSharedFlow()
    val settingsMutationEvents: SharedFlow<SettingsMutationEvent> = settingsEvents.asSharedFlow()
    val externalImportEvents: SharedFlow<ExternalImportEvent> =
        externalImportEventsInternal.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeBackupRestoreEvent() {
        restoreEvents.resetReplayCache()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeWeightMutationEvent() {
        weightEvents.resetReplayCache()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeSettingsMutationEvent() {
        settingsEvents.resetReplayCache()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeExternalImportEvent() {
        externalImportEventsInternal.resetReplayCache()
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

    suspend fun loadExternalImportPreview(
        fileUri: Uri,
    ): ExternalImportPreview {
        return try {
            val preview = withContext(Dispatchers.IO) {
                val json = readExternalImportText(fileUri)
                externalImportService.buildPreview(json)
            }
            pendingExternalImportPreview.value = preview
            preview
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            pendingExternalImportPreview.value = null
            externalImportEventsInternal.tryEmit(ExternalImportEvent.Failure(error))
            throw error
        }
    }

    fun requestExternalImport() {
        if (isExternalImportInProgress.value) return
        val preview = pendingExternalImportPreview.value ?: return
        setExternalImportInProgress(true)
        viewModelScope.launch {
            try {
                val result = externalImportService.commit(preview)
                pendingExternalImportPreview.value = null
                externalImportEventsInternal.tryEmit(ExternalImportEvent.Success(result))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                externalImportEventsInternal.tryEmit(ExternalImportEvent.Failure(error))
            } finally {
                setExternalImportInProgress(false)
            }
        }
    }

    fun clearPendingExternalImportPreview() {
        pendingExternalImportPreview.value = null
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

    fun setExternalImportInProgress(
        inProgress: Boolean,
    ) {
        isExternalImportInProgress.value = inProgress
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

    private fun readExternalImportText(fileUri: Uri): String {
        return applicationContext.contentResolver.openInputStream(fileUri)
            ?.use { inputStream -> readBoundedExternalImportBytes(inputStream) }
            ?.let { bytes -> String(bytes, Charsets.UTF_8) }
            ?: throw IOException("Unable to open the selected external import file.")
    }
}

data class SettingsUiState(
    val settingsState: SettingsState = SettingsState(),
    val userProfile: UserProfile = UserProfile(),
    val pendingPrompt: AuthenticationPromptRequest? = null,
    val securityErrorMessageRes: Int? = null,
    val pendingPreparedBackupExport: PendingPreparedBackupExport? = null,
    val pendingRestoreRequest: PendingBackupRestoreRequest? = null,
    val pendingExternalImportPreview: ExternalImportPreview? = null,
    val isBackupExportInProgress: Boolean = false,
    val isBackupRestoreInProgress: Boolean = false,
    val isExternalImportInProgress: Boolean = false,
    val isWeightMutationInProgress: Boolean = false,
)

data class PendingPreparedBackupExport(
    val displayName: String,
    val tempFilePath: String,
)

sealed class BackupRestoreEvent {
    data object Success : BackupRestoreEvent()
    data class Failure(val error: Throwable) : BackupRestoreEvent()
}

sealed class WeightMutationEvent {
    data class Failure(val error: Throwable) : WeightMutationEvent()
}

sealed class SettingsMutationEvent {
    data class Failure(val error: Throwable) : SettingsMutationEvent()
}

sealed interface HealthConnectSyncEvent {
    data object Success : HealthConnectSyncEvent
    data object Failure : HealthConnectSyncEvent
}

sealed interface ExternalImportEvent {
    data class Success(val result: ExternalImportCommitResult) : ExternalImportEvent
    data class Failure(val error: Throwable) : ExternalImportEvent
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

private fun readBoundedExternalImportBytes(inputStream: InputStream): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val bytesRead = inputStream.read(chunk)
        if (bytesRead == -1) break
        total += bytesRead
        if (total > MAX_EXTERNAL_IMPORT_JSON_BYTES) {
            throw IOException("Selected external import file exceeds the maximum supported size.")
        }
        buffer.write(chunk, 0, bytesRead)
    }
    return buffer.toByteArray()
}

private const val MAX_EXTERNAL_IMPORT_JSON_BYTES = 32L * 1024L * 1024L
