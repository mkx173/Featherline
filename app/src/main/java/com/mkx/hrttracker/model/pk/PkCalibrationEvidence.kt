package com.mkx.hrttracker.model.pk

import java.util.Collections
import java.util.UUID
import kotlin.math.max

enum class PkCalibrationEvidenceFailure {
    SCOPE_NOT_CONFIRMED,
    SHARED_INPUT_INVALID,
    SHARED_NUMERIC_FAILURE,
    INVALID_NONPOSITIVE_E2,
}

enum class PkCalibrationLabEvidenceState {
    EXCLUDED,
    UNASSIGNED_BELOW_INFORMATIVE_SIGNAL,
    UNASSIGNED_NO_DOMINANT_ROUTE,
    GUARD_DROPPED,
    INCLUDED,
}

enum class PkCalibrationEffectiveDisposition {
    AUTO,
    ACCEPTED,
    EXCLUDED,
}

/** Explicit allow-list for every stable identity absent from today's source models. */
@ConsistentCopyVisibility
data class PkCalibrationIdentityPolicy private constructor(
    val builtinE2AnalyteId: String,
    val targetHormoneId: String,
    val unitIdBySourceSnapshot: Map<String, String>,
    val supportedProviderIds: Set<String>,
    val supportedAssayMethodIds: Set<String>,
    val eventTypeIdByRoute: Map<PkRoute, String>,
    val routeIdByRoute: Map<PkRoute, String>,
    val compoundIdByCompound: Map<PkCompound, String>,
    val trustedPolicyVersions: Set<String>,
    val trustedIssuerIds: Set<String>,
    val trustedProvenanceRefs: Set<String>,
) {
    companion object {
        fun researchOrTest(
            builtinE2AnalyteId: String,
            targetHormoneId: String,
            unitIdBySourceSnapshot: Map<String, String>,
            supportedProviderIds: Set<String>,
            supportedAssayMethodIds: Set<String>,
            eventTypeIdByRoute: Map<PkRoute, String>,
            routeIdByRoute: Map<PkRoute, String>,
            compoundIdByCompound: Map<PkCompound, String>,
            trustedPolicyVersions: Set<String>,
            trustedIssuerIds: Set<String>,
            trustedProvenanceRefs: Set<String>,
        ): PkCalibrationIdentityPolicy? {
            if (!builtinE2AnalyteId.isStableAsciiIdentity() ||
                !targetHormoneId.isStableAsciiIdentity()
            ) {
                return null
            }
            val identitySets = listOf(
                unitIdBySourceSnapshot.keys,
                unitIdBySourceSnapshot.values.toSet(),
                supportedProviderIds,
                supportedAssayMethodIds,
                trustedPolicyVersions,
                trustedIssuerIds,
                trustedProvenanceRefs,
            )
            if (identitySets.any { values ->
                    values.isEmpty() || values.any { value -> !value.isStableAsciiIdentity() }
                }
            ) {
                return null
            }
            if (eventTypeIdByRoute.keys != PkRoute.entries.toSet() ||
                routeIdByRoute.keys != PkRoute.entries.toSet() ||
                compoundIdByCompound.keys != CalibrationE2Compounds
            ) {
                return null
            }
            if (eventTypeIdByRoute.values.any { value -> !value.isStableAsciiIdentity() } ||
                routeIdByRoute.values.any { value -> !value.isStableAsciiIdentity() } ||
                compoundIdByCompound.values.any { value -> !value.isStableAsciiIdentity() }
            ) {
                return null
            }
            if (unitIdBySourceSnapshot.values.toSet().size !=
                unitIdBySourceSnapshot.values.size ||
                eventTypeIdByRoute.values.toSet().size != eventTypeIdByRoute.size ||
                compoundIdByCompound.values.toSet().size != compoundIdByCompound.size
            ) {
                return null
            }
            if (routeIdByRoute != CanonicalRouteIdByEventRoute) return null
            return PkCalibrationIdentityPolicy(
                builtinE2AnalyteId = builtinE2AnalyteId,
                targetHormoneId = targetHormoneId,
                unitIdBySourceSnapshot = immutableMap(unitIdBySourceSnapshot),
                supportedProviderIds = immutableSet(supportedProviderIds),
                supportedAssayMethodIds = immutableSet(supportedAssayMethodIds),
                eventTypeIdByRoute = immutableMap(eventTypeIdByRoute),
                routeIdByRoute = immutableMap(routeIdByRoute),
                compoundIdByCompound = immutableMap(compoundIdByCompound),
                trustedPolicyVersions = immutableSet(trustedPolicyVersions),
                trustedIssuerIds = immutableSet(trustedIssuerIds),
                trustedProvenanceRefs = immutableSet(trustedProvenanceRefs),
            )
        }
    }
}

data class PkCalibrationLabEvidence(
    val resultId: UUID,
    val state: PkCalibrationLabEvidenceState,
    val route: PkCalibrationRoute?,
    val observedPgml: Double?,
    val totalDrugPgml: Double?,
    val dominantRouteDrugPgml: Double?,
    val otherRoutePopulationPgml: Double?,
    val routeAttributableObservedPgml: Double?,
    val effectiveDisposition: PkCalibrationEffectiveDisposition,
)

