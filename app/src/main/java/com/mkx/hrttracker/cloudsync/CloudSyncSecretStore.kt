package com.mkx.hrttracker.cloudsync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncSecretStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun storePassword(password: String) {
        val clearBytes = password.toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            val encrypted = cipher.doFinal(clearBytes)
            try {
                check(
                    preferences.edit()
                        .putString(KEY_PASSWORD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                        .putString(KEY_PASSWORD_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .commit()
                ) { "Unable to store cloud sync password." }
            } finally {
                encrypted.fill(0)
            }
        } finally {
            clearBytes.fill(0)
        }
    }

    fun loadPassword(): String? {
        val encryptedBase64 = preferences.getString(KEY_PASSWORD, null) ?: return null
        val ivBase64 = preferences.getString(KEY_PASSWORD_IV, null) ?: return null
        val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val clearBytes = try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
                )
                doFinal(encrypted)
            }
        } finally {
            encrypted.fill(0)
            iv.fill(0)
        }
        return try {
            String(clearBytes, Charsets.UTF_8)
        } finally {
            clearBytes.fill(0)
        }
    }

    fun clear() {
        preferences.edit().remove(KEY_PASSWORD).remove(KEY_PASSWORD_IV).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "hrt_tracker_secure_storage"
        const val KEY_PASSWORD = "cloud_sync_password"
        const val KEY_PASSWORD_IV = "cloud_sync_password_iv"
        const val KEY_ALIAS = "hrt_tracker_cloud_sync_master_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
