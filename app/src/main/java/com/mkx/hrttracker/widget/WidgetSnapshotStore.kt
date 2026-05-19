package com.mkx.hrttracker.widget

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkDoseMarker
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// ── Public data model ──────────────────────────────────────────────────────────

enum class WidgetDoseStatus { DONE, DUE_SOON, OVERDUE, UPCOMING, LOGGED_OUT_OF_WINDOW }

enum class WidgetDoseChip { LAST_NIGHT, COMING_UP }

data class WidgetDoseRow(
    val medicationName: String,
    val groupName: String,
    val colorKey: MedicationGroupColorKey?,   // null for manual records
    val routeLabel: String,
    val doseText: String,
    val status: WidgetDoseStatus,
    val scheduledAt: LocalDateTime,
    val trailingText: String?,
    val isManualRecord: Boolean,
    val contextChip: WidgetDoseChip?,
    val groupUuid: String?,                   // non-null only for DUE_SOON/OVERDUE non-manual
    val scheduleTimeUuid: String?,
    val medicationUuid: String? = null,       // non-null for scheduled non-manual rows
)

data class WidgetPkDoseMarkerRecord(
    val timeH: Double,
    val concentration: Double,
    val isPlanned: Boolean,
)

data class WidgetPkProjectionRecord(
    val generatedAtEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val pkProjectionExpiresAtEpochMillis: Long,
    val concentrationUnit: String,
    val timeH: List<Double>,
    val concentrations: List<Double>,
    val doseMarkers: List<WidgetPkDoseMarkerRecord>,
) {
    fun toPkProjectionResult(
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PkProjectionResult? {
        val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
        if (nowEpochMillis >= pkProjectionExpiresAtEpochMillis) return null
        return runCatching {
            PkProjectionResult(
                generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
                windowStart = Instant.ofEpochMilli(windowStartEpochMillis),
                windowEnd = Instant.ofEpochMilli(windowEndEpochMillis),
                concentrationUnit = PkConcentrationUnit.valueOf(concentrationUnit),
                timeH = timeH,
                concentrations = concentrations,
                doseMarkers = doseMarkers.map {
                    PkDoseMarker(timeH = it.timeH, concentration = it.concentration, isPlanned = it.isPlanned)
                },
            )
        }.getOrNull()
    }
}

data class WidgetSnapshotRecord(
    val schemaVersion: Int,
    val zoneId: String,
    val doneCount: Int,
    val totalCount: Int,
    val manualCount: Int,
    val hideMedicationDetails: Boolean,
    val adaptiveColorEnabled: Boolean,
    val widgetContentScale: Float,
    val widgetBackgroundAlpha: Float,
    val doseRows: List<WidgetDoseRow>,
    val pkProjection: WidgetPkProjectionRecord?,
)

// ── Internal storage plumbing ──────────────────────────────────────────────────

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
            diagnosticsLogger.warning(TAG, "widget_snapshot_read_failed bytes=${encrypted.size}", throwable)
            WidgetSnapshotState.Empty
        }
    }

    override suspend fun writeTo(t: WidgetSnapshotState, output: OutputStream) {
        val record = t.record ?: return
        output.write(crypto.encrypt(WidgetSnapshotCodec.encode(record)))
    }
}

internal object WidgetSnapshotCodec {

    fun encode(record: WidgetSnapshotRecord): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { s ->
            s.writeInt(WIDGET_SNAPSHOT_CODEC_VERSION)
            s.writeInt(record.schemaVersion)
            s.writeString(record.zoneId)
            s.writeInt(record.doneCount)
            s.writeInt(record.totalCount)
            s.writeInt(record.manualCount)
            s.writeBoolean(record.hideMedicationDetails)
            s.writeBoolean(record.adaptiveColorEnabled)
            s.writeFloat(record.widgetContentScale)
            s.writeFloat(record.widgetBackgroundAlpha)
            s.writeList(record.doseRows) { writeDoseRow(it) }
            s.writePkProjection(record.pkProjection)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): WidgetSnapshotRecord {
        return DataInputStream(ByteArrayInputStream(bytes)).use { s ->
            val version = s.readInt()
            require(version == WIDGET_SNAPSHOT_CODEC_VERSION) {
                "Unsupported widget snapshot codec version: $version."
            }
            WidgetSnapshotRecord(
                schemaVersion = s.readInt(),
                zoneId = s.readString(),
                doneCount = s.readInt(),
                totalCount = s.readInt(),
                manualCount = s.readInt(),
                hideMedicationDetails = s.readBoolean(),
                adaptiveColorEnabled = s.readBoolean(),
                widgetContentScale = s.readFloat(),
                widgetBackgroundAlpha = s.readFloat(),
                doseRows = s.readList { readDoseRow() },
                pkProjection = s.readPkProjection(),
            )
        }
    }

