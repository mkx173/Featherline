package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PkCalibrationEvidenceTest {
    @Test
    fun classifyLab_setsAsideOnlyWhatTheFitCannotUse() {
        val breakdown = breakdown(oral = 10.0)
        assertNull(PkCalibrationEvidenceAdapter.classifyLab(50.0, breakdown, 5.0))
        assertEquals(
            PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL,
            PkCalibrationEvidenceAdapter.classifyLab(50.0, breakdown, Math.nextUp(10.0)),
        )
        // Exactly at the floor is informative.
        assertNull(PkCalibrationEvidenceAdapter.classifyLab(50.0, breakdown, 10.0))
        assertEquals(
            PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE,
            PkCalibrationEvidenceAdapter.classifyLab(0.0, breakdown, 5.0),
        )
        // A non-positive value in a no-drug window is below-signal, not invalid:
        // the user is not asked to correct a value the fit never needed.
        assertEquals(
            PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL,
            PkCalibrationEvidenceAdapter.classifyLab(0.0, breakdown(), 5.0),
        )
        assertEquals(
            PkCalibrationLabIgnoreReason.NUMERIC_FAILURE,
            PkCalibrationEvidenceAdapter.classifyLab(50.0, null, 5.0),
        )
        assertEquals(
            PkCalibrationLabIgnoreReason.NUMERIC_FAILURE,
            PkCalibrationEvidenceAdapter.classifyLab(Double.NaN, breakdown, 5.0),
        )
    }

    @Test
    fun build_partitionsLabs_withoutFailingTheWholeEvaluation() {
        val included = uuid(3)
        val excluded = uuid(1)
        val quiet = uuid(2)
        val nonPositive = uuid(4)
        val input = PkCalibrationInput(
            labs = listOf(
                lab(included, hoursAfterOrigin = 4.0, value = 80.0),
                lab(excluded, hoursAfterOrigin = 4.0, value = 80.0),
                // Before any dose: no modeled drug at all.
                lab(quiet, hoursAfterOrigin = -24.0, value = 80.0),
                lab(nonPositive, hoursAfterOrigin = 6.0, value = 0.0),
            ),
            doseEvents = listOf(oralDose(uuid(100), timeH = 0.0)),
            originEpochMillis = OriginMillis,
            weightKg = 70.0,
            metadata = listOf(
                E2CalibrationMetadata(excluded, E2CalibrationDisposition.EXCLUDED, Instant.EPOCH)
            ),
        )
        val pool = requireNotNull(PkCalibrationEvidenceAdapter.build(input))

        assertEquals(listOf(included), pool.included.map { it.resultId })
        assertEquals(
            mapOf(
                quiet to PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL,
                nonPositive to PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE,
            ),
            pool.ignored,
        )
        assertEquals(setOf(excluded), input.excludedLabIds)

        val result = requireNotNull(PkCalibrationSolver.solve(pool))
        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        assertEquals(pool.ignored, result.ignoredLabs)
        assertEquals(listOf(PkCalibrationRoute.ORAL), result.promotedRoutes)
    }

    @Test
    fun build_sortsIncludedLabsByResultId_forDeterministicAccumulation() {
        val ids = listOf(uuid(9), uuid(2), uuid(5))
        val input = PkCalibrationInput(
            labs = ids.map { id -> lab(id, hoursAfterOrigin = 4.0, value = 80.0) },
            doseEvents = listOf(oralDose(uuid(100), timeH = 0.0)),
            originEpochMillis = OriginMillis,
            weightKg = 70.0,
        )
        val pool = requireNotNull(PkCalibrationEvidenceAdapter.build(input))
        assertEquals(ids.sortedBy { it.toString() }, pool.included.map { it.resultId })
    }

    @Test
    fun engine_reportsNoDoses_noLabs_andNumericFailureAsGlobalStates() {
        // No doses wins over no labs: adding a lab would not help.
        val nothing = PkCalibrationEngine.evaluate(
            PkCalibrationInput(emptyList(), emptyList(), OriginMillis, 70.0)
        )
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, nothing.result.globalState)
        assertTrue(!nothing.isReady)
        val labsOnly = PkCalibrationEngine.evaluate(
            PkCalibrationInput(
                labs = listOf(lab(uuid(1), hoursAfterOrigin = 1.0, value = 80.0)),
                doseEvents = emptyList(),
                originEpochMillis = OriginMillis,
                weightKg = 70.0,
            )
        )
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, labsOnly.result.globalState)

        val empty = PkCalibrationEngine.evaluate(
            PkCalibrationInput(emptyList(), listOf(oralDose(uuid(100), timeH = 0.0)), OriginMillis, 70.0)
        )
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, empty.result.globalState)

        val badWeight = PkCalibrationEngine.evaluate(
            PkCalibrationInput(
                labs = listOf(lab(uuid(1), hoursAfterOrigin = 1.0, value = 80.0)),
                doseEvents = listOf(oralDose(uuid(100), timeH = 0.0)),
                originEpochMillis = OriginMillis,
                weightKg = 0.0,
            )
        )
        assertEquals(PkCalibrationGlobalState.NUMERIC_FAILURE, badWeight.result.globalState)

        val ready = PkCalibrationEngine.evaluate(
            PkCalibrationInput(
                labs = listOf(lab(uuid(1), hoursAfterOrigin = 4.0, value = 80.0)),
                doseEvents = listOf(oralDose(uuid(100), timeH = 0.0)),
                originEpochMillis = OriginMillis,
                weightKg = 70.0,
            )
        )
        assertEquals(PkCalibrationGlobalState.READY, ready.result.globalState)
        assertTrue(ready.isReady)
    }

    private fun lab(id: UUID, hoursAfterOrigin: Double, value: Double) = PkCalibrationLab(
        resultId = id,
        collectedAtEpochMillis = OriginMillis + (hoursAfterOrigin * 3_600_000.0).toLong(),
        valuePgml = value,
    )

    private fun oralDose(id: UUID, timeH: Double) = PkDoseEvent(
        id = id,
        sourceGroupUuid = null,
        hormone = PkHormone.ESTRADIOL,
        route = PkRoute.ORAL,
        timeH = timeH,
        doseMg = 2.0,
        compound = PkCompound.E2,
    )

    private fun breakdown(oral: Double = 0.0): PkForwardBreakdown = requireNotNull(
        PkForwardBreakdown.create(
            PkCalibrationRoute.entries.associateWith { route ->
                if (route == PkCalibrationRoute.ORAL) oral else 0.0
            }
        )
    )

    private fun uuid(value: Long): UUID = UUID(0L, value)

    private companion object {
        const val OriginMillis = 1_700_000_000_000L
    }
}
