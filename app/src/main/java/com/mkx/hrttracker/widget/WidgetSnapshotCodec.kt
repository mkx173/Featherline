package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDateTime

internal object WidgetSnapshotCodec {

    fun encode(record: WidgetSnapshotRecord): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeInt(WIDGET_SNAPSHOT_CODEC_VERSION)
            stream.writeInt(record.schemaVersion)
            stream.writeString(record.zoneId)
            stream.writeLong(record.anchorDateEpochDay)
            stream.writeInt(record.doneCount)
            stream.writeInt(record.totalCount)
            stream.writeInt(record.manualCount)
            stream.writeBoolean(record.hasActiveGroups)
            stream.writeBoolean(record.hideMedicationDetails)
            stream.writeBoolean(record.adaptiveColorEnabled)
            stream.writeString(record.e2DisplayUnit)
            stream.writeString(record.appLanguageTag)
            stream.writeList(record.doseRows) { writeDoseRow(it) }
            stream.writePkProjection(record.pkProjection)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): WidgetSnapshotRecord {
        return DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val version = stream.readInt()
            require(version == WIDGET_SNAPSHOT_CODEC_VERSION) {
                "Unsupported widget snapshot codec version: $version."
            }
            WidgetSnapshotRecord(
                schemaVersion = stream.readInt(),
                zoneId = stream.readString(),
                anchorDateEpochDay = stream.readLong(),
                doneCount = stream.readInt(),
                totalCount = stream.readInt(),
                manualCount = stream.readInt(),
                hasActiveGroups = stream.readBoolean(),
                hideMedicationDetails = stream.readBoolean(),
                adaptiveColorEnabled = stream.readBoolean(),
                e2DisplayUnit = stream.readString(),
                appLanguageTag = stream.readString(),
                doseRows = stream.readList { readDoseRow() },
                pkProjection = stream.readPkProjection(),
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
        writeBoolean(row.isFromArchivedGroup)
        writeBoolean(row.contextChip != null)
        row.contextChip?.let { writeByte(it.ordinal) }
        writeNullableString(row.groupUuid)
        writeNullableString(row.scheduleTimeUuid)
        writeNullableString(row.medicationUuid)
        writeNullableString(row.entryUuid)
    }

    private fun DataInputStream.readDoseRow(): WidgetDoseRow = WidgetDoseRow(
        medicationName = readString(),
        groupName = readString(),
        colorKey = if (readBoolean()) MedicationGroupColorKey.fromStorageValue(readString()) else null,
        routeLabel = readString(),
        doseText = readString(),
        status = WidgetDoseStatus.entries[readByte().toInt() and BYTE_MASK],
        scheduledAt = readLocalDateTime(),
        trailingText = readNullableString(),
        isManualRecord = readBoolean(),
        isFromArchivedGroup = readBoolean(),
        contextChip = if (readBoolean()) WidgetDoseChip.entries[readByte().toInt() and BYTE_MASK] else null,
        groupUuid = readNullableString(),
        scheduleTimeUuid = readNullableString(),
        medicationUuid = readNullableString(),
        entryUuid = readNullableString(),
    )

    private fun DataOutputStream.writePkProjection(record: WidgetPkProjectionRecord?) {
        writeBoolean(record != null)
        record ?: return
        writeLong(record.generatedAtEpochMillis)
        writeLong(record.windowStartEpochMillis)
        writeLong(record.windowEndEpochMillis)
        writeLong(record.pkProjectionExpiresAtEpochMillis)
        writeString(record.concentrationUnit)
        writeList(record.timeH) { writeDouble(it) }
        writeList(record.concentrations) { writeDouble(it) }
        writeList(record.doseMarkers) { marker ->
            writeDouble(marker.timeH)
            writeDouble(marker.concentration)
            writeBoolean(marker.isPlanned)
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
            doseMarkers = readList {
                WidgetPkDoseMarkerRecord(
                    timeH = readDouble(),
                    concentration = readDouble(),
                    isPlanned = readBoolean(),
                )
            },
        )
    }

    private fun DataOutputStream.writeLocalDateTime(dateTime: LocalDateTime) {
        writeInt(dateTime.year)
        writeByte(dateTime.monthValue)
        writeByte(dateTime.dayOfMonth)
        writeByte(dateTime.hour)
        writeByte(dateTime.minute)
        writeByte(dateTime.second)
    }

    private fun DataInputStream.readLocalDateTime(): LocalDateTime = LocalDateTime.of(
        readInt(),
        readByte().toInt() and BYTE_MASK,
        readByte().toInt() and BYTE_MASK,
        readByte().toInt() and BYTE_MASK,
        readByte().toInt() and BYTE_MASK,
        readByte().toInt() and BYTE_MASK,
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

    private fun <T> DataOutputStream.writeList(
        values: List<T>,
        write: DataOutputStream.(T) -> Unit
    ) {
        writeInt(values.size)
        values.forEach { write(it) }
    }

    private fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> {
        val size = readInt()
        require(size >= 0) { "List size must not be negative." }
        val stream = this
        return List(size) { stream.readItem() }
    }
}

private const val WIDGET_SNAPSHOT_CODEC_VERSION = 14
private const val BYTE_MASK = 0xff
