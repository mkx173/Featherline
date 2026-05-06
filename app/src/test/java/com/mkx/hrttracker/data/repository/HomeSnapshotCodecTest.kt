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
import org.junit.Assert.assertEquals
import org.junit.Test
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
            colorKey = MedicationGroupColorKey.ORCHID,
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
            sourceFingerprint = "fingerprint",
            payloadJson = """{"payload":true}""",
            latestEstradiolEntry = latestEntry,
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            sourceFingerprint = "home-fingerprint",
            pkProjection = pkRecord,
            activeGroups = listOf(group),
            scheduleEntries = listOf(latestEntry),
            antiandrogenHistoryEntries = listOf(antiandrogenEntry),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record, decoded)
    }
}
