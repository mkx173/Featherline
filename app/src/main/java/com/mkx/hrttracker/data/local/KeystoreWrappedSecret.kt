package com.mkx.hrttracker.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A small secret stored in prefs, encrypted under a Keystore key. Seam for JVM tests. */
interface WrappedSecret {
    /** The clear bytes, or null when nothing is stored. The caller zeroes them. */
    fun read(): ByteArray?

    fun write(clearBytes: ByteArray)

    fun clear()
}

/**
 * Stores [blobPrefKey]/[ivPrefKey] in [PREFS_NAME] as Base64, AES-256-GCM
 * encrypted under the Keystore key [keyAlias] (created on first use, no user
 * authentication). Several instances may share one alias.
 */
class KeystoreWrappedSecret(
    private val context: Context,
    internal val keyAlias: String,
    internal val blobPrefKey: String,
    internal val ivPrefKey: String,
) : WrappedSecret {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun read(): ByteArray? {
        val encryptedBytes = prefs.getString(blobPrefKey, null)
        val iv = prefs.getString(ivPrefKey, null)
        if (encryptedBytes == null || iv == null) return null

        return decrypt(
            encryptedBytes = Base64.decode(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.decode(iv, Base64.NO_WRAP)
        )
    }

    override fun write(clearBytes: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        }
        val encryptedBytes = cipher.doFinal(clearBytes)

        val committed = prefs.edit()
            .putString(blobPrefKey, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
            .putString(ivPrefKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        check(committed) { "Failed to persist $blobPrefKey." }
    }

    override fun clear() {
        val committed = prefs.edit()
            .remove(blobPrefKey)
            .remove(ivPrefKey)
            .commit()
        check(committed) { "Failed to clear $blobPrefKey." }
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

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
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

    internal companion object {
        internal const val PREFS_NAME = "hrt_tracker_secure_storage"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
