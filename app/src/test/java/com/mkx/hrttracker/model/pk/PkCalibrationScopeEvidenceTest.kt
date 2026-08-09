package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.exp

class PkCalibrationScopeEvidenceTest {
    @Test
    fun engineFacade_mapsGlobalFailuresAndPublishesOnlyCanonicalReadyRoutes() {
        val fixture = fixture()
        val ready = PkCalibrationEngine.compute(
            input = fixture.input(),
            metadata = emptyList(),
            identityPolicy = fixture.policy,
            config = fixture.config,
            attestationProvider = fixture.attestationProvider,
        )

        assertEquals(PkCalibrationGlobalState.READY, ready.globalState)
        assertEquals(
            PkCalibrationRoute.entries,
            ready.routeResults.map(PkRouteCalibrationResult::route),
        )
        assertEquals(PkCalibrationRoute.entries.size, ready.routeResults.size)

        val unresolvedWeight = PkCalibrationEngine.compute(
            input = fixture.input(weightKg = null),
            metadata = emptyList(),
            identityPolicy = fixture.policy,
            config = fixture.config,
            attestationProvider = fixture.attestationProvider,
        )
        assertEquals(PkCalibrationGlobalState.SHARED_INPUT_INVALID, unresolvedWeight.globalState)
        assertEquals(setOf(PkCalibrationReason.SHARED_INPUT_INVALID), unresolvedWeight.globalReasons)
        assertTrue(unresolvedWeight.routeResults.isEmpty())
        assertTrue(unresolvedWeight.promotedRoutes.isEmpty())
        assertTrue(unresolvedWeight.displayParams.routeLogScale.isEmpty())

        val unavailableScope = PkCalibrationEngine.compute(
            input = fixture.input(),
            metadata = emptyList(),
            identityPolicy = fixture.policy,
            config = fixture.config,
            attestationProvider = PkCalibrationAttestationProvider.Unavailable,
        )
        assertEquals(PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED, unavailableScope.globalState)
        assertEquals(
            setOf(PkCalibrationReason.SCOPE_NOT_CONFIRMED),
            unavailableScope.globalReasons,
        )
        assertTrue(unavailableScope.routeResults.isEmpty())
    }

    @Test
    fun nonpositivePrecedence_outweighsLaterDuplicateMetadataInputFailure() {
        val zero = lab(
            resultId = uuid(1),
            sourceValue = 0.0,
            canonicalValuePgml = 0.0,
        )
        val other = lab(resultId = uuid(2))
        val fixture = fixture(labs = listOf(zero, other))
        val duplicateMetadata = listOf(Instant.EPOCH, Instant.ofEpochMilli(1)).map { updatedAt ->
            requireNotNull(
                E2CalibrationMetadata.create(
                    resultId = other.resultId,
                    disposition = E2CalibrationDisposition.AUTO,
                    acceptedRecord = null,
                    updatedAt = updatedAt,
                )
            )
        }

        assertFailure(
            fixture.build(metadata = duplicateMetadata),
            PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2,
        )
        val result = PkCalibrationEngine.compute(
            input = fixture.input(),
            metadata = duplicateMetadata,
            identityPolicy = fixture.policy,
            config = fixture.config,
            attestationProvider = fixture.attestationProvider,
        )
        assertEquals(PkCalibrationGlobalState.SHARED_INPUT_INVALID, result.globalState)
        assertEquals(
            setOf(PkCalibrationReason.INVALID_NONPOSITIVE_E2),
            result.globalReasons,
        )
        assertTrue(result.routeResults.isEmpty())
    }

    @Test
    fun jcs_matchesRfc8785SampleAndIsConcurrentDeterministic() {
        val payload = linkedMapOf<String, Any?>(
            "numbers" to listOf(
                333333333.33333329,
                1E30,
                4.50,
                2e-3,
                0.000000000000000000000000001,
            ),
            "string" to "\u20ac$\u000f\nA'B\"\\\\\"/",
            "literals" to listOf(null, true, false),
        )
        val expected =
            "{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"}"

