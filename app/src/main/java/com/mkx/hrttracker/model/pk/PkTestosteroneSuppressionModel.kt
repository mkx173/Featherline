package com.mkx.hrttracker.model.pk

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.abs

/**
 * A simple phenomenological model for Testosterone suppression by Estradiol.
 * Baseline T is reduced depending on E2 concentration via an Emax (Hill) equation:
 *
 *   T_predicted = max(floorT, baselineT * sensitivity / (e2 + sensitivity))
 *
 * E2 units: pg/mL
 * T units:  ng/dL
 *
 * Parameters are calibrated from paired (E2, T) lab results using iterative
 * gradient descent in log-space (logBaselineT, logSensitivity), which mirrors
 * how the main E2 PK pipeline calibrates logAmplitude / logClearance.
 */
class PkTestosteroneSuppressionModel {

    /**
     * Calibration state for T suppression parameters.
     * @param logBaselineT   ln(unsuppressed baseline T in ng/dL), default ~600 ng/dL
     * @param logSensitivity ln(E2 IC50 in pg/mL), default ~100 pg/mL
     */
    data class SuppressState(
        val logBaselineT: Double = ln(600.0),
        val logSensitivity: Double = ln(100.0)
    )

    /**
     * Paired E2/T measurement used for calibration.
     *
     * @param e2PgMl   E2 concentration at the time of blood draw, in pg/mL.
     * @param tNgDl    T concentration at the time of blood draw, in ng/dL.
     */
    data class TLabPoint(val e2PgMl: Double, val tNgDl: Double)

    /** Floor T concentration (fully suppressed female reference), in ng/dL. */
    private val floorT = 15.0

    /**
     * Predict T concentration given an E2 concentration and the current calibration state.
     */
    fun predictT(e2PgMl: Double, state: SuppressState): Double {
        val baselineT = exp(state.logBaselineT)
        val sensitivity = exp(state.logSensitivity)
        val suppressedT = baselineT * (sensitivity / (e2PgMl + sensitivity))
        return max(floorT, suppressedT)
    }

    /**
     * Calibrate [SuppressState] from paired E2/T lab measurements using gradient descent
     * in log-space. Returns the prior if [labPoints] is empty.
     *
     * This follows the same pattern as [PkCalibrationPipeline] but uses a direct algebraic
     * model instead of a dynamic EKF, since T(E2) is a static Emax function.
     *
     * @param labPoints  List of (E2 pg/mL, T ng/dL) pairs from blood tests.
     * @param prior      Starting state (default priors are population-level estimates).
     * @param iterations Number of gradient descent steps.
     * @param learningRate Step size in log-parameter space.
     */
    fun calibrate(
        labPoints: List<TLabPoint>,
        prior: SuppressState = SuppressState(),
        iterations: Int = 200,
        learningRate: Double = 0.05,
    ): SuppressState {
        if (labPoints.isEmpty()) return prior

        // Filter out implausible points (e.g. E2 <= 0 or T <= floorT).
        val validPoints = labPoints.filter { it.e2PgMl > 0.0 && it.tNgDl > floorT }
        if (validPoints.isEmpty()) return prior

        var logBaselineT = prior.logBaselineT
        var logSensitivity = prior.logSensitivity

        repeat(iterations) {
            val baselineT = exp(logBaselineT)
            val sensitivity = exp(logSensitivity)

            var gradLogBaseline = 0.0
            var gradLogSensitivity = 0.0

            for (point in validPoints) {
                val e2 = point.e2PgMl
                val tObs = point.tNgDl
                val ratio = sensitivity / (e2 + sensitivity)
                val tPred = (baselineT * ratio).coerceAtLeast(floorT)

                // MSE loss gradient: d(MSE)/d(param)
                val residual = tPred - tObs

                // d(tPred)/d(logBaselineT) = tPred (since tPred = baselineT * ratio, d/d(ln B) = tPred)
                gradLogBaseline += residual * tPred

                // d(tPred)/d(logSensitivity) = baselineT * sensitivity * e2 / (e2 + sensitivity)^2
                // In log-space: multiply by sensitivity (chain rule for d/d(ln s) = s * d/ds)
                gradLogSensitivity += residual * baselineT * sensitivity * e2 /
                        ((e2 + sensitivity) * (e2 + sensitivity))
            }

            val n = validPoints.size.toDouble()
            logBaselineT -= learningRate * gradLogBaseline / n
            logSensitivity -= learningRate * gradLogSensitivity / n

            // Clamp to physically plausible ranges:
            // baseline T: 50–3000 ng/dL;  E2 IC50: 10–2000 pg/mL
            logBaselineT = logBaselineT.coerceIn(ln(50.0), ln(3000.0))
            logSensitivity = logSensitivity.coerceIn(ln(10.0), ln(2000.0))
        }

        return SuppressState(logBaselineT = logBaselineT, logSensitivity = logSensitivity)
    }
}