    private fun DataOutputStream.writeDoseRow(row: WidgetDoseRow) {
        writeString(row.medicationName)
        writeString(row.groupName)
        writeBoolean(row.colorKey != null)
        row.colorKey?.let { writeString(it.name) }
        writeString(row.routeLabel)
        writeString(row.doseText)
        writeByte(row.status.ordinal)
        writeLocalDateTime(row.scheduledAt)
        writeNullableString(row.trailingText)
        writeBoolean(row.isManualRecord)
        writeBoolean(row.contextChip != null)
        row.contextChip?.let { writeByte(it.ordinal) }
        writeNullableString(row.groupUuid)
        writeNullableString(row.scheduleTimeUuid)
        writeNullableString(row.medicationUuid)
    }

    private fun DataInputStream.readDoseRow(): WidgetDoseRow = WidgetDoseRow(
        medicationName = readString(),
        groupName = readString(),
        colorKey = if (readBoolean()) MedicationGroupColorKey.fromStorageValue(readString()) else null,
        routeLabel = readString(),
        doseText = readString(),
        status = WidgetDoseStatus.entries[readByte().toInt() and 0xff],
        scheduledAt = readLocalDateTime(),
        trailingText = readNullableString(),
        isManualRecord = readBoolean(),
        contextChip = if (readBoolean()) WidgetDoseChip.entries[readByte().toInt() and 0xff] else null,
        groupUuid = readNullableString(),
        scheduleTimeUuid = readNullableString(),
        medicationUuid = readNullableString(),
    )

    private fun DataOutputStream.writePkProjection(rec: WidgetPkProjectionRecord?) {
        writeBoolean(rec != null)
        rec ?: return
        writeLong(rec.generatedAtEpochMillis)
        writeLong(rec.windowStartEpochMillis)
        writeLong(rec.windowEndEpochMillis)
        writeLong(rec.pkProjectionExpiresAtEpochMillis)
        writeString(rec.concentrationUnit)
        writeList(rec.timeH) { writeDouble(it) }
        writeList(rec.concentrations) { writeDouble(it) }
        writeList(rec.doseMarkers) { m ->
            writeDouble(m.timeH)
            writeDouble(m.concentration)
            writeBoolean(m.isPlanned)
        }
    }

    private fun DataInputStream.readPkProjection(): WidgetPkProjectionRecord? {
        if (!readBoolean()) return null
        return WidgetPkProjectionRecord(
            generatedAtEpochMillis = readLong(),
            windowStartEpochMillis = readLong(),
            windowEndEpochMillis = readLong(),
            pkProjectionExpiresAtEpochMillis = readLong(),
            concentrationUnit = readString(),
            timeH = readList { readDouble() },
            concentrations = readList { readDouble() },
            doseMarkers = readList { WidgetPkDoseMarkerRecord(readDouble(), readDouble(), readBoolean()) },
        )
    }

    private fun DataOutputStream.writeLocalDateTime(dt: LocalDateTime) {
        writeInt(dt.year)
        writeByte(dt.monthValue)
        writeByte(dt.dayOfMonth)
        writeByte(dt.hour)
        writeByte(dt.minute)
        writeByte(dt.second)
    }

    private fun DataInputStream.readLocalDateTime(): LocalDateTime = LocalDateTime.of(
        readInt(),
        readByte().toInt() and 0xff,
        readByte().toInt() and 0xff,
        readByte().toInt() and 0xff,
        readByte().toInt() and 0xff,
        readByte().toInt() and 0xff,
    )

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size >= 0) { "String size must not be negative." }
        return ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        value?.let { writeString(it) }
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readString() else null

    private fun <T> DataOutputStream.writeList(values: List<T>, write: DataOutputStream.(T) -> Unit) {
        writeInt(values.size)
        values.forEach { write(it) }
    }

    private fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> {
        val size = readInt()
        require(size >= 0) { "List size must not be negative." }
        val stream = this
        val result = ArrayList<T>(size)
        repeat(size) { result.add(stream.readItem()) }
        return result
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
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(enc)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        gen.init(
            KeyGenParameterSpec.Builder(MASTER_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
                    diagnosticsLogger.warning(TAG, "widget_snapshot_schema_mismatch expected=$WIDGET_SNAPSHOT_SCHEMA_VERSION actual=${state.record.schemaVersion}")
                }
                null
            }
        }.distinctUntilChanged()

    suspend fun readSnapshot(): WidgetSnapshotRecord? {
        val record = context.widgetSnapshotDataStore.data.first().record ?: return null
        if (record.schemaVersion != WIDGET_SNAPSHOT_SCHEMA_VERSION) {
            diagnosticsLogger.warning(TAG, "widget_snapshot_schema_mismatch expected=$WIDGET_SNAPSHOT_SCHEMA_VERSION actual=${record.schemaVersion}")
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

internal const val WIDGET_SNAPSHOT_SCHEMA_VERSION = 7
private const val TAG = "WidgetSnapshotStore"
private const val WIDGET_SNAPSHOT_CODEC_VERSION = 6
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
private val MAGIC = byteArrayOf('W'.code.toByte(), 'D'.code.toByte(), 'G'.code.toByte(), 'T'.code.toByte())