        val first = canonicalJsonBytes(payload)
        assertEquals(expected, first.toString(StandardCharsets.UTF_8))

        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (0 until 128).map {
                executor.submit<ByteArray> { canonicalJsonBytes(payload) }
            }.map { future -> future.get() }
            assertTrue(results.all(first::contentEquals))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun jcs_matchesRfc8785AppendixBFiniteNumberVectors() {
        val vectors = listOf(
            "0000000000000000" to "0",
            "8000000000000000" to "0",
            "0000000000000001" to "5e-324",
            "8000000000000001" to "-5e-324",
            "7fefffffffffffff" to "1.7976931348623157e+308",
            "ffefffffffffffff" to "-1.7976931348623157e+308",
            "4340000000000000" to "9007199254740992",
            "c340000000000000" to "-9007199254740992",
            "4430000000000000" to "295147905179352830000",
            "44b52d02c7e14af5" to "9.999999999999997e+22",
            "44b52d02c7e14af6" to "1e+23",
            "44b52d02c7e14af7" to "1.0000000000000001e+23",
            "444b1ae4d6e2ef4e" to "999999999999999700000",
            "444b1ae4d6e2ef4f" to "999999999999999900000",
            "444b1ae4d6e2ef50" to "1e+21",
            "3eb0c6f7a0b5ed8c" to "9.999999999999997e-7",
            "3eb0c6f7a0b5ed8d" to "0.000001",
            "41b3de4355555553" to "333333333.3333332",
            "41b3de4355555554" to "333333333.33333325",
            "41b3de4355555555" to "333333333.3333333",
            "41b3de4355555556" to "333333333.3333334",
            "41b3de4355555557" to "333333333.33333343",
            "becbf647612f3696" to "-0.0000033333333333333333",
            "43143ff3c1cb0959" to "1424953923781206.2",
        )
        val values = vectors.map { (bits, _) ->
            Double.fromBits(bits.toULong(16).toLong())
        }
        val expected = vectors.joinToString(
            prefix = "{\"values\":[",
            postfix = "]}",
            separator = ",",
        ) { (_, representation) -> representation }

        assertEquals(
            expected,
            canonicalJsonBytes(mapOf("values" to values)).toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun scopeInput_normalizesNegativeZeroDoseAndRejectsNegativeZeroWeight() {
        val source = lab()
        val negativeZeroEvent = medicationEvent(
            eventId = uuid(11),
            route = PkRoute.ORAL,
            timeH = 0.0,
            doseMg = -0.0,
        )
        val positiveZeroEvent = medicationEvent(
            eventId = uuid(11),
            route = PkRoute.ORAL,
            timeH = 0.0,
            doseMg = 0.0,
        )
        val negativeZeroInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs = listOf(source),
                medicationEvents = listOf(negativeZeroEvent),
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
        val positiveZeroInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs = listOf(source),
                medicationEvents = listOf(positiveZeroEvent),
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )

        assertEquals(0L, negativeZeroEvent.event.doseMg.toRawBits())
        assertEquals("0000000000000000", (-0.0).toCanonicalBinary64Bits())
        assertEquals(positiveZeroInput.digest, negativeZeroInput.digest)
        assertNull(
            PkCalibrationScopeInputSnapshot.create(
                labs = listOf(source),
                medicationEvents = listOf(negativeZeroEvent),
                resolvedCurrentWeightKg = -0.0,
                forwardModelVersion = ForwardModelVersion,
            )
        )
    }

    @Test
    fun nonIdentityUnitConversion_isVerifiedAndUsableAsEvidence() {
        val canonicalPgml = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.E2,
            value = 367.1,
            unit = BloodUnitKey.PMOL_L,
        )
        val convertedLab = lab(
            sourceValue = 367.1,
            canonicalValuePgml = canonicalPgml,
            sourceUnitSnapshot = PmolSourceUnitSnapshot,
            unitId = PmolUnitId,
        )
        val convertedFixture = fixture(labs = listOf(convertedLab))
        val convertedPolicy = identityPolicy(
            unitIdBySourceSnapshot = mapOf(
                SourceUnitSnapshot to UnitId,
                PmolSourceUnitSnapshot to PmolUnitId,
            )
        )
        val canonicalInput = ready(
            convertedFixture.build(identityPolicy = convertedPolicy)
        ).canonicalInput

        val verification = convertedLab.verifyCanonicalValuePgml()
            as PkCalibrationCanonicalE2ValueVerification.Verified
        assertEquals(canonicalPgml, verification.canonicalValuePgml, 0.0)
        assertEquals(
            convertedLab.resultId,
            canonicalInput.authorizedLabs.single().resultId,
        )
        assertEquals(
            convertedLab.sourceValueBits,
            requireNotNull(
                canonicalInput.acceptanceRecordFor(convertedLab.resultId)
            ).sourceValueBits,
        )
    }

    @Test
    fun scopeInput_acceptsBothSignedJcsTimestampEdges() {
        val minimumLab = lab(
            resultId = uuid(1),
            collectedAtEpochMillis = -JcsSafeIntegerMax,
        )
        val maximumLab = lab(
            resultId = uuid(2),
            collectedAtEpochMillis = JcsSafeIntegerMax,
        )
        assertNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs = listOf(minimumLab, maximumLab),
                medicationEvents = listOf(
                    medicationEvent(
                        eventId = uuid(11),
                        route = PkRoute.ORAL,
                        timeH = 0.0,
                        epochMillis = -JcsSafeIntegerMax,
                    ),
                    medicationEvent(
                        eventId = uuid(12),
                        route = PkRoute.ORAL,
                        timeH = 0.0,
                        epochMillis = JcsSafeIntegerMax,
                    ),
                ),
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
    }

