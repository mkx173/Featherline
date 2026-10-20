package com.mkx.hrttracker.data.backup

import android.content.Context
import com.mkx.hrttracker.data.local.KeystoreWrappedSecret
import com.mkx.hrttracker.data.local.WrappedSecret
import com.mkx.hrttracker.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupKeyState {
    data object NoKey : BackupKeyState

    // Null only if the keyring has no recovery slot, which this build never writes.
    data class Key(
        val recoveryCreatedAt: Long?,
        val passkeySlots: List<KeyringSlot> = emptyList(),
        val securityKeySlots: List<KeyringSlot> = emptyList(),
    ) : BackupKeyState
}

/**
 * The Keystore can't decrypt the MK blob. Not NoKey, and it won't recover in
 * this process; callers tell it apart from a plain file read failure.
 */
class BackupKeyUnreadableException(cause: Throwable) :
    IOException("The backup key can't be read.", cause)

/**
 * The device's backup key: MK and recovery key Keystore-wrapped in prefs, and
 * the keyring as raw JSON in `files/keyring.json` (kept verbatim so fields this
 * build doesn't model survive). Both stores are excluded from Auto Backup.
 *
 * The two stores can't be written atomically, so [install] writes the keyring
 * first and MK last, and every read applies one rule: Key only when MK and the
 * keyring both exist and `mkCheck(MK)` matches. Anything else is NoKey and the
 * leftovers are deleted, so a crash mid-write lands at NoKey. The one
 * exception is a keyring this build can't decode: it is left in place for the
 * newer build that may have written it.
 */
