package com.mkx.hrttracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreOutcome
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
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
    e2EstimateRepository: E2EstimateRepository,
    private val weightSyncCoordinator: WeightSyncCoordinator,
    private val healthConnectCapabilityReconciler: HealthConnectCapabilityReconciler,
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
    // Replayed like restoreEvents: the export outlives a recreation, and the new
    // composition still has to launch the folder picker or show the result.
    private val exportEvents = MutableSharedFlow<BackupExportEvent>(
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
        val preparedBackupExport = values[4] as PendingPreparedBackupExport?
        val restoreRequest = values[5] as PendingBackupRestoreRequest?
        val externalImportPreview = values[6] as ExternalImportPreview?
        val exportInProgress = values[7] as Boolean
        val restoreInProgress = values[8] as Boolean
        val externalImportInProgress = values[9] as Boolean
        val weightInProgress = values[10] as Boolean
        val calibrationAdjusted = values[11] as Boolean?
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
        viewModelScope.launch {
            runCatching { backupExportService.discardAbandonedPreparedBackups() }
        }
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

    fun setClassicNavigationBarEnabled(enabled: Boolean) {
        launchSettingsMutation {
            settingsRepository.setClassicNavigationBarEnabled(enabled)
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
            // Startup reconciles if the process dies before the reschedule; it outlives Settings.
            runReminderSideEffectBestEffort {
                medicationReminderScheduler.markReconcilePending()
            }
            try {
                settingsRepository.setRemindersEnabled(enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                settingsEvents.tryEmit(SettingsMutationEvent.Failure(error))
                return@launch
            }
            withContext(NonCancellable) {
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

    /**
     * Prepares a backup in viewModelScope, so a recreation neither cancels it nor loses
     * its file; [BackupExportEvent.ChooseDirectory] then asks the screen for a folder.
     */
    fun requestBackupExport(password: String) {
        // Checked and set synchronously, as requestBackupRestore does: the dialog's own
        // busy check reads the flag its last frame drew, so a repeat tap or IME Done
        // before the next frame still reaches here.
        if (isBackupExportInProgress.value || pendingPreparedBackupExport.value != null) return
        isBackupExportInProgress.value = true
        viewModelScope.launch {
            val event = try {
                val prepared = backupExportService.prepareBackupExport(password = password)
                setPendingPreparedBackupExport(
                    displayName = prepared.displayName,
                    tempFilePath = prepared.tempFilePath,
                )
                BackupExportEvent.ChooseDirectory
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                BackupExportEvent.Failure
            } finally {
                isBackupExportInProgress.value = false
            }
            // Emitted once idle: the screen may answer at once, as a folder picker that
            // fails to open hands the export straight back.
            exportEvents.tryEmit(event)
        }
    }

    /** The folder picker's result for the prepared backup; null if the picker was cancelled. */
    fun onBackupDirectoryChosen(directoryUri: Uri?) {
        if (isBackupExportInProgress.value) return
        val pending = pendingPreparedBackupExport.value
        if (pending == null) {
            // Nothing prepared to write: the process died while the picker was open.
            if (directoryUri != null) exportEvents.tryEmit(BackupExportEvent.Failure)
            return
        }
        val prepared = PreparedBackupExport(
            displayName = pending.displayName,
            tempFilePath = pending.tempFilePath,
        )
        isBackupExportInProgress.value = true
        viewModelScope.launch {
            try {
                if (directoryUri == null) {
                    backupExportService.discardPreparedBackup(prepared)
                } else {
                    val exported = backupExportService.exportPreparedBackup(
                        directoryUri = directoryUri,
                        preparedBackupExport = prepared,
                    )
                    exportEvents.tryEmit(BackupExportEvent.Success(exported.displayName))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                exportEvents.tryEmit(BackupExportEvent.Failure)
            } finally {
                isBackupExportInProgress.value = false
                clearPendingPreparedBackupExport()
            }
        }
    }

    val backupExportEvents: SharedFlow<BackupExportEvent> = exportEvents.asSharedFlow()

    override fun onCleared() {
        // A prepared backup the folder picker never answered: no later screen can finish it.
        // ponytail: one unlink on the main thread; viewModelScope is already cancelled here.
        pendingPreparedBackupExport.value?.let { pending -> File(pending.tempFilePath).delete() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeBackupExportEvent() {
        exportEvents.resetReplayCache()
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
                val outcome = backupRestoreService.restoreBackupBytes(
                    encryptedBytes = ownedBytes,
                    password = password,
                )
                restoreEvents.tryEmit(
                    when (outcome) {
                        BackupRestoreOutcome.RESTORED -> BackupRestoreEvent.Success
                        BackupRestoreOutcome.FOLLOW_UP_FAILED -> BackupRestoreEvent.FollowUpFailed
                    }
                )
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
    /** The basis Home's hero title names: null until a Home estimate with a calibration status exists. */
    val calibrationAdjusted: Boolean? = null,
)

data class PendingPreparedBackupExport(
    val displayName: String,
    val tempFilePath: String,
)

sealed class BackupRestoreEvent {
    data object Success : BackupRestoreEvent()

    /** The data was replaced, but some of its settings or reminders weren't. */
    data object FollowUpFailed : BackupRestoreEvent()
    data class Failure(val error: Throwable) : BackupRestoreEvent()
}

sealed interface BackupExportEvent {
    /** The backup is prepared: pick the folder it goes in. */
    data object ChooseDirectory : BackupExportEvent
    data class Success(val displayName: String) : BackupExportEvent
    data object Failure : BackupExportEvent
}

sealed class WeightMutationEvent {
    data class Failure(val error: Throwable) : WeightMutationEvent()
}

sealed class SettingsMutationEvent {
    data class Failure(val error: Throwable) : SettingsMutationEvent()
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
