package com.mkx.hrttracker.model.pk

import java.util.Collections
import java.util.UUID

enum class PkCalibrationEvidenceFailure {
    SCOPE_NOT_CONFIRMED,
    NO_USABLE_LABS,
    SHARED_INPUT_INVALID,
    SHARED_NUMERIC_FAILURE,
    INVALID_NONPOSITIVE_E2,
}

enum class PkCalibrationLabEvidenceState {
    EXCLUDED,
    UNASSIGNED_BELOW_INFORMATIVE_SIGNAL,
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
            )
        }
    }
}

/**
 * v10.0 §A10.1: one shared evidence pool, no per-route ownership. An INCLUDED
 * lab carries the full population decomposition; every route it touches is
 * informed in proportion to its modeled share.
 */
data class PkCalibrationLabEvidence(
    val resultId: UUID,
    val state: PkCalibrationLabEvidenceState,
    val observedPgml: Double?,
    val totalDrugPgml: Double?,
    val breakdown: PkForwardBreakdown?,
    val effectiveDisposition: PkCalibrationEffectiveDisposition,
)

@ConsistentCopyVisibility
data class PkCalibrationCanonicalInputSnapshot private constructor(
    val authorizedLabs: List<PkCalibrationE2LabSource>,
    val medicationEvents: List<PkCalibrationMedicationEventSource>,
    val forwardTimeOriginEpochMillis: Long,
    val resolvedCurrentWeightKg: Double,
    val metadata: List<E2CalibrationMetadata>,
    val attestation: PkCalibrationAttestation,
    val scopeInputSnapshot: PkCalibrationScopeInputSnapshot,
    val forwardModelVersion: String,
    val calibrationModelVersion: String,
    /** Exact validated config used for evidence classification. */
    val config: PkCalibrationConfig,
    val acceptanceRecordByResultId: Map<UUID, PkCalibrationAcceptanceRecord>,
) {
    /** The acceptance record a Keep action must persist for [resultId] right now. */
    fun acceptanceRecordFor(resultId: UUID): PkCalibrationAcceptanceRecord? =
        acceptanceRecordByResultId[resultId]

    companion object {
        internal fun create(
            authorizedLabs: List<PkCalibrationE2LabSource>,
            medicationEvents: List<PkCalibrationMedicationEventSource>,
            forwardTimeOriginEpochMillis: Long,
            resolvedCurrentWeightKg: Double,
            metadata: List<E2CalibrationMetadata>,
            attestation: PkCalibrationAttestation,
            scopeInputSnapshot: PkCalibrationScopeInputSnapshot,
            forwardModelVersion: String,
            calibrationModelVersion: String,
            config: PkCalibrationConfig,
            acceptanceRecordByResultId: Map<UUID, PkCalibrationAcceptanceRecord>,
        ): PkCalibrationCanonicalInputSnapshot? {
            if (!config.isSolverEligible()) return null
            return PkCalibrationCanonicalInputSnapshot(
                authorizedLabs = immutableList(authorizedLabs),
                medicationEvents = immutableList(medicationEvents),
                forwardTimeOriginEpochMillis = forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = resolvedCurrentWeightKg,
                metadata = immutableList(metadata),
                attestation = attestation,
                scopeInputSnapshot = scopeInputSnapshot,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
                config = config,
                acceptanceRecordByResultId = immutableMap(acceptanceRecordByResultId),
            )
        }
    }
}

@ConsistentCopyVisibility
data class PkCalibrationEvidencePool private constructor(
    val canonicalInput: PkCalibrationCanonicalInputSnapshot,
    val included: List<PkCalibrationLabEvidence>,
    val unassigned: List<PkCalibrationLabEvidence>,
    val excluded: List<PkCalibrationLabEvidence>,
) {
    val authorizedE2ResultCount: Int get() = canonicalInput.authorizedLabs.size
    val config: PkCalibrationConfig get() = canonicalInput.config

    companion object {
        internal fun create(
            canonicalInput: PkCalibrationCanonicalInputSnapshot,
            included: List<PkCalibrationLabEvidence>,
            unassigned: List<PkCalibrationLabEvidence>,
            excluded: List<PkCalibrationLabEvidence>,
        ): PkCalibrationEvidencePool? {
            if (!canonicalInput.config.isSolverEligible()) return null
            val allIds = (included + unassigned + excluded)
                .map(PkCalibrationLabEvidence::resultId)
            if (allIds.distinct().size != allIds.size) return null
            if (included.any { item ->
                    item.state != PkCalibrationLabEvidenceState.INCLUDED ||
                            item.breakdown == null ||
                            item.observedPgml?.takeIf { value ->
                                value.isFinite() && value > 0.0
                            } == null ||
                            item.totalDrugPgml?.toBits() !=
                            item.breakdown.totalDrugPgml.toBits()
                }
            ) {
                return null
            }
            return PkCalibrationEvidencePool(
                canonicalInput = canonicalInput,
                included = immutableList(
                    included.sortedBy { item -> item.resultId.toString().lowercase() }
                ),
                unassigned = immutableList(unassigned),
                excluded = immutableList(excluded),
            )
        }
    }
}