@ConsistentCopyVisibility
data class PkCalibrationRouteEvidence private constructor(
    val route: PkCalibrationRoute,
    val dominantCandidates: List<PkCalibrationLabEvidence>,
    val included: List<PkCalibrationLabEvidence>,
) {
    val dominantCandidateLabCount: Int get() = dominantCandidates.size
    val dominantLabCount: Int get() = included.size

    companion object {
        internal fun create(
            route: PkCalibrationRoute,
            dominantCandidates: List<PkCalibrationLabEvidence>,
            included: List<PkCalibrationLabEvidence>,
        ): PkCalibrationRouteEvidence? {
            val candidateIds = dominantCandidates.map(PkCalibrationLabEvidence::resultId)
            val includedIds = included.map(PkCalibrationLabEvidence::resultId)
            if (candidateIds.distinct().size != candidateIds.size ||
                includedIds.distinct().size != includedIds.size
            ) {
                return null
            }
            if (dominantCandidates.any { item ->
                    item.route != route ||
                            (item.state != PkCalibrationLabEvidenceState.INCLUDED &&
                                    item.state != PkCalibrationLabEvidenceState.GUARD_DROPPED)
                } || included.any { item ->
                    item.route != route || item.state != PkCalibrationLabEvidenceState.INCLUDED
                }
            ) {
                return null
            }
            val candidateById = dominantCandidates.associateBy(
                PkCalibrationLabEvidence::resultId
            )
            if (included.any { item -> candidateById[item.resultId] != item }) return null

            val canonicalCandidates = dominantCandidates.sortedBy { item ->
                item.resultId.toString().lowercase()
            }
            val canonicalIncluded = included.sortedBy { item ->
                item.resultId.toString().lowercase()
            }
            return PkCalibrationRouteEvidence(
                route = route,
                dominantCandidates = immutableList(canonicalCandidates),
                included = immutableList(canonicalIncluded),
            )
        }
    }
}

@ConsistentCopyVisibility
data class PkCalibrationCanonicalInputSnapshot private constructor(
    val authorizedLabs: List<PkCalibrationE2LabSource>,
    val medicationEvents: List<PkCalibrationMedicationEventSource>,
    val forwardTimeOriginEpochMillis: Long,
    val resolvedCurrentWeightKg: Double,
    val metadata: List<E2CalibrationMetadata>,
    val scopeDecision: PkCalibrationScopeDecision,
    val scopeDecisionDigest: CanonicalDigest,
    val scopeInputSnapshot: PkCalibrationScopeInputSnapshot,
    val forwardModelVersion: String,
    val calibrationModelVersion: String,
    /** Exact validated config used for evidence classification and review digests. */
    val config: PkCalibrationConfig,
    val reviewDigestByResultId: Map<UUID, CanonicalDigest>,
) {
    fun reviewDigestFor(resultId: UUID): CanonicalDigest? = reviewDigestByResultId[resultId]

    companion object {
        internal fun create(
            authorizedLabs: List<PkCalibrationE2LabSource>,
            medicationEvents: List<PkCalibrationMedicationEventSource>,
            forwardTimeOriginEpochMillis: Long,
            resolvedCurrentWeightKg: Double,
            metadata: List<E2CalibrationMetadata>,
            scopeDecision: PkCalibrationScopeDecision,
            scopeDecisionDigest: CanonicalDigest,
            scopeInputSnapshot: PkCalibrationScopeInputSnapshot,
            forwardModelVersion: String,
            calibrationModelVersion: String,
            config: PkCalibrationConfig,
            reviewDigestByResultId: Map<UUID, CanonicalDigest>,
        ): PkCalibrationCanonicalInputSnapshot? {
            if (!config.isSolverEligible()) return null
            return PkCalibrationCanonicalInputSnapshot(
                authorizedLabs = immutableList(authorizedLabs),
                medicationEvents = immutableList(medicationEvents),
                forwardTimeOriginEpochMillis = forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = resolvedCurrentWeightKg,
                metadata = immutableList(metadata),
                scopeDecision = scopeDecision,
                scopeDecisionDigest = scopeDecisionDigest,
                scopeInputSnapshot = scopeInputSnapshot,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
                config = config,
                reviewDigestByResultId = immutableMap(reviewDigestByResultId),
            )
        }
    }
}

