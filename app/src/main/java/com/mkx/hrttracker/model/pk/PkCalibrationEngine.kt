package com.mkx.hrttracker.model.pk

/** One immutable evaluation; a READY result owns the evidence that produced it. */
class PkCalibrationEvaluation internal constructor(
    val result: PkCalibrationResult,
    internal val evidence: PkCalibrationEvidencePool?,
) {
    val isReady: Boolean get() = evidence != null
}

/** Presentation-free facade for one complete calibration computation. */
object PkCalibrationEngine {
    fun evaluate(input: PkCalibrationInput): PkCalibrationEvaluation {
        val evaluation = evaluateCollected(input)
        if (input.futureLabIds.isEmpty()) return evaluation
        // Set aside like any lab the fit can't use, whatever the state, so its row says why.
        val future = input.futureLabIds.associateWith { PkCalibrationLabIgnoreReason.FUTURE }
        return PkCalibrationEvaluation(
            evaluation.result.copy(ignoredLabs = evaluation.result.ignoredLabs + future),
            evaluation.evidence,
        )
    }

    private fun evaluateCollected(input: PkCalibrationInput): PkCalibrationEvaluation {
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
            ?: return PkCalibrationEvaluation(
                PkCalibrationResult(PkCalibrationGlobalState.NUMERIC_FAILURE),
                null,
            )
        return PkCalibrationEvaluation(PkCalibrationSolver.solve(evidence), evidence)
    }
}