    @Test
    fun scopeInputDigest_isOrderIndependentButOneBitSensitive() {
        val labs = listOf(
            lab(resultId = uuid(1), collectedAtEpochMillis = OriginMillis + 8 * HourMillis),
            lab(resultId = uuid(2), collectedAtEpochMillis = OriginMillis + 12 * HourMillis),
        )
        val events = listOf(
            medicationEvent(uuid(11), PkRoute.ORAL, timeH = 0.0),
            medicationEvent(uuid(12), PkRoute.GEL, timeH = 1.0),
        )
        val ordered = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs,
                events,
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        val reordered = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs.reversed(),
                events.reversed(),
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        val oneBitWeight = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs,
                events,
                Math.nextUp(BodyWeightKg),
                ForwardModelVersion,
            )
        )

        assertEquals(ordered.digest, reordered.digest)
        assertEquals(
            listOf(uuid(1), uuid(2)),
            reordered.labs.map(PkCalibrationE2LabSource::resultId),
        )
        assertEquals(listOf(uuid(11), uuid(12)), reordered.medicationEvents.map { it.event.id })
        assertNotEquals(ordered.digest, oneBitWeight.digest)
    }

    @Test
    fun scopeFactories_rejectDuplicatesEmptyAndUnresolvedIdentity() {
        val source = lab()

        assertNull(
            PkCalibrationScopeInputSnapshot.create(
                emptyList(),
                emptyList(),
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        assertNull(
            PkCalibrationScopeInputSnapshot.create(
                listOf(source, source),
                emptyList(),
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        assertNull(
            PkCalibrationE2LabSource.create(
                panel = bloodPanel(source.resultId, 100.0, 100.0),
                result = bloodPanel(source.resultId, 100.0, 100.0).results.single(),
                analyteId = "é2",
                unitId = UnitId,
                providerId = ProviderId,
                assayMethodId = AssayId,
            )
        )
        assertNull(PkCalibrationIdentityPolicy.researchOrTest(
            builtinE2AnalyteId = AnalyteId,
            targetHormoneId = HormoneId,
            unitIdBySourceSnapshot = mapOf(SourceUnitSnapshot to UnitId),
            supportedProviderIds = setOf(ProviderId),
            supportedAssayMethodIds = setOf(AssayId),
            eventTypeIdByRoute = EventTypeIds - PkRoute.GEL,
            routeIdByRoute = RouteIds,
            compoundIdByCompound = CompoundIds,
        ))
        assertNull(PkCalibrationIdentityPolicy.researchOrTest(
            builtinE2AnalyteId = AnalyteId,
            targetHormoneId = HormoneId,
            unitIdBySourceSnapshot = mapOf(SourceUnitSnapshot to UnitId),
            supportedProviderIds = setOf(ProviderId),
            supportedAssayMethodIds = setOf(AssayId),
            eventTypeIdByRoute = EventTypeIds,
            routeIdByRoute = RouteIds,
            compoundIdByCompound = CompoundIds +
                    (PkCompound.EU to CompoundIds.getValue(PkCompound.E2)),
        ))

        val mismatchedCanonicalPanel = bloodPanel(
            resultId = uuid(3),
            sourceValue = 100.0,
            canonicalValuePgml = Math.nextUp(100.0),
        )
        val mismatchedCanonicalSource = requireNotNull(
            PkCalibrationE2LabSource.create(
                panel = mismatchedCanonicalPanel,
                result = mismatchedCanonicalPanel.results.single(),
                analyteId = AnalyteId,
                unitId = UnitId,
                providerId = ProviderId,
                assayMethodId = AssayId,
            )
        )
        assertFailure(
            fixture(labs = listOf(mismatchedCanonicalSource)).build(),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )
    }

    @Test
    fun productionScopeAndMissingOrInvalidWeight_failClosed() {
        val fixture = fixture()
        val unavailableProvider = PkCalibrationAttestationProvider.Unavailable

        assertFailure(
            fixture.build(
                attestationProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(weightKg = null),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )
        assertFailure(
            fixture.build(weightKg = 0.0),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )
        assertFailure(
            fixture.build(config = PkCalibrationConfig.productionDefault()),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        val throwingProvider = PkCalibrationAttestationProvider {
            error("attestation source unavailable")
        }
        assertFailure(
            fixture.build(attestationProvider = throwingProvider),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )

        val invalidEvent = medicationEvent(
            eventId = uuid(11),
            route = PkRoute.ORAL,
            timeH = 0.0,
            eventTypeId = "event:unknown/v1",
        )
        assertFailure(
            fixture.build(
                weightKg = null,
                attestationProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(
                forwardModelVersion = "invalid model identity",
                attestationProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(
                events = listOf(invalidEvent),
                attestationProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(forwardModelVersion = "invalid model identity"),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )
    }

    @Test
    fun unknownSourceIdentitiesFailClosed() {
        val unknownLab = lab(analyteId = "analyte:unknown/v1")
        val unknownLabFixture = fixture(labs = listOf(unknownLab))
        assertFailure(
            unknownLabFixture.build(),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )

        val unknownEvent = medicationEvent(
            eventId = uuid(11),
            route = PkRoute.ORAL,
            timeH = 0.0,
            eventTypeId = "event:unknown/v1",
        )
        val unknownEventFixture = fixture(events = listOf(unknownEvent))
        assertFailure(
            unknownEventFixture.build(),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )

        val panel = bloodPanel(uuid(1), 100.0, 100.0)
        val wrongUnit = requireNotNull(
            PkCalibrationE2LabSource.create(
                panel = panel,
                result = panel.results.single(),
                analyteId = AnalyteId,
                unitId = "hrttracker:unit/other/v1",
                providerId = ProviderId,
                assayMethodId = AssayId,
            )
        )
        val wrongUnitFixture = fixture(labs = listOf(wrongUnit))
        assertFailure(
            wrongUnitFixture.build(),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )

        assertNull(
            PkCalibrationIdentityPolicy.researchOrTest(
                builtinE2AnalyteId = AnalyteId,
                targetHormoneId = HormoneId,
                unitIdBySourceSnapshot = mapOf(SourceUnitSnapshot to UnitId),
                supportedProviderIds = setOf(ProviderId),
                supportedAssayMethodIds = setOf(AssayId),
                eventTypeIdByRoute = EventTypeIds,
                routeIdByRoute = RouteIds + (PkRoute.ORAL to "route:other/v1"),
                compoundIdByCompound = CompoundIds,
            )
        )
    }

    @Test
    fun includedRowsRequireExactDerivationAndClassifyNonFiniteAsSharedNumericFailure() {
        val mismatchedCanonical = lab(
            canonicalValuePgml = Math.nextUp(100.0),
        )
        assertFailure(
            fixture(labs = listOf(mismatchedCanonical)).build(),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )

        val nonFiniteSource = lab(
            sourceValue = Double.NaN,
            canonicalValuePgml = Double.NaN,
        )
        assertFailure(
            fixture(labs = listOf(nonFiniteSource)).build(),
            PkCalibrationEvidenceFailure.SHARED_NUMERIC_FAILURE,
        )

        val nonFiniteCanonical = lab(
            sourceValue = 100.0,
            canonicalValuePgml = Double.POSITIVE_INFINITY,
        )
        assertFailure(
            fixture(labs = listOf(nonFiniteCanonical)).build(),
            PkCalibrationEvidenceFailure.SHARED_NUMERIC_FAILURE,
        )

        val structurallyInvalidUnit = lab(
            sourceValue = Double.NaN,
            canonicalValuePgml = Double.NaN,
            sourceUnitSnapshot = "unsupported_unit",
            unitId = "hrttracker:unit/unsupported/v1",
        )
        val structurallyInvalidPolicy = identityPolicy(
            unitIdBySourceSnapshot = mapOf(
                "unsupported_unit" to "hrttracker:unit/unsupported/v1"
            )
        )
        assertFailure(
            fixture(labs = listOf(structurallyInvalidUnit)).build(
                identityPolicy = structurallyInvalidPolicy,
            ),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )
    }

    @Test
    fun excludedRowsSkipUnboundValueAndUnitValidationAndKeepReviewStable() {
        val original = lab(
            sourceUnitSnapshot = "legacy_unknown_unit",
            unitId = "unit:legacy/v1",
            sourceValue = Double.NaN,
            canonicalValuePgml = Double.NaN,
        )
        val fixture = fixture(labs = listOf(original))
        val excludedMetadata = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = original.resultId,
                disposition = E2CalibrationDisposition.EXCLUDED,
                acceptedRecord = null,
                updatedAt = Instant.EPOCH,
            )
        )

        val first = ready(fixture.build(metadata = listOf(excludedMetadata)))
        val changed = lab(
            resultId = original.resultId,
            sourceUnitSnapshot = "different_unknown_unit",
            unitId = "unit:different/v1",
            sourceValue = Double.POSITIVE_INFINITY,
            canonicalValuePgml = Double.NEGATIVE_INFINITY,
        )
        val second = ready(
            fixture.build(
                labs = listOf(changed),
                metadata = listOf(excludedMetadata),
            )
        )

        // ponytail: an excluded row has no acceptance record — it cannot be ACCEPTED as-is.
        assertNull(first.canonicalInput.acceptanceRecordFor(original.resultId))
        assertNull(second.canonicalInput.acceptanceRecordFor(original.resultId))
        assertNull(first.excluded.single().observedPgml)
        assertNull(second.excluded.single().observedPgml)
    }

    @Test
    fun timestampOverflowAndOutOfRangeInstantsFailClosed() {
        val result = bloodPanel(uuid(1), 100.0, 100.0).results.single()
        val extremePanel = bloodPanel(uuid(1), 100.0, 100.0).copy(
            collectedAt = Instant.MAX,
            results = listOf(result),
        )
        assertNull(
            PkCalibrationE2LabSource.create(
                panel = extremePanel,
                result = result,
                analyteId = AnalyteId,
                unitId = UnitId,
                providerId = ProviderId,
                assayMethodId = AssayId,
            )
        )
        val ordinaryEvent = medicationEvent(uuid(44), PkRoute.ORAL, 0.0).event
        assertNull(
            PkCalibrationMedicationEventSource.create(
                event = ordinaryEvent,
                epochMillis = 9_007_199_254_740_992L,
                eventTypeId = EventTypeIds.getValue(PkRoute.ORAL),
                hormoneId = HormoneId,
                routeId = RouteIds.getValue(PkRoute.ORAL),
                compoundId = CompoundIds.getValue(PkCompound.E2),
            )
        )

        val fixture = fixture()
        assertFailure(
            fixture.build(originMillis = Long.MIN_VALUE),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )

    }

    @Test
    fun nonpositiveAuthorizedNonExcludedE2IsGlobalButExcludedDoesNotBlock() {
        val zeroLab = lab(sourceValue = 0.0, canonicalValuePgml = 0.0)
        val fixture = fixture(labs = listOf(zeroLab))

        assertFailure(
            fixture.build(),
            PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2,
        )

        val excluded = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = zeroLab.resultId,
                disposition = E2CalibrationDisposition.EXCLUDED,
                acceptedRecord = null,
                updatedAt = Instant.EPOCH,
            )
        )
        val ready = ready(fixture.build(metadata = listOf(excluded)))
        assertEquals(1, ready.excluded.size)
        assertTrue(ready.included.isEmpty())
    }

    @Test
    fun invalidNonpositiveFailureCarriesOnlyDrugWindowLabIds() {
        // Phase-3 finding #4: a nonpositive lab drawn before any dose is
        // Unassigned and blocks nothing; only the drug-window lab may be
        // surfaced as blocking on the failure result.
        val blocking = lab(resultId = uuid(1), sourceValue = 0.0, canonicalValuePgml = 0.0)
        val preDose = lab(
            resultId = uuid(2),
            collectedAtEpochMillis = OriginMillis - 8 * HourMillis,
            sourceValue = 0.0,
            canonicalValuePgml = 0.0,
        )
        val fixture = fixture(labs = listOf(blocking, preDose))

        val failed = fixture.build() as PkCalibrationEvidenceBuildResult.Failed
        assertEquals(PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2, failed.failure)
        assertEquals(setOf(blocking.resultId), failed.invalidNonpositiveLabIds)

        val result = PkCalibrationEngine.compute(
            input = fixture.input(),
            metadata = emptyList(),
            identityPolicy = fixture.policy,
            config = fixture.config,
            attestationProvider = fixture.attestationProvider,
        )
        assertEquals(PkCalibrationGlobalState.SHARED_INPUT_INVALID, result.globalState)
        assertEquals(setOf(blocking.resultId), result.invalidNonpositiveLabIds)
    }

    @Test
    fun mixedRouteObservationIsIncludedWithFullBreakdown() {
        // v10.0 §A10.1: no dominance rule — an even route split is included.
        val evenSplit = breakdown(
            PkCalibrationRoute.INJECTION to 5.0,
            PkCalibrationRoute.ORAL to 5.0,
        )
        val included = classifyCalibrationObservation(100.0, evenSplit, 1.0)
            as PkCalibrationObservationClassification.Included
        assertEquals(evenSplit, included.breakdown)
    }

    @Test
    fun informativeSignalUsesExactThresholdWithZeroBelowIt() {
        val atThreshold = classifyCalibrationObservation(
            observedPgml = 100.0,
            breakdown = breakdown(PkCalibrationRoute.ORAL to 1.0),
            drugMinInformativePgml = 1.0,
        )
        assertTrue(atThreshold is PkCalibrationObservationClassification.Included)

        val oneBitBelow = classifyCalibrationObservation(
            observedPgml = 100.0,
            breakdown = breakdown(PkCalibrationRoute.ORAL to Math.nextDown(1.0)),
            drugMinInformativePgml = 1.0,
        ) as PkCalibrationObservationClassification.Unassigned
        assertEquals(Math.nextDown(1.0), oneBitBelow.totalDrugPgml, 0.0)

        val zeroSignal = classifyCalibrationObservation(
            observedPgml = 100.0,
            breakdown = breakdown(),
            drugMinInformativePgml = 1.0,
        ) as PkCalibrationObservationClassification.Unassigned
        assertEquals(0L, zeroSignal.totalDrugPgml.toBits())

        // Precedence: below-informative-signal wins over a nonpositive observation.
        assertTrue(
            classifyCalibrationObservation(
                observedPgml = 0.0,
                breakdown = breakdown(PkCalibrationRoute.ORAL to Math.nextDown(1.0)),
                drugMinInformativePgml = 1.0,
            ) is PkCalibrationObservationClassification.Unassigned
        )
    }

    @Test
    fun observationBelowOtherRoutePopulationContributionIsStillIncluded() {
        // v10.0 §A10.1: no positivity guard — an observation smaller than the
        // other routes' population contribution still joins the shared pool.
        val skewed = breakdown(
            PkCalibrationRoute.INJECTION to 8.0,
            PkCalibrationRoute.ORAL to 2.0,
        )
        val included = classifyCalibrationObservation(0.5, skewed, 1.0)
            as PkCalibrationObservationClassification.Included
        assertEquals(skewed, included.breakdown)
        assertTrue(
            classifyCalibrationObservation(0.0, skewed, 1.0) is
                    PkCalibrationObservationClassification.InvalidNonpositive
        )
    }

    @Test
    fun adapterIncludesMixedRouteLabInSharedPool() {
        val events = listOf(
            medicationEvent(uuid(11), PkRoute.ORAL, timeH = 0.0),
            medicationEvent(
                uuid(12),
                PkRoute.INJECTION,
                timeH = 0.0,
                compound = PkCompound.EV,
            ),
        )
        val forward = requireNotNull(
            PkE2ForwardModel.create(events.map { source -> source.event }, BodyWeightKg)
        )
        val breakdown = requireNotNull(forward.breakdownAt(8.0))
        assertTrue(
            PkCalibrationRoute.entries.count { route ->
                breakdown.byRouteDrugPgml.getValue(route) > 0.0
            } >= 2
        )
        val observed = breakdown.totalDrugPgml * 0.25
        val mixedLab = lab(
            collectedAtEpochMillis = OriginMillis + 8 * HourMillis,
            sourceValue = observed,
            canonicalValuePgml = observed,
        )
        val pool = ready(fixture(labs = listOf(mixedLab), events = events).build())

        val evidence = pool.included.single()
        assertEquals(PkCalibrationLabEvidenceState.INCLUDED, evidence.state)
        assertEquals(observed, requireNotNull(evidence.observedPgml), 0.0)
        assertEquals(
            requireNotNull(evidence.breakdown).totalDrugPgml.toBits(),
            requireNotNull(evidence.totalDrugPgml).toBits(),
        )
        assertTrue(pool.unassigned.isEmpty())
        assertTrue(pool.excluded.isEmpty())
    }

    @Test
    fun exclusionAndAcceptanceUseCurrentAcceptanceRecords() {
        val labs = listOf(
            lab(uuid(1), sourceValue = 100.0, canonicalValuePgml = 100.0),
            lab(uuid(2), sourceValue = 120.0, canonicalValuePgml = 120.0),
        )
        val fixture = fixture(labs = labs)
        val autoReady = ready(fixture.build())
        val currentRecord = requireNotNull(
            autoReady.canonicalInput.acceptanceRecordFor(uuid(1))
        )
        val accepted = requireNotNull(
            E2CalibrationMetadata.create(
                uuid(1),
                E2CalibrationDisposition.ACCEPTED,
                currentRecord,
                Instant.EPOCH,
            )
        )
        val acceptedReady = ready(fixture.build(metadata = listOf(accepted)))
        val acceptedEvidence = acceptedReady.included
            .single { evidence -> evidence.resultId == uuid(1) }
        assertEquals(PkCalibrationEffectiveDisposition.ACCEPTED, acceptedEvidence.effectiveDisposition)
        assertEquals(
            currentRecord,
            acceptedReady.canonicalInput.acceptanceRecordFor(uuid(1)),
        )

        val staleRecord = requireNotNull(
            PkCalibrationAcceptanceRecord.create(
                calibrationModelVersion = CalibrationModelVersion,
                sourceValueBits = "4058c00000000000",
                collectedAtEpochMillis = labs[0].collectedAtEpochMillis,
            )
        )
        val stale = requireNotNull(
            E2CalibrationMetadata.create(
                uuid(1),
                E2CalibrationDisposition.ACCEPTED,
                staleRecord,
                Instant.ofEpochMilli(999),
            )
        )
        val staleEvidence = ready(fixture.build(metadata = listOf(stale))).included
            .single { evidence -> evidence.resultId == uuid(1) }
        assertEquals(PkCalibrationEffectiveDisposition.AUTO, staleEvidence.effectiveDisposition)

        val excluded = requireNotNull(
            E2CalibrationMetadata.create(
                uuid(1),
                E2CalibrationDisposition.EXCLUDED,
                null,
                Instant.EPOCH,
            )
        )
        val excludedReady = ready(fixture.build(metadata = listOf(excluded)))
        val changedExcludedLab = lab(
            uuid(1),
            sourceValue = Math.nextUp(100.0),
            canonicalValuePgml = Math.nextUp(100.0),
        )
        val changedExcluded = ready(
            fixture.build(
                labs = listOf(changedExcludedLab, labs[1]),
                metadata = listOf(excluded),
            )
        )
        assertEquals(
            excludedReady.canonicalInput.acceptanceRecordFor(uuid(2)),
            changedExcluded.canonicalInput.acceptanceRecordFor(uuid(2)),
        )
    }

    @Test
    fun engineCompute_staleAcceptedRecordMakesTheOutlierActionableAgain() {
        val event = medicationEvent(uuid(201), PkRoute.ORAL, timeH = 0.0)
        val forward = requireNotNull(
            PkE2ForwardModel.create(listOf(event.event), BodyWeightKg)
        )
        val outlierId = uuid(205)
        val observations = listOf(
            Triple(uuid(202), 4.0, 0.0),
            Triple(uuid(203), 8.0, 0.0),
            Triple(uuid(204), 12.0, 0.0),
            Triple(outlierId, 16.0, 1.6),
        )
        val labs = observations.map { (resultId, timeH, qLogRatio) ->
            val population = requireNotNull(forward.breakdownAt(timeH)).totalDrugPgml
            val observed = population * exp(qLogRatio)
            check(population > 0.0 && observed.isFinite())
            lab(
                resultId = resultId,
                collectedAtEpochMillis = OriginMillis + (timeH * HourMillis).toLong(),
                sourceValue = observed,
                canonicalValuePgml = observed,
            )
        }
        val fixture = fixture(labs = labs, events = listOf(event))
        val currentRecord = requireNotNull(
            ready(fixture.build()).canonicalInput.acceptanceRecordFor(outlierId)
        )

        fun compute(
            metadata: List<E2CalibrationMetadata>,
            calibrationModelVersion: String,
        ): PkRouteCalibrationResult {
            val result = PkCalibrationEngine.compute(
                input = fixture.input(calibrationModelVersion = calibrationModelVersion),
                metadata = metadata,
                identityPolicy = fixture.policy,
                config = fixture.config,
                attestationProvider = fixture.attestationProvider,
            )
            assertEquals(PkCalibrationGlobalState.READY, result.globalState)
            return result.routeResults.single { route ->
                route.route == PkCalibrationRoute.ORAL
            }
        }

        val autoResult = compute(emptyList(), CalibrationModelVersion)
        assertTrue(outlierId in autoResult.unreviewedOutlierLabIds)

        val accepted = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = outlierId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedRecord = currentRecord,
                updatedAt = Instant.EPOCH,
            )
        )
        val acceptedResult = compute(listOf(accepted), CalibrationModelVersion)
        val staleResult = compute(listOf(accepted), "pk-calibration:test/v10")

        assertFalse(outlierId in acceptedResult.unreviewedOutlierLabIds)
        assertTrue(outlierId in staleResult.unreviewedOutlierLabIds)
        assertTrue(PkCalibrationReason.UNREVIEWED_OUTLIER in staleResult.reasons)
    }

    @Test
    fun includedSourceOneBitChangeStalesAcceptanceRecord() {
        val originalLab = lab(sourceValue = 100.0, canonicalValuePgml = 100.0)
        val changedLab = lab(
            resultId = originalLab.resultId,
            sourceValue = Math.nextUp(100.0),
            canonicalValuePgml = Math.nextUp(100.0),
        )

        val fixture = fixture(labs = listOf(originalLab))
        val originalRecord = ready(fixture.build()).canonicalInput
            .acceptanceRecordFor(originalLab.resultId)
        val changedRecord = ready(fixture.build(labs = listOf(changedLab))).canonicalInput
            .acceptanceRecordFor(originalLab.resultId)
        assertNotEquals(originalRecord, changedRecord)
    }

    @Test
    fun calibrationModelVersionInvalidatesAcceptedReview() {
        val fixture = fixture()
        val original = ready(fixture.build())
        val resultId = fixture.labs.single().resultId
        val accepted = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = resultId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedRecord = requireNotNull(
                    original.canonicalInput.acceptanceRecordFor(resultId)
                ),
                updatedAt = Instant.EPOCH,
            )
        )

        val acceptedReady = ready(fixture.build(metadata = listOf(accepted)))
        val changedVersionReady = ready(
            fixture.build(
                metadata = listOf(accepted),
                calibrationModelVersion = "pk-calibration:test/v10",
            )
        )

        assertEquals(
            PkCalibrationEffectiveDisposition.ACCEPTED,
            acceptedReady.included.single().effectiveDisposition,
        )
        assertEquals(
            PkCalibrationEffectiveDisposition.AUTO,
            changedVersionReady.included.single().effectiveDisposition,
        )
        assertNotEquals(
            acceptedReady.canonicalInput.acceptanceRecordFor(resultId),
            changedVersionReady.canonicalInput.acceptanceRecordFor(resultId),
        )
    }

    @Test
    fun otherRouteValueChangeKeepsAcceptance() {
        // v10.0 §A2: acceptance staleness is per-result. Editing a different lab
        // no longer invalidates an acceptance the way the whole-snapshot review
        // digest deliberately did.
        val input = routeSeparatedInput()
        val fixture = fixture(labs = input.labs, events = input.events)
        val original = ready(fixture.build())
        val acceptedResultId = input.labs[0].resultId
        val accepted = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = acceptedResultId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedRecord = requireNotNull(
                    original.canonicalInput.acceptanceRecordFor(acceptedResultId)
                ),
                updatedAt = Instant.EPOCH,
            )
        )
        val acceptedReady = ready(fixture.build(metadata = listOf(accepted)))
        val otherRouteLab = input.labs[1]
        val changedOtherRouteLab = lab(
            resultId = otherRouteLab.resultId,
            collectedAtEpochMillis = otherRouteLab.collectedAtEpochMillis,
            sourceValue = Math.nextUp(otherRouteLab.sourceValue),
            canonicalValuePgml = Math.nextUp(otherRouteLab.sourceValue),
        )
        val changedReady = ready(
            fixture.build(
                labs = listOf(input.labs[0], changedOtherRouteLab),
                metadata = listOf(accepted),
            )
        )

        val acceptedEvidence = acceptedReady.included
            .single { evidence -> evidence.resultId == acceptedResultId }
        val survivingEvidence = changedReady.included
            .single { evidence -> evidence.resultId == acceptedResultId }
        assertEquals(PkCalibrationEffectiveDisposition.ACCEPTED,
            acceptedEvidence.effectiveDisposition)
        assertEquals(PkCalibrationEffectiveDisposition.ACCEPTED,
            survivingEvidence.effectiveDisposition)
        assertEquals(
            acceptedReady.canonicalInput.acceptanceRecordFor(acceptedResultId),
            changedReady.canonicalInput.acceptanceRecordFor(acceptedResultId),
        )
    }

    private data class RouteSeparatedInput(
        val labs: List<PkCalibrationE2LabSource>,
        val events: List<PkCalibrationMedicationEventSource>,
    )

    private fun routeSeparatedInput(): RouteSeparatedInput {
        val events = listOf(
            medicationEvent(
                uuid(81),
                PkRoute.INJECTION,
                timeH = 0.0,
                compound = PkCompound.EV,
            ),
            medicationEvent(uuid(82), PkRoute.ORAL, timeH = 2_000.0),
        )
        val forward = requireNotNull(
            PkE2ForwardModel.create(events.map { source -> source.event }, BodyWeightKg)
        )
        val labs = listOf(uuid(83) to 8.0, uuid(84) to 2_008.0).map { (resultId, timeH) ->
            val observed = requireNotNull(forward.breakdownAt(timeH)).totalDrugPgml
            check(observed.isFinite() && observed > 0.0)
            lab(
                resultId = resultId,
                collectedAtEpochMillis = OriginMillis + (timeH * HourMillis).toLong(),
                sourceValue = observed,
                canonicalValuePgml = observed,
            )
        }
        return RouteSeparatedInput(labs = labs, events = events)
    }

    private data class Fixture(
        val labs: List<PkCalibrationE2LabSource>,
        val events: List<PkCalibrationMedicationEventSource>,
        val attestationProvider: PkCalibrationAttestationProvider,
        val policy: PkCalibrationIdentityPolicy,
        val config: PkCalibrationConfig,
    ) {
        fun input(
            labs: List<PkCalibrationE2LabSource> = this.labs,
            events: List<PkCalibrationMedicationEventSource> = this.events,
            weightKg: Double? = BodyWeightKg,
            originMillis: Long = OriginMillis,
            forwardModelVersion: String = ForwardModelVersion,
            calibrationModelVersion: String = CalibrationModelVersion,
        ): PkCalibrationInputSnapshot {
            return requireNotNull(
                PkCalibrationInputSnapshot.create(
                    labs = labs,
                    medicationEvents = events,
                    forwardTimeOriginEpochMillis = originMillis,
                    resolvedCurrentWeightKg = weightKg,
                    forwardModelVersion = forwardModelVersion,
                    calibrationModelVersion = calibrationModelVersion,
                )
            )
        }

        fun build(
            labs: List<PkCalibrationE2LabSource> = this.labs,
            events: List<PkCalibrationMedicationEventSource> = this.events,
            weightKg: Double? = BodyWeightKg,
            metadata: List<E2CalibrationMetadata> = emptyList(),
            identityPolicy: PkCalibrationIdentityPolicy = policy,
            config: PkCalibrationConfig = this.config,
            attestationProvider: PkCalibrationAttestationProvider = this.attestationProvider,
            originMillis: Long = OriginMillis,
            forwardModelVersion: String = ForwardModelVersion,
            calibrationModelVersion: String = CalibrationModelVersion,
        ): PkCalibrationEvidenceBuildResult {
            return PkCalibrationEvidenceAdapter.build(
                labs = labs,
                medicationEvents = events,
                forwardTimeOriginEpochMillis = originMillis,
                resolvedCurrentWeightKg = weightKg,
                metadata = metadata,
                identityPolicy = identityPolicy,
                config = config,
                attestationProvider = attestationProvider,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
            )
        }
    }

    private fun fixture(
        labs: List<PkCalibrationE2LabSource> = listOf(lab()),
        events: List<PkCalibrationMedicationEventSource> = listOf(
            medicationEvent(uuid(11), PkRoute.ORAL, timeH = 0.0)
        ),
    ): Fixture {
        return Fixture(
            labs = labs,
            events = events,
            attestationProvider = PkCalibrationAttestationProvider {
                PkCalibrationAttestation(1_234L)
            },
            policy = identityPolicy(),
            config = requireNotNull(
                PkCalibrationConfig.researchOrTest(
                    drugMinInformativePgml = 1e-12,
                    rLog = 0.04,
                )
            ),
        )
    }

    private fun identityPolicy(
        unitIdBySourceSnapshot: Map<String, String> = mapOf(SourceUnitSnapshot to UnitId),
    ): PkCalibrationIdentityPolicy {
        return requireNotNull(
            PkCalibrationIdentityPolicy.researchOrTest(
                builtinE2AnalyteId = AnalyteId,
                targetHormoneId = HormoneId,
                unitIdBySourceSnapshot = unitIdBySourceSnapshot,
                supportedProviderIds = setOf(ProviderId),
                supportedAssayMethodIds = setOf(AssayId),
                eventTypeIdByRoute = EventTypeIds,
                routeIdByRoute = RouteIds,
                compoundIdByCompound = CompoundIds,
            )
        )
    }

    private fun lab(
        resultId: UUID = uuid(1),
        collectedAtEpochMillis: Long = OriginMillis + 8 * HourMillis,
        sourceValue: Double = 100.0,
        canonicalValuePgml: Double = 100.0,
        analyteId: String = AnalyteId,
        sourceUnitSnapshot: String = SourceUnitSnapshot,
        unitId: String = UnitId,
        providerId: String = ProviderId,
        assayMethodId: String = AssayId,
    ): PkCalibrationE2LabSource {
        val panel = bloodPanel(
            resultId = resultId,
            sourceValue = sourceValue,
            canonicalValuePgml = canonicalValuePgml,
            collectedAtEpochMillis = collectedAtEpochMillis,
            sourceUnitSnapshot = sourceUnitSnapshot,
        )
        return requireNotNull(
            PkCalibrationE2LabSource.create(
                panel = panel,
                result = panel.results.single(),
                analyteId = analyteId,
                unitId = unitId,
                providerId = providerId,
                assayMethodId = assayMethodId,
            )
        )
    }

    private fun bloodPanel(
        resultId: UUID,
        sourceValue: Double,
        canonicalValuePgml: Double,
        collectedAtEpochMillis: Long = OriginMillis + 8 * HourMillis,
        sourceUnitSnapshot: String = SourceUnitSnapshot,
    ): BloodTestPanel {
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = Instant.EPOCH,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = sourceValue,
            unitSnapshot = sourceUnitSnapshot,
            canonicalValue = canonicalValuePgml,
        )
        return BloodTestPanel(
            uuid = uuid(resultId.leastSignificantBits + 1_000),
            collectedAt = Instant.ofEpochMilli(collectedAtEpochMillis),
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(result),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private fun medicationEvent(
        eventId: UUID,
        route: PkRoute,
        timeH: Double,
        doseMg: Double = 2.0,
        eventTypeId: String = EventTypeIds.getValue(route),
        compound: PkCompound = PkCompound.E2,
        epochMillis: Long = OriginMillis + (timeH * HourMillis).toLong(),
    ): PkCalibrationMedicationEventSource {
        val event = PkDoseEvent(
            id = eventId,
            sourceGroupUuid = null,
            hormone = PkHormone.ESTRADIOL,
            route = route,
            timeH = timeH,
            doseMg = doseMg,
            compound = compound,
        )
        return requireNotNull(
            PkCalibrationMedicationEventSource.create(
                event = event,
                epochMillis = epochMillis,
                eventTypeId = eventTypeId,
                hormoneId = HormoneId,
                routeId = RouteIds.getValue(route),
                compoundId = CompoundIds.getValue(compound),
            )
        )
    }

    private fun breakdown(
        vararg contributions: Pair<PkCalibrationRoute, Double>,
    ): PkForwardBreakdown {
        val values = contributions.toMap()
        val canonical = linkedMapOf<PkCalibrationRoute, Double>()
        for (route in PkCalibrationRoute.entries) {
            canonical[route] = values[route] ?: 0.0
        }
        return requireNotNull(PkForwardBreakdown.create(canonical))
    }

    private fun ready(
        result: PkCalibrationEvidenceBuildResult,
    ): PkCalibrationEvidencePool {
        return (result as PkCalibrationEvidenceBuildResult.Ready).pool
    }

    private fun assertFailure(
        result: PkCalibrationEvidenceBuildResult,
        expected: PkCalibrationEvidenceFailure,
    ) {
        assertEquals(expected, (result as PkCalibrationEvidenceBuildResult.Failed).failure)
    }

    private fun uuid(value: Long): UUID = UUID(0, value)

    private companion object {
        const val OriginMillis = 1_700_000_000_000L
        const val HourMillis = 3_600_000L
        const val JcsSafeIntegerMax = 9_007_199_254_740_991L
        const val BodyWeightKg = 70.0
        const val AnalyteId = "hrttracker:analyte/e2/v1"
        const val HormoneId = "hrttracker:hormone/estradiol/v1"
        const val UnitId = "hrttracker:unit/pg-ml/v1"
        const val SourceUnitSnapshot = "pg_ml"
        const val PmolUnitId = "hrttracker:unit/pmol-l/v1"
        const val PmolSourceUnitSnapshot = "pmol_l"
        const val ProviderId = "provider:test/v1"
        const val AssayId = "assay:test/v1"
        const val ForwardModelVersion = "pk-forward:test/v1"
        const val CalibrationModelVersion = "pk-calibration:test/v9"

        val EventTypeIds = linkedMapOf(
            PkRoute.INJECTION to "event:injection-dose/v1",
            PkRoute.PATCH_APPLY to "event:patch-apply/v1",
            PkRoute.PATCH_REMOVE to "event:patch-remove/v1",
            PkRoute.GEL to "event:gel-dose/v1",
            PkRoute.ORAL to "event:oral-dose/v1",
            PkRoute.SUBLINGUAL to "event:sublingual-dose/v1",
        )
        val RouteIds = linkedMapOf(
            PkRoute.INJECTION to "injection",
            PkRoute.PATCH_APPLY to "patch",
            PkRoute.PATCH_REMOVE to "patch",
            PkRoute.GEL to "gel",
            PkRoute.ORAL to "oral",
            PkRoute.SUBLINGUAL to "sublingual",
        )
        val CompoundIds = linkedMapOf(
            PkCompound.E2 to "compound:e2/v1",
            PkCompound.EB to "compound:eb/v1",
            PkCompound.EV to "compound:ev/v1",
            PkCompound.EC to "compound:ec/v1",
            PkCompound.EN to "compound:en/v1",
            PkCompound.EU to "compound:eu/v1",
        )
    }
}
