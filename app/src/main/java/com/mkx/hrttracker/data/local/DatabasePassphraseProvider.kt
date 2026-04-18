package com.mkx.hrttracker.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    @Volatile
    private var cachedPassphrase: ByteArray? = null

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPassphrase(): ByteArray {
        cachedPassphrase?.let { return it.copyOf() }

        val encryptedPassphrase = prefs.getString(KEY_DATABASE_PASSPHRASE, null)
        val iv = prefs.getString(KEY_DATABASE_PASSPHRASE_IV, null)

        if (encryptedPassphrase != null && iv != null) {
            val passphrase = when (readMasterKeyMode()) {
                MasterKeyMode.STANDARD -> decryptWithStandardKey(
                    encryptedBytes = Base64.decode(encryptedPassphrase, Base64.NO_WRAP),
                    iv = Base64.decode(iv, Base64.NO_WRAP)
                )
                MasterKeyMode.SCREEN_LOCK -> {
                    throw IllegalStateException("The database passphrase is locked behind screen lock authentication.")
                }
            }

            try {
                cachePassphrase(passphrase)
                return passphrase.copyOf()
            } finally {
                passphrase.fill(0)
            }
        }

        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
        try {
            persistEncryptedBlob(
                encryptedBlob = encryptWithStandardKey(passphrase),
                mode = MasterKeyMode.STANDARD
            )
            cachePassphrase(passphrase)
            return passphrase.copyOf()
        } finally {
            passphrase.fill(0)
        }
    }

    @Throws(UserNotAuthenticatedException::class)
    fun enableScreenLockProtection() {
        val passphrase = loadOrCreatePassphraseForMigration()
        try {
            val encryptedBlob = encryptWithScreenLockKey(passphrase)
            persistEncryptedBlob(
                encryptedBlob = encryptedBlob,
                mode = MasterKeyMode.SCREEN_LOCK
            )
            cachePassphrase(passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    fun disableScreenLockProtection() {
        val passphrase = cachedPassphrase?.copyOf()
            ?: when (readMasterKeyMode()) {
                MasterKeyMode.STANDARD -> getPassphrase()
                MasterKeyMode.SCREEN_LOCK -> {
                    throw IllegalStateException("Unlock the app before disabling screen lock protection.")
                }
            }

        try {
            persistEncryptedBlob(
                encryptedBlob = encryptWithStandardKey(passphrase),
                mode = MasterKeyMode.STANDARD
            )
            cachePassphrase(passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    @Throws(UserNotAuthenticatedException::class)
    fun primeScreenLockPassphraseCache() {
        if (readMasterKeyMode() != MasterKeyMode.SCREEN_LOCK) {
            return
        }

        val encryptedPassphrase = prefs.getString(KEY_DATABASE_PASSPHRASE, null)
            ?: throw IllegalStateException("No screen-lock-protected database passphrase is stored.")
        val iv = prefs.getString(KEY_DATABASE_PASSPHRASE_IV, null)
            ?: throw IllegalStateException("No screen-lock-protected database passphrase is stored.")

        val passphrase = decryptWithScreenLockKey(
            encryptedBytes = Base64.decode(encryptedPassphrase, Base64.NO_WRAP),
            iv = Base64.decode(iv, Base64.NO_WRAP)
        )
        try {
            cachePassphrase(passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    fun clearPassphraseCache() {
        cachedPassphrase?.fill(0)
        cachedPassphrase = null
    }

    fun isScreenLockProtectionEnabled(): Boolean {
        return readMasterKeyMode() == MasterKeyMode.SCREEN_LOCK
    }

    private fun loadOrCreatePassphraseForMigration(): ByteArray {
        cachedPassphrase?.let { return it.copyOf() }
        return when (readMasterKeyMode()) {
            MasterKeyMode.STANDARD -> getPassphrase()
            MasterKeyMode.SCREEN_LOCK -> {
                throw IllegalStateException("Unlock the app before migrating the protected database passphrase.")
            }
        }
    }

    private fun encryptWithStandardKey(clearBytes: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateStandardMasterKey())
        }

        return EncryptedBlob(
            encryptedBytes = cipher.doFinal(clearBytes),
            iv = cipher.iv
        )
    }

    private fun decryptWithStandardKey(encryptedBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateStandardMasterKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
        }

        return cipher.doFinal(encryptedBytes)
    }

    @Throws(UserNotAuthenticatedException::class)
    private fun encryptWithScreenLockKey(clearBytes: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateScreenLockMasterKey())
        }

        return EncryptedBlob(
            encryptedBytes = cipher.doFinal(clearBytes),
            iv = cipher.iv
        )
    }

    @Throws(UserNotAuthenticatedException::class)
    private fun decryptWithScreenLockKey(encryptedBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateScreenLockMasterKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
        }

        return cipher.doFinal(encryptedBytes)
    }

    private fun persistEncryptedBlob(
        encryptedBlob: EncryptedBlob,
        mode: MasterKeyMode
    ) {
        prefs.edit(commit = true) {
            putString(
                KEY_DATABASE_PASSPHRASE,
                Base64.encodeToString(encryptedBlob.encryptedBytes, Base64.NO_WRAP)
            )
            putString(
                KEY_DATABASE_PASSPHRASE_IV,
                Base64.encodeToString(encryptedBlob.iv, Base64.NO_WRAP)
            )
            putString(KEY_MASTER_KEY_MODE, mode.name)
        }
    }

    private fun cachePassphrase(passphrase: ByteArray) {
        cachedPassphrase?.fill(0)
        cachedPassphrase = passphrase.copyOf()
    }

    private fun getOrCreateStandardMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(STANDARD_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        val spec = KeyGenParameterSpec.Builder(
            STANDARD_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateScreenLockMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(SCREEN_LOCK_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        val spec = KeyGenParameterSpec.Builder(
            SCREEN_LOCK_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                SCREEN_LOCK_AUTH_VALIDITY_DURATION_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .setInvalidatedByBiometricEnrollment(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun readMasterKeyMode(): MasterKeyMode {
        return MasterKeyMode.fromStorageValue(prefs.getString(KEY_MASTER_KEY_MODE, null))
            ?: MasterKeyMode.STANDARD
    }

    private enum class MasterKeyMode {
        STANDARD,
        SCREEN_LOCK;

        companion object {
            fun fromStorageValue(value: String?): MasterKeyMode? {
                return entries.firstOrNull { it.name == value }
            }
        }
    }

    private data class EncryptedBlob(
        val encryptedBytes: ByteArray,
        val iv: ByteArray
    )

    private companion object {
        private const val PREFS_NAME = "hrt_tracker_secure_storage"
        private const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
        private const val KEY_DATABASE_PASSPHRASE_IV = "database_passphrase_iv"
        private const val KEY_MASTER_KEY_MODE = "database_master_key_mode"
        private const val STANDARD_KEY_ALIAS = "hrt_tracker_database_master_key"
        private const val SCREEN_LOCK_KEY_ALIAS = "hrt_tracker_database_screen_lock_master_key"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PASSPHRASE_SIZE_BYTES = 32
        private const val SCREEN_LOCK_AUTH_VALIDITY_DURATION_SECONDS = 15
    }
}
