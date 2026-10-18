package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationPromotedCovariance
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** `home_e2_inputs.pb`: what the E2 estimates are derived from, and the fit. */
class HomeE2InputsCodecTest {
    private fun minimalRecord() = E2InputsRecord(
        generation = 0L,
        anchorDateEpochDay = 0L,
        zoneId = "Asia/Tokyo",
        option = HomeE2ChartWindowOption.SEVEN_DAYS,
        weightKg = null,
        estradiolEntries = emptyList(),
        plannedSources = emptyList(),
    )

    @Test
    fun encodeDecode_roundTripsEveryField() {
        val medicine = testMedicine(uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val group = MedicationGroup(
            uuid = UUID.fromString("4510ad0b-b565-43c7-b52f-3d8ab73873c1"),
            name = "Home estradiol",
            colorKey = MedicationGroupColorKey.PLUM,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = medicine)),
            createdAt = Instant.ofEpochMilli(11L),
            updatedAt = Instant.ofEpochMilli(12L),
        )
        val record = E2InputsRecord(
            engineVersion = PK_ENGINE_VERSION,
            generation = 7L,
            anchorDateEpochDay = LocalDate.of(2026, 5, 6).toEpochDay(),
            zoneId = "Asia/Tokyo",
            option = HomeE2ChartWindowOption.THIRTY_DAYS,
            weightKg = 61.5,
            estradiolEntries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("65c7a865-df3a-4ed3-9e60-1ae6af7b6bd3"),
                    medicine = medicine,
                    sourceGroupUuid = group.uuid,
                    appliedAt = Instant.ofEpochMilli(1_777_777L),
                    scheduledFor = LocalDateTime.of(2026, 5, 6, 8, 0),
                ),
            ),
            plannedSources = listOf(group),
            labs = listOf(
                PkCalibrationLab(UUID(0L, 1L), collectedAtEpochMillis = 1_700_000_000_000L, valuePgml = 130.0),
                PkCalibrationLab(UUID(0L, 2L), collectedAtEpochMillis = 1_700_600_000_000L, valuePgml = -1.0),
            ),
            labMetadata = listOf(
                E2CalibrationMetadata(UUID(0L, 1L), E2CalibrationDisposition.REVIEWED, Instant.ofEpochMilli(5L)),
                E2CalibrationMetadata(UUID(0L, 2L), E2CalibrationDisposition.EXCLUDED, Instant.ofEpochMilli(6L)),
            ),
            fit = E2FitRecord(
                fitId = Digest("ab".repeat(32)),
                epoch = 2,
                outcome = FitOutcome.Solved(calibrationResult(), originEpochMillis = 1_700_000_000_000L),
            ),
        )

        for (inputs in listOf(
            record,
            record.copy(fit = record.fit?.copy(outcome = FitOutcome.Failed)),
            record.copy(engineVersion = PK_ENGINE_VERSION + 1, weightKg = null, fit = null),
            minimalRecord(),
        )) {
            assertEquals(inputs, HomeSnapshotCodec.decodeE2Inputs(HomeSnapshotCodec.encodeE2Inputs(inputs)))
        }
    }

    @Test
    fun anotherCodecVersion_readsAsMissing() = runTest {
        val serializer = EncryptedRecordSerializer(
            crypto = object : HomeSnapshotCrypto {
                override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
                override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
            },
            name = "home_e2_inputs",
            encode = HomeSnapshotCodec::encodeE2Inputs,
            decode = HomeSnapshotCodec::decodeE2Inputs,
        )
        val otherVersion = HomeSnapshotCodec.encodeE2Inputs(minimalRecord()).also { bytes ->
            ByteBuffer.wrap(bytes).putInt(0, 2)
        }

        assertThrows(IllegalArgumentException::class.java) { HomeSnapshotCodec.decodeE2Inputs(otherVersion) }
        assertNull(serializer.readFrom(ByteArrayInputStream(otherVersion)).record)
        assertEquals(
            minimalRecord(),
            serializer.readFrom(ByteArrayInputStream(HomeSnapshotCodec.encodeE2Inputs(minimalRecord()))).record,
        )
    }

    @Test
    fun encodeDecode_roundTripsEntriesSharingMedicineAndNullMedicine() {
        val sharedMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
            key = MedicationKey.ESTRADIOL_VALERATE,
        )
        val group = UUID.fromString("aaaa0000-0000-0000-0000-0000000000a0")
        val slot = UUID.fromString("aaaa0000-0000-0000-0000-0000000000b0")
        val entries = (0 until 3).map { index ->
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
        val record = minimalRecord().copy(estradiolEntries = entries)

        val decoded = HomeSnapshotCodec.decodeE2Inputs(HomeSnapshotCodec.encodeE2Inputs(record))

        assertEquals(record, decoded)
        // The three estradiol entries must resolve back to the same medicine
        // identity, and the patch-off row must stay null.
        assertEquals(
            listOf(sharedMedicine.uuid, sharedMedicine.uuid, sharedMedicine.uuid, null),
            decoded.estradiolEntries.map { it.medicine?.uuid },
        )
    }

    @Test
    fun encode_dedupesMedicineSharedAcrossEntries() {
        // A medicine shared across many entries must be serialized once. Encode
        // 40 entries that share one medicine vs 40 that each carry a distinct
        // medicine; the shared payload should be a fraction of the distinct one.
        fun recordWith(entries: List<MedicationLogEntry>) = minimalRecord().copy(estradiolEntries = entries)

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

        val sharedSize = HomeSnapshotCodec.encodeE2Inputs(recordWith(shared)).size
        val distinctSize = HomeSnapshotCodec.encodeE2Inputs(recordWith(distinct)).size

        assertTrue(
            "Shared-medicine encoding ($sharedSize B) should be far smaller than " +
                    "distinct-medicine encoding ($distinctSize B)",
            sharedSize < distinctSize / 2,
        )
    }

    @Test
    fun fit_roundTripsEveryCalibrationResultField_andFailed() {
        val solved = E2FitRecord(
            fitId = Digest("ab".repeat(32)),
            epoch = 3,
            outcome = FitOutcome.Solved(calibrationResult(), originEpochMillis = 1_700_000_000_000L),
        )
        val notReady = solved.copy(
            outcome = FitOutcome.Solved(PkCalibrationResult(PkCalibrationGlobalState.NO_USABLE_LABS), 0L),
        )
        for (fit in listOf(solved, notReady, solved.copy(outcome = FitOutcome.Failed))) {
            assertEquals(fit, HomeSnapshotCodec.decodeFit(HomeSnapshotCodec.encodeFit(fit)))
        }
    }

    @Test
    fun fit_encodesEqualResultsIdentically_whateverTheirSetAndMapOrder() {
        // fitKey digests these bytes: iteration order must not reach it.
        val result = calibrationResult()
        val reordered = result.copy(
            routeResults = result.routeResults.map { row ->
                row.copy(
                    reasons = row.reasons.reversed().toSet(),
                    unreviewedOutlierLabIds = row.unreviewedOutlierLabIds.reversed().toSet(),
                )
            },
            ignoredLabs = result.ignoredLabs.entries.reversed().associate { (id, reason) -> id to reason },
        )
        assertEquals(result, reordered)
        fun bytes(result: PkCalibrationResult) =
            HomeSnapshotCodec.encodeFit(E2FitRecord(Digest("00"), 0, FitOutcome.Solved(result, 0L)))
        assertArrayEquals(bytes(result), bytes(reordered))
    }

    private fun calibrationResult() = PkCalibrationResult(
        globalState = PkCalibrationGlobalState.READY,
        routeResults = PkCalibrationRoute.entries.map { route ->
            when (route) {
                PkCalibrationRoute.INJECTION -> PkRouteCalibrationResult(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                    reasons = setOf(PkCalibrationReason.UNCERTAIN, PkCalibrationReason.UNREVIEWED_OUTLIER),
                    fittedBeta = 0.4,
                    betaPosteriorSd = 0.25,
                    supportingLabCount = 2,
                    minStudentTWeight = 0.2,
                    unreviewedOutlierLabIds = setOf(UUID(0L, 7L), UUID(0L, 3L)),
                )

                PkCalibrationRoute.ORAL -> PkRouteCalibrationResult(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                    fittedBeta = -0.1,
                    betaPosteriorSd = 0.05,
                    supportingLabCount = 4,
                    minStudentTWeight = 0.9,
                )

                else -> PkRouteCalibrationResult(route, PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL)
            }
        },
        promotedBetaCovariance = PkCalibrationPromotedCovariance(
            routes = listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            values = listOf(listOf(0.0625, -0.001), listOf(-0.001, 0.0025)),
        ),
        ignoredLabs = mapOf(
            UUID(0L, 9L) to PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL,
            UUID(0L, 5L) to PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE,
        ),
    )
}
