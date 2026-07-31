package com.mkx.hrttracker.wear.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

const val WEAR_SNAPSHOT_PATH = "/featherline/dose-snapshot"
const val WEAR_REQUEST_SNAPSHOT_PATH = "/featherline/request-dose-snapshot"
const val WEAR_LOG_DOSE_PATH = "/featherline/log-dose"
const val WEAR_PAYLOAD_KEY = "payload"
const val WEAR_UPDATED_AT_KEY = "updated_at"

enum class WearDoseStatus {
    DONE,
    DUE_SOON,
    OVERDUE,
    UPCOMING,
    LOGGED_OUT_OF_WINDOW,
}

data class WearDoseRow(
    val medicationName: String,
    val groupName: String,
    val routeLabel: String,
    val doseText: String,
    val status: WearDoseStatus,
    val scheduledAt: String,
    val trailingText: String?,
    val groupUuid: String?,
    val scheduleTimeUuid: String?,
)

data class WearDoseSnapshot(
    val generatedAtEpochMillis: Long,
    val zoneId: String,
    val anchorDateEpochDay: Long,
    val doneCount: Int,
    val totalCount: Int,
    val hideMedicationDetails: Boolean,
    val appLanguageTag: String,
    val rows: List<WearDoseRow>,
    val estradiol: WearEstradiolSnapshot? = null,
)

data class WearEstradiolSnapshot(
    val currentValueText: String,
    val unitLabel: String,
    val samples: List<Double>,
    val sampleIntervalMinutes: Int,
)

data class WearLogDoseCommand(
    val requestId: String,
    val groupUuid: String,
    val scheduleTimeUuid: String?,
    val scheduledAt: String,
)

object WearProtocolCodec {
    fun encodeSnapshot(snapshot: WearDoseSnapshot): ByteArray =
        encode { stream ->
            stream.writeInt(SNAPSHOT_PROTOCOL_VERSION)
            stream.writeLong(snapshot.generatedAtEpochMillis)
            stream.writeBoundedString(snapshot.zoneId)
            stream.writeLong(snapshot.anchorDateEpochDay)
            stream.writeInt(snapshot.doneCount)
            stream.writeInt(snapshot.totalCount)
            stream.writeBoolean(snapshot.hideMedicationDetails)
            stream.writeBoundedString(snapshot.appLanguageTag)
            require(snapshot.rows.size <= MAX_ROWS) { "Too many Wear dose rows." }
            stream.writeInt(snapshot.rows.size)
            snapshot.rows.forEach { row ->
                stream.writeBoundedString(row.medicationName)
                stream.writeBoundedString(row.groupName)
                stream.writeBoundedString(row.routeLabel)
                stream.writeBoundedString(row.doseText)
                stream.writeByte(row.status.ordinal)
                stream.writeBoundedString(row.scheduledAt)
                stream.writeNullableString(row.trailingText)
                stream.writeNullableString(row.groupUuid)
                stream.writeNullableString(row.scheduleTimeUuid)
            }
            stream.writeEstradiolSnapshot(snapshot.estradiol)
        }

    fun decodeSnapshot(bytes: ByteArray): WearDoseSnapshot =
        decode(bytes) { stream ->
            val version = stream.readSnapshotVersion()
            WearDoseSnapshot(
                generatedAtEpochMillis = stream.readLong(),
                zoneId = stream.readBoundedString(),
                anchorDateEpochDay = stream.readLong(),
                doneCount = stream.readInt(),
                totalCount = stream.readInt(),
                hideMedicationDetails = stream.readBoolean(),
                appLanguageTag = stream.readBoundedString(),
                rows = List(stream.readBoundedCount(MAX_ROWS)) {
                    WearDoseRow(
                        medicationName = stream.readBoundedString(),
                        groupName = stream.readBoundedString(),
                        routeLabel = stream.readBoundedString(),
                        doseText = stream.readBoundedString(),
                        status = WearDoseStatus.entries[
                            stream.readUnsignedByte().also {
                                require(it < WearDoseStatus.entries.size) {
                                    "Unknown Wear dose status."
                                }
                            }
                        ],
                        scheduledAt = stream.readBoundedString(),
                        trailingText = stream.readNullableString(),
                        groupUuid = stream.readNullableString(),
                        scheduleTimeUuid = stream.readNullableString(),
                    )
                },
                estradiol = if (version >= SNAPSHOT_PROTOCOL_VERSION_WITH_ESTRADIOL) {
                    stream.readEstradiolSnapshot()
                } else {
                    null
                },
            )
        }

