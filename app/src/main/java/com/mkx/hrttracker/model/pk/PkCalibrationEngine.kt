package com.mkx.hrttracker.model.pk

import java.util.Collections

/**
 * One explicit, immutable read of the semantic inputs consumed by calibration.
 *
 * Construction validates identities needed to represent every global result. The
 * evidence adapter remains responsible for semantic validation and deterministic
 * global-failure precedence (including unresolved or invalid Current Weight).
 */
@ConsistentCopyVisibility
data class PkCalibrationInputSnapshot private constructor(
    val labs: List<PkCalibrationE2LabSource>,
    val medicationEvents: List<PkCalibrationMedicationEventSource>,
    val forwardTimeOriginEpochMillis: Long,
    val resolvedCurrentWeightKg: Double?,
    val forwardModelVersion: String,
    val calibrationModelVersion: String,
) {
    companion object {
        fun create(
            labs: List<PkCalibrationE2LabSource>,
            medicationEvents: List<PkCalibrationMedicationEventSource>,
            forwardTimeOriginEpochMillis: Long,
            resolvedCurrentWeightKg: Double?,
            forwardModelVersion: String,
            calibrationModelVersion: String,
        ): PkCalibrationInputSnapshot? {
            if (!forwardModelVersion.isStableAsciiIdentity() ||
                !calibrationModelVersion.isStableAsciiIdentity()
            ) {
                return null
            }
            return PkCalibrationInputSnapshot(
                labs = Collections.unmodifiableList(ArrayList(labs)),
                medicationEvents = Collections.unmodifiableList(ArrayList(medicationEvents)),
                forwardTimeOriginEpochMillis = forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = resolvedCurrentWeightKg,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
            )
        }
    }
}

/**
 * One immutable engine-issued evaluation. A READY instance atomically owns the solver result
 * and the exact canonical evidence/config/scope/review snapshot that produced it.
 */
class PkCalibrationEngineEvaluation private constructor(
    val result: PkCalibrationResult,
    internal val readyEvidence: PkCalibrationEvidencePool?,
    proof: Any,
) {
    init {
        require(proof === PkCalibrationEngineEvaluationBindingProof)
    }

    val isReady: Boolean get() = readyEvidence != null

    /**
     * Pure convenience boundary for the later stateful engine API.
     *
     * Frozen Phase-1 contract: a non-READY evaluation returns null, leaving the chart consumer
     * on its existing population projection. For a READY evaluation, numeric render failure is
     * represented by a non-null NUMERIC_UNAVAILABLE result instead.
     */
    fun renderFor(domain: PkChartDomain): PkCalibrationRenderResult? {
        return PkCalibrationRenderer.render(this, domain)
    }

    internal companion object {
        fun ready(solution: PkCalibrationBoundSolution): PkCalibrationEngineEvaluation? {
            val result = solution.result
            val evidence = solution.evidence
            val canonical = evidence.canonicalInput
            if (result.globalState != PkCalibrationGlobalState.READY ||
                result.forwardModelVersion != canonical.forwardModelVersion ||
                result.calibrationModelVersion != canonical.calibrationModelVersion
            ) {
                return null
            }
            return PkCalibrationEngineEvaluation(
                result,
                evidence,
                PkCalibrationEngineEvaluationBindingProof,
            )
        }

        fun notReady(result: PkCalibrationResult): PkCalibrationEngineEvaluation {
            require(result.globalState != PkCalibrationGlobalState.READY)
            return PkCalibrationEngineEvaluation(
                result,
                null,
                PkCalibrationEngineEvaluationBindingProof,
            )
        }
    }
}

private object PkCalibrationEngineEvaluationBindingProof

/** Presentation-free facade for one complete, atomic calibration computation. */
object PkCalibrationEngine {
    fun compute(
        input: PkCalibrationInputSnapshot,
        metadata: List<E2CalibrationMetadata>,
        identityPolicy: PkCalibrationIdentityPolicy,
        config: PkCalibrationConfig,
        attestationProvider: PkCalibrationAttestationProvider,
    ): PkCalibrationResult {
        return evaluate(
            input = input,
            metadata = metadata,
            identityPolicy = identityPolicy,
            config = config,
            attestationProvider = attestationProvider,
        ).result
    }

    fun evaluate(
        input: PkCalibrationInputSnapshot,
        metadata: List<E2CalibrationMetadata>,
        identityPolicy: PkCalibrationIdentityPolicy,
        config: PkCalibrationConfig,
        attestationProvider: PkCalibrationAttestationProvider,
    ): PkCalibrationEngineEvaluation {
        return when (
            val evidence = PkCalibrationEvidenceAdapter.build(
                labs = input.labs,
                medicationEvents = input.medicationEvents,
                forwardTimeOriginEpochMillis = input.forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = input.resolvedCurrentWeightKg,
                metadata = metadata,
                identityPolicy = identityPolicy,
                config = config,
                attestationProvider = attestationProvider,
                forwardModelVersion = input.forwardModelVersion,
                calibrationModelVersion = input.calibrationModelVersion,
            )
        ) {
            is PkCalibrationEvidenceBuildResult.Ready -> {
                val solved = PkCalibrationSolver.solveBound(evidence.pool)
                if (solved != null) {
                    requireNotNull(PkCalibrationEngineEvaluation.ready(solved))
                } else {
                    PkCalibrationEngineEvaluation.notReady(
                        failureResult(
                            input,
                            PkCalibrationGlobalState.SHARED_NUMERIC_FAILURE,
                            PkCalibrationReason.NUMERIC_FAILURE,
                        )
                    )
                }
            }

            is PkCalibrationEvidenceBuildResult.Failed -> {
                val (state, reason) = when (evidence.failure) {
                    PkCalibrationEvidenceFailure.INVALID_NONPOSITIVE_E2 ->
                        PkCalibrationGlobalState.SHARED_INPUT_INVALID to
                                PkCalibrationReason.INVALID_NONPOSITIVE_E2

                    PkCalibrationEvidenceFailure.SCOPE_NOT_CONFIRMED ->
                        PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED to
                                PkCalibrationReason.SCOPE_NOT_CONFIRMED

                    PkCalibrationEvidenceFailure.SHARED_INPUT_INVALID ->
                        PkCalibrationGlobalState.SHARED_INPUT_INVALID to
                                PkCalibrationReason.SHARED_INPUT_INVALID

                    PkCalibrationEvidenceFailure.SHARED_NUMERIC_FAILURE ->
                        PkCalibrationGlobalState.SHARED_NUMERIC_FAILURE to
                                PkCalibrationReason.NUMERIC_FAILURE
                }
                PkCalibrationEngineEvaluation.notReady(
                    failureResult(input, state, reason)
                )
            }
        }
    }

    private fun failureResult(
        input: PkCalibrationInputSnapshot,
        state: PkCalibrationGlobalState,
        reason: PkCalibrationReason,
    ): PkCalibrationResult {
        return requireNotNull(
            PkCalibrationResult.create(
                globalState = state,
                globalReasons = setOf(reason),
                forwardModelVersion = input.forwardModelVersion,
                calibrationModelVersion = input.calibrationModelVersion,
            )
        )
    }
}
