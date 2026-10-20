package com.mkx.hrttracker.ui.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupExportedFile
import com.mkx.hrttracker.data.backup.BackupKeyState
import com.mkx.hrttracker.data.backup.BackupKeyStore
import com.mkx.hrttracker.data.backup.BackupKeyUnreadableException
import com.mkx.hrttracker.data.backup.KeyringCrypto
import com.mkx.hrttracker.data.backup.KeyringSlot
import com.mkx.hrttracker.data.backup.PasskeyCancelledException
import com.mkx.hrttracker.data.backup.PasskeyPrfUnsupportedException
import com.mkx.hrttracker.data.backup.PasskeyProviderUnavailableException
import com.mkx.hrttracker.data.backup.PasskeySlots
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.IncompatibleBackupFileException
import com.mkx.hrttracker.data.backup.WrongRecoveryKeyException
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.backup.RecoveryKey
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
import com.mkx.hrttracker.ui.security.AuthenticationPromptRequest
import com.mkx.hrttracker.util.AppLockSecurityManager
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val backupExportService: BackupExportService,
    private val backupRestoreService: BackupRestoreService,
    private val externalImportService: ExternalImportService,
    private val backupKeyStore: BackupKeyStore,
    private val keyringCrypto: KeyringCrypto,
    private val passkeySlots: PasskeySlots,
    private val appLockSecurityManager: AppLockSecurityManager,
) : ViewModel() {
    private val pendingPreparedBackupExport = MutableStateFlow<PendingPreparedBackupExport?>(null)
    private val pendingRestoreRequest = MutableStateFlow<PendingBackupRestoreRequest?>(null)
    private val pendingExternalImportPreview = MutableStateFlow<ExternalImportPreview?>(null)
    private val isBackupExportInProgress = MutableStateFlow(false)
    private val isBackupRestoreInProgress = MutableStateFlow(false)
    private val isExternalImportInProgress = MutableStateFlow(false)
    private val keySetup = MutableStateFlow<BackupKeySetupUiState?>(null)
    private val restoreKey = MutableStateFlow<BackupRestoreKeyUiState?>(null)
    private val encryptionKey = MutableStateFlow(BackupEncryptionKeyUiState())
    private var nextPromptId = 0L

    // What the device credential in [encryptionKey]'s prompt unlocks.
    private var pendingKeyAction: KeyAction? = null
    private var pendingRemovePasskeyId: String? = null

    // E2 re-link: the new MK, held from the install until the last old
    // passkey slot is linked or skipped, then zeroed by [endRelink].
    @VisibleForTesting
    internal var relinkMasterKey: ByteArray? = null
        private set
    private var relinkQueue: List<KeyringSlot> = emptyList()

    // Flow C's MK, from this phone's store (C2 directly) or the file's
    // recovery slot (C1), with the recovery key typed at C1. Kept out of the
    // UI state next to [pendingRestoreRequest]; zeroed by [endRestoreKey] on
    // every exit, onCleared() is only a backstop.
    @VisibleForTesting
    internal var restoreMasterKey: ByteArray? = null
        private set
    private var restoreRecoveryKey: String? = null

    // Key setup's MK, generated with [keySetup]'s recovery key. Kept out of
    // the UI state; zeroed by [endKeySetup] on every exit, onCleared() is
    // only a backstop.
    @VisibleForTesting
    internal var keySetupMasterKey: ByteArray? = null
        private set
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
        keySetup,
        restoreKey,
        encryptionKey,
    ) { values ->
        BackupSettingsUiState(
            pendingPreparedBackupExport = values[0] as PendingPreparedBackupExport?,
            pendingRestoreRequest = values[1] as PendingBackupRestoreRequest?,
            pendingExternalImportPreview = values[2] as ExternalImportPreview?,
            isBackupExportInProgress = values[3] as Boolean,
            isBackupRestoreInProgress = values[4] as Boolean,
            isExternalImportInProgress = values[5] as Boolean,
            keySetup = values[6] as BackupKeySetupUiState?,
            restoreKey = values[7] as BackupRestoreKeyUiState?,
            encryptionKey = values[8] as BackupEncryptionKeyUiState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BackupSettingsUiState(),
    )

    // Null until the store's first read; the footer treats that as unknown.
    val backupKeyState: StateFlow<BackupKeyState?> = backupKeyStore.keyState

    /**
     * Backup to file. With a key on this phone, prepares the export and
     * returns it for the folder picker (Flow B). Without one, starts key setup
     * (Flow A) and returns null; [confirmKeySetup] then finishes the export,
     * or shows the passkey offer first where one can work.
     */
    suspend fun onBackupToFileClick(): PreparedBackupExport? {
        if (isBackupExportInProgress.value || keySetup.value != null) return null
        setBackupExportInProgress(true)
        try {
            var mk: ByteArray? = null
            try {
                // Asked on every tap: keyState may still be null (not read yet).
                mk = try {
                    backupKeyStore.masterKey()
                } catch (_: BackupKeyUnreadableException) {
                    // ponytail: the Keystore can't decrypt the MK blob, and it
                    // won't for the rest of this process. Set up a new key:
                    // install overwrites both halves.
                    null
                }
                // Any other failure (the keyring file, say) is an export
                // failure, never a reason to replace a key this phone holds.
                val keyringJson = mk?.let { backupKeyStore.keyringJson() }
                if (mk != null && keyringJson != null) {
                    return prepareBackupExport(mk, keyringJson)
                }
            } finally {
                mk?.fill(0)
            }
            val secureRandom = SecureRandom()
            keySetupMasterKey = ByteArray(MASTER_KEY_LENGTH_BYTES).also(secureRandom::nextBytes)
            keySetup.value = BackupKeySetupUiState(recoveryKey = RecoveryKey.generate(secureRandom))
            return null
        } finally {
            setBackupExportInProgress(false)
        }
    }

    /** Cancel or system back in key setup: nothing is written. */
    fun cancelKeySetup() {
        if (isBackupExportInProgress.value) return
        endKeySetup()
    }

    /**
     * I saved it: installs the key and prepares the export for the folder
     * picker, or, where a passkey can work, shows the passkey offer first
     * and returns null.
     */
    suspend fun confirmKeySetup(): PreparedBackupExport? {
        if (isBackupExportInProgress.value) return null
        val setup = keySetup.value?.takeUnless { it.isReplace } ?: return null
        val mk = keySetupMasterKey ?: return null
        setBackupExportInProgress(true)
        try {
            // Argon2id (64 MiB, t=3): off the main thread, as at unlock.
            val keyringJson = withContext(Dispatchers.Default) {
                keyringCrypto.encode(
                    keyringCrypto.newKeyring(mk, setup.recoveryKey, now = System.currentTimeMillis())
                )
            }
            backupKeyStore.install(mk, setup.recoveryKey, keyringJson)
            if (passkeySlots.canOffer()) {
                encryptionKey.update { it.copy(isPasskeyOfferShown = true) }
                return null
            }
            return prepareBackupExport(mk, keyringJson)
        } finally {
            // Installed or failed, this setup is over: a retry makes a new key,
            // and after an install the next tap is Flow B.
            endKeySetup()
            setBackupExportInProgress(false)
        }
    }

    /** E1 Recovery key: the device credential, then Show. */
    fun onShowRecoveryKeyClick() = requestKeyCredential(KeyAction.SHOW)

    /** E2, after the explain dialog: the device credential, then the new key. */
    fun onReplaceKeyClick() {
        if (isBackupExportInProgress.value || keySetup.value != null) return
        requestKeyCredential(KeyAction.REPLACE)
    }

    // No screen lock: go ahead without a prompt, as the threat model allows.
    private fun requestKeyCredential(action: KeyAction) {
        if (appLockSecurityManager.isScreenLockMissing()) {
            runKeyAction(action)
            return
        }
        appLockSecurityManager.availabilityErrorMessageRes()?.let { messageRes ->
            encryptionKey.update { it.copy(messageRes = messageRes) }
            return
        }
        pendingKeyAction = action
        encryptionKey.update {
            it.copy(
                pendingPrompt = AuthenticationPromptRequest(
                    id = ++nextPromptId,
                    titleRes = when (action) {
                        KeyAction.SHOW -> R.string.settings_backup_show_key_prompt_title
                        KeyAction.REPLACE -> R.string.settings_backup_replace_key
                        KeyAction.REMOVE_PASSKEY -> R.string.settings_backup_remove_passkey
                    },
                    subtitleRes = when (action) {
                        KeyAction.SHOW -> R.string.settings_backup_show_key_prompt_subtitle
                        KeyAction.REPLACE -> R.string.settings_backup_replace_key_prompt_subtitle
                        KeyAction.REMOVE_PASSKEY -> R.string.settings_backup_remove_passkey_prompt_subtitle
                    },
                ),
            )
        }
    }

    fun onKeyCredentialAuthenticated() {
        val action = pendingKeyAction ?: return
        pendingKeyAction = null
        encryptionKey.update { it.copy(pendingPrompt = null) }
        runKeyAction(action)
    }

    // Cancel maps to no message, so it stops silently.
    fun onKeyCredentialError(errorCode: Int) {
        pendingKeyAction = null
        encryptionKey.update {
            it.copy(
                pendingPrompt = null,
                messageRes = appLockSecurityManager.promptErrorMessageRes(errorCode),
            )
        }
    }

    private fun runKeyAction(action: KeyAction) {
        when (action) {
            KeyAction.SHOW -> viewModelScope.launch {
                // Any failure, the Keystore's included, ends Show: there is
                // nothing to show and nothing here to repair.
                val recoveryKey = try {
                    backupKeyStore.recoveryKey()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                encryptionKey.update {
                    if (recoveryKey == null) {
                        it.copy(messageRes = R.string.settings_backup_key_read_failed)
                    } else {
                        it.copy(shownRecoveryKey = recoveryKey)
                    }
                }
            }

            KeyAction.REPLACE -> {
                if (isBackupExportInProgress.value || keySetup.value != null) return
                val secureRandom = SecureRandom()
                keySetupMasterKey = ByteArray(MASTER_KEY_LENGTH_BYTES).also(secureRandom::nextBytes)
                keySetup.value = BackupKeySetupUiState(
                    recoveryKey = RecoveryKey.generate(secureRandom),
                    isReplace = true,
                )
            }

            KeyAction.REMOVE_PASSKEY -> {
                val slotId = pendingRemovePasskeyId ?: return
                pendingRemovePasskeyId = null
                encryptionKey.update { it.copy(removePasskeySlotId = slotId) }
            }
        }
    }

    /**
     * Flow A's offer, Add passkey: adds a passkey slot as E3 does, then the
     * export (Flow B). A failure only toasts: the key is already installed.
     */
    suspend fun acceptPasskeyOffer(activity: Activity): PreparedBackupExport? {
        val state = encryptionKey.value
        if (!state.isPasskeyOfferShown || state.isPasskeyInProgress) return null
        encryptionKey.update { it.copy(isPasskeyInProgress = true) }
        val messageRes = try {
            addPasskeySlot(activity)
            null
        } catch (error: CancellationException) {
            // The screen went away mid-sheet; the offer stays for another try.
            encryptionKey.update { it.copy(isPasskeyInProgress = false) }
            throw error
        } catch (error: Exception) {
            passkeyErrorMessageRes(error)
        }
        encryptionKey.update {
            it.copy(isPasskeyOfferShown = false, isPasskeyInProgress = false, messageRes = messageRes)
        }
        return onBackupToFileClick()
    }

    /** Flow A's offer, Not now or back: the export (Flow B). */
    suspend fun declinePasskeyOffer(): PreparedBackupExport? {
        val state = encryptionKey.value
        if (!state.isPasskeyOfferShown || state.isPasskeyInProgress) return null
        encryptionKey.update { it.copy(isPasskeyOfferShown = false) }
        return onBackupToFileClick()
    }

    /**
     * E3 Add passkey: the account notice, with no device credential first;
     * creating the passkey already verifies the user.
     */
    fun onAddPasskeyClick() {
        if (encryptionKey.value.isPasskeyInProgress) return
        encryptionKey.update { it.copy(isAddPasskeyNoticeShown = true) }
    }

    fun dismissAddPasskeyNotice() {
        encryptionKey.update { it.copy(isAddPasskeyNoticeShown = false) }
    }

    /** The notice's Continue: creates the passkey and appends its slot. */
    fun confirmAddPasskey(activity: Activity) {
        if (encryptionKey.value.isPasskeyInProgress) return
        encryptionKey.update { it.copy(isAddPasskeyNoticeShown = false, isPasskeyInProgress = true) }
        viewModelScope.launch {
            try {
                addPasskeySlot(activity)
                endPasskeyAction(null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                endPasskeyAction(passkeyErrorMessageRes(error))
            }
        }
    }

    // Creates a passkey for this phone's MK and appends its slot to the keyring.
    private suspend fun addPasskeySlot(activity: Activity) {
        var mk: ByteArray? = null
        try {
            mk = checkNotNull(backupKeyStore.masterKey()) { "No backup key." }
            val keyring = keyringCrypto.decode(checkNotNull(backupKeyStore.keyringJson()))
            val slot = passkeySlots.add(activity, mk)
            backupKeyStore.replaceKeyring(keyringCrypto.encode(keyring.copy(slots = keyring.slots + slot)))
        } finally {
            mk?.fill(0)
        }
    }

    /** E3 Remove: the device credential, then the confirm. */
    fun onRemovePasskeyClick(slotId: String) {
        if (encryptionKey.value.isPasskeyInProgress) return
        pendingRemovePasskeyId = slotId
        requestKeyCredential(KeyAction.REMOVE_PASSKEY)
    }

    fun dismissRemovePasskey() {
        encryptionKey.update { it.copy(removePasskeySlotId = null) }
    }

    /** Rewrites the keyring without the slot. The passkey itself stays in its provider. */
    fun confirmRemovePasskey() {
        val slotId = encryptionKey.value.removePasskeySlotId ?: return
        if (encryptionKey.value.isPasskeyInProgress) return
        encryptionKey.update { it.copy(removePasskeySlotId = null, isPasskeyInProgress = true) }
        viewModelScope.launch {
            try {
                val keyring = keyringCrypto.decode(checkNotNull(backupKeyStore.keyringJson()))
                backupKeyStore.replaceKeyring(
                    keyringCrypto.encode(keyring.copy(slots = keyring.slots.filterNot { it.id == slotId }))
                )
                endPasskeyAction(null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                endPasskeyAction(R.string.settings_backup_passkeys_update_failed)
            }
        }
    }

    /** E2 re-link: wraps the new MK under the current old passkey slot. */
    fun linkPasskey(activity: Activity) {
        val slot = encryptionKey.value.relinkSlot ?: return
        val mk = relinkMasterKey ?: return
        if (encryptionKey.value.isPasskeyInProgress) return
        encryptionKey.update { it.copy(isPasskeyInProgress = true) }
        viewModelScope.launch {
            try {
                val newSlot = passkeySlots.rewrap(activity, slot, mk)
                val keyring = keyringCrypto.decode(checkNotNull(backupKeyStore.keyringJson()))
                backupKeyStore.replaceKeyring(keyringCrypto.encode(keyring.copy(slots = keyring.slots + newSlot)))
                encryptionKey.update { it.copy(isPasskeyInProgress = false) }
                nextRelink()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The dialog stays: try again or Skip.
                endPasskeyAction(passkeyErrorMessageRes(error))
            }
        }
    }

    /** E2 re-link Skip: the old slot is left out of the new keyring. */
    fun skipPasskeyLink() {
        if (encryptionKey.value.isPasskeyInProgress) return
        nextRelink()
    }

    private fun nextRelink() {
        val next = relinkQueue.firstOrNull()
        relinkQueue = relinkQueue.drop(1)
        if (next == null) {
            endRelink()
            encryptionKey.update { it.copy(relinkSlot = null, isNewKeyActive = true) }
        } else {
            encryptionKey.update { it.copy(relinkSlot = next) }
        }
    }

    private fun endRelink() {
        relinkMasterKey?.fill(0)
        relinkMasterKey = null
        relinkQueue = emptyList()
    }

    private fun endPasskeyAction(@StringRes messageRes: Int?) {
        encryptionKey.update { it.copy(isPasskeyInProgress = false, messageRes = messageRes) }
    }

    // Cancel is silent.
    @StringRes
    private fun passkeyErrorMessageRes(error: Exception): Int? = when (error) {
        is PasskeyCancelledException -> null
        is PasskeyProviderUnavailableException -> R.string.settings_backup_passkey_no_provider
        is PasskeyPrfUnsupportedException -> R.string.settings_backup_passkey_no_prf
        else -> R.string.settings_backup_passkey_failed
    }

    fun dismissShownRecoveryKey() {
        encryptionKey.update { it.copy(shownRecoveryKey = null) }
    }

    /**
     * E2's I saved it: installs the new key over the old one. No export; the
     * "New key active" dialog offers one. Runs in viewModelScope so a
     * recreate mid-install still reaches that dialog.
     */
    fun confirmReplaceKey() {
        if (isBackupExportInProgress.value) return
        val setup = keySetup.value?.takeIf { it.isReplace } ?: return
        val mk = keySetupMasterKey ?: return
        setBackupExportInProgress(true)
        viewModelScope.launch {
            try {
                // The old passkey slots, offered for re-link once the new key is in.
                val oldPasskeySlots = runCatching {
                    backupKeyStore.keyringJson()?.let { json ->
                        keyringCrypto.decode(json).slots.filter { it.type == KeyringCrypto.SLOT_TYPE_PASSKEY }
                    }
                }.getOrNull().orEmpty()
                val keyringJson = withContext(Dispatchers.Default) {
                    keyringCrypto.encode(
                        keyringCrypto.newKeyring(mk, setup.recoveryKey, now = System.currentTimeMillis())
                    )
                }
                // ponytail: keyring before MK, so a crash in between reads as
                // No key: the old key is gone and the next backup runs Flow A.
                backupKeyStore.install(mk, setup.recoveryKey, keyringJson)
                endRelink()
                relinkMasterKey = mk.copyOf()
                relinkQueue = oldPasskeySlots
                nextRelink()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                encryptionKey.update { it.copy(messageRes = R.string.settings_backup_replace_key_failed) }
            } finally {
                endKeySetup()
                setBackupExportInProgress(false)
            }
        }
    }

    fun dismissNewKeyActive() {
        encryptionKey.update { it.copy(isNewKeyActive = false) }
    }

    fun consumeEncryptionKeyMessage() {
        encryptionKey.update { it.copy(messageRes = null) }
    }

    private enum class KeyAction { SHOW, REPLACE, REMOVE_PASSKEY }

    private suspend fun prepareBackupExport(
        mk: ByteArray,
        keyringJson: String,
    ): PreparedBackupExport {
        val preparedBackupExport = backupExportService.prepareBackupExport(
            mk = mk,
            keyringJson = keyringJson,
        )
        setPendingPreparedBackupExport(
            displayName = preparedBackupExport.displayName,
            tempFilePath = preparedBackupExport.tempFilePath,
        )
        return preparedBackupExport
    }

    private fun endKeySetup() {
        keySetupMasterKey?.fill(0)
        keySetupMasterKey = null
        keySetup.value = null
    }

    override fun onCleared() {
        endKeySetup()
        endRestoreKey()
        endRelink()
        super.onCleared()
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
    // [password] for a v2/v3 file; a v4 file (C2) restores with the held MK.
    fun requestBackupRestore(password: String? = null) {
        // Guard against a rapid double-tap / IME re-submit on the
        // password dialog: this method is only invoked from the main
        // thread, so a sequential check-and-set is enough to prevent a
        // second launch from starting while the first is in flight.
        if (isBackupRestoreInProgress.value) return
        val request = pendingRestoreRequest.value ?: return
        val ownedBytes = request.encryptedBytes
        if (ownedBytes.isEmpty()) return
        val keyringJson = request.keyringJson
        val mk = restoreMasterKey
        val recoveryKey = restoreRecoveryKey
        val adopt = restoreKey.value?.let { it.canAdopt && it.adopt } == true
        // v4 still at C1, or v2/v3 without its password.
        if (keyringJson != null && mk == null) return
        if (keyringJson == null && password == null) return
        // Flip the in-progress flag *synchronously* before launching so
        // a second click that lands before the coroutine runs sees it
        // and short-circuits at the guard above.
        setBackupRestoreInProgress(true)
        // Keep the pending request in state while the restore is active so
        // the password dialog stays rendered and locked across a slow Room
        // open or the activity recreate caused by restored settings.
        viewModelScope.launch {
            try {
                if (keyringJson != null && mk != null) {
                    backupRestoreService.restoreBackupBytes(encryptedBytes = ownedBytes, mk = mk)
                    // After the commit only: the payload tag just vouched
                    // for the peeked keyring, which is installed verbatim.
                    // Re-check the phone has no key: canAdopt was fixed at
                    // file open, where a one-off Keystore failure reads as
                    // No key, and install would overwrite a real one.
                    if (adopt && !hasOwnKey()) {
                        try {
                            backupKeyStore.install(mk, recoveryKey, keyringJson)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // ponytail: the data is restored, so this still
                            // counts as success. The phone reads as No key
                            // and the next backup sets up a key (Flow A).
                        }
                    }
                } else {
                    backupRestoreService.restoreBackupBytes(
                        encryptedBytes = ownedBytes,
                        password = checkNotNull(password),
                    )
                }
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

    /**
     * Holds a validated file for restore and picks its dialog (Flow C): v2/v3
     * the password dialog; v4 C2 when this phone's key made it, else C1.
     * Throws [IncompatibleBackupFileException] without taking [encryptedBytes].
     */
    suspend fun setPendingRestoreRequest(
        fileUri: Uri,
        displayName: String?,
        encryptedBytes: ByteArray,
    ) {
        val keyringJson = backupRestoreService.peekRestoreKeyring(encryptedBytes)
        var mk: ByteArray? = null
        var restoreKeyState: BackupRestoreKeyUiState? = null
        if (keyringJson != null) {
            val keyring = try {
                keyringCrypto.decode(keyringJson)
            } catch (error: IllegalArgumentException) {
                throw IncompatibleBackupFileException("Backup keyring is not readable.", error)
            }
            mk = try {
                backupKeyStore.masterKey()
            } catch (_: BackupKeyUnreadableException) {
                // As at backup: a Keystore failure reads as No key. Nothing is
                // cleared or written here; only an adopt would install. Any
                // other failure is a restore failure.
                null
            }
            val hasKey = mk != null
            if (mk != null && !mkCheckMatches(mk, keyring.mkCheck)) {
                mk.fill(0)
                mk = null
            }
            restoreKeyState = BackupRestoreKeyUiState(
                isUnlocked = mk != null,
                canAdopt = !hasKey,
                passkeySlots = keyring.slots.filter { it.type == KeyringCrypto.SLOT_TYPE_PASSKEY },
            )
        }
        val previous = pendingRestoreRequest.value
        endRestoreKey()
        restoreMasterKey = mk
        restoreKey.value = restoreKeyState
        pendingRestoreRequest.value = PendingBackupRestoreRequest(
            uri = fileUri,
            displayName = displayName,
            encryptedBytes = encryptedBytes,
            keyringJson = keyringJson,
        )
        previous?.encryptedBytes?.fill(0)
    }

    fun clearPendingRestoreRequest() {
        val previous = pendingRestoreRequest.value
        pendingRestoreRequest.value = null
        endRestoreKey()
        previous?.encryptedBytes?.fill(0)
    }

    fun onUnlockInputChanged(input: String) {
        restoreKey.update { it?.copy(input = input, isWrongKey = false, isNotAKey = false) }
    }

    /** C1 Scan: a recovery key unlocks at once. Null (cancelled, no camera) does nothing. */
    fun onUnlockScanned(contents: String?) {
        contents ?: return
        if (restoreKey.value?.let { it.isUnlocked || it.isUnlocking } != false) return
        val recoveryKey = RecoveryKey.parse(contents)
        if (recoveryKey == null) {
            restoreKey.update { it?.copy(isWrongKey = false, isNotAKey = true) }
            return
        }
        restoreKey.update { it?.copy(input = recoveryKey, isWrongKey = false, isNotAKey = false) }
        unlockBackupRestore()
    }

    /** C1 Unlock: opens the file's recovery slot, then C2. */
    fun unlockBackupRestore() {
        val request = pendingRestoreRequest.value ?: return
        val keyringJson = request.keyringJson ?: return
        val state = restoreKey.value ?: return
        if (state.isUnlocked || state.isUnlocking) return
        val recoveryKey = RecoveryKey.parse(state.input) ?: return
        restoreKey.value = state.copy(isUnlocking = true, isWrongKey = false, isNotAKey = false)
        viewModelScope.launch {
            var mk: ByteArray? = null
            try {
                // Argon2id: a moment of CPU.
                mk = withContext(Dispatchers.Default) {
                    keyringCrypto.unwrapRecovery(keyringCrypto.decode(keyringJson), recoveryKey)
                }
                // Cancelled or replaced while unwrapping: drop it.
                if (pendingRestoreRequest.value !== request) return@launch
                restoreMasterKey = mk
                restoreRecoveryKey = recoveryKey
                mk = null
                restoreKey.update { it?.copy(isUnlocked = true, isUnlocking = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: WrongRecoveryKeyException) {
                // No attempt limit: the file is open to offline guessing anyway.
                if (pendingRestoreRequest.value === request) {
                    restoreKey.update { it?.copy(isWrongKey = true, isUnlocking = false) }
                }
            } catch (error: Exception) {
                // Not a wrong key: a malformed slot (incompatible) or a crypto
                // failure. Close C1 with the matching toast.
                if (pendingRestoreRequest.value === request) {
                    clearPendingRestoreRequest()
                    restoreEvents.tryEmit(BackupRestoreEvent.Failure(error))
                }
            } finally {
                mk?.fill(0)
            }
        }
    }

    /**
     * C1 Use passkey: one Credential Manager sheet opens the file's passkey
     * slot. The recovery key comes from the keyring, so an adopt caches it
     * and Show recovery key works on this phone later.
     */
    fun unlockBackupRestoreWithPasskey(activity: Activity) {
        val request = pendingRestoreRequest.value ?: return
        val keyringJson = request.keyringJson ?: return
        val state = restoreKey.value ?: return
        if (state.isUnlocked || state.isUnlocking || state.passkeySlots.isEmpty()) return
        restoreKey.value = state.copy(isUnlocking = true, isWrongKey = false, isNotAKey = false)
        viewModelScope.launch {
            var mk: ByteArray? = null
            try {
                mk = passkeySlots.unlock(activity, state.passkeySlots).second
                val recoveryKey = runCatching {
                    keyringCrypto.unwrapRecoveryKey(keyringCrypto.decode(keyringJson), mk)
                }.getOrNull()
                if (pendingRestoreRequest.value !== request) return@launch
                restoreMasterKey = mk
                restoreRecoveryKey = recoveryKey
                mk = null
                restoreKey.update { it?.copy(isUnlocked = true, isUnlocking = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // C1 stays with the field: the recovery key still works.
                if (pendingRestoreRequest.value === request) {
                    restoreKey.update { it?.copy(isUnlocking = false) }
                    encryptionKey.update { it.copy(messageRes = passkeyErrorMessageRes(error)) }
                }
            } finally {
                mk?.fill(0)
            }
        }
    }

    fun onAdoptRestoreKeyChanged(adopt: Boolean) {
        restoreKey.update { it?.copy(adopt = adopt) }
    }

    private fun endRestoreKey() {
        restoreMasterKey?.fill(0)
        restoreMasterKey = null
        restoreRecoveryKey = null
        restoreKey.value = null
    }

    // The Keystore failure reads as No key here too: install overwrites both
    // halves. Any other failure counts as a key, so nothing is overwritten.
    private suspend fun hasOwnKey(): Boolean = try {
        backupKeyStore.masterKey()?.let { mk ->
            mk.fill(0)
            true
        } ?: false
    } catch (_: BackupKeyUnreadableException) {
        false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        true
    }

    private fun mkCheckMatches(mk: ByteArray, mkCheck: String): Boolean = MessageDigest.isEqual(
        keyringCrypto.mkCheck(mk).toByteArray(Charsets.UTF_8),
        mkCheck.toByteArray(Charsets.UTF_8),
    )

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
    val keySetup: BackupKeySetupUiState? = null,
    // Flow C, for a v4 file in [pendingRestoreRequest]; null for v2/v3.
    val restoreKey: BackupRestoreKeyUiState? = null,
    val encryptionKey: BackupEncryptionKeyUiState = BackupEncryptionKeyUiState(),
)

// The Encryption key group (E1, E2).
data class BackupEncryptionKeyUiState(
    val pendingPrompt: AuthenticationPromptRequest? = null,
    // E1 Show: the recovery key on screen.
    val shownRecoveryKey: String? = null,
    // E2 done: "New key active".
    val isNewKeyActive: Boolean = false,
    // E3 Add: "Anyone who can sign in…".
    val isAddPasskeyNoticeShown: Boolean = false,
    // Flow A, after the install: Add a passkey? (Not now / Add passkey).
    val isPasskeyOfferShown: Boolean = false,
    // E3 Remove, after the credential: the confirm for this slot.
    val removePasskeySlotId: String? = null,
    // E2 re-link: the old passkey slot being offered (Link / Skip).
    val relinkSlot: KeyringSlot? = null,
    // A Credential Manager call or a keyring write is running.
    val isPasskeyInProgress: Boolean = false,
    // A one-shot toast.
    @param:StringRes val messageRes: Int? = null,
)

data class BackupRestoreKeyUiState(
    // False: C1 (unlock). True: C2 (confirm), with the MK held.
    val isUnlocked: Boolean,
    // A No-key phone: C2 offers to keep the file's recovery key.
    val canAdopt: Boolean,
    val adopt: Boolean = true,
    val input: String = "",
    val isWrongKey: Boolean = false,
    // A scanned code that isn't a recovery key; cleared like [isWrongKey].
    val isNotAKey: Boolean = false,
    val isUnlocking: Boolean = false,
    // The file's passkey slots: C1 offers Use passkey when there are any.
    val passkeySlots: List<KeyringSlot> = emptyList(),
)

data class BackupKeySetupUiState(
    val recoveryKey: String,
    // E2 Replace rather than Flow A: no export follows.
    val isReplace: Boolean = false,
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
    // A v4 file's keyring, verbatim as peeked; null for v2/v3.
    val keyringJson: String? = null,
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

private const val MASTER_KEY_LENGTH_BYTES = 32
private const val MAX_EXTERNAL_IMPORT_JSON_BYTES = 32L * 1024L * 1024L
