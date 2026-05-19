package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDateTime

class WidgetSnapshotCodecTest {

    private val baseRecord = WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = "UTC",
        doneCount = 1,
        totalCount = 3,
        manualCount = 1,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        doseRows = listOf(
            WidgetDoseRow(
                medicationName = "Estradiol",
                groupName = "Morning group",
                colorKey = MedicationGroupColorKey.ROSE,
                routeLabel = "Patch",
                doseText = "0.1 mg",
                status = WidgetDoseStatus.DONE,
                scheduledAt = LocalDateTime.of(2026, 5, 18, 8, 0, 0),
                trailingText = null,
                isManualRecord = false,
                contextChip = null,
                groupUuid = null,
                scheduleTimeUuid = null,
            ),
            WidgetDoseRow(
                medicationName = "Progesterone",
                groupName = "Evening group",
                colorKey = MedicationGroupColorKey.VIOLET,
                routeLabel = "Oral",
                doseText = "100 mg",
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = LocalDateTime.of(2026, 5, 18, 20, 0, 0),
                trailingText = "20:00",
                isManualRecord = false,
                contextChip = null,
                groupUuid = "123e4567-e89b-12d3-a456-426614174000",
                scheduleTimeUuid = "223e4567-e89b-12d3-a456-426614174001",
            ),
            WidgetDoseRow(
                medicationName = "Estradiol",
                groupName = "",
                colorKey = null,
                routeLabel = "Injection",
                doseText = "5 mg",
                status = WidgetDoseStatus.DONE,
                scheduledAt = LocalDateTime.of(2026, 5, 18, 9, 0, 0),
                trailingText = "Manual",
                isManualRecord = true,
                contextChip = null,
                groupUuid = null,
                scheduleTimeUuid = null,
            ),
            WidgetDoseRow(
                medicationName = "Estradiol",
                groupName = "Morning group",
                colorKey = MedicationGroupColorKey.SKY,
                routeLabel = "Patch",
                doseText = "0.1 mg",
                status = WidgetDoseStatus.UPCOMING,
                scheduledAt = LocalDateTime.of(2026, 5, 19, 8, 0, 0),
                trailingText = "08:00",
                isManualRecord = false,
                contextChip = WidgetDoseChip.COMING_UP,
                groupUuid = null,
                scheduleTimeUuid = null,
            ),
        ),
        pkProjection = WidgetPkProjectionRecord(
            generatedAtEpochMillis = 1_747_540_000_000L,
            windowStartEpochMillis = 1_746_935_200_000L,
            windowEndEpochMillis = 1_748_145_200_000L,
            pkProjectionExpiresAtEpochMillis = Long.MAX_VALUE,
            concentrationUnit = "PG_PER_ML",
            timeH = listOf(0.0, 1.0, 2.0),
            concentrations = listOf(120.0, 115.0, 110.0),
            doseMarkers = listOf(WidgetPkDoseMarkerRecord(timeH = 0.5, concentration = 118.0, isPlanned = false)),
        ),
    )

    @Test
    fun `codec round-trips a full record`() {
        val bytes = WidgetSnapshotCodec.encode(baseRecord)
        val decoded = WidgetSnapshotCodec.decode(bytes)
        assertEquals(baseRecord, decoded)
    }

    @Test
    fun `codec round-trips a record with null projection and empty rows`() {
        val minimal = baseRecord.copy(pkProjection = null, doseRows = emptyList())
        assertEquals(minimal, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(minimal)))
    }

    @Test
    fun `codec round-trips a LAST_NIGHT chip row`() {
        val row = baseRecord.doseRows.first().copy(
            contextChip = WidgetDoseChip.LAST_NIGHT,
            trailingText = "2h later",
        )
        val record = baseRecord.copy(doseRows = listOf(row))
        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `codec round-trips a manual row with null colorKey`() {
        val row = WidgetDoseRow(
            medicationName = "Estradiol",
            groupName = "",
            colorKey = null,
            routeLabel = "Injection",
            doseText = "",
            status = WidgetDoseStatus.DONE,
            scheduledAt = LocalDateTime.of(2026, 5, 18, 9, 0, 0),
            trailingText = "Manual",
            isManualRecord = true,
            contextChip = null,
            groupUuid = null,
            scheduleTimeUuid = null,
        )
        val record = baseRecord.copy(doseRows = listOf(row))
        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `codec round-trips hideMedicationDetails=true`() {
        val record = baseRecord.copy(hideMedicationDetails = true)
        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `decode throws on wrong codec version`() {
        val bytes = WidgetSnapshotCodec.encode(baseRecord).copyOf()
        bytes[0] = 99.toByte()
        assertThrows(IllegalArgumentException::class.java) { WidgetSnapshotCodec.decode(bytes) }
    }

    @Test
    fun `WidgetSnapshotSerializer returns empty state on corrupt bytes`() {
        val serializer = WidgetSnapshotSerializer(PassthroughWidgetCrypto)
        var result: WidgetSnapshotState? = null
        kotlinx.coroutines.runBlocking {
            result = serializer.readFrom(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))
        }
        assertEquals(WidgetSnapshotState.Empty, result)
    }
}

private object PassthroughWidgetCrypto : WidgetSnapshotCrypto {
    override fun encrypt(plaintext: ByteArray) = plaintext
    override fun decrypt(ciphertext: ByteArray) = ciphertext
}
