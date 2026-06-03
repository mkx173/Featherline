package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        val medicine = testMedicine(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            key = MedicationKey.ESTRADIOL,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 12.0,
                unitsLastTotal = 30.0,
                warnAtDaysRemaining = 14,
            ),
        )
        val antiandrogenMedicine = testMedicine(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            key = MedicationKey.SPIRONOLACTONE,
        )
        val latestEntry = MedicationLogEntry(
            uuid = UUID.fromString("65c7a865-df3a-4ed3-9e60-1ae6af7b6bd3"),
            medicine = medicine,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 2),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = UUID.fromString("4510ad0b-b565-43c7-b52f-3d8ab73873c1"),
            scheduleTimeUuid = UUID.fromString("56c99b34-6a42-42e0-b2cc-2c68a4c8e5f5"),
            appliedAt = Instant.ofEpochMilli(1_777_777L),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = LocalDateTime.of(2026, 5, 6, 8, 0),
            count = 2,
        )
        val antiandrogenEntry = latestEntry.copy(
            uuid = UUID.fromString("b14559ed-9f8a-4e81-8b63-e4e1ab9e1102"),
            medicine = antiandrogenMedicine,
            category = MedicationCategory.ANTIANDROGEN,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = null,
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
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 2),
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
            stockMedicines = listOf(
                medicine,
                testCustomMedicine(
                    uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    medicationName = "Injection stock",
                    category = MedicationCategory.ESTRADIOL,
                    preparation = MedicinePreparation.InjectionMultiUseVial(
                        concentrationMgPerMl = 20.0,
                        vialVolumeMl = 1.0,
                    ),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 1.0,
                        openContainerAmount = 0.5,
                        warnAtDaysRemaining = 7,
                    ),
                ),
            ),
            stockFulfillmentEntries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 2),
                    sourceGroupUuid = group.uuid,
                    scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
                    appliedAt = latestEntry.appliedAt,
                    appliedAtTimeZoneId = latestEntry.appliedAtTimeZoneId,
                    scheduledFor = latestEntry.scheduledFor,
                )
            ),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record, decoded)
        // Identity-level assertion: the round-trip preserves the medicine
        // reference and the dose instruction's structured form (a stale
        // codec that dropped these would still hit the data-class equals
        // by coincidence, so spell out the load-bearing fields here).
        val decodedMedication = decoded.activeGroups.single().medications.single()
        assertEquals(medicine.uuid, decodedMedication.medicine?.uuid)
        assertEquals(
            DoseInstruction.TabletFraction(numerator = 1, denominator = 2),
            decodedMedication.doseInstruction,
        )
        assertEquals(2.0, decoded.scheduleEntries.single().equivalentE2Mg!!, 0.000001)
    }

    @Test
    fun encodeDecode_preservesArchivedGroups() {
        val medicine = testMedicine(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            key = MedicationKey.ESTRADIOL,
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
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 2),
                    count = 2,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.ofEpochMilli(11L),
            updatedAt = Instant.ofEpochMilli(12L),
        )
        val archivedGroup = group.copy(
            archivedAt = Instant.parse("2026-05-01T00:00:00Z"),
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            archivedGroups = listOf(archivedGroup),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record.archivedGroups, decoded.archivedGroups)
        assertEquals(record, decoded)
    }

    @Test
    fun encodeDecode_roundTripsPatchOffSlotWithNullMedicine() {
        // A PATCH_OFF slot is the one application type whose medicine
        // reference may be null. Without explicit nullable-medicine plumbing
        // the codec would either NPE on encode or fail equals on decode.
        val patchOffMedication = MedicationGroupMedication(
            uuid = UUID.fromString("9aaaaaaa-0000-0000-0000-000000000001"),
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            count = 1,
        )
        val patchOffLogEntry = MedicationLogEntry(
            uuid = UUID.fromString("9aaaaaaa-0000-0000-0000-000000000002"),
            medicine = null,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = Instant.ofEpochMilli(50L),
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = listOf(
                MedicationGroup(
                    uuid = UUID.fromString("9aaaaaaa-0000-0000-0000-000000000010"),
                    name = "Patch removals",
                    colorKey = MedicationGroupColorKey.ROSE,
                    schedule = MedicationGroupSchedule(
                        type = MedicationGroupScheduleType.DAILY,
                        interval = 1,
                        since = LocalDate.of(2026, 5, 1),
                        weeklyDaysOfWeek = emptySet(),
                        times = listOf(LocalTime.of(20, 0)),
                    ),
                    medications = listOf(patchOffMedication),
                    notificationsEnabled = false,
                    createdAt = Instant.ofEpochMilli(0L),
                    updatedAt = Instant.ofEpochMilli(0L),
                )
            ),
            scheduleEntries = listOf(patchOffLogEntry),
            antiandrogenHistoryEntries = emptyList(),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record, decoded)
    }

    @Test
    fun encodeDecode_roundTripsCapsulePreparation() {
        val capsuleMedicine = testCustomMedicine(
            uuid = UUID.fromString("8aaaaaaa-0000-0000-0000-000000000001"),
            medicationName = "Progesterone",
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )
        val capsuleMedication = MedicationGroupMedication(
            uuid = UUID.fromString("8aaaaaaa-0000-0000-0000-000000000002"),
            medicine = capsuleMedicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            count = 1,
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = listOf(
                MedicationGroup(
                    uuid = UUID.fromString("8aaaaaaa-0000-0000-0000-000000000010"),
                    name = "Capsules",
                    colorKey = MedicationGroupColorKey.TEAL,
                    schedule = MedicationGroupSchedule(
                        type = MedicationGroupScheduleType.DAILY,
                        interval = 1,
                        since = LocalDate.of(2026, 5, 1),
                        weeklyDaysOfWeek = emptySet(),
                        times = listOf(LocalTime.of(9, 0)),
                    ),
                    medications = listOf(capsuleMedication),
                    notificationsEnabled = false,
                    createdAt = Instant.ofEpochMilli(0L),
                    updatedAt = Instant.ofEpochMilli(0L),
                )
            ),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        val restoredPreparation = decoded.activeGroups.single()
            .medications.single()
            .medicine
            ?.preparation
        assertEquals(
            MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
            restoredPreparation,
        )
    }

    @Test
    fun encodeDecode_roundTripsPkEntriesSharingMedicineAndNullMedicine() {
        val sharedMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
            key = MedicationKey.ESTRADIOL_VALERATE,
        )
        val group = UUID.fromString("aaaa0000-0000-0000-0000-0000000000a0")
        val slot = UUID.fromString("aaaa0000-0000-0000-0000-0000000000b0")
        val pkEntries = (0 until 3).map { index ->
            testMedicationLogEntry(
                uuid = UUID.fromString("aaaa0000-0000-0000-0000-00000000010$index"),
                medicine = sharedMedicine,
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
                equivalentE2Mg = 5.0,
                sourceGroupUuid = group,
                scheduleTimeUuid = slot,
                appliedAt = Instant.ofEpochMilli(1_000L + index),
                scheduledFor = LocalDateTime.of(2026, 5, index + 1, 8, 0),
            )
        } + testMedicationLogEntry(
            // A PATCH_OFF-style row carries a null medicine; the pooled codec
            // must round-trip the -1 reference back to null rather than NPE.
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-0000000001ff"),
            medicine = null,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            sourceGroupUuid = null,
            appliedAt = Instant.ofEpochMilli(2_000L),
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            pkEntries = pkEntries,
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(record, decoded)
        // The three estradiol entries must resolve back to the same medicine
        // identity, and the patch-off row must stay null.
        assertEquals(
            listOf(sharedMedicine.uuid, sharedMedicine.uuid, sharedMedicine.uuid, null),
            decoded.pkEntries.map { it.medicine?.uuid },
        )
    }

    @Test
    fun encodeDecode_preservesDoseAmountDelta() {
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = listOf(
                testMedicationLogEntry(
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(1_000L),
                ).copy(doseAmountDelta = 0.1)
            ),
            antiandrogenHistoryEntries = emptyList(),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        assertEquals(0.1, decoded.scheduleEntries.single().doseAmountDelta)
    }

    @Test
    fun encode_dedupesMedicineSharedAcrossPkEntries() {
        // A medicine shared across many entries must be serialized once. Encode
        // 40 entries that share one medicine vs 40 that each carry a distinct
        // medicine; the shared payload should be a fraction of the distinct one.
        fun recordWith(entries: List<MedicationLogEntry>) = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 1L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            pkEntries = entries,
        )

        val sharedMedicine = testMedicine(
            uuid = UUID.fromString("bbbb0000-0000-0000-0000-000000000001"),
            key = MedicationKey.ESTRADIOL_VALERATE,
        )
        val shared = (0 until 40).map { index ->
            testMedicationLogEntry(
                uuid = UUID.fromString("bbbb0000-0000-0000-0000-0000000002%02d".format(index)),
                medicine = sharedMedicine,
                sourceGroupUuid = null,
                appliedAt = Instant.ofEpochMilli(1_000L + index),
            )
        }
        val distinct = (0 until 40).map { index ->
            testMedicationLogEntry(
                uuid = UUID.fromString("cccc0000-0000-0000-0000-0000000002%02d".format(index)),
                medicine = testMedicine(
                    uuid = UUID.fromString("dddd0000-0000-0000-0000-0000000002%02d".format(index)),
                    key = MedicationKey.ESTRADIOL_VALERATE,
                ),
                sourceGroupUuid = null,
                appliedAt = Instant.ofEpochMilli(1_000L + index),
            )
        }

        val sharedSize = HomeSnapshotCodec.encode(recordWith(shared)).size
        val distinctSize = HomeSnapshotCodec.encode(recordWith(distinct)).size

        assertTrue(
            "Shared-medicine encoding ($sharedSize B) should be far smaller than " +
                "distinct-medicine encoding ($distinctSize B)",
            sharedSize < distinctSize / 2,
        )
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
    fun decode_rejectsVersionTenPayloadLayout_priorToMedicineIdentity() {
        // v10 stored MedicationDetails / MedicationDose blobs that no longer
        // exist on the domain model. The codec rejects v10 outright so
        // stale caches on first launch after the medicine-identity bump
        // trigger a one-time rebuild rather than mis-decoding.
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(10)
            stream.writeInt(HOME_SNAPSHOT_SCHEMA_VERSION)
        }
        val exception = assertThrows(IllegalArgumentException::class.java) {
            HomeSnapshotCodec.decode(output.toByteArray())
        }
        assertEquals("Unsupported Home snapshot version: 10.", exception.message)
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

    @Test
    fun decode_normalizesLegacyEmptyOpenContainerStock() {
        val legacyMedicine = testCustomMedicine(
            uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            medicationName = "Legacy snapshot vial",
            category = MedicationCategory.ESTRADIOL,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 1.0,
            ),
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                openContainerAmount = 0.0,
            ),
        )
        val record = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 7L,
            generatedAtEpochMillis = 100L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            stockMedicines = listOf(legacyMedicine),
        )

        val decoded = HomeSnapshotCodec.decode(HomeSnapshotCodec.encode(record))

        val stock = decoded.stockMedicines.single().stock
        assertEquals(1.0, stock.unitsRemaining!!, 1e-9)
        assertEquals(1.0, stock.openContainerAmount!!, 1e-9)
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
