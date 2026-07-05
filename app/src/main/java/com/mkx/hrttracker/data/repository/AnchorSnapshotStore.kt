package com.mkx.hrttracker.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class AnchorSnapshotState(val record: AnchorSnapshotRecord?) {
    companion object {
        val Empty = AnchorSnapshotState(record = null)
    }
}

internal class AnchorSnapshotSerializer(
    private val crypto: AnchorSnapshotCrypto,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) : Serializer<AnchorSnapshotState> {
    override val defaultValue = AnchorSnapshotState.Empty

    override suspend fun readFrom(input: InputStream): AnchorSnapshotState {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return AnchorSnapshotState.Empty
        return runCatching {
            AnchorSnapshotState(record = AnchorSnapshotCodec.decode(crypto.decrypt(encrypted)))
        }.getOrElse { throwable ->
            diagnosticsLogger.warning(
                TAG,
                "anchor_snapshot_read_failed bytes=${encrypted.size}",
                throwable
            )
            AnchorSnapshotState.Empty
        }
    }

    override suspend fun writeTo(t: AnchorSnapshotState, output: OutputStream) {
        val record = t.record ?: return
        output.write(crypto.encrypt(AnchorSnapshotCodec.encode(record)))
    }
}

// ── Crypto ─────────────────────────────────────────────────────────────────────

internal interface AnchorSnapshotCrypto {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

private class AndroidAnchorSnapshotCrypto : AnchorSnapshotCrypto {
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
        require(magic.contentEquals(MAGIC)) { "Unsupported anchor snapshot container." }
        val version = buf.get().toInt() and BYTE_MASK
        require(version == CONTAINER_VERSION) { "Unsupported anchor snapshot container version: $version." }
        val ivLen = buf.get().toInt() and BYTE_MASK
        require(ivLen in 1..MAX_GCM_IV_LENGTH_BYTES && buf.remaining() > ivLen) { "Invalid anchor snapshot IV." }
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

internal val Context.anchorSnapshotDataStore: DataStore<AnchorSnapshotState> by dataStore(
    fileName = "anchor_snapshot.pb",
    serializer = AnchorSnapshotSerializer(AndroidAnchorSnapshotCrypto()),
    corruptionHandler = ReplaceFileCorruptionHandler { AnchorSnapshotState.Empty },
)

// ── Store ──────────────────────────────────────────────────────────────────────

// Cold-start seed for the Milestones timeline: the last-known tracked-dates list,
// readable before the SQLCipher database opens. Overwritten on every journal change
// (JournalRepository observer), so it also self-clears when all anchors are deleted.
@Singleton
class AnchorSnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    suspend fun read(): List<TrackedDate>? {
        val record = context.anchorSnapshotDataStore.data.first().record ?: return null
        if (record.schemaVersion != ANCHOR_SNAPSHOT_SCHEMA_VERSION) {
            diagnosticsLogger.warning(
                TAG,
                "anchor_snapshot_schema_mismatch expected=$ANCHOR_SNAPSHOT_SCHEMA_VERSION actual=${record.schemaVersion}"
            )
            return null
        }
        return record.trackedDates
    }

    // Best-effort by contract: a DataStore/keystore failure is logged and swallowed so no
    // caller can be failed by the snapshot (it is an optimization, never a source of truth).
    // Returns true on success; false lets the caller degrade a failed overwrite to a clear()
    // so the previous snapshot can't survive and seed stale/deleted anchors next cold start.
    suspend fun write(dates: List<TrackedDate>): Boolean =
        runCatching {
            context.anchorSnapshotDataStore.updateData {
                AnchorSnapshotState(AnchorSnapshotRecord(ANCHOR_SNAPSHOT_SCHEMA_VERSION, dates))
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            diagnosticsLogger.warning(
                TAG,
                "anchor_snapshot_write_failed count=${dates.size}",
                throwable
            )
        }.isSuccess

    // Best-effort clear, mirroring write's contract. Writes AnchorSnapshotState.Empty, whose
    // null record makes the serializer emit zero bytes (skipping the crypto path entirely),
    // so read() then returns null (no seed) — the safe degrade after a failed overwrite.
    suspend fun clear() {
        runCatching {
            context.anchorSnapshotDataStore.updateData { AnchorSnapshotState.Empty }
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            diagnosticsLogger.warning(TAG, "anchor_snapshot_clear_failed", throwable)
        }
    }
}

// ── Constants ──────────────────────────────────────────────────────────────────

private const val TAG = "AnchorSnapshotStore"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "hrt_anchor_snapshot_key"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_SIZE_BITS = 256
private const val GCM_TAG_LENGTH_BITS = 128
private const val CONTAINER_VERSION = 1
private const val VERSION_LENGTH_BYTES = 1
private const val IV_LENGTH_BYTES = 1
private const val MAX_GCM_IV_LENGTH_BYTES = 32
private const val BYTE_MASK = 0xff
private val MAGIC =
    byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte())