sealed interface PkCalibrationEvidenceBuildResult {
    data class Ready(val pool: PkCalibrationEvidencePool) :
        PkCalibrationEvidenceBuildResult

    data class Failed(
        val failure: PkCalibrationEvidenceFailure,
        /** Per-lab verdicts behind an [PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2] failure. */
        val invalidNonpositiveLabIds: Set<UUID> = emptySet(),
    ) : PkCalibrationEvidenceBuildResult
}

internal data class PkCalibrationValidatedScopeInput(
    val attestation: PkCalibrationAttestation,
    val authorizedLabs: List<PkCalibrationE2LabSource>,
    val resolvedCurrentWeightKg: Double,
    val scopeInputSnapshot: PkCalibrationScopeInputSnapshot,
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
        attestationProvider: PkCalibrationAttestationProvider,
        forwardModelVersion: String,
        calibrationModelVersion: String,
    ): PkCalibrationScopeInputValidationResult {
        val attestation = runCatching { attestationProvider.currentAttestation() }.getOrNull()
            ?: return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
        if (!config.isSolverEligible()) {
            return failedScope(PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED)
        }

        // Attested but zero labs is its own state (Phase-3 finding #5): the UI
        // must say "add an E2 result", never loop on the attestation CTA.
        if (labs.isEmpty()) {
            return failedScope(PkCalibrationEvidenceFailure.NO_USABLE_LABS)
        }
        if (labs.map(PkCalibrationE2LabSource::resultId).distinct().size != labs.size) {
            return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        }
        val authorizedLabs = ArrayList(labs)
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
            labs = authorizedLabs,
            medicationEvents = medicationEvents,
            resolvedCurrentWeightKg = weightKg,
            forwardModelVersion = forwardModelVersion,
        ) ?: return failedScope(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        return PkCalibrationScopeInputValidationResult.Ready(
            PkCalibrationValidatedScopeInput(
                attestation = attestation,
                authorizedLabs = immutableList(authorizedLabs),
                resolvedCurrentWeightKg = weightKg,
                scopeInputSnapshot = scopeInputSnapshot,
            )
        )
    }

    private fun failedScope(
        failure: PkCalibrationEvidenceFailure,
    ): PkCalibrationScopeInputValidationResult.Failed {
        return PkCalibrationScopeInputValidationResult.Failed(failure)
    }
}