@ConsistentCopyVisibility
data class PkCalibrationEvidencePartition private constructor(
    val canonicalInput: PkCalibrationCanonicalInputSnapshot,
    val routeEvidence: List<PkCalibrationRouteEvidence>,
    val unassigned: List<PkCalibrationLabEvidence>,
    val excluded: List<PkCalibrationLabEvidence>,
) {
    val authorizedE2ResultCount: Int get() = canonicalInput.authorizedLabs.size
    val config: PkCalibrationConfig get() = canonicalInput.config

    companion object {
        internal fun create(
            canonicalInput: PkCalibrationCanonicalInputSnapshot,
            routeEvidence: List<PkCalibrationRouteEvidence>,
            unassigned: List<PkCalibrationLabEvidence>,
            excluded: List<PkCalibrationLabEvidence>,
        ): PkCalibrationEvidencePartition? {
            if (!canonicalInput.config.isSolverEligible()) return null
            if (routeEvidence.map(PkCalibrationRouteEvidence::route) !=
                PkCalibrationRoute.entries
            ) {
                return null
            }
            if (routeEvidence.any { item ->
                    PkCalibrationRouteEvidence.create(
                        route = item.route,
                        dominantCandidates = item.dominantCandidates,
                        included = item.included,
                    ) == null
                }
            ) {
                return null
            }
            val candidateIds = routeEvidence.flatMap { item ->
                item.dominantCandidates.map(PkCalibrationLabEvidence::resultId)
            }
            val includedIds = routeEvidence.flatMap { item ->
                item.included.map(PkCalibrationLabEvidence::resultId)
            }
            if (candidateIds.distinct().size != candidateIds.size ||
                includedIds.distinct().size != includedIds.size
            ) {
                return null
            }
            return PkCalibrationEvidencePartition(
                canonicalInput = canonicalInput,
                routeEvidence = immutableList(routeEvidence),
                unassigned = immutableList(unassigned),
                excluded = immutableList(excluded),
            )
        }
    }
}

sealed interface PkCalibrationEvidenceBuildResult {
    data class Ready(val partition: PkCalibrationEvidencePartition) :
        PkCalibrationEvidenceBuildResult

    data class Failed(val failure: PkCalibrationEvidenceFailure) :
        PkCalibrationEvidenceBuildResult
}

internal data class PkCalibrationValidatedScopeInput(
    val decision: PkCalibrationScopeDecision,
    val authorizedLabs: List<PkCalibrationE2LabSource>,
    val resolvedCurrentWeightKg: Double,
    val scopeInputSnapshot: PkCalibrationScopeInputSnapshot,
    val scopeDecisionDigest: CanonicalDigest,
)

internal sealed interface PkCalibrationScopeInputValidationResult {
    data class Ready(val value: PkCalibrationValidatedScopeInput) :
        PkCalibrationScopeInputValidationResult

    data class Failed(val failure: PkCalibrationEvidenceFailure) :
        PkCalibrationScopeInputValidationResult
}

/**
 * Exact non-numeric scope preflight shared by computation and review actions.
 * Lab fit values and units are deliberately outside this boundary so an
 * authorized invalid/non-positive row can still be explicitly excluded.
 */
internal object PkCalibrationScopeInputValidator {
    @Suppress("LongParameterList")
    fun validate(
        labs: List<PkCalibrationE2LabSource>,
        medicationEvents: List<PkCalibrationMedicationEventSource>,
        forwardTimeOriginEpochMillis: Long,
        resolvedCurrentWeightKg: Double?,
        identityPolicy: PkCalibrationIdentityPolicy,
        config: PkCalibrationConfig,
        scopeDecisionProvider: PkCalibrationScopeDecisionProvider,
        forwardModelVersion: String,
        calibrationModelVersion: String,
    ): PkCalibrationScopeInputValidationResult {
        val decision = runCatching { scopeDecisionProvider.currentDecision() }.getOrNull()
            ?: return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
        if (decision.policyVersion !in identityPolicy.trustedPolicyVersions ||
            decision.issuerId !in identityPolicy.trustedIssuerIds ||
            decision.provenanceRef !in identityPolicy.trustedProvenanceRefs
        ) {
            return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
        }
        if (!config.isSolverEligible()) {
            return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
        }

        val authorizedLabs = ArrayList<PkCalibrationE2LabSource>()
        for (authorization in decision.authorizedE2Results) {
            val lab = labs.singleOrNull { candidate ->
                candidate.resultId == authorization.resultId
            } ?: return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
            if (lab.resultScopeDigest() != authorization.resultScopeDigest) {
                return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
            }
            authorizedLabs += lab
        }
        if (authorizedLabs.any { lab -> !identityPolicy.acceptsScopeIdentity(lab) }) {
            return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        }
        authorizedLabs.sortBy { lab -> lab.resultId.toString() }

        val weightKg = resolvedCurrentWeightKg
            ?.takeIf { value -> value.isFinite() && value > 0.0 }
            ?: return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        if (!forwardModelVersion.isStableAsciiIdentity() ||
            !calibrationModelVersion.isStableAsciiIdentity()
        ) {
            return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        }
        if (medicationEvents.map { source -> source.event.id }.distinct().size !=
            medicationEvents.size ||
            medicationEvents.any { source ->
                !identityPolicy.accepts(source, forwardTimeOriginEpochMillis)
            }
        ) {
            return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        }

        val scopeInputSnapshot = PkCalibrationScopeInputSnapshot.create(
            authorizedE2Results = decision.authorizedE2Results,
            medicationEvents = medicationEvents,
            resolvedCurrentWeightKg = weightKg,
            forwardModelVersion = forwardModelVersion,
        ) ?: return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        if (scopeInputSnapshot.digest != decision.inputSnapshotDigest) {
            return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
        }
        return PkCalibrationScopeInputValidationResult.Ready(
            PkCalibrationValidatedScopeInput(
                decision = decision,
                authorizedLabs = immutableList(authorizedLabs),
                resolvedCurrentWeightKg = weightKg,
                scopeInputSnapshot = scopeInputSnapshot,
                scopeDecisionDigest = decision.digest(),
            )
        )
    }

