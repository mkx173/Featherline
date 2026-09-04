package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The POSTERIOR_MODE_AMBIGUOUS warning must mean the posterior really has
 * more than one mode. Two starts that converge into the same basin by
 * different exits (gradient tolerance vs step tolerance) must never be
 * reported as two modes, and well-posed evidence must never fail numerically.
 *
 * Single active route: the number of local minima of the restricted
 * objective is counted by brute force (gradient sign changes on a fine grid)
 * and compared with the solver's verdict over a seeded sweep that includes
 * outlier-dominated evidence, where a basin's curvature is far below the
 * prior precision and the step-tolerance exit is loosest. Guards the
 * JOINT_* tolerances: loosening JOINT_MODE_DISTINCT_TOL below the step-exit
 * precision, or tightening JOINT_GRAD_TOL past binary64 resolution of the
 * objective, shows up here as a false ambiguity or a numeric failure.
 */
class PkJointMapSolverAmbiguitySweepTest {
    private val rLog = PkCalibrationConfig.Default.rLog

    @Test
    fun ambiguityVerdict_matchesBruteForceMinimaCount_onSingleRouteEvidence() {
        val random = Random(20260902)
        var trulyAmbiguous = 0
        repeat(SWEEP_SIZE) { iteration ->
            val labs = if (iteration % 4 == 0) {
                clusteredSingleRouteLabs(random, iteration)
            } else {
                randomSingleRouteLabs(random, iteration)
            }
            val objective = requireNotNull(PkJointStudentTObjective.fromEvidence(labs, rLog))
            val minima = bruteForceMinima(objective)
            assertTrue("iteration $iteration: no minimum inside the solver's scan range", minima.isNotEmpty())
            val fitted = PkJointMapSolver.fit(objective)
            assertTrue("iteration $iteration: numeric failure on well-posed evidence", fitted != null)
            fitted!!
            assertEquals(
                "iteration $iteration: minima=$minima beta=${fitted.beta.toList()}",
                minima.size > 1,
                fitted.ambiguous,
            )
            val beta = fitted.beta[PkCalibrationRoute.INJECTION.ordinal]
            assertTrue(
                "iteration $iteration: MAP $beta is not one of the minima $minima",
                minima.any { minimum -> abs(minimum - beta) < 1e-3 },
            )
            if (minima.size > 1) trulyAmbiguous += 1
        }
        assertTrue("sweep never hit a truly bimodal case", trulyAmbiguous > 0)
    }

    /** Between 1 and 6 labs; some are far outliers so the Student-t basin is shallow. */
    private fun randomSingleRouteLabs(random: Random, iteration: Int): List<PkCalibrationIncludedLab> {
        val count = 1 + random.nextInt(6)
        val trueBeta = random.nextDouble(-0.6, 0.6)
        val sqrtR = sqrt(rLog)
        return List(count) { index ->
            val drug = exp(random.nextDouble(ln(5.0), ln(300.0)))
            val z = when (random.nextInt(4)) {
                0 -> random.nextDouble(-6.0, 6.0) // outlier
                else -> random.nextDouble(-1.5, 1.5)
            }
            val observed = drug * exp(trueBeta + z * sqrtR)
            PkCalibrationIncludedLab(
                resultId = UUID(iteration.toLong(), index.toLong()),
                observedPgml = observed,
                breakdown = requireNotNull(
                    PkForwardBreakdown.create(
                        PkCalibrationRoute.entries.associateWith { route ->
                            if (route == PkCalibrationRoute.INJECTION) drug else 0.0
                        }
                    )
                ),
            )
        }
    }

    /**
     * Two clusters of labs straddling the model at +/- q: separated widely
     * enough the posterior is bimodal, so the sweep crosses the threshold in
     * both directions rather than exercising one side of the verdict only.
     */
    private fun clusteredSingleRouteLabs(random: Random, iteration: Int): List<PkCalibrationIncludedLab> {
        val q = random.nextDouble(0.3, 2.0) * sqrt(3.0 * PkCalibrationDefaults.STUDENT_T_NU * rLog)
        val perCluster = 1 + random.nextInt(4)
        val drug = exp(random.nextDouble(ln(5.0), ln(300.0)))
        return List(2 * perCluster) { index ->
            val sign = if (index < perCluster) -1.0 else 1.0
            PkCalibrationIncludedLab(
                resultId = UUID(iteration.toLong(), index.toLong()),
                observedPgml = drug * exp(sign * q),
                breakdown = requireNotNull(
                    PkForwardBreakdown.create(
                        PkCalibrationRoute.entries.associateWith { route ->
                            if (route == PkCalibrationRoute.INJECTION) drug else 0.0
                        }
                    )
                ),
            )
        }
    }

    /**
     * Local minima of the restricted objective from gradient sign changes
     * (- to +), scanned over the solver's own range: beyond the stationary
     * bound the prior's pull dominates the bounded Student-t score, so no
     * minimum lies outside it.
     */
    private fun bruteForceMinima(objective: PkJointStudentTObjective): List<Double> {
        val routeIndex = PkCalibrationRoute.INJECTION.ordinal
        val priorVariance = PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD *
                PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
        val stationaryBound = priorVariance * objective.points.size *
                (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                (2.0 * sqrt(PkCalibrationDefaults.STUDENT_T_NU) * sqrt(objective.rLog))
        val halfWidth = min(stationaryBound, PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA)
        val step = 1e-3
        val steps = (2 * halfWidth / step).toInt()
        fun gradientAt(value: Double): Double {
            val beta = DoubleArray(objective.routeCount)
            beta[routeIndex] = value
            return requireNotNull(objective.gradient(beta))[0]
        }
        val minima = ArrayList<Double>()
        var previous = -halfWidth
        var previousGradient = gradientAt(previous)
        for (index in 1..steps) {
            val value = -halfWidth + index * step
            val gradient = gradientAt(value)
            if (previousGradient < 0.0 && gradient >= 0.0) {
                minima += (previous + value) / 2.0
            }
            previous = value
            previousGradient = gradient
        }
        return minima
    }

    private companion object {
        const val SWEEP_SIZE = 1500
    }
}
