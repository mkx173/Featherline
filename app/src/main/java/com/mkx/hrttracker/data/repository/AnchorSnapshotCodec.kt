package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate

// The persisted cold-start seed for the Milestones timeline: the full tracked-dates
// list, so the screen can render real anchors before the SQLCipher database opens.
internal data class AnchorSnapshotRecord(
    val schemaVersion: Int,
    val trackedDates: List<TrackedDate>,
)

internal const val ANCHOR_SNAPSHOT_SCHEMA_VERSION = 1

// Field encoding mirrors HomeSnapshotStore.writeTrackedDate/readTrackedDate so the two
// stores can't drift on how a TrackedDate is serialized.
internal object AnchorSnapshotCodec {

    fun encode(record: AnchorSnapshotRecord): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeInt(ANCHOR_SNAPSHOT_CODEC_VERSION)
            stream.writeInt(record.schemaVersion)
            stream.writeInt(record.trackedDates.size)
            record.trackedDates.forEach { stream.writeTrackedDate(it) }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): AnchorSnapshotRecord {
        return DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val version = stream.readInt()
            require(version == ANCHOR_SNAPSHOT_CODEC_VERSION) {
                "Unsupported anchor snapshot codec version: $version."
            }
            val schemaVersion = stream.readInt()
            val size = stream.readInt()
            require(size >= 0) { "List size must not be negative." }
            AnchorSnapshotRecord(
                schemaVersion = schemaVersion,
                trackedDates = List(size) { stream.readTrackedDate() },
            )
        }
    }

    private fun DataOutputStream.writeTrackedDate(date: TrackedDate) {
        writeString(date.id)
        writeString(date.name)
        writeString(date.icon.storageKey)
        writeLong(date.date.toEpochDay())
        writeNullableString(date.palette?.name)
        writeNullableString(date.heroBackground.storageKey)
        writeBoolean(date.pinnedOrder != null)
        date.pinnedOrder?.let { writeInt(it) }
        writeLong(date.createdAtEpochMillis)
    }

    private fun DataInputStream.readTrackedDate(): TrackedDate = TrackedDate(
        id = readString(),
        name = readString(),
        icon = AnchorIcon.fromStorageValue(readString()),
        date = LocalDate.ofEpochDay(readLong()),
        palette = readNullableString()?.let(MedicationGroupColorKey::fromStorageValueOrNull),
        heroBackground = HeroBackground.fromStorageValue(readNullableString()),
        pinnedOrder = if (readBoolean()) readInt() else null,
        createdAtEpochMillis = readLong(),
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
}

private const val ANCHOR_SNAPSHOT_CODEC_VERSION = 1
