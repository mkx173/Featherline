package com.mkx.hrttracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupExportedFile
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
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
class BackupSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val backupExportService: BackupExportService,
    private val backupRestoreService: BackupRestoreService,
    private val externalImportService: ExternalImportService,
) : ViewModel() {
    private val pendingPreparedBackupExport = MutableStateFlow<PendingPreparedBackupExport?>(null)
    private val pendingRestoreRequest = MutableStateFlow<PendingBackupRestoreRequest?>(null)
    private val pendingExternalImportPreview = MutableStateFlow<ExternalImportPreview?>(null)
    private val isBackupExportInProgress = MutableStateFlow(false)
    private val isBackupRestoreInProgress = MutableStateFlow(false)
    private val isExternalImportInProgress = MutableStateFlow(false)
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

    val uiState: StateFlow<BackupSettingsUiState> = combine(
        pendingPreparedBackupExport,
        pendingRestoreRequest,
        pendingExternalImportPreview,
        isBackupExportInProgress,
        isBackupRestoreInProgress,
        isExternalImportInProgress,
    ) { values ->
        BackupSettingsUiState(
            pendingPreparedBackupExport = values[0] as PendingPreparedBackupExport?,
            pendingRestoreRequest = values[1] as PendingBackupRestoreRequest?,
            pendingExternalImportPreview = values[2] as ExternalImportPreview?,
            isBackupExportInProgress = values[3] as Boolean,
            isBackupRestoreInProgress = values[4] as Boolean,
            isExternalImportInProgress = values[5] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BackupSettingsUiState(),
    )

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
    val externalImportEvents: SharedFlow<ExternalImportEvent> =
        externalImportEventsInternal.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeBackupRestoreEvent() {
        restoreEvents.resetReplayCache()
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

    private fun readExternalImportText(fileUri: Uri): String {
        return applicationContext.contentResolver.openInputStream(fileUri)
            ?.use { inputStream -> readBoundedExternalImportBytes(inputStream) }
            ?.let { bytes -> String(bytes, Charsets.UTF_8) }
            ?: throw IOException("Unable to open the selected external import file.")
    }
}

data class BackupSettingsUiState(
    val pendingPreparedBackupExport: PendingPreparedBackupExport? = null,
    val pendingRestoreRequest: PendingBackupRestoreRequest? = null,
    val pendingExternalImportPreview: ExternalImportPreview? = null,
    val isBackupExportInProgress: Boolean = false,
    val isBackupRestoreInProgress: Boolean = false,
    val isExternalImportInProgress: Boolean = false,
)

data class PendingPreparedBackupExport(
    val displayName: String,
    val tempFilePath: String,
)

sealed class BackupRestoreEvent {
    data object Success : BackupRestoreEvent()
    data class Failure(val error: Throwable) : BackupRestoreEvent()
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
