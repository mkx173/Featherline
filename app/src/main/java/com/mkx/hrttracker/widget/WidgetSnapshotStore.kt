package com.mkx.hrttracker.widget

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal data class WidgetSnapshotState(val record: WidgetSnapshotRecord?) {
    companion object {
        val Empty = WidgetSnapshotState(record = null)
    }
}

internal class WidgetSnapshotSerializer(
    private val crypto: WidgetSnapshotCrypto,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) : Serializer<WidgetSnapshotState> {
    override val defaultValue = WidgetSnapshotState.Empty

    override suspend fun readFrom(input: InputStream): WidgetSnapshotState {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return WidgetSnapshotState.Empty
        return runCatching {
            WidgetSnapshotState(record = WidgetSnapshotCodec.decode(crypto.decrypt(encrypted)))
        }.getOrElse { throwable ->
            diagnosticsLogger.warning(
                TAG,
                "widget_snapshot_read_failed bytes=${encrypted.size}",
                throwable
            )
            WidgetSnapshotState.Empty
        }
    }

    override suspend fun writeTo(t: WidgetSnapshotState, output: OutputStream) {
        val record = t.record ?: return
        output.write(crypto.encrypt(WidgetSnapshotCodec.encode(record)))
    }
}

// ── Crypto ─────────────────────────────────────────────────────────────────────

internal interface WidgetSnapshotCrypto {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

private class AndroidWidgetSnapshotCrypto : WidgetSnapshotCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return ByteBuffer
            .allocate(MAGIC.size + VERSION_LENGTH_BYTES + IV_LENGTH_BYTES + iv.size + encrypted.size)
            .put(MAGIC).put(CONTAINER_VERSION.toByte()).put(iv.size.toByte()).put(iv).put(encrypted)
            .array()
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(ciphertext)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        require(magic.contentEquals(MAGIC)) { "Unsupported widget snapshot container." }
        val version = buf.get().toInt() and BYTE_MASK
        require(version == CONTAINER_VERSION) { "Unsupported widget snapshot container version: $version." }
        val ivLen = buf.get().toInt() and BYTE_MASK
        require(ivLen in 1..MAX_GCM_IV_LENGTH_BYTES && buf.remaining() > ivLen) { "Invalid widget snapshot IV." }
        val iv = ByteArray(ivLen).also { buf.get(it) }
        val enc = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(enc)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(AES_KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}

// ── DataStore extension ────────────────────────────────────────────────────────

internal val Context.widgetSnapshotDataStore: DataStore<WidgetSnapshotState> by dataStore(
    fileName = "widget_snapshot.pb",
    serializer = WidgetSnapshotSerializer(AndroidWidgetSnapshotCrypto()),
    corruptionHandler = ReplaceFileCorruptionHandler { WidgetSnapshotState.Empty },
)

// ── Store ──────────────────────────────────────────────────────────────────────

@Singleton
class WidgetSnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    fun observeSnapshot(): Flow<WidgetSnapshotRecord?> =
        context.widgetSnapshotDataStore.data.map { state ->
            state.record?.takeIf { it.schemaVersion == WIDGET_SNAPSHOT_SCHEMA_VERSION } ?: run {
                if (state.record != null) {
                    diagnosticsLogger.warning(
                        TAG,
                        "widget_snapshot_schema_mismatch expected=$WIDGET_SNAPSHOT_SCHEMA_VERSION actual=${state.record.schemaVersion}"
                    )
                }
                null
            }
        }.distinctUntilChanged()

    suspend fun readSnapshot(): WidgetSnapshotRecord? {
        val record = context.widgetSnapshotDataStore.data.first().record ?: return null
        if (record.schemaVersion != WIDGET_SNAPSHOT_SCHEMA_VERSION) {
            diagnosticsLogger.warning(
                TAG,
                "widget_snapshot_schema_mismatch expected=$WIDGET_SNAPSHOT_SCHEMA_VERSION actual=${record.schemaVersion}"
            )
            return null
        }
        return record
    }

    suspend fun writeSnapshot(record: WidgetSnapshotRecord) {
        context.widgetSnapshotDataStore.updateData { WidgetSnapshotState(record) }
    }

    suspend fun clearSnapshot() {
        context.widgetSnapshotDataStore.updateData { WidgetSnapshotState.Empty }
    }
}

// ── Constants ──────────────────────────────────────────────────────────────────

internal const val WIDGET_SNAPSHOT_SCHEMA_VERSION = 16
private const val TAG = "WidgetSnapshotStore"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "hrt_widget_snapshot_key"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_SIZE_BITS = 256
private const val GCM_TAG_LENGTH_BITS = 128
private const val CONTAINER_VERSION = 1
private const val VERSION_LENGTH_BYTES = 1
private const val IV_LENGTH_BYTES = 1
private const val MAX_GCM_IV_LENGTH_BYTES = 32
private const val BYTE_MASK = 0xff
private val MAGIC =
    byteArrayOf('W'.code.toByte(), 'D'.code.toByte(), 'G'.code.toByte(), 'T'.code.toByte())
