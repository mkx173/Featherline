package com.mkx.hrttracker.widget

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
        doseRows = listOf(
            WidgetDoseRow(
                displayName = "Estradiol patch",
                count = 1,
                status = WidgetDoseStatus.DONE,
                scheduledAt = LocalDateTime.of(2026, 5, 18, 8, 0, 0),
                doneAtEpochMillis = 1_747_540_800_000L,
                groupUuid = null,
                scheduleTimeUuid = null,
            ),
            WidgetDoseRow(
                displayName = "Progesterone",
                count = 2,
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = LocalDateTime.of(2026, 5, 18, 20, 0, 0),
                doneAtEpochMillis = null,
                groupUuid = "123e4567-e89b-12d3-a456-426614174000",
                scheduleTimeUuid = "223e4567-e89b-12d3-a456-426614174001",
            ),
        ),
        nextDueDose = WidgetNextDose(
            name = "Progesterone",
            scheduledAt = LocalDateTime.of(2026, 5, 18, 20, 0, 0),
            status = WidgetDoseStatus.DUE_SOON,
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
    fun `codec round-trips a record with null projection and null nextDueDose`() {
        val minimal = baseRecord.copy(pkProjection = null, nextDueDose = null, doseRows = emptyList())
        val decoded = WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(minimal))
        assertEquals(minimal, decoded)
    }

    @Test
    fun `codec round-trips overdue dose row without action fields`() {
        val row = WidgetDoseRow(
            displayName = "Test",
            count = 1,
            status = WidgetDoseStatus.OVERDUE,
            scheduledAt = LocalDateTime.of(2026, 5, 18, 6, 0, 0),
            doneAtEpochMillis = null,
            groupUuid = "aaa-bbb",
            scheduleTimeUuid = null,
        )
        val record = baseRecord.copy(doseRows = listOf(row))
        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `decode throws on wrong codec version`() {
        val bytes = WidgetSnapshotCodec.encode(baseRecord).copyOf()
        bytes[0] = 99.toByte()  // corrupt first byte of the codec version Int
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

// Test double — bypasses encryption
private object PassthroughWidgetCrypto : WidgetSnapshotCrypto {
    override fun encrypt(plaintext: ByteArray) = plaintext
    override fun decrypt(ciphertext: ByteArray) = ciphertext
}