    fun encodeLogDoseCommand(command: WearLogDoseCommand): ByteArray =
        encode { stream ->
            stream.writeInt(COMMAND_PROTOCOL_VERSION)
            stream.writeBoundedString(command.requestId)
            stream.writeBoundedString(command.groupUuid)
            stream.writeNullableString(command.scheduleTimeUuid)
            stream.writeBoundedString(command.scheduledAt)
        }

    fun decodeLogDoseCommand(bytes: ByteArray): WearLogDoseCommand =
        decode(bytes) { stream ->
            stream.requireCommandVersion()
            WearLogDoseCommand(
                requestId = stream.readBoundedString(),
                groupUuid = stream.readBoundedString(),
                scheduleTimeUuid = stream.readNullableString(),
                scheduledAt = stream.readBoundedString(),
            )
        }

    private fun encode(write: (DataOutputStream) -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use(write)
        return output.toByteArray().also {
            require(it.size <= MAX_PAYLOAD_BYTES) { "Wear payload is too large." }
        }
    }

    private fun <T> decode(bytes: ByteArray, read: (DataInputStream) -> T): T {
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "Wear payload is too large." }
        return DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
            read(stream).also {
                require(stream.available() == 0) { "Wear payload contains trailing data." }
            }
        }
    }

    private fun DataInputStream.readSnapshotVersion(): Int =
        readInt().also { version ->
            require(version in MIN_SNAPSHOT_PROTOCOL_VERSION..SNAPSHOT_PROTOCOL_VERSION) {
                "Unsupported Wear snapshot protocol version."
            }
        }

    private fun DataInputStream.requireCommandVersion() {
        require(readInt() == COMMAND_PROTOCOL_VERSION) {
            "Unsupported Wear command protocol version."
        }
    }

    private fun DataOutputStream.writeBoundedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Wear protocol string is too large." }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBoundedString(): String {
        val size = readBoundedCount(MAX_STRING_BYTES)
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        value?.let { writeBoundedString(it) }
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readBoundedString() else null

    private fun DataOutputStream.writeEstradiolSnapshot(snapshot: WearEstradiolSnapshot?) {
        writeBoolean(snapshot != null)
        snapshot ?: return
        require(snapshot.sampleIntervalMinutes in 1..MAX_SAMPLE_INTERVAL_MINUTES) {
            "Wear estradiol sample interval is invalid."
        }
        require(snapshot.samples.size in 1..MAX_ESTRADIOL_SAMPLES) {
            "Wear estradiol sample count is invalid."
        }
        writeBoundedString(snapshot.currentValueText)
        writeBoundedString(snapshot.unitLabel)
        writeInt(snapshot.sampleIntervalMinutes)
        writeInt(snapshot.samples.size)
        snapshot.samples.forEach { concentration ->
            require(concentration.isFinite() && concentration >= 0.0) {
                "Wear estradiol concentration is invalid."
            }
            writeDouble(concentration)
        }
    }

    private fun DataInputStream.readEstradiolSnapshot(): WearEstradiolSnapshot? {
        if (!readBoolean()) return null
        val currentValueText = readBoundedString()
        val unitLabel = readBoundedString()
        val sampleIntervalMinutes = readInt().also { interval ->
            require(interval in 1..MAX_SAMPLE_INTERVAL_MINUTES) {
                "Wear estradiol sample interval is invalid."
            }
        }
        val samples = List(readBoundedCount(MAX_ESTRADIOL_SAMPLES)) {
            readDouble().also { concentration ->
                require(concentration.isFinite() && concentration >= 0.0) {
                    "Wear estradiol concentration is invalid."
                }
            }
        }
        require(samples.isNotEmpty()) { "Wear estradiol samples are empty." }
        return WearEstradiolSnapshot(
            currentValueText = currentValueText,
            unitLabel = unitLabel,
            samples = samples,
            sampleIntervalMinutes = sampleIntervalMinutes,
        )
    }

    private fun DataInputStream.readBoundedCount(maximum: Int): Int =
        readInt().also { require(it in 0..maximum) { "Wear payload count is invalid." } }
}

private const val MIN_SNAPSHOT_PROTOCOL_VERSION = 1
private const val SNAPSHOT_PROTOCOL_VERSION_WITH_ESTRADIOL = 2
private const val SNAPSHOT_PROTOCOL_VERSION = 2
private const val COMMAND_PROTOCOL_VERSION = 1
private const val MAX_ROWS = 64
private const val MAX_ESTRADIOL_SAMPLES = 97
private const val MAX_SAMPLE_INTERVAL_MINUTES = 24 * 60
private const val MAX_STRING_BYTES = 4_096
private const val MAX_PAYLOAD_BYTES = 256 * 1_024