/** Pure scope validation, canonical snapshot, and shared-pool evidence adapter. */
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
        attestationProvider: PkCalibrationAttestationProvider,
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
                attestationProvider = attestationProvider,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
            )
        ) {
            is PkCalibrationScopeInputValidationResult.Failed -> {
                return failed(validation.failure)
            }

            is PkCalibrationScopeInputValidationResult.Ready -> validation.value
        }
        val attestation = validatedScope.attestation
        val authorizedLabs = validatedScope.authorizedLabs
        val weightKg = validatedScope.resolvedCurrentWeightKg
        val scopeInputSnapshot = validatedScope.scopeInputSnapshot

        val authorizedResultIds = authorizedLabs
            .map(PkCalibrationE2LabSource::resultId)
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
        val invalidNonpositiveLabIds = linkedSetOf<UUID>()

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
                            invalidNonpositiveLabIds += lab.resultId
                        }

                        PkCalibrationObservationClassification.NumericFailure -> {
                            hasSharedNumericFailure = true
                        }

                        is PkCalibrationObservationClassification.Included,
                        is PkCalibrationObservationClassification.Unassigned,
                        -> Unit
                    }
                }
            }
        }

        if (invalidNonpositiveLabIds.isNotEmpty()) {
            return PkCalibrationEvidenceBuildResult.Failed(
                failure = PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2,
                invalidNonpositiveLabIds = immutableSet(invalidNonpositiveLabIds),
            )
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

        val acceptanceRecords = linkedMapOf<UUID, PkCalibrationAcceptanceRecord>()
        for (lab in authorizedLabs) {
            // An explicitly excluded row skips value binding entirely; it can never
            // be ACCEPTED, so it needs no current acceptance record.
            if (lab.resultId in excludedIds) continue
            val sourceValueBits = lab.sourceValueBits
                ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
            acceptanceRecords[lab.resultId] = PkCalibrationAcceptanceRecord.create(
                calibrationModelVersion = calibrationModelVersion,
                sourceValueBits = sourceValueBits,
                collectedAtEpochMillis = lab.collectedAtEpochMillis,
                unitId = lab.unitId,
            ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        }

        val canonicalMedicationEvents = scopeInputSnapshot.medicationEvents

        val included = mutableListOf<PkCalibrationLabEvidence>()
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
                    storedMetadata.acceptedRecord == acceptanceRecords[lab.resultId]
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
                        state = PkCalibrationLabEvidenceState
                            .UNASSIGNED_BELOW_INFORMATIVE_SIGNAL,
                        observedPgml = verifiedCanonicalValueByResultId.getValue(lab.resultId),
                        totalDrugPgml = classification.totalDrugPgml,
                        effectiveDisposition = effectiveDisposition,
                    )
                }

                is PkCalibrationObservationClassification.Included -> {
                    included += lab.evidence(
                        state = PkCalibrationLabEvidenceState.INCLUDED,
                        observedPgml = verifiedCanonicalValueByResultId.getValue(lab.resultId),
                        totalDrugPgml = classification.breakdown.totalDrugPgml,
                        breakdown = classification.breakdown,
                        effectiveDisposition = effectiveDisposition,
                    )
                }
            }
        }

        val canonicalInput = PkCalibrationCanonicalInputSnapshot.create(
            authorizedLabs = authorizedLabs,
            medicationEvents = canonicalMedicationEvents,
            forwardTimeOriginEpochMillis = forwardTimeOriginEpochMillis,
            resolvedCurrentWeightKg = weightKg,
            metadata = sortedAuthorizedMetadata,
            attestation = attestation,
            scopeInputSnapshot = scopeInputSnapshot,
            forwardModelVersion = forwardModelVersion,
            calibrationModelVersion = calibrationModelVersion,
            config = config,
            acceptanceRecordByResultId = acceptanceRecords,
        ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        val pool = PkCalibrationEvidencePool.create(
            canonicalInput = canonicalInput,
            included = included,
            unassigned = unassigned.sortedBy { item -> item.resultId.toString() },
            excluded = excluded.sortedBy { item -> item.resultId.toString() },
        ) ?: return failed(PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID)
        return PkCalibrationEvidenceBuildResult.Ready(pool)
    }
}

internal sealed interface PkCalibrationObservationClassification {
    data object InvalidNonpositive : PkCalibrationObservationClassification
    data object NumericFailure : PkCalibrationObservationClassification

    data class Unassigned(
        val totalDrugPgml: Double,
    ) : PkCalibrationObservationClassification

    data class Included(
        val breakdown: PkForwardBreakdown,
    ) : PkCalibrationObservationClassification
}

/**
 * v10.0 §A10.1: no dominance partition and no positivity guard. Every
 * informative positive lab joins the one shared evidence set; the model mean
 * over all routes is positive for every finite beta, so there is nothing to
 * protect by dropping data.
 */
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
        return PkCalibrationObservationClassification.Unassigned(totalDrugPgml = total)
    }
    if (observedPgml <= 0.0) {
        return PkCalibrationObservationClassification.InvalidNonpositive
    }
    return PkCalibrationObservationClassification.Included(breakdown = breakdown)
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
    totalDrugPgml: Double? = null,
    breakdown: PkForwardBreakdown? = null,
    effectiveDisposition: PkCalibrationEffectiveDisposition,
): PkCalibrationLabEvidence {
    return PkCalibrationLabEvidence(
        resultId = resultId,
        state = state,
        observedPgml = observedPgml,
        totalDrugPgml = totalDrugPgml,
        breakdown = breakdown,
        effectiveDisposition = effectiveDisposition,
    )
}

private fun failed(
    failure: PkCalibrationEvidenceFailure,
): PkCalibrationEvidenceBuildResult.Failed {
    return PkCalibrationEvidenceBuildResult.Failed(failure)
}

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