@Singleton
class BackupKeyStore internal constructor(
    private val masterKeySecret: WrappedSecret,
    private val recoveryKeySecret: WrappedSecret,
    private val keyringFile: File,
    // Must be on the same filesystem as [keyringFile] for the rename.
    private val keyringTempFile: File,
    private val keyringCrypto: KeyringCrypto,
    private val appScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        keyringCrypto: KeyringCrypto,
        @AppScope appScope: CoroutineScope,
    ) : this(
        masterKeySecret = KeystoreWrappedSecret(context, KEY_ALIAS, "backup_master_key", "backup_master_key_iv"),
        recoveryKeySecret = KeystoreWrappedSecret(context, KEY_ALIAS, "backup_recovery_key", "backup_recovery_key_iv"),
        keyringFile = File(context.filesDir, KEYRING_FILE_NAME),
        // no_backup is never backed up or transferred, so the temp needs no
        // exclusion rule, and it sits beside files/ in the app's data dir.
        keyringTempFile = File(context.noBackupFilesDir, "$KEYRING_FILE_NAME.tmp"),
        keyringCrypto = keyringCrypto,
        appScope = appScope,
        ioDispatcher = Dispatchers.IO,
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow<BackupKeyState?>(null)

    /**
     * Null until the first read finishes. The first access starts that read
     * off the main thread; every call below also refreshes it.
     */
    val keyState: StateFlow<BackupKeyState?> by lazy {
        appScope.launch {
            // ponytail: a Keystore failure leaves the state null; the next call retries.
            runCatching { withStored { } }
        }
        state.asStateFlow()
    }

    /** A copy of MK, or null at NoKey. The caller zeroes it. */
    suspend fun masterKey(): ByteArray? = withStored { it.mk.copyOf() }

    suspend fun recoveryKey(): String? = withStored {
        recoveryKeySecret.read()?.let { bytes ->
            try {
                String(bytes, Charsets.UTF_8)
            } finally {
                bytes.fill(0)
            }
        }
    }

    /** The keyring exactly as installed. */
    suspend fun keyringJson(): String? = withStored { it.keyringJson }

    /**
     * Replaces any existing key. [keyringJson] is stored verbatim and must be a
     * keyring for [mk]. [mk] stays the caller's to zero.
     */
    suspend fun install(mk: ByteArray, recoveryKey: String?, keyringJson: String) {
        val keyring = keyringCrypto.decode(keyringJson)
        require(mkCheckMatches(mk, keyring)) { "The keyring does not belong to this master key." }

        withContext(ioDispatcher) {
            mutex.withLock {
                writeKeyringFile(keyringJson)
                // Recovery key before MK: MK is the commit point, so a crash
                // never pairs a new MK with an old recovery key.
                if (recoveryKey == null) {
                    recoveryKeySecret.clear()
                } else {
                    val bytes = recoveryKey.toByteArray(Charsets.UTF_8)
                    try {
                        recoveryKeySecret.write(bytes)
                    } finally {
                        bytes.fill(0)
                    }
                }
                masterKeySecret.write(mk)
                state.value = keyStateOf(keyring)
            }
        }
    }

    /**
     * Swaps the keyring for one of the same MK (a slot added or removed),
     * keeping MK and the recovery key. Throws when [keyringJson] belongs to
     * another MK or the phone has no key.
     */
    suspend fun replaceKeyring(keyringJson: String) {
        val keyring = keyringCrypto.decode(keyringJson)
        withContext(ioDispatcher) {
            mutex.withLock {
                val stored = readLocked() ?: throw IOException("No backup key to update.")
                try {
                    require(mkCheckMatches(stored.mk, keyring)) { "The keyring does not belong to this master key." }
                } finally {
                    stored.mk.fill(0)
                }
                writeKeyringFile(keyringJson)
                state.value = keyStateOf(keyring)
            }
        }
    }

    suspend fun clear() {
        withContext(ioDispatcher) {
            mutex.withLock { clearLocked() }
        }
    }

    private suspend fun <T> withStored(block: (Stored) -> T): T? = withContext(ioDispatcher) {
        mutex.withLock {
            val stored = readLocked()
            state.value = stored?.let { keyStateOf(it.keyring) } ?: BackupKeyState.NoKey
            stored?.let {
                try {
                    block(it)
                } finally {
                    it.mk.fill(0)
                }
            }
        }
    }

    private fun readLocked(): Stored? {
        // Keyring first, so a failed file read never strands an unzeroed MK copy.
        val keyringJson = if (keyringFile.isFile) keyringFile.readText(Charsets.UTF_8) else null
        val mk = try {
            masterKeySecret.read()
        } catch (error: Exception) {
            throw BackupKeyUnreadableException(error)
        }
        if (mk != null && keyringJson != null) {
            val keyring = runCatching { keyringCrypto.decode(keyringJson) }.getOrNull()
            if (keyring == null) {
                // ponytail: a keyring this build can't decode may be a newer
                // build's (downgrade). Leave both halves for it; the next
                // install here overwrites them anyway.
                mk.fill(0)
                return null
            }
            if (mkCheckMatches(mk, keyring)) return Stored(mk, keyringJson, keyring)
        }
        // A stray half (crash between the two writes, or an interrupted
        // Replace's mkCheck mismatch) and any leftover recovery key go.
        // Clearing nothing is a no-op, so this is cheap at NoKey.
        mk?.fill(0)
        clearLocked()
        return null
    }

    private fun clearLocked() {
        // MK first: once it's gone the state is NoKey whatever else survives.
        masterKeySecret.clear()
        keyringFile.delete()
        keyringTempFile.delete()
        recoveryKeySecret.clear()
        state.value = BackupKeyState.NoKey
    }

    private fun writeKeyringFile(keyringJson: String) {
        FileOutputStream(keyringTempFile).use { output ->
            output.write(keyringJson.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!keyringTempFile.renameTo(keyringFile)) {
            keyringTempFile.delete()
            throw IOException("Failed to move the keyring into place.")
        }
    }

    private fun mkCheckMatches(mk: ByteArray, keyring: Keyring): Boolean = MessageDigest.isEqual(
        keyringCrypto.mkCheck(mk).toByteArray(Charsets.UTF_8),
        keyring.mkCheck.toByteArray(Charsets.UTF_8),
    )

    private fun keyStateOf(keyring: Keyring) = BackupKeyState.Key(
        recoveryCreatedAt = keyring.slots.firstOrNull { it.type == RECOVERY_SLOT_TYPE }?.createdAt,
        passkeySlots = keyring.slots.filter { it.type == KeyringCrypto.SLOT_TYPE_PASSKEY },
        securityKeySlots = keyring.slots.filter { it.type == KeyringCrypto.SLOT_TYPE_SECURITY_KEY },
    )

    private class Stored(val mk: ByteArray, val keyringJson: String, val keyring: Keyring)

    private companion object {
        private const val KEY_ALIAS = "featherline_backup_master_key"
        private const val KEYRING_FILE_NAME = "keyring.json"
        private const val RECOVERY_SLOT_TYPE = "recovery"
    }
}