    private fun failedScope(
        failure: PkCalibrationEvidenceFailure,
    ): PkCalibrationScopeInputValidationResult.Failed {
        return PkCalibrationScopeInputValidationResult.Failed(failure)
    }
}

/** Pure scope validation, canonical snapshot, and dominant-route evidence adapter. */
object PkCalibrationEvidenceAdapter {
    @Suppress("LongParameterList")
    fun build(
        labs: List<PkCalibrationE2LabSource>,
        medicationEvents: List<PkCalibrationMedicationEventSource>,
        forwardTimeOriginEpochMillis: Long,
        resolvedCurrentWeightKg: Double?,
        metadata: List<E2CalibrationMetadata>,
        identityPolicy: PkCalibrationIdentityPolicy,
        config: PkCalibrationConfig,
        scopeDecisionProvider: PkCalibrationScopeDecisionProvider,
        forwardModelVersion: String,
        calibrationModelVersion: String,
    ): PkCalibrationEvidenceBuildResult {
        val validatedScope = when (
            val validation = PkCalibrationScopeInputValidator.validate(
                labs = labs,
                medicationEvents = medicationEvents,
                forwardTimeOriginEpochMillis = forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = resolvedCurrentWeightKg,
                identityPolicy = identityPolicy,
                config = config,
                scopeDecisionProvider = scopeDecisionProvider,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
            )
        ) {
            is PkCalibrationScopeInputValidationResult.Failed -> {
                return failed(validation.failure)
            }

            is PkCalibrationScopeInputValidationResult.Ready -> validation.value
        }
        val decision = validatedScope.decision
        val authorizedLabs = validatedScope.authorizedLabs
        val weightKg = validatedScope.resolvedCurrentWeightKg
        val scopeInputSnapshot = validatedScope.scopeInputSnapshot
        val scopeDecisionDigest = validatedScope.scopeDecisionDigest

        val authorizedResultIds = decision.authorizedE2Results
            .map(PkAuthorizedE2Result::resultId)
            .toSet()
        val authorizedMetadata = metadata.filter { item ->
            item.resultId in authorizedResultIds
        }
        val sortedAuthorizedMetadata = authorizedMetadata.sortedBy { item ->
            item.resultId.toString()
        }
        val metadataGroups = sortedAuthorizedMetadata.groupBy(E2CalibrationMetadata::resultId)
        var hasSharedInputFailure = metadataGroups.values.any { items -> items.size != 1 }
        var hasSharedNumericFailure = false
        var hasInvalidNonpositiveE2 = false

        // A duplicate row is itself invalid input, but an unambiguous disposition
        // still lets the higher-precedence non-positive predicate be evaluated.
        val excludedIdsForPrecedence = metadataGroups.asSequence()
            .filter { (_, items) ->
                items.all { item -> item.disposition == E2CalibrationDisposition.EXCLUDED }
            }
            .map(Map.Entry<UUID, List<E2CalibrationMetadata>>::key)
            .toSet()

        val verifiedCanonicalValueByResultId = linkedMapOf<UUID, Double>()
        val classificationByResultId = linkedMapOf<UUID, PkCalibrationObservationClassification>()
        val forwardModel = PkE2ForwardModel.create(
            events = scopeInputSnapshot.medicationEvents.map { source -> source.event },
            bodyWeightKg = weightKg,
        ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        val dMin = requireNotNull(config.drugMinInformativePgml)

        for (lab in authorizedLabs) {
            if (lab.resultId in excludedIdsForPrecedence) continue
            if (!identityPolicy.acceptsFitIdentity(lab)) {
                hasSharedInputFailure = true
                continue
            }
            when (val verification = lab.verifyCanonicalValuePgml()) {
                PkCalibrationCanonicalE2ValueVerification.InvalidInput -> {
                    hasSharedInputFailure = true
                }

                PkCalibrationCanonicalE2ValueVerification.NumericFailure -> {
                    hasSharedNumericFailure = true
                }

                is PkCalibrationCanonicalE2ValueVerification.Verified -> {
                    verifiedCanonicalValueByResultId[lab.resultId] =
                        verification.canonicalValuePgml
                    val timeH = epochDifferenceHours(
                        epochMillis = lab.collectedAtEpochMillis,
                        originEpochMillis = forwardTimeOriginEpochMillis,
                    )
                    if (timeH == null) {
                        hasSharedInputFailure = true
                        continue
                    }
                    val breakdown = forwardModel.breakdownAt(timeH)
                    if (breakdown == null) {
                        hasSharedNumericFailure = true
                        continue
                    }
                    val classification = classifyCalibrationObservation(
                        observedPgml = verification.canonicalValuePgml,
                        breakdown = breakdown,
                        drugMinInformativePgml = dMin,
                    )
                    classificationByResultId[lab.resultId] = classification
                    when (classification) {
                        PkCalibrationObservationClassification.InvalidNonpositive -> {
                            hasInvalidNonpositiveE2 = true
                        }

                        PkCalibrationObservationClassification.NumericFailure -> {
                            hasSharedNumericFailure = true
                        }

                        is PkCalibrationObservationClassification.Candidate,
                        is PkCalibrationObservationClassification.Unassigned,
                        -> Unit
                    }
                }
            }
        }

        if (hasInvalidNonpositiveE2) {
            return failed(PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2)
        }
        if (hasSharedInputFailure) {
            return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        }
        if (hasSharedNumericFailure) {
            return failed(PkCalibrationEvidenceFailure.SHARED_NUMERIC_FAILURE)
        }

        val metadataById = sortedAuthorizedMetadata.associateBy(
            E2CalibrationMetadata::resultId
        )
        val excludedIds = metadataById.values.asSequence()
            .filter { item -> item.disposition == E2CalibrationDisposition.EXCLUDED }
            .map(E2CalibrationMetadata::resultId)
            .toSet()

        val reviewDigests = linkedMapOf<UUID, CanonicalDigest>()
        for (lab in authorizedLabs) {
            reviewDigests[lab.resultId] = wholeReviewDigest(
                targetResultId = lab.resultId,
                authorizedLabs = authorizedLabs,
                excludedResultIds = excludedIds,
                scopeDecisionDigest = scopeDecisionDigest,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
                config = config,
                identityPolicy = identityPolicy,
            )
        }

        val canonicalMedicationEvents = scopeInputSnapshot.medicationEvents

        val candidatesByRoute = linkedMapOf<PkCalibrationRoute, MutableList<PkCalibrationLabEvidence>>()
        val includedByRoute = linkedMapOf<PkCalibrationRoute, MutableList<PkCalibrationLabEvidence>>()
        for (route in PkCalibrationRoute.entries) {
            candidatesByRoute[route] = mutableListOf()
            includedByRoute[route] = mutableListOf()
        }
        val unassigned = mutableListOf<PkCalibrationLabEvidence>()
        val excluded = mutableListOf<PkCalibrationLabEvidence>()
        for (lab in authorizedLabs) {
            val storedMetadata = metadataById[lab.resultId]
            if (storedMetadata?.disposition == E2CalibrationDisposition.EXCLUDED) {
                excluded += lab.evidence(
                    state = PkCalibrationLabEvidenceState.EXCLUDED,
                    observedPgml = null,
                    effectiveDisposition = PkCalibrationEffectiveDisposition.EXCLUDED,
                )
                continue
            }
            val acceptedEffective = storedMetadata?.disposition ==
                    E2CalibrationDisposition.ACCEPTED &&
                    storedMetadata.acceptedReviewDigest == reviewDigests[lab.resultId]
            val effectiveDisposition = if (acceptedEffective) {
                PkCalibrationEffectiveDisposition.ACCEPTED
            } else {
                PkCalibrationEffectiveDisposition.AUTO
            }
            val classification = classificationByResultId.getValue(lab.resultId)
            when (classification) {
                PkCalibrationObservationClassification.InvalidNonpositive,
                PkCalibrationObservationClassification.NumericFailure,
                -> error("Global evidence failures were handled before partition assembly.")

                is PkCalibrationObservationClassification.Unassigned -> {
                    unassigned += lab.evidence(
                        state = classification.state,
                        observedPgml = verifiedCanonicalValueByResultId.getValue(lab.resultId),
                        totalDrugPgml = classification.totalDrugPgml,
                        effectiveDisposition = effectiveDisposition,
                    )
                }

                is PkCalibrationObservationClassification.Candidate -> {
                    val evidence = lab.evidence(
                        state = if (classification.included) {
                            PkCalibrationLabEvidenceState.INCLUDED
                        } else {
                            PkCalibrationLabEvidenceState.GUARD_DROPPED
                        },
                        observedPgml = verifiedCanonicalValueByResultId.getValue(lab.resultId),
                        route = classification.route,
                        totalDrugPgml = classification.totalDrugPgml,
                        dominantRouteDrugPgml = classification.dominantRouteDrugPgml,
                        otherRoutePopulationPgml = classification.otherRoutePopulationPgml,
                        routeAttributableObservedPgml =
                            classification.routeAttributableObservedPgml,
                        effectiveDisposition = effectiveDisposition,
                    )
                    candidatesByRoute.getValue(classification.route) += evidence
                    if (classification.included) {
                        includedByRoute.getValue(classification.route) += evidence
                    }
                }
            }
        }

        val routeEvidence = ArrayList<PkCalibrationRouteEvidence>(
            PkCalibrationRoute.entries.size
        )
        for (route in PkCalibrationRoute.entries) {
            val candidates = candidatesByRoute.getValue(route).sortedBy { item ->
                item.resultId.toString()
            }
            val included = includedByRoute.getValue(route).sortedBy { item ->
                item.resultId.toString()
            }
            val item = PkCalibrationRouteEvidence.create(
                route = route,
                dominantCandidates = candidates,
                included = included,
            ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
            routeEvidence += item
        }
        val canonicalInput = PkCalibrationCanonicalInputSnapshot.create(
            authorizedLabs = authorizedLabs,
            medicationEvents = canonicalMedicationEvents,
            forwardTimeOriginEpochMillis = forwardTimeOriginEpochMillis,
            resolvedCurrentWeightKg = weightKg,
            metadata = sortedAuthorizedMetadata,
            scopeDecision = decision,
            scopeDecisionDigest = scopeDecisionDigest,
            scopeInputSnapshot = scopeInputSnapshot,
            forwardModelVersion = forwardModelVersion,
            calibrationModelVersion = calibrationModelVersion,
            config = config,
            reviewDigestByResultId = reviewDigests,
        ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        val partition = PkCalibrationEvidencePartition.create(
            canonicalInput = canonicalInput,
            routeEvidence = routeEvidence,
            unassigned = unassigned.sortedBy { item -> item.resultId.toString() },
            excluded = excluded.sortedBy { item -> item.resultId.toString() },
        ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        return PkCalibrationEvidenceBuildResult.Ready(partition)
    }
}

internal sealed interface PkCalibrationObservationClassification {
    data object InvalidNonpositive : PkCalibrationObservationClassification
    data object NumericFailure : PkCalibrationObservationClassification

    data class Unassigned(
        val state: PkCalibrationLabEvidenceState,
        val totalDrugPgml: Double,
    ) : PkCalibrationObservationClassification

    data class Candidate(
        val route: PkCalibrationRoute,
        val totalDrugPgml: Double,
        val dominantRouteDrugPgml: Double,
        val otherRoutePopulationPgml: Double,
        val routeAttributableObservedPgml: Double,
        val included: Boolean,
    ) : PkCalibrationObservationClassification
}

internal fun classifyCalibrationObservation(
    observedPgml: Double,
    breakdown: PkForwardBreakdown,
    drugMinInformativePgml: Double,
): PkCalibrationObservationClassification {
    if (!observedPgml.isFinite() ||
        !drugMinInformativePgml.isFinite() || drugMinInformativePgml <= 0.0
    ) {
        return PkCalibrationObservationClassification.NumericFailure
    }
    val total = breakdown.totalDrugPgml
    if (!total.isFinite() || total < 0.0) {
        return PkCalibrationObservationClassification.NumericFailure
    }
    if (total < drugMinInformativePgml) {
        return PkCalibrationObservationClassification.Unassigned(
            state = PkCalibrationLabEvidenceState.UNASSIGNED_BELOW_INFORMATIVE_SIGNAL,
            totalDrugPgml = total,
        )
    }
    if (observedPgml <= 0.0) {
        return PkCalibrationObservationClassification.InvalidNonpositive
    }

    var dominantRoute: PkCalibrationRoute? = null
    var dominantContribution = 0.0
    for (route in PkCalibrationRoute.entries) {
        val contribution = breakdown.byRouteDrugPgml[route]
            ?: return PkCalibrationObservationClassification.NumericFailure
        if (contribution >= PkCalibrationDefaults.DOMINANT_ROUTE_SHARE_MIN * total) {
            if (dominantRoute != null) {
                return PkCalibrationObservationClassification.NumericFailure
            }
            dominantRoute = route
            dominantContribution = contribution
        }
    }
    val route = dominantRoute ?: return PkCalibrationObservationClassification.Unassigned(
        state = PkCalibrationLabEvidenceState.UNASSIGNED_NO_DOMINANT_ROUTE,
        totalDrugPgml = total,
    )
    val baseline = (total - dominantContribution).normalizePositiveZero()
    if (!baseline.isFinite() || baseline < 0.0 || baseline > total) {
        return PkCalibrationObservationClassification.NumericFailure
    }
    val attributable = observedPgml - baseline
    if (!attributable.isFinite()) {
        return PkCalibrationObservationClassification.NumericFailure
    }
    val guard = max(
        PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_FRACTION * total,
        PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_PGML,
    )
    return PkCalibrationObservationClassification.Candidate(
        route = route,
        totalDrugPgml = total,
        dominantRouteDrugPgml = dominantContribution,
        otherRoutePopulationPgml = baseline,
        routeAttributableObservedPgml = attributable,
        included = attributable >= guard,
    )
}

internal fun PkCalibrationIdentityPolicy.acceptsScopeIdentity(
    lab: PkCalibrationE2LabSource,
): Boolean {
    return lab.analyteId == builtinE2AnalyteId &&
            lab.providerId in supportedProviderIds &&
            lab.assayMethodId in supportedAssayMethodIds
}

private fun PkCalibrationIdentityPolicy.acceptsFitIdentity(
    lab: PkCalibrationE2LabSource,
): Boolean {
    return acceptsScopeIdentity(lab) &&
            lab.unitId == unitIdBySourceSnapshot[lab.sourceUnitSnapshot]
}

private fun PkCalibrationIdentityPolicy.accepts(
    source: PkCalibrationMedicationEventSource,
    forwardTimeOriginEpochMillis: Long,
): Boolean {
    val event = source.event
    if (source.hormoneId != targetHormoneId ||
        source.eventTypeId != eventTypeIdByRoute[event.route] ||
        source.routeId != routeIdByRoute[event.route] ||
        source.compoundId != compoundIdByCompound[event.compound]
    ) {
        return false
    }
    val expectedTimeH = epochDifferenceHours(
        epochMillis = source.epochMillis,
        originEpochMillis = forwardTimeOriginEpochMillis,
    ) ?: return false
    return expectedTimeH.toBits() == event.timeH.toBits()
}

private fun PkCalibrationE2LabSource.evidence(
    state: PkCalibrationLabEvidenceState,
    observedPgml: Double?,
    route: PkCalibrationRoute? = null,
    totalDrugPgml: Double? = null,
    dominantRouteDrugPgml: Double? = null,
    otherRoutePopulationPgml: Double? = null,
    routeAttributableObservedPgml: Double? = null,
    effectiveDisposition: PkCalibrationEffectiveDisposition,
): PkCalibrationLabEvidence {
    return PkCalibrationLabEvidence(
        resultId = resultId,
        state = state,
        route = route,
        observedPgml = observedPgml,
        totalDrugPgml = totalDrugPgml,
        dominantRouteDrugPgml = dominantRouteDrugPgml,
        otherRoutePopulationPgml = otherRoutePopulationPgml,
        routeAttributableObservedPgml = routeAttributableObservedPgml,
        effectiveDisposition = effectiveDisposition,
    )
}

private fun wholeReviewDigest(
    targetResultId: UUID,
    authorizedLabs: List<PkCalibrationE2LabSource>,
    excludedResultIds: Set<UUID>,
    scopeDecisionDigest: CanonicalDigest,
    forwardModelVersion: String,
    calibrationModelVersion: String,
    config: PkCalibrationConfig,
    identityPolicy: PkCalibrationIdentityPolicy,
): CanonicalDigest {
    val includedResults = authorizedLabs.asSequence()
        .filterNot { lab -> lab.resultId in excludedResultIds }
        .sortedBy { lab -> lab.resultId.toString() }
        .map { lab ->
            linkedMapOf<String, Any?>(
                "resultId" to lab.resultId.toString().lowercase(),
                "sourceValue" to requireNotNull(lab.sourceValueBits),
                "unitId" to lab.unitId,
                "analyteId" to lab.analyteId,
                "providerId" to lab.providerId,
                "assayMethodId" to lab.assayMethodId,
                "collectedAt" to lab.collectedAtEpochMillis,
            )
        }
        .toList()
    val excluded = excludedResultIds.map { resultId -> resultId.toString().lowercase() }
        .sorted()
    return canonicalDigest(
        schema = PK_CALIBRATION_OUTLIER_REVIEW_DIGEST_SCHEMA,
        payload = linkedMapOf(
            "schema" to PK_CALIBRATION_OUTLIER_REVIEW_DIGEST_SCHEMA,
            "targetResultId" to targetResultId.toString().lowercase(),
            "includedAuthorizedE2Results" to includedResults,
            "excludedAuthorizedE2ResultIds" to excluded,
            "scopeDecisionDigest" to linkedMapOf(
                "schema" to scopeDecisionDigest.schema,
                "algorithm" to scopeDecisionDigest.algorithm,
                "hexLower" to scopeDecisionDigest.hexLower,
            ),
            "forwardModelVersion" to forwardModelVersion,
            "calibrationModelVersion" to calibrationModelVersion,
            "constants" to reviewConstantsPayload(config, identityPolicy),
        ),
    )
}

private fun reviewConstantsPayload(
    config: PkCalibrationConfig,
    identityPolicy: PkCalibrationIdentityPolicy,
): Map<String, Any?> {
    val caps = PkCalibrationRoute.entries.map { route ->
        val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE.getValue(route)
        linkedMapOf(
            "routeId" to route.stableId,
            "minInclusive" to cap.minInclusive.toCanonicalBinary64Bits(),
            "maxInclusive" to cap.maxInclusive.toCanonicalBinary64Bits(),
        )
    }
    val eventRouteMapping = PkRoute.entries.map { eventRoute ->
        linkedMapOf(
            "eventTypeId" to identityPolicy.eventTypeIdByRoute.getValue(eventRoute),
            "routeId" to identityPolicy.routeIdByRoute.getValue(eventRoute),
            "calibrationRouteId" to eventRoute.calibrationRoute()?.stableId,
        )
    }
    val compoundIdentities = CalibrationE2CompoundOrder.map { compound ->
        identityPolicy.compoundIdByCompound.getValue(compound)
    }
    return linkedMapOf(
        "canonicalRouteOrder" to PkCalibrationRoute.entries.map(PkCalibrationRoute::stableId),
        "eventRouteMapping" to eventRouteMapping,
        "targetHormoneId" to identityPolicy.targetHormoneId,
        "targetCompoundIds" to compoundIdentities,
        "exactRouteResidualLaw" to "a=y-(D-d)",
        "dominanceLaw" to "d>=w0*D",
        "studentTNu" to PkCalibrationDefaults.STUDENT_T_NU.toCanonicalBinary64Bits(),
        "routeLogScalePriorSd" to
                PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD.toCanonicalBinary64Bits(),
        "rLog" to requireNotNull(config.rLog).toCanonicalBinary64Bits(),
        "drugMinInformativePgml" to
                requireNotNull(config.drugMinInformativePgml).toCanonicalBinary64Bits(),
        "dominantRouteShareMin" to
                PkCalibrationDefaults.DOMINANT_ROUTE_SHARE_MIN.toCanonicalBinary64Bits(),
        "minDominantLabsForPromotion" to
                PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_PROMOTION,
        "minDominantLabsForExtremeScale" to
                PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_EXTREME_SCALE,
        "extremeScaleCoreMin" to
                PkCalibrationDefaults.EXTREME_SCALE_CORE_MIN.toCanonicalBinary64Bits(),
        "extremeScaleCoreMax" to
                PkCalibrationDefaults.EXTREME_SCALE_CORE_MAX.toCanonicalBinary64Bits(),
        "routeAttributableMinFraction" to
                PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_FRACTION
                    .toCanonicalBinary64Bits(),
        "routeAttributableMinPgml" to
                PkCalibrationDefaults.ROUTE_ATTRIBUTABLE_MIN_PGML
                    .toCanonicalBinary64Bits(),
        "displayScaleCaps" to caps,
        "drugSignalLogRangeMin" to
                PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN.toCanonicalBinary64Bits(),
        "routeLogScalePosteriorSdMaxForFullCalibration" to
                PkCalibrationDefaults
                    .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION
                    .toCanonicalBinary64Bits(),
        "robustRmseGateFactor" to
                PkCalibrationDefaults.ROBUST_RMSE_GATE_FACTOR
                    .toCanonicalBinary64Bits(),
        "outlierWeightMin" to
                PkCalibrationDefaults.OUTLIER_WEIGHT_MIN.toCanonicalBinary64Bits(),
        "globalSearchNumericGuardAbsBeta" to
                PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA
                    .toCanonicalBinary64Bits(),
        "stationaryIntervalMaxCellsPerRoute" to
                PkCalibrationDefaults.STATIONARY_INTERVAL_MAX_CELLS_PER_ROUTE,
        "stationaryRootEnclosureBetaTol" to
                PkCalibrationDefaults.STATIONARY_ROOT_ENCLOSURE_BETA_TOL
                    .toCanonicalBinary64Bits(),
        "stationaryRootMinSeparation" to
                PkCalibrationDefaults.STATIONARY_ROOT_MIN_SEPARATION
                    .toCanonicalBinary64Bits(),
        "stationaryRootBetaAbsTol" to
                PkCalibrationDefaults.STATIONARY_ROOT_BETA_ABS_TOL
                    .toCanonicalBinary64Bits(),
        "stationaryRootMaxEval" to PkCalibrationDefaults.STATIONARY_ROOT_MAX_EVAL,
    )
}

private fun failed(
    failure: PkCalibrationEvidenceFailure,
): PkCalibrationEvidenceBuildResult.Failed {
    return PkCalibrationEvidenceBuildResult.Failed(failure)
}

private fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this

private fun <T> immutableList(source: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(source))
}

private fun <T> immutableSet(source: Set<T>): Set<T> {
    return Collections.unmodifiableSet(LinkedHashSet(source))
}

private fun <K, V> immutableMap(source: Map<K, V>): Map<K, V> {
    return Collections.unmodifiableMap(LinkedHashMap(source))
}

private val CalibrationE2Compounds = setOf(
    PkCompound.E2,
    PkCompound.EB,
    PkCompound.EV,
    PkCompound.EC,
    PkCompound.EN,
    PkCompound.EU,
)

private val CalibrationE2CompoundOrder = listOf(
    PkCompound.E2,
    PkCompound.EB,
    PkCompound.EV,
    PkCompound.EC,
    PkCompound.EN,
    PkCompound.EU,
)

private const val MILLIS_PER_HOUR = 3_600_000.0

private fun epochDifferenceHours(
    epochMillis: Long,
    originEpochMillis: Long,
): Double? {
    val difference = runCatching { Math.subtractExact(epochMillis, originEpochMillis) }
        .getOrNull() ?: return null
    return (difference / MILLIS_PER_HOUR).takeIf(Double::isFinite)
}

private val CanonicalRouteIdByEventRoute = linkedMapOf(
    PkRoute.INJECTION to PkCalibrationRoute.INJECTION.stableId,
    PkRoute.PATCH_APPLY to PkCalibrationRoute.PATCH.stableId,
    PkRoute.PATCH_REMOVE to PkCalibrationRoute.PATCH.stableId,
    PkRoute.GEL to PkCalibrationRoute.GEL.stableId,
    PkRoute.ORAL to PkCalibrationRoute.ORAL.stableId,
    PkRoute.SUBLINGUAL to PkCalibrationRoute.SUBLINGUAL.stableId,
)
