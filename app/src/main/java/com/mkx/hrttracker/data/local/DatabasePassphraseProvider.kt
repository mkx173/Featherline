package com.mkx.hrttracker.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
            val passphrase = decrypt(
                encryptedBytes = Base64.decode(encryptedPassphrase, Base64.NO_WRAP),
                iv = Base64.decode(iv, Base64.NO_WRAP)
            )

            try {
                cachePassphrase(passphrase)
                return passphrase.copyOf()
            } finally {
                passphrase.fill(0)
            }
        }

        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
        try {
            persistEncryptedBlob(encrypt(passphrase))
            cachePassphrase(passphrase)
            return passphrase.copyOf()
        } finally {
            passphrase.fill(0)
        }
    }

    private fun encrypt(clearBytes: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        }

        return EncryptedBlob(
            encryptedBytes = cipher.doFinal(clearBytes),
            iv = cipher.iv
        )
    }

    private fun decrypt(encryptedBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateMasterKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
        }

        return cipher.doFinal(encryptedBytes)
    }

    private fun persistEncryptedBlob(encryptedBlob: EncryptedBlob) {
        prefs.edit(commit = true) {
            putString(
                KEY_DATABASE_PASSPHRASE,
                Base64.encodeToString(encryptedBlob.encryptedBytes, Base64.NO_WRAP)
            )
            putString(
                KEY_DATABASE_PASSPHRASE_IV,
                Base64.encodeToString(encryptedBlob.iv, Base64.NO_WRAP)
            )
        }
    }

    private fun cachePassphrase(passphrase: ByteArray) {
        cachedPassphrase?.fill(0)
        cachedPassphrase = passphrase.copyOf()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
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

    private data class EncryptedBlob(
        val encryptedBytes: ByteArray,
        val iv: ByteArray
    )

    private companion object {
        private const val PREFS_NAME = "hrt_tracker_secure_storage"
        private const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
        private const val KEY_DATABASE_PASSPHRASE_IV = "database_passphrase_iv"
        private const val MASTER_KEY_ALIAS = "hrt_tracker_database_master_key"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PASSPHRASE_SIZE_BYTES = 32
    }
}
