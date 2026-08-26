package com.mkx.hrttracker.model.pk

import java.util.UUID

/** One E2 lab observation. The value is the canonical pg/mL stored on the result. */
data class PkCalibrationLab(
    val resultId: UUID,
    val collectedAtEpochMillis: Long,
    val valuePgml: Double,
)

/** Every value one calibration evaluation reads. */
data class PkCalibrationInput(
    val labs: List<PkCalibrationLab>,
    /** Estradiol dose events with `timeH` relative to [originEpochMillis]. */
    val doseEvents: List<PkDoseEvent>,
    val originEpochMillis: Long,
    val weightKg: Double,
    val metadata: List<E2CalibrationMetadata> = emptyList(),
    val config: PkCalibrationConfig = PkCalibrationConfig.Default,
) {
    val excludedLabIds: Set<UUID>
        get() = metadata
            .filter { it.disposition == E2CalibrationDisposition.EXCLUDED }
            .map { it.resultId }
            .toSet()
}

/**
 * v10.0 §A10.1: one included lab carries the full population decomposition;
 * every route it touches is informed in proportion to its modeled share.
 */
data class PkCalibrationIncludedLab(
    val resultId: UUID,
    val observedPgml: Double,
    val breakdown: PkForwardBreakdown,
)

class PkCalibrationEvidencePool internal constructor(
    val input: PkCalibrationInput,
    internal val forwardModel: PkE2ForwardModel,
    /** Sorted by result id for deterministic accumulation. */
    val included: List<PkCalibrationIncludedLab>,
    val ignored: Map<UUID, PkCalibrationLabIgnoreReason>,
)

/**
 * Classifies every non-excluded lab. Nothing here fails the evaluation: a lab
 * the fit cannot use is set aside with a reason and the rest proceed.
 */
object PkCalibrationEvidenceAdapter {
    /** Null only when the forward model itself cannot be built from the dose history. */
    fun build(input: PkCalibrationInput): PkCalibrationEvidencePool? {
        val forwardModel = PkE2ForwardModel.create(input.doseEvents, input.weightKg)
            ?: return null
        val excluded = input.excludedLabIds
        val included = ArrayList<PkCalibrationIncludedLab>()
        val ignored = linkedMapOf<UUID, PkCalibrationLabIgnoreReason>()
        for (lab in input.labs.sortedBy { it.resultId.toString() }) {
            if (lab.resultId in excluded) continue
            val breakdown = forwardModel.breakdownAt(
                epochDifferenceHours(lab.collectedAtEpochMillis, input.originEpochMillis)
            )
            val reason = classifyLab(lab.valuePgml, breakdown, input.config.drugMinInformativePgml)
            if (reason != null) {
                ignored[lab.resultId] = reason
            } else {
                included += PkCalibrationIncludedLab(lab.resultId, lab.valuePgml, breakdown!!)
            }
        }
        return PkCalibrationEvidencePool(input, forwardModel, included, ignored)
    }

    /** Null means the lab joins the fit. */
    internal fun classifyLab(
        observedPgml: Double,
        breakdown: PkForwardBreakdown?,
        drugMinInformativePgml: Double,
    ): PkCalibrationLabIgnoreReason? {
        if (breakdown == null || !observedPgml.isFinite()) {
            return PkCalibrationLabIgnoreReason.NUMERIC_FAILURE
        }
        if (breakdown.totalDrugPgml < drugMinInformativePgml) {
            return PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL
        }
        if (observedPgml <= 0.0) return PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE
        return null
    }
}
