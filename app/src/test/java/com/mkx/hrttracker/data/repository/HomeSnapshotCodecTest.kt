package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class HomeSnapshotCodecTest {
    @Test
    fun encodeDecode_preservesHomeSnapshotPayload() {
        val latestEntry = MedicationLogEntry(
            uuid = UUID.fromString("65c7a865-df3a-4ed3-9e60-1ae6af7b6bd3"),
            details = MedicationDetails(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = UUID.fromString("4510ad0b-b565-43c7-b52f-3d8ab73873c1"),
            scheduleTimeUuid = UUID.fromString("56c99b34-6a42-42e0-b2cc-2c68a4c8e5f5"),
            appliedAt = Instant.ofEpochMilli(1_777_777L),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = LocalDateTime.of(2026, 5, 6, 8, 0),
            count = 2,
        )
        val antiandrogenEntry = latestEntry.copy(
            uuid = UUID.fromString("b14559ed-9f8a-4e81-8b63-e4e1ab9e1102"),
            details = MedicationDetails(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
                selection = MedicationSelection.Catalog(MedicationKey.SPIRONOLACTONE),
                dose = MedicationDose.MgAsMedicine(100.0),
            ),
            dosageMgAsEstradiol = null,
        )
        val group = MedicationGroup(
            uuid = UUID.fromString("4510ad0b-b565-43c7-b52f-3d8ab73873c1"),
            name = "Home estradiol",
            colorKey = MedicationGroupColorKey.PLUM,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 2,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                times = listOf(LocalTime.of(8, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = UUID.fromString("56c99b34-6a42-42e0-b2cc-2c68a4c8e5f5"),
                        time = LocalTime.of(8, 0),
                        effectiveFrom = LocalDateTime.of(2026, 4, 1, 0, 0),
                    )
                ),
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("d02c3d8a-76e4-4d48-a3c3-795c61a3cd17"),
                    details = latestEntry.details,
                    count = 2,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.ofEpochMilli(11L),
            updatedAt = Instant.ofEpochMilli(12L),
            includePastScheduledSlots = false,
            replacedByGroupUuid = UUID.fromString("07ee7f42-94a0-40cb-a68a-2b54850bb5e1"),
            recreatedFromGroupUuid = UUID.fromString("fac484d4-7dc7-4ec4-b410-bb91afc5605d"),
        )
        val pkRecord = HomePkProjectionRecord(
            generatedAtEpochMillis = 10L,
            windowStartEpochMillis = 20L,
            windowEndEpochMillis = 30L,
            pkProjectionExpiresAtEpochMillis = 25L,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
            timeH = listOf(0.0, 1.0, 2.0),
            concentrations = listOf(10.0, 20.0, 30.0),
            doseMarkers = listOf(
                HomePkProjectionDoseMarkerRecord(
                    timeH = 1.0,
                    concentration = 20.0,
                )
            ),
            latestEstradiolEntry = latestEntry,
            chartWindowHours = 168,
            densePolicy = HomePkDenseSamplePolicyRecord.Interval(hours = 0.1),
            includesPostDoseOffsets = false,
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 7L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = pkRecord,
            activeGroups = listOf(group),
            scheduleEntries = listOf(latestEntry),
            antiandrogenHistoryEntries = listOf(antiandrogenEntry),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record, decoded)
    }

    @Test
    fun decode_rejectsLegacyVersionFourPayloadLayout() {
        val bytes = legacyVersionFourBytes()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            HomeSnapshotCodec.decode(bytes)
        }

        assertEquals("Unsupported Home snapshot version: 4.", exception.message)
    }

    @Test
    fun decode_rejectsVersionEightPayloadLayout_priorToFingerprintFields() {
        // v8 omits the chartWindowHours / densePolicy / includesPostDoseOffsets
        // fingerprint introduced in v9. The codec rejects v8 outright so stale
        // caches on first launch after the bump trigger a one-time rebuild
        // rather than mis-decoding.
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(8)
            stream.writeInt(HOME_SNAPSHOT_SCHEMA_VERSION)
        }
        val exception = assertThrows(IllegalArgumentException::class.java) {
            HomeSnapshotCodec.decode(output.toByteArray())
        }
        assertEquals("Unsupported Home snapshot version: 8.", exception.message)
    }

    @Test
    fun encodeDecode_roundTripsBudgetDenseSamplePolicy() {
        val pkRecord = HomePkProjectionRecord(
            generatedAtEpochMillis = 10L,
            windowStartEpochMillis = 20L,
            windowEndEpochMillis = 30L,
            pkProjectionExpiresAtEpochMillis = 25L,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
            timeH = listOf(0.0),
            concentrations = listOf(0.0),
            doseMarkers = emptyList(),
            latestEstradiolEntry = null,
            chartWindowHours = 720,
            densePolicy = HomePkDenseSamplePolicyRecord.Budget(segmentCount = 2240),
            includesPostDoseOffsets = true,
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = pkRecord,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record, decoded)
    }

    private fun legacyVersionFourBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(4)
            stream.writeInt(HOME_SNAPSHOT_SCHEMA_VERSION)
            stream.writeLong(7L)
            stream.writeLong(100L)
            stream.writeLong(LocalDate.of(2026, 5, 6).toEpochDay())
            stream.writeTestString("Asia/Tokyo")
            stream.writeBoolean(true)
            stream.writeLong(10L)
            stream.writeLong(20L)
            stream.writeLong(30L)
            stream.writeTestString(
                """{"concentrationUnit":"PG_PER_ML","timeH":[],"concentrations":[],"doseMarkers":[]}"""
            )
            stream.writeBoolean(false)
            stream.writeInt(0)
            stream.writeInt(0)
            stream.writeInt(0)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeTestString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
