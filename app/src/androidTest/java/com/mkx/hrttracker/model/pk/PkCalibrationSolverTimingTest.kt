package com.mkx.hrttracker.model.pk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Random
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.exp

/**
 * On-device timing of one calibration evaluation (forward model, evidence,
 * joint fit) against lab count and route count. Not a correctness suite:
 * it only asserts that every size solves. Opt in with
 * `-Pandroid.testInstrumentationRunnerArguments.pkTiming=true`; read the
 * table from logcat tag [TAG].
 */
@RunWith(AndroidJUnit4::class)
class PkCalibrationSolverTimingTest {
    @Test
    fun evaluate_timingTable() {
        assumeTrue(
            "opt-in timing run",
            InstrumentationRegistry.getArguments().getString("pkTiming") == "true",
        )
        val labCounts = listOf(5, 10, 20, 27, 50, 100)
        for (routeCount in listOf(1, 5)) {
            val doses = doseHistory(routeCount)
            for (labCount in labCounts) {
                val input = PkCalibrationInput(
                    labs = labs(doses, labCount, routeCount),
                    doseEvents = doses,
                    originEpochMillis = 0L,
                    weightKg = 70.0,
                )
                repeat(WARMUP_ITERATIONS) { PkCalibrationEngine.evaluate(input) }
                val samplesMs = ArrayList<Double>()
                var evaluation: PkCalibrationEvaluation? = null
                val budgetEnd = System.nanoTime() + MEASURE_BUDGET_NANOS
                while (samplesMs.size < MIN_ITERATIONS ||
                    (samplesMs.size < MAX_ITERATIONS && System.nanoTime() < budgetEnd)
                ) {
                    val start = System.nanoTime()
                    evaluation = PkCalibrationEngine.evaluate(input)
                    samplesMs += (System.nanoTime() - start) / 1e6
                }
                val result = checkNotNull(evaluation).result
                assertEquals(PkCalibrationGlobalState.READY, result.globalState)
                assertFalse(result.routeResults.any { row ->
                    row.displayState == PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE
                })
                val sorted = samplesMs.sorted()
                Log.i(
                    TAG,
                    "labs=%d routes=%d median=%.1fms min=%.1fms max=%.1fms iterations=%d promoted=%s ambiguous=%b".format(
                        labCount,
                        routeCount,
                        sorted[sorted.size / 2],
                        sorted.first(),
                        sorted.last(),
                        sorted.size,
                        result.promotedRoutes.joinToString(",") { it.stableId },
                        result.routeResults.any {
                            PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS in it.reasons
                        },
                    ),
                )
            }
        }
    }

    /**
     * One year of doses. One route: EV 5 mg every 7 d. Five routes: the year
     * split into five eras, each dominated by one route, so every route has
     * supporting labs and is fitted.
     */
    private fun doseHistory(routeCount: Int): List<PkDoseEvent> {
        val events = ArrayList<PkDoseEvent>()
        fun event(route: PkRoute, timeH: Double, doseMg: Double, compound: PkCompound, rate: Double? = null) {
            events += PkDoseEvent(
                id = UUID(route.ordinal.toLong(), events.size.toLong()),
                sourceGroupUuid = null,
                hormone = PkHormone.ESTRADIOL,
                route = route,
                timeH = timeH,
                doseMg = doseMg,
                compound = compound,
                releaseRateMcgPerDay = rate,
                areaCm2 = if (route == PkRoute.GEL) 750.0 else null,
            )
        }
        if (routeCount == 1) {
            var t = 0.0
            while (t < YEAR_H) {
                event(PkRoute.INJECTION, t, 5.0, PkCompound.EV)
                t += 7.0 * 24.0
            }
            return events
        }
        val eraH = YEAR_H / 5.0
        // Era 0 injection, 1 patch, 2 gel, 3 oral, 4 sublingual.
        var t = 0.0
        while (t < eraH) {
            event(PkRoute.INJECTION, t, 5.0, PkCompound.EV)
            t += 7.0 * 24.0
        }
        t = eraH
        while (t < 2.0 * eraH) {
            event(PkRoute.PATCH_APPLY, t, 0.0, PkCompound.E2, rate = 100.0)
            event(PkRoute.PATCH_REMOVE, t + 3.5 * 24.0, 0.0, PkCompound.E2)
            t += 3.5 * 24.0
        }
        t = 2.0 * eraH
        while (t < 3.0 * eraH) {
            event(PkRoute.GEL, t, 1.5, PkCompound.E2)
            t += 24.0
        }
        t = 3.0 * eraH
        while (t < 4.0 * eraH) {
            event(PkRoute.ORAL, t, 2.0, PkCompound.E2)
            t += 24.0
        }
        t = 4.0 * eraH
        while (t < YEAR_H) {
            event(PkRoute.SUBLINGUAL, t, 1.0, PkCompound.E2)
            t += 24.0
        }
        return events
    }

    /** Labs spread over the year, drawn from the true scaled model with 15% log noise; every 7th is a 4-sigma outlier. */
    private fun labs(doses: List<PkDoseEvent>, count: Int, routeCount: Int): List<PkCalibrationLab> {
        val model = requireNotNull(PkE2ForwardModel.create(doses, 70.0))
        val trueBeta = mapOf(
            PkCalibrationRoute.INJECTION to 0.25,
            PkCalibrationRoute.PATCH to -0.30,
            PkCalibrationRoute.GEL to 0.40,
            PkCalibrationRoute.ORAL to -0.15,
            PkCalibrationRoute.SUBLINGUAL to 0.10,
        )
        val random = Random(20261011L + routeCount)
        return List(count) { index ->
            val timeH = (10.0 + (365.0 - 10.0) * (index + 0.5) / count) * 24.0
            val breakdown = requireNotNull(model.breakdownAt(timeH))
            val mean = PkCalibrationRoute.entries.sumOf { route ->
                exp(trueBeta.getValue(route)) * breakdown.byRouteDrugPgml.getValue(route)
            }
            val z = if (index % 7 == 6) 4.0 else random.nextGaussian()
            PkCalibrationLab(
                resultId = UUID(99L, index.toLong()),
                collectedAtEpochMillis = (timeH * 3_600_000.0).toLong(),
                valuePgml = mean * exp(z * 0.15),
            )
        }
    }

    private companion object {
        const val TAG = "PkCalibrationTiming"
        const val YEAR_H = 365.0 * 24.0
        const val WARMUP_ITERATIONS = 2
        const val MIN_ITERATIONS = 3
        const val MAX_ITERATIONS = 10
        const val MEASURE_BUDGET_NANOS = 3_000_000_000L
    }
}
