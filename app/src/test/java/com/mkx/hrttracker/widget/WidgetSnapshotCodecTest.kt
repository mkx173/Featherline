package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDateTime

class WidgetSnapshotCodecTest {

    private val baseRecord = WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = "UTC",
        anchorDateEpochDay = LocalDateTime.of(2026, 5, 18, 0, 0, 0).toLocalDate().toEpochDay(),
        doneCount = 1,
        totalCount = 3,
        manualCount = 1,
        hasActiveGroups = true,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        e2DisplayUnit = "pg_ml",
        appLanguageTag = "zh-Hans",
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
            doseMarkers = listOf(
                WidgetPkDoseMarkerRecord(
                    timeH = 0.5,
                    concentration = 118.0,
                    isPlanned = false
                )
            ),
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
    fun `codec round-trips e2DisplayUnit`() {
        val record = baseRecord.copy(e2DisplayUnit = "pmol_l")
        assertEquals(
            "pmol_l",
            WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)).e2DisplayUnit
        )
    }

    @Test
    fun `codec round-trips appLanguageTag`() {
        // The live-rendered widget chrome resolves its strings against this tag, so it
        // must survive process death intact or the chrome would fall back to the system
        // language while the baked medication strings stay in the app language.
        val record = baseRecord.copy(appLanguageTag = "en")
        assertEquals(
            "en",
            WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)).appLanguageTag
        )
    }

    @Test
    fun `codec round-trips anchorDateEpochDay`() {
        val record = baseRecord.copy(
            anchorDateEpochDay = LocalDateTime.of(2026, 5, 19, 0, 0).toLocalDate().toEpochDay()
        )
        assertEquals(
            record.anchorDateEpochDay,
            WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)).anchorDateEpochDay,
        )
    }

    @Test
    fun staleDateCheck_marksOlderAnchorDateStale() {
        val now = LocalDateTime.of(2026, 5, 19, 0, 1)

        assertTrue(baseRecord.isAnchoredBefore(now))
        assertFalse(
            baseRecord.copy(anchorDateEpochDay = now.toLocalDate().toEpochDay())
                .isAnchoredBefore(now)
        )
    }

    @Test
    fun `decode throws on wrong codec version`() {
        val bytes = WidgetSnapshotCodec.encode(baseRecord).copyOf()
        bytes[0] = 99.toByte()
        assertThrows(IllegalArgumentException::class.java) { WidgetSnapshotCodec.decode(bytes) }
    }

    @Test
    fun `codec round-trips a scheduled row with entryUuid null`() {
        val row = baseRecord.doseRows.first().copy(entryUuid = null)
        val record = baseRecord.copy(doseRows = listOf(row))
        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun codecRoundTripsMedicineUuidOnDoseRow() {
        // medicineUuid is the deep-link target the widget hands to MainActivity
        // for "edit this entry". The round-trip must preserve it byte-for-byte
        // or row taps after a process death will fail to resolve their entry.
        val row = baseRecord.doseRows.first().copy(
            medicationUuid = "aaaaaaaa-0000-0000-0000-000000000000",
        )
        val record = baseRecord.copy(doseRows = listOf(row))

        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `codec round-trips a manual row with entryUuid set`() {
        val row = WidgetDoseRow(
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
            entryUuid = "aaaaaaaa-0000-0000-0000-000000000000",
        )
        val record = baseRecord.copy(doseRows = listOf(row))
        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `codec round-trips imported row flag`() {
        val row = baseRecord.doseRows.first().copy(
            isManualRecord = true,
            isImportedRecord = true,
        )
        val record = baseRecord.copy(doseRows = listOf(row))

        assertEquals(record, WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record)))
    }

    @Test
    fun `codec round-trips isFromArchivedGroup flag`() {
        val archivedRow = baseRecord.doseRows[1].copy(isFromArchivedGroup = true)
        val activeRow = baseRecord.doseRows.first().copy(isFromArchivedGroup = false)
        val record = baseRecord.copy(doseRows = listOf(archivedRow, activeRow))
        val decoded = WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(record))
        assertEquals(record, decoded)
        assertTrue(decoded.doseRows[0].isFromArchivedGroup)
        assertFalse(decoded.doseRows[1].isFromArchivedGroup)
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
