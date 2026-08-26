package com.mkx.hrttracker.model.pk

/** One immutable evaluation; a READY result owns the evidence that produced it. */
class PkCalibrationEvaluation internal constructor(
    val result: PkCalibrationResult,
    internal val evidence: PkCalibrationEvidencePool?,
) {
    val isReady: Boolean get() = evidence != null

    /** Null for a non-READY evaluation; numeric render failure is a NUMERIC_UNAVAILABLE result. */
    fun renderFor(domain: PkChartDomain): PkCalibrationRenderResult? {
        return PkCalibrationRenderer.render(this, domain)
    }
}

/** Presentation-free facade for one complete calibration computation. */
object PkCalibrationEngine {
    fun evaluate(input: PkCalibrationInput): PkCalibrationEvaluation {
        // Checked before labs: without doses, adding a lab would not help.
        if (input.doseEvents.isEmpty()) {
            return PkCalibrationEvaluation(
                PkCalibrationResult(PkCalibrationGlobalState.NO_DOSE_HISTORY),
                null,
            )
        }
        if (input.labs.isEmpty()) {
            return PkCalibrationEvaluation(
                PkCalibrationResult(PkCalibrationGlobalState.NO_USABLE_LABS),
                null,
            )
        }
        val evidence = PkCalibrationEvidenceAdapter.build(input)
        val solved = evidence?.let(PkCalibrationSolver::solve)
        if (evidence == null || solved == null) {
            return PkCalibrationEvaluation(
                PkCalibrationResult(PkCalibrationGlobalState.NUMERIC_FAILURE),
                null,
            )
        }
        return PkCalibrationEvaluation(solved, evidence)
    }
}
