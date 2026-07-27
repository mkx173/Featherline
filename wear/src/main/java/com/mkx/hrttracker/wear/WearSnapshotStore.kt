package com.mkx.hrttracker.wear

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mkx.hrttracker.wear.protocol.WearDoseSnapshot
import com.mkx.hrttracker.wear.protocol.WearProtocolCodec
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object WearSnapshotStore {
    fun read(context: Context): WearDoseSnapshot? {
        val encoded = preferences(context).getString(KEY_ENCRYPTED_SNAPSHOT, null) ?: return null
        return runCatching {
            val envelope = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(envelope)
            val ivSize = buffer.int
            require(ivSize in 12..32 && buffer.remaining() > ivSize)
            val iv = ByteArray(ivSize).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            WearProtocolCodec.decodeSnapshot(cipher.doFinal(ciphertext))
        }.getOrNull()
    }

    fun write(context: Context, snapshot: WearDoseSnapshot) {
        val encoded = runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(WearProtocolCodec.encodeSnapshot(snapshot))
            val envelope = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + ciphertext.size)
                .putInt(cipher.iv.size)
                .put(cipher.iv)
                .put(ciphertext)
                .array()
            Base64.encodeToString(envelope, Base64.NO_WRAP)
        }.getOrNull() ?: return

        preferences(context).edit()
            .putString(KEY_ENCRYPTED_SNAPSHOT, encoded)
            .apply()
    }

    fun observe(context: Context): Flow<WearDoseSnapshot?> = callbackFlow {
        val preferences = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ENCRYPTED_SNAPSHOT) {
                trySend(read(context))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(context))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
    }
}

private const val PREFERENCES_NAME = "featherline_wear_snapshot"
private const val KEY_ENCRYPTED_SNAPSHOT = "encrypted_snapshot"
private const val KEY_ALIAS = "featherline_wear_snapshot_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
