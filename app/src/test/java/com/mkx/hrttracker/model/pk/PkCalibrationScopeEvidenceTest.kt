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
import java.security.MessageDigest
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
            scopeDecisionProvider = fixture.provider,
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
            scopeDecisionProvider = fixture.provider,
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
            scopeDecisionProvider = ProductionUnavailablePkCalibrationScopeDecisionProvider,
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
                    acceptedReviewDigest = null,
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
            scopeDecisionProvider = fixture.provider,
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
    fun protocolSchemas_matchHardCodedCanonicalBytesAndSha256Fixtures() {
        val fixture = fixture()
        val canonicalInput = ready(fixture.build()).canonicalInput
        val lab = fixture.labs.single()

        assertTrue(canonicalInput.config === fixture.config)

        assertCanonicalFixture(
            ScopeResultCanonicalJson,
            ScopeResultSha256,
            lab.resultScopeDigest(),
        )
        assertCanonicalFixture(
            ScopeInputCanonicalJson,
            ScopeInputSha256,
            canonicalInput.scopeInputSnapshot.digest,
        )
        assertCanonicalFixture(
            ScopeDecisionCanonicalJson,
            ScopeDecisionSha256,
            canonicalInput.scopeDecisionDigest,
        )
        assertCanonicalFixture(
            OutlierReviewCanonicalJson,
            OutlierReviewSha256,
            requireNotNull(canonicalInput.reviewDigestFor(lab.resultId)),
        )

        assertFalse(ScopeResultCanonicalJson.contains("sourceValue"))
        assertFalse(ScopeResultCanonicalJson.contains("unitId"))
        assertTrue(ScopeInputCanonicalJson.contains("\"areaCm2\":null"))
        assertTrue(ScopeInputCanonicalJson.contains("\"releaseRateMcgPerDay\":null"))
        assertTrue(ScopeInputCanonicalJson.contains("\"sourceGroupUuid\":null"))
        assertTrue(ScopeInputCanonicalJson.contains("\"sublingualTheta\":null"))
        assertFalse(ScopeInputCanonicalJson.contains("\"timeH\""))
        assertFalse(ScopeDecisionCanonicalJson.contains("null"))
        assertTrue(
            OutlierReviewCanonicalJson.contains("\"excludedAuthorizedE2ResultIds\":[]")
        )
        assertTrue(
            OutlierReviewCanonicalJson.contains(
                "\"calibrationRouteId\":null,\"eventTypeId\":\"event:patch-remove/v1\""
            )
        )
        assertFalse(OutlierReviewCanonicalJson.contains("canonicalValuePgml"))
        assertFalse(OutlierReviewCanonicalJson.contains("sourceUnitSnapshot"))
        assertFalse(OutlierReviewCanonicalJson.contains("acceptedReviewDigest"))
        assertFalse(OutlierReviewCanonicalJson.contains("disposition"))
    }

    @Test
    fun scopeInputProtocolFixtures_bindRouteReassignmentAndNormalizeNegativeZeroDose() {
        val source = lab()
        val authorization = requireNotNull(
            PkAuthorizedE2Result.create(source.resultId, source.resultScopeDigest())
        )
        val reassigned = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = listOf(authorization),
                medicationEvents = listOf(
                    medicationEvent(uuid(11), PkRoute.GEL, timeH = 0.0)
                ),
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
        assertCanonicalFixture(
            RouteReassignedScopeInputCanonicalJson,
            RouteReassignedScopeInputSha256,
            reassigned.digest,
        )

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
                authorizedE2Results = listOf(authorization),
                medicationEvents = listOf(negativeZeroEvent),
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
        val positiveZeroInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = listOf(authorization),
                medicationEvents = listOf(positiveZeroEvent),
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )

        assertEquals(0L, negativeZeroEvent.event.doseMg.toRawBits())
        assertEquals("0000000000000000", (-0.0).toCanonicalBinary64Bits())
        assertEquals(positiveZeroInput.digest, negativeZeroInput.digest)
        assertCanonicalFixture(
            NegativeZeroDoseScopeInputCanonicalJson,
            NegativeZeroDoseScopeInputSha256,
            negativeZeroInput.digest,
        )
        assertNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = listOf(authorization),
                medicationEvents = listOf(negativeZeroEvent),
                resolvedCurrentWeightKg = -0.0,
                forwardModelVersion = ForwardModelVersion,
            )
        )
    }

    @Test
    fun outlierReviewProtocolFixture_bindsSuccessfulNonIdentityUnitConversion() {
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
        assertCanonicalFixture(
            ConvertedUnitOutlierReviewCanonicalJson,
            ConvertedUnitOutlierReviewSha256,
            requireNotNull(canonicalInput.reviewDigestFor(convertedLab.resultId)),
        )
    }

    @Test
    fun protocolFixtures_acceptBothSignedJcsTimestampEdges() {
        val minimumLab = lab(
            resultId = uuid(1),
            collectedAtEpochMillis = -JcsSafeIntegerMax,
        )
        val maximumLab = lab(
            resultId = uuid(2),
            collectedAtEpochMillis = JcsSafeIntegerMax,
        )
        assertCanonicalFixture(
            MinimumTimestampScopeResultCanonicalJson,
            MinimumTimestampScopeResultSha256,
            minimumLab.resultScopeDigest(),
        )
        assertCanonicalFixture(
            MaximumTimestampScopeResultCanonicalJson,
            MaximumTimestampScopeResultSha256,
            maximumLab.resultScopeDigest(),
        )

        val authorizations = listOf(minimumLab, maximumLab).map { lab ->
            requireNotNull(PkAuthorizedE2Result.create(lab.resultId, lab.resultScopeDigest()))
        }
        val timestampEdgeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = authorizations,
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
        assertCanonicalFixture(
            TimestampEdgesScopeInputCanonicalJson,
            TimestampEdgesScopeInputSha256,
            timestampEdgeInput.digest,
        )

        val minimumDecision = requireNotNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(99),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.ofEpochMilli(-JcsSafeIntegerMax),
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = timestampEdgeInput.digest,
                authorizedE2Results = authorizations,
            )
        )
        val maximumDecision = requireNotNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(99),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.ofEpochMilli(JcsSafeIntegerMax),
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = timestampEdgeInput.digest,
                authorizedE2Results = authorizations,
            )
        )
        assertCanonicalFixture(
            MinimumTimestampScopeDecisionCanonicalJson,
            MinimumTimestampScopeDecisionSha256,
            minimumDecision.digest(),
        )
        assertCanonicalFixture(
            MaximumTimestampScopeDecisionCanonicalJson,
            MaximumTimestampScopeDecisionSha256,
            maximumDecision.digest(),
        )
    }

    @Test
    fun scopeInputDigest_isOrderIndependentButOneBitSensitive() {
        val labs = listOf(
            lab(resultId = uuid(1), collectedAtEpochMillis = OriginMillis + 8 * HourMillis),
            lab(resultId = uuid(2), collectedAtEpochMillis = OriginMillis + 12 * HourMillis),
        )
        val authorizations = labs.map { lab ->
            requireNotNull(PkAuthorizedE2Result.create(lab.resultId, lab.resultScopeDigest()))
        }
        val events = listOf(
            medicationEvent(uuid(11), PkRoute.ORAL, timeH = 0.0),
            medicationEvent(uuid(12), PkRoute.GEL, timeH = 1.0),
        )
        val ordered = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizations,
                events,
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        val reordered = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizations.reversed(),
                events.reversed(),
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        val oneBitWeight = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizations,
                events,
                Math.nextUp(BodyWeightKg),
                ForwardModelVersion,
            )
        )

        assertEquals(ordered.digest, reordered.digest)
        assertEquals(
            listOf(uuid(1), uuid(2)),
            reordered.authorizedE2Results.map(PkAuthorizedE2Result::resultId),
        )
        assertEquals(listOf(uuid(11), uuid(12)), reordered.medicationEvents.map { it.event.id })
        assertNotEquals(ordered.digest, oneBitWeight.digest)
    }

    @Test
    fun scopeFactories_rejectDuplicatesEmptyAndUnresolvedIdentity() {
        val source = lab()
        val authorization = requireNotNull(
            PkAuthorizedE2Result.create(source.resultId, source.resultScopeDigest())
        )

        assertNull(
            PkCalibrationScopeInputSnapshot.create(
                emptyList(),
                emptyList(),
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        val validScopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                listOf(authorization),
                emptyList(),
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        assertNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(90),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.EPOCH,
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = validScopeInput.digest,
                authorizedE2Results = listOf(authorization, authorization),
            )
        )
        assertNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(90),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.EPOCH,
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = validScopeInput.digest,
                authorizedE2Results = emptyList(),
            )
        )
        assertNull(
            PkCalibrationScopeInputSnapshot.create(
                listOf(authorization, authorization),
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
            trustedPolicyVersions = setOf(PolicyVersion),
            trustedIssuerIds = setOf(IssuerId),
            trustedProvenanceRefs = setOf(ProvenanceRef),
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
            trustedPolicyVersions = setOf(PolicyVersion),
            trustedIssuerIds = setOf(IssuerId),
            trustedProvenanceRefs = setOf(ProvenanceRef),
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
        val unavailableProvider = ProductionUnavailablePkCalibrationScopeDecisionProvider

        assertFailure(
            fixture.build(
                scopeProvider = unavailableProvider,
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
        val throwingProvider = object : PkCalibrationScopeDecisionProvider {
            override fun currentDecision(): PkCalibrationScopeDecision? {
                error("trusted source unavailable")
            }
        }
        assertFailure(
            fixture.build(scopeProvider = throwingProvider),
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
                scopeProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(
                forwardModelVersion = "invalid model identity",
                scopeProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(
                events = listOf(invalidEvent),
                scopeProvider = unavailableProvider,
            ),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )
        assertFailure(
            fixture.build(forwardModelVersion = "invalid model identity"),
            PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID,
        )
    }

    @Test
    fun unknownPolicyProvenanceAndSourceIdentitiesFailClosed() {
        val fixture = fixture()
        val unknownPolicy = identityPolicy(
            trustedPolicyVersions = setOf("scope-policy:other/v1")
        )
        assertFailure(
            fixture.build(identityPolicy = unknownPolicy),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )

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
                trustedPolicyVersions = setOf(PolicyVersion),
                trustedIssuerIds = setOf(IssuerId),
                trustedProvenanceRefs = setOf(ProvenanceRef),
            )
        )
    }

    @Test
    fun unauthorizedExtraWithUnknownIdentityIsIgnored() {
        val fixture = fixture()
        val unauthorized = lab(
            resultId = uuid(77),
            analyteId = "analyte:unknown/v1",
            sourceUnitSnapshot = "unsupported_unit",
            unitId = "unit:unknown/v1",
            sourceValue = Double.NaN,
            canonicalValuePgml = Double.NaN,
        )

        val ready = ready(fixture.build(labs = fixture.labs + unauthorized))

        assertEquals(1, ready.authorizedE2ResultCount)
        assertEquals(fixture.labs.single().resultId, ready.canonicalInput.authorizedLabs.single().resultId)
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
                acceptedReviewDigest = null,
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

        assertEquals(first.canonicalInput.reviewDigestFor(original.resultId),
            second.canonicalInput.reviewDigestFor(original.resultId))
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

        val authorization = requireNotNull(
            PkAuthorizedE2Result.create(
                fixture.labs.single().resultId,
                fixture.labs.single().resultScopeDigest(),
            )
        )
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                listOf(authorization),
                fixture.events,
                BodyWeightKg,
                ForwardModelVersion,
            )
        )
        assertNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(99),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.MAX,
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = scopeInput.digest,
                authorizedE2Results = listOf(authorization),
            )
        )
        assertNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(99),
                revision = 9_007_199_254_740_992L,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.EPOCH,
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = scopeInput.digest,
                authorizedE2Results = listOf(authorization),
            )
        )
    }

    @Test
    fun scopeDigestMismatchesRevokeBeforeEvidenceEvaluation() {
        val fixture = fixture()
        val movedLab = lab(
            resultId = fixture.labs.single().resultId,
            collectedAtEpochMillis = fixture.labs.single().collectedAtEpochMillis + 1,
        )
        assertFailure(
            fixture.build(labs = listOf(movedLab)),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )

        val editedEvent = medicationEvent(
            eventId = fixture.events.single().event.id,
            route = PkRoute.ORAL,
            timeH = 0.0,
            doseMg = Math.nextUp(fixture.events.single().event.doseMg),
        )
        assertFailure(
            fixture.build(events = listOf(editedEvent)),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
        )

        assertFailure(
            fixture.build(weightKg = Math.nextUp(BodyWeightKg)),
            PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED,
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
                acceptedReviewDigest = null,
                updatedAt = Instant.EPOCH,
            )
        )
        val ready = ready(fixture.build(metadata = listOf(excluded)))
        assertEquals(1, ready.excluded.size)
        assertTrue(ready.routeEvidence.all { route -> route.dominantLabCount == 0 })
    }

    @Test
    fun dominanceUsesExactMultiplyComparisonForAllRoutes() {
        for (route in PkCalibrationRoute.entries) {
            val exact = breakdown(route to 8.0, otherRoute(route) to 2.0)
            val classification = classifyCalibrationObservation(100.0, exact, 1.0)
            val candidate = classification as PkCalibrationObservationClassification.Candidate
            assertEquals(route, candidate.route)
            assertTrue(candidate.included)
        }

        val below = Math.nextDown(8.0)
        val noOwner = breakdown(
            PkCalibrationRoute.INJECTION to below,
            PkCalibrationRoute.ORAL to 10.0 - below,
        )
        assertEquals(10.0, noOwner.totalDrugPgml, 0.0)
        assertTrue(
            classifyCalibrationObservation(100.0, noOwner, 1.0) is
                    PkCalibrationObservationClassification.Unassigned
        )

        val overlap = breakdown(
            PkCalibrationRoute.INJECTION to 5.0,
            PkCalibrationRoute.ORAL to 5.0,
        )
        val overlapResult = classifyCalibrationObservation(100.0, overlap, 1.0)
        assertEquals(
            PkCalibrationLabEvidenceState.UNASSIGNED_NO_DOMINANT_ROUTE,
            (overlapResult as PkCalibrationObservationClassification.Unassigned).state,
        )
    }

    @Test
    fun informativeSignalUsesExactThresholdWithZeroBelowIt() {
        val atThreshold = classifyCalibrationObservation(
            observedPgml = 100.0,
            breakdown = breakdown(PkCalibrationRoute.ORAL to 1.0),
            drugMinInformativePgml = 1.0,
        )
        assertTrue(atThreshold is PkCalibrationObservationClassification.Candidate)

        val oneBitBelow = classifyCalibrationObservation(
            observedPgml = 100.0,
            breakdown = breakdown(PkCalibrationRoute.ORAL to Math.nextDown(1.0)),
            drugMinInformativePgml = 1.0,
        ) as PkCalibrationObservationClassification.Unassigned
        assertEquals(
            PkCalibrationLabEvidenceState.UNASSIGNED_BELOW_INFORMATIVE_SIGNAL,
            oneBitBelow.state,
        )

        val zeroSignal = classifyCalibrationObservation(
            observedPgml = 100.0,
            breakdown = breakdown(),
            drugMinInformativePgml = 1.0,
        ) as PkCalibrationObservationClassification.Unassigned
        assertEquals(
            PkCalibrationLabEvidenceState.UNASSIGNED_BELOW_INFORMATIVE_SIGNAL,
            zeroSignal.state,
        )
        assertEquals(0L, zeroSignal.totalDrugPgml.toBits())
    }

    @Test
    fun residualAndGuardRespectPositiveZeroCancellationAndAbsoluteFloor() {
        val pureRoute = classifyCalibrationObservation(
            observedPgml = 0.5,
            breakdown = breakdown(PkCalibrationRoute.ORAL to 10.0),
            drugMinInformativePgml = 1e-20,
        ) as PkCalibrationObservationClassification.Candidate
        assertEquals(0L, pureRoute.otherRoutePopulationPgml.toBits())
        assertTrue(pureRoute.included)

        val cancellation = classifyCalibrationObservation(
            observedPgml = 2.0,
            breakdown = breakdown(
                PkCalibrationRoute.ORAL to 8.0,
                PkCalibrationRoute.INJECTION to 2.0,
            ),
            drugMinInformativePgml = 1e-20,
        ) as PkCalibrationObservationClassification.Candidate
        assertEquals(0L, cancellation.routeAttributableObservedPgml.toBits())
        assertFalse(cancellation.included)

        val tinySignal = breakdown(PkCalibrationRoute.ORAL to 1e-15)
        val absoluteFloorEquality = classifyCalibrationObservation(
            observedPgml = PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_PGML,
            breakdown = tinySignal,
            drugMinInformativePgml = 1e-20,
        ) as PkCalibrationObservationClassification.Candidate
        val absoluteFloorOneBitBelow = classifyCalibrationObservation(
            observedPgml = Math.nextDown(PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_PGML),
            breakdown = tinySignal,
            drugMinInformativePgml = 1e-20,
        ) as PkCalibrationObservationClassification.Candidate
        assertTrue(absoluteFloorEquality.included)
        assertFalse(absoluteFloorOneBitBelow.included)
    }

    @Test
    fun positivityGuardUsesExactSubtractionMaxAndEqualityInclusion() {
        val breakdown = breakdown(
            PkCalibrationRoute.INJECTION to 8.0,
            PkCalibrationRoute.ORAL to 2.0,
        )
        val equal = classifyCalibrationObservation(2.5, breakdown, 1.0)
            as PkCalibrationObservationClassification.Candidate
        val below = classifyCalibrationObservation(Math.nextDown(2.5), breakdown, 1.0)
            as PkCalibrationObservationClassification.Candidate

        assertEquals(2.0, equal.otherRoutePopulationPgml, 0.0)
        assertEquals(0.5, equal.routeAttributableObservedPgml, 0.0)
        assertTrue(equal.included)
        assertFalse(below.included)
        assertTrue(below.routeAttributableObservedPgml < 0.5)
    }

    @Test
    fun adapterKeepsCandidateAndIncludedCountsDistinct() {
        val event = medicationEvent(uuid(11), PkRoute.ORAL, timeH = 0.0)
        val forward = requireNotNull(
            PkE2ForwardModel.create(listOf(event.event), BodyWeightKg)
        )
        val total = requireNotNull(forward.breakdownAt(8.0)).totalDrugPgml
        val guard = PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_FRACTION * total
        val includedLab = lab(
            resultId = uuid(1),
            collectedAtEpochMillis = OriginMillis + 8 * HourMillis,
            sourceValue = guard,
            canonicalValuePgml = guard,
        )
        val droppedLab = lab(
            resultId = uuid(2),
            collectedAtEpochMillis = OriginMillis + 8 * HourMillis,
            sourceValue = Math.nextDown(guard),
            canonicalValuePgml = Math.nextDown(guard),
        )
        val fixture = fixture(labs = listOf(includedLab, droppedLab), events = listOf(event))
        val oral = ready(fixture.build()).routeEvidence
            .single { route -> route.route == PkCalibrationRoute.ORAL }

        assertEquals(2, oral.dominantCandidateLabCount)
        assertEquals(1, oral.dominantLabCount)
        assertEquals(
            setOf(
                PkCalibrationLabEvidenceState.INCLUDED,
                PkCalibrationLabEvidenceState.GUARD_DROPPED,
            ),
            oral.dominantCandidates.map(PkCalibrationLabEvidence::state).toSet(),
        )
    }

    @Test
    fun allGuardDroppedCandidatesStayIsolatedToTheirRoutes() {
        val input = routeSeparatedInput(included = false)
        val ready = ready(fixture(labs = input.labs, events = input.events).build())

        val injection = ready.routeEvidence.single { route ->
            route.route == PkCalibrationRoute.INJECTION
        }
        val oral = ready.routeEvidence.single { route ->
            route.route == PkCalibrationRoute.ORAL
        }
        assertEquals(1, injection.dominantCandidateLabCount)
        assertEquals(0, injection.dominantLabCount)
        assertEquals(input.labs[0].resultId, injection.dominantCandidates.single().resultId)
        assertEquals(1, oral.dominantCandidateLabCount)
        assertEquals(0, oral.dominantLabCount)
        assertEquals(input.labs[1].resultId, oral.dominantCandidates.single().resultId)
        assertTrue(
            ready.routeEvidence
                .filterNot { route ->
                    route.route == PkCalibrationRoute.INJECTION ||
                            route.route == PkCalibrationRoute.ORAL
                }
                .all { route -> route.dominantCandidateLabCount == 0 }
        )
        assertTrue(ready.unassigned.isEmpty())
        assertTrue(
            ready.routeEvidence
                .flatMap(PkCalibrationRouteEvidence::dominantCandidates)
                .all { evidence -> evidence.state == PkCalibrationLabEvidenceState.GUARD_DROPPED }
        )
    }

    @Test
    fun exclusionAndAcceptanceUseNonrecursiveWholeReviewDigests() {
        val labs = listOf(
            lab(uuid(1), sourceValue = 100.0, canonicalValuePgml = 100.0),
            lab(uuid(2), sourceValue = 120.0, canonicalValuePgml = 120.0),
        )
        val fixture = fixture(labs = labs)
        val autoReady = ready(fixture.build())
        val currentDigest = requireNotNull(
            autoReady.canonicalInput.reviewDigestFor(uuid(1))
        )
        val accepted = requireNotNull(
            E2CalibrationMetadata.create(
                uuid(1),
                E2CalibrationDisposition.ACCEPTED,
                currentDigest,
                Instant.EPOCH,
            )
        )
        val acceptedReady = ready(fixture.build(metadata = listOf(accepted)))
        val acceptedEvidence = acceptedReady.routeEvidence
            .flatMap(PkCalibrationRouteEvidence::dominantCandidates)
            .single { evidence -> evidence.resultId == uuid(1) }
        assertEquals(PkCalibrationEffectiveDisposition.ACCEPTED, acceptedEvidence.effectiveDisposition)
        assertEquals(
            currentDigest,
            acceptedReady.canonicalInput.reviewDigestFor(uuid(1)),
        )

        val staleDigest = requireNotNull(
            CanonicalDigest.create(
                PK_CALIBRATION_OUTLIER_REVIEW_DIGEST_SCHEMA,
                "SHA-256",
                "f".repeat(64),
            )
        )
        val stale = requireNotNull(
            E2CalibrationMetadata.create(
                uuid(1),
                E2CalibrationDisposition.ACCEPTED,
                staleDigest,
                Instant.ofEpochMilli(999),
            )
        )
        val staleEvidence = ready(fixture.build(metadata = listOf(stale))).routeEvidence
            .flatMap(PkCalibrationRouteEvidence::dominantCandidates)
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
        assertNotEquals(
            currentDigest,
            excludedReady.canonicalInput.reviewDigestFor(uuid(1)),
        )

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
            excludedReady.canonicalInput.reviewDigestFor(uuid(2)),
            changedExcluded.canonicalInput.reviewDigestFor(uuid(2)),
        )
    }

    @Test
    fun engineCompute_staleAcceptedDigestMakesTheOutlierActionableAgain() {
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
        val currentDigest = requireNotNull(
            ready(fixture.build()).canonicalInput.reviewDigestFor(outlierId)
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
                scopeDecisionProvider = fixture.provider,
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
                acceptedReviewDigest = currentDigest,
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
    fun includedSourceOneBitChangeChangesReviewButNotScopeAuthorization() {
        val originalLab = lab(sourceValue = 100.0, canonicalValuePgml = 100.0)
        val changedLab = lab(
            resultId = originalLab.resultId,
            sourceValue = Math.nextUp(100.0),
            canonicalValuePgml = Math.nextUp(100.0),
        )
        assertEquals(originalLab.resultScopeDigest(), changedLab.resultScopeDigest())

        val fixture = fixture(labs = listOf(originalLab))
        val originalDigest = ready(fixture.build()).canonicalInput
            .reviewDigestFor(originalLab.resultId)
        val changedDigest = ready(fixture.build(labs = listOf(changedLab))).canonicalInput
            .reviewDigestFor(originalLab.resultId)
        assertNotEquals(originalDigest, changedDigest)
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
                acceptedReviewDigest = original.canonicalInput.reviewDigestFor(resultId),
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
            acceptedReady.routeEvidence
                .flatMap(PkCalibrationRouteEvidence::dominantCandidates)
                .single()
                .effectiveDisposition,
        )
        assertEquals(
            PkCalibrationEffectiveDisposition.AUTO,
            changedVersionReady.routeEvidence
                .flatMap(PkCalibrationRouteEvidence::dominantCandidates)
                .single()
                .effectiveDisposition,
        )
        assertNotEquals(
            acceptedReady.canonicalInput.reviewDigestFor(resultId),
            changedVersionReady.canonicalInput.reviewDigestFor(resultId),
        )
    }

    @Test
    fun otherRouteValueChangeInvalidatesWholeReviewAcceptance() {
        val input = routeSeparatedInput(included = true)
        val fixture = fixture(labs = input.labs, events = input.events)
        val original = ready(fixture.build())
        val acceptedResultId = input.labs[0].resultId
        val accepted = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = acceptedResultId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedReviewDigest = original.canonicalInput.reviewDigestFor(acceptedResultId),
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

        val acceptedEvidence = acceptedReady.routeEvidence
            .single { route -> route.route == PkCalibrationRoute.INJECTION }
            .dominantCandidates.single()
        val invalidatedEvidence = changedReady.routeEvidence
            .single { route -> route.route == PkCalibrationRoute.INJECTION }
            .dominantCandidates.single()
        assertEquals(PkCalibrationEffectiveDisposition.ACCEPTED,
            acceptedEvidence.effectiveDisposition)
        assertEquals(PkCalibrationEffectiveDisposition.AUTO,
            invalidatedEvidence.effectiveDisposition)
        assertNotEquals(
            acceptedReady.canonicalInput.reviewDigestFor(acceptedResultId),
            changedReady.canonicalInput.reviewDigestFor(acceptedResultId),
        )
    }

    private data class RouteSeparatedInput(
        val labs: List<PkCalibrationE2LabSource>,
        val events: List<PkCalibrationMedicationEventSource>,
    )

    private fun routeSeparatedInput(included: Boolean): RouteSeparatedInput {
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
        val observations = listOf(
            Triple(uuid(83), PkCalibrationRoute.INJECTION, 8.0),
            Triple(uuid(84), PkCalibrationRoute.ORAL, 2_008.0),
        )
        val labs = observations.map { (resultId, route, timeH) ->
            val breakdown = requireNotNull(forward.breakdownAt(timeH))
            val dominant = breakdown.byRouteDrugPgml.getValue(route)
            val baseline = (breakdown.totalDrugPgml - dominant).let { value ->
                if (value == 0.0) 0.0 else value
            }
            val guard = kotlin.math.max(
                PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_FRACTION *
                        breakdown.totalDrugPgml,
                PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_PGML,
            )
            val attributable = if (included) guard * 2.0 else guard * 0.25
            val observed = baseline + attributable
            val classification = classifyCalibrationObservation(
                observedPgml = observed,
                breakdown = breakdown,
                drugMinInformativePgml = 1e-20,
            ) as PkCalibrationObservationClassification.Candidate
            check(classification.route == route && classification.included == included)
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
        val provider: PkCalibrationScopeDecisionProvider,
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
            scopeProvider: PkCalibrationScopeDecisionProvider = provider,
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
                scopeDecisionProvider = scopeProvider,
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
        val authorizations = labs.map { source ->
            requireNotNull(
                PkAuthorizedE2Result.create(source.resultId, source.resultScopeDigest())
            )
        }
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = authorizations,
                medicationEvents = events,
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
        val decision = requireNotNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(99),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.ofEpochMilli(1234),
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = scopeInput.digest,
                authorizedE2Results = authorizations,
            )
        )
        val provider = requireNotNull(
            ResearchOrTestPkCalibrationScopeDecisionProvider.create(
                decision,
                PolicyVersion,
                IssuerId,
                ProvenanceRef,
            )
        )
        return Fixture(
            labs = labs,
            events = events,
            provider = provider,
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
        trustedPolicyVersions: Set<String> = setOf(PolicyVersion),
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
                trustedPolicyVersions = trustedPolicyVersions,
                trustedIssuerIds = setOf(IssuerId),
                trustedProvenanceRefs = setOf(ProvenanceRef),
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

    private fun otherRoute(route: PkCalibrationRoute): PkCalibrationRoute {
        return if (route == PkCalibrationRoute.INJECTION) {
            PkCalibrationRoute.ORAL
        } else {
            PkCalibrationRoute.INJECTION
        }
    }

    private fun ready(
        result: PkCalibrationEvidenceBuildResult,
    ): PkCalibrationEvidencePartition {
        return (result as PkCalibrationEvidenceBuildResult.Ready).partition
    }

    private fun assertFailure(
        result: PkCalibrationEvidenceBuildResult,
        expected: PkCalibrationEvidenceFailure,
    ) {
        assertEquals(expected, (result as PkCalibrationEvidenceBuildResult.Failed).failure)
    }

    private fun assertCanonicalFixture(
        expectedCanonicalJson: String,
        expectedSha256: String,
        actualDigest: CanonicalDigest,
    ) {
        val expectedBytes = expectedCanonicalJson.toByteArray(StandardCharsets.UTF_8)
        val fixtureSha256 = MessageDigest.getInstance("SHA-256")
            .digest(expectedBytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        assertFalse(expectedCanonicalJson.endsWith('\n'))
        assertEquals(expectedSha256, fixtureSha256)
        assertEquals("SHA-256", actualDigest.algorithm)
        assertEquals(expectedSha256, actualDigest.hexLower)
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
        const val PolicyVersion = "scope-policy:test/v1"
        const val IssuerId = "issuer:test/v1"
        const val ProvenanceRef = "urn:test:scope-provenance:v1"
        const val ForwardModelVersion = "pk-forward:test/v1"
        const val CalibrationModelVersion = "pk-calibration:test/v9"

        const val ScopeResultSha256 =
            "2693c3f5f1863cabd0ded54e9aa32d82cc44b572da629719edd6340dbb736231"
        const val ScopeResultCanonicalJson =
            """{"analyteId":"hrttracker:analyte/e2/v1","assayMethodId":"assay:test/v1","collectedAt":1700028800000,"providerId":"provider:test/v1","resultId":"00000000-0000-0000-0000-000000000001","schema":"hrttracker.calibration-scope-result/v1"}"""

        const val ScopeInputSha256 =
            "eba73f49bbfd74522a84cc56680dd0793dfa9be9149d9a0dee71ff1153350ddb"
        const val ScopeInputCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"2693c3f5f1863cabd0ded54e9aa32d82cc44b572da629719edd6340dbb736231","schema":"hrttracker.calibration-scope-result/v1"}}],"forwardModelVersion":"pk-forward:test/v1","medicationEvents":[{"areaCm2":null,"compoundId":"compound:e2/v1","doseMg":"4000000000000000","epochMillis":1700000000000,"eventTypeId":"event:oral-dose/v1","eventUuid":"00000000-0000-0000-0000-00000000000b","hormoneId":"hrttracker:hormone/estradiol/v1","releaseRateMcgPerDay":null,"routeId":"oral","sourceGroupUuid":null,"sublingualTheta":null}],"resolvedCurrentWeight":"4051800000000000","schema":"hrttracker.calibration-scope-input/v1"}"""

        const val RouteReassignedScopeInputSha256 =
            "3ca8003343bb8606ce548b599bf8f44e99c18c77855250b8848bfeb41a5f4a7b"
        const val RouteReassignedScopeInputCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"2693c3f5f1863cabd0ded54e9aa32d82cc44b572da629719edd6340dbb736231","schema":"hrttracker.calibration-scope-result/v1"}}],"forwardModelVersion":"pk-forward:test/v1","medicationEvents":[{"areaCm2":null,"compoundId":"compound:e2/v1","doseMg":"4000000000000000","epochMillis":1700000000000,"eventTypeId":"event:gel-dose/v1","eventUuid":"00000000-0000-0000-0000-00000000000b","hormoneId":"hrttracker:hormone/estradiol/v1","releaseRateMcgPerDay":null,"routeId":"gel","sourceGroupUuid":null,"sublingualTheta":null}],"resolvedCurrentWeight":"4051800000000000","schema":"hrttracker.calibration-scope-input/v1"}"""

        const val NegativeZeroDoseScopeInputSha256 =
            "6bb1afcf24b25de4dd26f8655a89ff5f3507b08cacf64ede1ea09dd4c0639185"
        const val NegativeZeroDoseScopeInputCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"2693c3f5f1863cabd0ded54e9aa32d82cc44b572da629719edd6340dbb736231","schema":"hrttracker.calibration-scope-result/v1"}}],"forwardModelVersion":"pk-forward:test/v1","medicationEvents":[{"areaCm2":null,"compoundId":"compound:e2/v1","doseMg":"0000000000000000","epochMillis":1700000000000,"eventTypeId":"event:oral-dose/v1","eventUuid":"00000000-0000-0000-0000-00000000000b","hormoneId":"hrttracker:hormone/estradiol/v1","releaseRateMcgPerDay":null,"routeId":"oral","sourceGroupUuid":null,"sublingualTheta":null}],"resolvedCurrentWeight":"4051800000000000","schema":"hrttracker.calibration-scope-input/v1"}"""

        const val MinimumTimestampScopeResultSha256 =
            "29b13127c1584b640f1fd711bf854eb825e1e53fb4648ef3bdcb38474f4972f4"
        const val MinimumTimestampScopeResultCanonicalJson =
            """{"analyteId":"hrttracker:analyte/e2/v1","assayMethodId":"assay:test/v1","collectedAt":-9007199254740991,"providerId":"provider:test/v1","resultId":"00000000-0000-0000-0000-000000000001","schema":"hrttracker.calibration-scope-result/v1"}"""

        const val MaximumTimestampScopeResultSha256 =
            "27f7a04e4c231782d988bddd33dcf11656ffa2f8fa2199388f4759322bebdca4"
        const val MaximumTimestampScopeResultCanonicalJson =
            """{"analyteId":"hrttracker:analyte/e2/v1","assayMethodId":"assay:test/v1","collectedAt":9007199254740991,"providerId":"provider:test/v1","resultId":"00000000-0000-0000-0000-000000000002","schema":"hrttracker.calibration-scope-result/v1"}"""

        const val TimestampEdgesScopeInputSha256 =
            "6a7100d58f19f23b0a3f333adf10f3b13257abe7a3d1739cedc6edfc4bc3be1e"
        const val TimestampEdgesScopeInputCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"29b13127c1584b640f1fd711bf854eb825e1e53fb4648ef3bdcb38474f4972f4","schema":"hrttracker.calibration-scope-result/v1"}},{"resultId":"00000000-0000-0000-0000-000000000002","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"27f7a04e4c231782d988bddd33dcf11656ffa2f8fa2199388f4759322bebdca4","schema":"hrttracker.calibration-scope-result/v1"}}],"forwardModelVersion":"pk-forward:test/v1","medicationEvents":[{"areaCm2":null,"compoundId":"compound:e2/v1","doseMg":"4000000000000000","epochMillis":-9007199254740991,"eventTypeId":"event:oral-dose/v1","eventUuid":"00000000-0000-0000-0000-00000000000b","hormoneId":"hrttracker:hormone/estradiol/v1","releaseRateMcgPerDay":null,"routeId":"oral","sourceGroupUuid":null,"sublingualTheta":null},{"areaCm2":null,"compoundId":"compound:e2/v1","doseMg":"4000000000000000","epochMillis":9007199254740991,"eventTypeId":"event:oral-dose/v1","eventUuid":"00000000-0000-0000-0000-00000000000c","hormoneId":"hrttracker:hormone/estradiol/v1","releaseRateMcgPerDay":null,"routeId":"oral","sourceGroupUuid":null,"sublingualTheta":null}],"resolvedCurrentWeight":"4051800000000000","schema":"hrttracker.calibration-scope-input/v1"}"""

        const val MinimumTimestampScopeDecisionSha256 =
            "9f7c40f624e64665ebffa1017830e2c929607d256e61ce7097866fc3f1a60cb7"
        const val MinimumTimestampScopeDecisionCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"29b13127c1584b640f1fd711bf854eb825e1e53fb4648ef3bdcb38474f4972f4","schema":"hrttracker.calibration-scope-result/v1"}},{"resultId":"00000000-0000-0000-0000-000000000002","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"27f7a04e4c231782d988bddd33dcf11656ffa2f8fa2199388f4759322bebdca4","schema":"hrttracker.calibration-scope-result/v1"}}],"decisionId":"00000000-0000-0000-0000-000000000063","inputSnapshotDigest":{"algorithm":"SHA-256","hexLower":"6a7100d58f19f23b0a3f333adf10f3b13257abe7a3d1739cedc6edfc4bc3be1e","schema":"hrttracker.calibration-scope-input/v1"},"issuedAt":-9007199254740991,"issuerId":"issuer:test/v1","policyVersion":"scope-policy:test/v1","provenanceRef":"urn:test:scope-provenance:v1","revision":1,"schema":"hrttracker.calibration-scope-decision/v1"}"""

        const val MaximumTimestampScopeDecisionSha256 =
            "d3001c9728396dd1d027ddac23322219abccbaf188f8a39f0a46014865b6f282"
        const val MaximumTimestampScopeDecisionCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"29b13127c1584b640f1fd711bf854eb825e1e53fb4648ef3bdcb38474f4972f4","schema":"hrttracker.calibration-scope-result/v1"}},{"resultId":"00000000-0000-0000-0000-000000000002","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"27f7a04e4c231782d988bddd33dcf11656ffa2f8fa2199388f4759322bebdca4","schema":"hrttracker.calibration-scope-result/v1"}}],"decisionId":"00000000-0000-0000-0000-000000000063","inputSnapshotDigest":{"algorithm":"SHA-256","hexLower":"6a7100d58f19f23b0a3f333adf10f3b13257abe7a3d1739cedc6edfc4bc3be1e","schema":"hrttracker.calibration-scope-input/v1"},"issuedAt":9007199254740991,"issuerId":"issuer:test/v1","policyVersion":"scope-policy:test/v1","provenanceRef":"urn:test:scope-provenance:v1","revision":1,"schema":"hrttracker.calibration-scope-decision/v1"}"""

        const val ScopeDecisionSha256 =
            "7bb0d143af02c1ec92018a57c5c08ef1b4d0b581f715f0ac8287dc218d267924"
        const val ScopeDecisionCanonicalJson =
            """{"authorizedE2Results":[{"resultId":"00000000-0000-0000-0000-000000000001","resultScopeDigest":{"algorithm":"SHA-256","hexLower":"2693c3f5f1863cabd0ded54e9aa32d82cc44b572da629719edd6340dbb736231","schema":"hrttracker.calibration-scope-result/v1"}}],"decisionId":"00000000-0000-0000-0000-000000000063","inputSnapshotDigest":{"algorithm":"SHA-256","hexLower":"eba73f49bbfd74522a84cc56680dd0793dfa9be9149d9a0dee71ff1153350ddb","schema":"hrttracker.calibration-scope-input/v1"},"issuedAt":1234,"issuerId":"issuer:test/v1","policyVersion":"scope-policy:test/v1","provenanceRef":"urn:test:scope-provenance:v1","revision":1,"schema":"hrttracker.calibration-scope-decision/v1"}"""

        const val OutlierReviewSha256 =
            "85089d6d85b3a1cb0e2dcb05f3d889114a417dc493f1280779ca6944f77c10c4"
        const val OutlierReviewCanonicalJson =
            """{"calibrationModelVersion":"pk-calibration:test/v9","constants":{"canonicalRouteOrder":["injection","patch","gel","oral","sublingual"],"displayScaleCaps":[{"maxInclusive":"4000000000000000","minInclusive":"3fe0000000000000","routeId":"injection"},{"maxInclusive":"4000000000000000","minInclusive":"3fe0000000000000","routeId":"patch"},{"maxInclusive":"4008000000000000","minInclusive":"3fd0000000000000","routeId":"gel"},{"maxInclusive":"4000000000000000","minInclusive":"3fe0000000000000","routeId":"oral"},{"maxInclusive":"4008000000000000","minInclusive":"3fd0000000000000","routeId":"sublingual"}],"dominanceLaw":"d>=w0*D","dominantRouteShareMin":"3fe999999999999a","drugMinInformativePgml":"3d719799812dea11","drugSignalLogRangeMin":"3fe62e42fefa39ef","eventRouteMapping":[{"calibrationRouteId":"injection","eventTypeId":"event:injection-dose/v1","routeId":"injection"},{"calibrationRouteId":"patch","eventTypeId":"event:patch-apply/v1","routeId":"patch"},{"calibrationRouteId":null,"eventTypeId":"event:patch-remove/v1","routeId":"patch"},{"calibrationRouteId":"gel","eventTypeId":"event:gel-dose/v1","routeId":"gel"},{"calibrationRouteId":"oral","eventTypeId":"event:oral-dose/v1","routeId":"oral"},{"calibrationRouteId":"sublingual","eventTypeId":"event:sublingual-dose/v1","routeId":"sublingual"}],"exactRouteResidualLaw":"a=y-(D-d)","extremeScaleCoreMax":"4000000000000000","extremeScaleCoreMin":"3fe0000000000000","globalSearchNumericGuardAbsBeta":"4034000000000000","minDominantLabsForExtremeScale":3,"minDominantLabsForPromotion":2,"outlierWeightMin":"3fd0000000000000","rLog":"3fa47ae147ae147b","robustRmseGateFactor":"4000000000000000","routeAttributableMinFraction":"3fa999999999999a","routeAttributableMinPgml":"3d719799812dea11","routeLogScalePosteriorSdMaxForFullCalibration":"3fc999999999999a","routeLogScalePriorSd":"3fd3333333333333","stationaryIntervalMaxCellsPerRoute":50000,"stationaryRootBetaAbsTol":"3d719799812dea11","stationaryRootEnclosureBetaTol":"3d719799812dea11","stationaryRootMaxEval":200,"stationaryRootMinSeparation":"3e112e0be826d695","studentTNu":"4010000000000000","targetCompoundIds":["compound:e2/v1","compound:eb/v1","compound:ev/v1","compound:ec/v1","compound:en/v1","compound:eu/v1"],"targetHormoneId":"hrttracker:hormone/estradiol/v1"},"excludedAuthorizedE2ResultIds":[],"forwardModelVersion":"pk-forward:test/v1","includedAuthorizedE2Results":[{"analyteId":"hrttracker:analyte/e2/v1","assayMethodId":"assay:test/v1","collectedAt":1700028800000,"providerId":"provider:test/v1","resultId":"00000000-0000-0000-0000-000000000001","sourceValue":"4059000000000000","unitId":"hrttracker:unit/pg-ml/v1"}],"schema":"hrttracker.calibration-outlier-review/v1","scopeDecisionDigest":{"algorithm":"SHA-256","hexLower":"7bb0d143af02c1ec92018a57c5c08ef1b4d0b581f715f0ac8287dc218d267924","schema":"hrttracker.calibration-scope-decision/v1"},"targetResultId":"00000000-0000-0000-0000-000000000001"}"""

        const val ConvertedUnitOutlierReviewSha256 =
            "d66f2d5ab42843d32bc5224a8c804943ea6cddf5a3e2accedc197f5221f930ad"
        const val ConvertedUnitOutlierReviewCanonicalJson =
            """{"calibrationModelVersion":"pk-calibration:test/v9","constants":{"canonicalRouteOrder":["injection","patch","gel","oral","sublingual"],"displayScaleCaps":[{"maxInclusive":"4000000000000000","minInclusive":"3fe0000000000000","routeId":"injection"},{"maxInclusive":"4000000000000000","minInclusive":"3fe0000000000000","routeId":"patch"},{"maxInclusive":"4008000000000000","minInclusive":"3fd0000000000000","routeId":"gel"},{"maxInclusive":"4000000000000000","minInclusive":"3fe0000000000000","routeId":"oral"},{"maxInclusive":"4008000000000000","minInclusive":"3fd0000000000000","routeId":"sublingual"}],"dominanceLaw":"d>=w0*D","dominantRouteShareMin":"3fe999999999999a","drugMinInformativePgml":"3d719799812dea11","drugSignalLogRangeMin":"3fe62e42fefa39ef","eventRouteMapping":[{"calibrationRouteId":"injection","eventTypeId":"event:injection-dose/v1","routeId":"injection"},{"calibrationRouteId":"patch","eventTypeId":"event:patch-apply/v1","routeId":"patch"},{"calibrationRouteId":null,"eventTypeId":"event:patch-remove/v1","routeId":"patch"},{"calibrationRouteId":"gel","eventTypeId":"event:gel-dose/v1","routeId":"gel"},{"calibrationRouteId":"oral","eventTypeId":"event:oral-dose/v1","routeId":"oral"},{"calibrationRouteId":"sublingual","eventTypeId":"event:sublingual-dose/v1","routeId":"sublingual"}],"exactRouteResidualLaw":"a=y-(D-d)","extremeScaleCoreMax":"4000000000000000","extremeScaleCoreMin":"3fe0000000000000","globalSearchNumericGuardAbsBeta":"4034000000000000","minDominantLabsForExtremeScale":3,"minDominantLabsForPromotion":2,"outlierWeightMin":"3fd0000000000000","rLog":"3fa47ae147ae147b","robustRmseGateFactor":"4000000000000000","routeAttributableMinFraction":"3fa999999999999a","routeAttributableMinPgml":"3d719799812dea11","routeLogScalePosteriorSdMaxForFullCalibration":"3fc999999999999a","routeLogScalePriorSd":"3fd3333333333333","stationaryIntervalMaxCellsPerRoute":50000,"stationaryRootBetaAbsTol":"3d719799812dea11","stationaryRootEnclosureBetaTol":"3d719799812dea11","stationaryRootMaxEval":200,"stationaryRootMinSeparation":"3e112e0be826d695","studentTNu":"4010000000000000","targetCompoundIds":["compound:e2/v1","compound:eb/v1","compound:ev/v1","compound:ec/v1","compound:en/v1","compound:eu/v1"],"targetHormoneId":"hrttracker:hormone/estradiol/v1"},"excludedAuthorizedE2ResultIds":[],"forwardModelVersion":"pk-forward:test/v1","includedAuthorizedE2Results":[{"analyteId":"hrttracker:analyte/e2/v1","assayMethodId":"assay:test/v1","collectedAt":1700028800000,"providerId":"provider:test/v1","resultId":"00000000-0000-0000-0000-000000000001","sourceValue":"4076f1999999999a","unitId":"hrttracker:unit/pmol-l/v1"}],"schema":"hrttracker.calibration-outlier-review/v1","scopeDecisionDigest":{"algorithm":"SHA-256","hexLower":"7bb0d143af02c1ec92018a57c5c08ef1b4d0b581f715f0ac8287dc218d267924","schema":"hrttracker.calibration-scope-decision/v1"},"targetResultId":"00000000-0000-0000-0000-000000000001"}"""

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
