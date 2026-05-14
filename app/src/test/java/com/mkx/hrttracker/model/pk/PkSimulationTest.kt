package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.exp

class PkSimulationTest {
    @Test
    fun oneCompartmentAmount_matchesBatemanEquation() {
        val params = PkParams(
            fracFast = 1.0,
            k1Fast = 0.32,
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = 0.41,
            bioavailability = 0.03,
            rateMgH = 0.0,
            fastBioavailability = 0.03,
            slowBioavailability = 0.03,
        )

        val amount = ThreeCompartmentModel.oneCompartmentAmount(
            tau = 1.0,
            doseMg = 2.0,
            p = params,
        )

        val expected = 2.0 * 0.03 * 0.32 / (0.32 - 0.41) *
            (exp(-0.41) - exp(-0.32))
        assertEquals(expected, amount, 1e-12)
    }

    @Test
    fun patchAmount_zeroOrderDecaysAfterRemoval() {
        val params = PkParams(
            fracFast = 1.0,
            k1Fast = 0.0,
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = 0.5,
            bioavailability = 1.0,
            rateMgH = 0.1,
            fastBioavailability = 1.0,
            slowBioavailability = 1.0,
        )

        val amount = ThreeCompartmentModel.patchAmount(
            tau = 3.0,
            doseMg = 0.0,
            wearH = 2.0,
            p = params,
        )

        val expectedAtRemoval = 0.1 / 0.5 * (1.0 - exp(-0.5 * 2.0))
        val expected = expectedAtRemoval * exp(-0.5)
        assertEquals(expected, amount, 1e-12)
    }

    @Test
    fun simulateMainEstradiolTrend_keepsInjectionEsterDoseAsMedicineMg() {
        val now = LocalDateTime.of(2026, 5, 5, 12, 0)
        val zoneId = ZoneId.systemDefault()
        val injectionAt = now.minusHours(48)
        val bodyWeightKg = 70.0
        val medicineDoseMg = 5.0
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_VALERATE,
                applicationType = MedicationApplicationType.INJECTION,
                dose = MedicationDose.MgAsMedicine(medicineDoseMg),
            ),
            dosageMgAsEstradiol = medicineDoseMg * PkCatalog.activeFactor(PkCompound.EV),
            sourceGroupUuid = null,
            appliedAt = testInstant(injectionAt),
        )

        val trend = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(entry),
                bodyWeightKg = bodyWeightKg,
                now = now,
                zoneId = zoneId,
            )
        )
        val expected = PkSimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = null,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.INJECTION,
                    timeH = 36.0,
                    doseMg = medicineDoseMg,
                    compound = PkCompound.EV,
                )
            ),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = bodyWeightKg,
            startTimeH = 0.0,
            endTimeH = 168.0,
            numberOfSteps = 169,
        ).run(sampleTimeH = listOf(0.0, 84.0))

        assertEquals(expected.concentrationAt(84.0) ?: 0.0, trend.currentConcentration, 1e-9)
    }

    @Test
    fun simulateMainEstradiolTrend_usesMedicationLogsAndBodyWeight() {
        val now = LocalDateTime.of(2026, 5, 5, 12, 0)
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(now.minusMinutes(90).plusSeconds(12).plusNanos(345_000_000)),
        )

        val result = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = listOf(entry),
            bodyWeightKg = 50.0,
            now = now,
            zoneId = ZoneId.systemDefault(),
        )

        assertNotNull(result)
        val trend = checkNotNull(result)
        assertEquals(PkConcentrationUnit.PG_PER_ML, trend.concentrationUnit)
        assertEquals(8, trend.dailyConcentrations.size)
        assertEquals(1692, trend.chartConcentrations.size)
        assertEquals(trend.chartTimeH.size, trend.chartConcentrations.size)
        assertEquals(1, trend.chartSampleIntervalHours)
        assertEquals(168, trend.chartWindowHours)
        assertEquals(84.0, trend.predictionStartTimeH, 1e-9)
        assertTrue(trend.chartTimeH.contains(82.0))
        assertTrue(trend.chartTimeH.contains(82.5))
        assertTrue(trend.chartTimeH.contains(82.5034))
        assertTrue(trend.chartTimeH.contains(82.7534))
        assertTrue(trend.chartTimeH.contains(83.0034))
        assertEquals(
            225,
            trend.chartTimeH.count { timeH -> timeH > 60.0 && timeH < 82.5034 }
        )
        assertEquals(82.5034, trend.doseMarkers.single().timeH, 1e-9)
        assertTrue(trend.currentConcentration > 0.0)
        assertTrue(trend.chartConcentrations.last() < trend.currentConcentration)
        assertTrue(checkNotNull(trend.chartConcentrations.maxOrNull()) > checkNotNull(trend.chartConcentrations.minOrNull()))
        val currentIndex = trend.chartTimeH.indexOf(trend.predictionStartTimeH)
        assertEquals(trend.currentConcentration, trend.chartConcentrations[currentIndex], 1e-9)
    }

    @Test
    fun simulateMainEstradiolTrend_usesDefaultBodyWeightWhenUnset() {
        val result = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = emptyList(),
            bodyWeightKg = null,
            now = LocalDateTime.of(2026, 5, 5, 12, 0),
        )
        val explicitDefaultResult = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = emptyList(),
            bodyWeightKg = 70.0,
            now = LocalDateTime.of(2026, 5, 5, 12, 0),
        )

        assertNotNull(result)
        assertEquals(explicitDefaultResult, result)
    }

    @Test
    fun simulateMainEstradiolProjection_canServeLaterMainTrendFromCache() {
        val zoneId = ZoneId.systemDefault()
        val generatedAt = LocalDateTime.of(2026, 5, 5, 12, 0)
        val laterLaunch = generatedAt.plusDays(2).plusHours(3)
        val entries = listOf(
            testMedicationLogEntry(
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL_VALERATE,
                    applicationType = MedicationApplicationType.INJECTION,
                    dose = MedicationDose.MgAsMedicine(5.0),
                ),
                dosageMgAsEstradiol = 5.0 * PkCatalog.activeFactor(PkCompound.EV),
                sourceGroupUuid = null,
                appliedAt = testInstant(generatedAt.minusDays(2)),
            ),
            testMedicationLogEntry(
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0),
                ),
                dosageMgAsEstradiol = 2.0,
                sourceGroupUuid = null,
                appliedAt = testInstant(generatedAt.minusHours(6)),
            )
        )

        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = entries,
            bodyWeightKg = 62.0,
            generatedAt = generatedAt,
            zoneId = zoneId,
            futureDays = 14,
        )
        val cachedTrend = checkNotNull(
            projection.toMainEstradiolTrend(
                now = laterLaunch,
                zoneId = zoneId,
            )
        )
        val directTrend = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = entries,
                bodyWeightKg = 62.0,
                now = laterLaunch,
                zoneId = zoneId,
            )
        )

        assertEquals(directTrend.currentConcentration, cachedTrend.currentConcentration, 1e-9)
        assertEquals(directTrend.previousDayConcentration, cachedTrend.previousDayConcentration, 1e-9)
        assertEquals(directTrend.dailyConcentrations, cachedTrend.dailyConcentrations)
        assertEquals(directTrend.chartWindowHours, cachedTrend.chartWindowHours)
        assertEquals(directTrend.predictionStartTimeH, cachedTrend.predictionStartTimeH, 1e-9)
    }

    @Test
    fun patchSimulation_pairsAcrossGroupsForRemoveAll() {
        val groupA = UUID.randomUUID()
        val groupB = UUID.randomUUID()
        val releaseRate = 50.0

        fun simulate(events: List<PkDoseEvent>): List<Double> {
            return PkSimulationEngine(
                events = events,
                hormone = PkHormone.ESTRADIOL,
                bodyWeightKg = 70.0,
                startTimeH = 0.0,
                endTimeH = 200.0,
                numberOfSteps = 201,
            ).run(sampleTimeH = listOf(0.0, 84.0, 150.0)).concentrations
        }

        val crossGroup = simulate(
            listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 0.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupB,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_REMOVE,
                    timeH = 84.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                ),
            )
        )
        val sameGroup = simulate(
            listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 0.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_REMOVE,
                    timeH = 84.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                ),
            )
        )

        assertEquals(sameGroup, crossGroup)
        // Sanity: the patch must actually have been removed — concentration at
        // t=150 should be strictly lower than at t=84 (decay phase).
        assertTrue(crossGroup[2] < crossGroup[1])
    }

    @Test
    fun patchSimulation_removeAtSameInstantAsApplyPairsWithPreviousApply() {
        // Logging "remove old patch" and "apply new patch" at the same instant
        // (common when swapping patches) must pair the remove with the previous
        // apply, not the new one — the new patch should still be on at the end
        // of the run.
        val groupA = UUID.randomUUID()
        val releaseRate = 50.0

        val swapResult = PkSimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 0.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_REMOVE,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
            ),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = 70.0,
            startTimeH = 0.0,
            endTimeH = 400.0,
            numberOfSteps = 401,
        ).run(sampleTimeH = listOf(0.0, 168.0, 200.0))

        // Equivalent decomposition: a removed first patch + a still-on second patch.
        // The sum of the two single-event runs must match the combined run, which
        // is only true if the REMOVE bound the FIRST apply (not the second one).
        val firstPatchOnly = PkSimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 0.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_REMOVE,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                ),
            ),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = 70.0,
            startTimeH = 0.0,
            endTimeH = 400.0,
            numberOfSteps = 401,
        ).run(sampleTimeH = listOf(0.0, 168.0, 200.0))
        val secondPatchOnly = PkSimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
            ),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = 70.0,
            startTimeH = 0.0,
            endTimeH = 400.0,
            numberOfSteps = 401,
        ).run(sampleTimeH = listOf(0.0, 168.0, 200.0))

        for (timeH in listOf(0.0, 168.0, 200.0)) {
            val expected = (firstPatchOnly.concentrationAt(timeH) ?: 0.0) +
                (secondPatchOnly.concentrationAt(timeH) ?: 0.0)
            val actual = swapResult.concentrationAt(timeH) ?: 0.0
            assertEquals("at t=$timeH", expected, actual, 1e-9)
        }
        // Sanity: at t=200 the second (still-on) patch must dominate.
        assertTrue((secondPatchOnly.concentrationAt(200.0) ?: 0.0) > 0.0)
    }

    @Test
    fun patchSimulation_unmatchedApplyKeepsReleasingIndefinitely() {
        // Without an explicit PATCH_REMOVE, a patch wears forever. The engine
        // does not infer a removal from a later PATCH_APPLY — users who don't
        // log removals must accept that their PK projection shows an indefinitely
        // worn patch.
        val groupA = UUID.randomUUID()
        val releaseRate = 50.0

        val unmatchedResult = PkSimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 0.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
            ),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = 70.0,
            startTimeH = 0.0,
            endTimeH = 400.0,
            numberOfSteps = 401,
        ).run(sampleTimeH = listOf(0.0, 200.0, 400.0))

        // The first patch should still be saturating at zero-order rate (no
        // implicit removal at t=168). A test for that: concentration at t=400
        // must exceed any pure-decay scenario where the patch was actually
        // removed.
        val firstStaysOn = unmatchedResult.concentrationAt(400.0) ?: 0.0
        val removedAt168 = PkSimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 0.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_REMOVE,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                ),
                PkDoseEvent(
                    id = UUID.randomUUID(),
                    sourceGroupUuid = groupA,
                    hormone = PkHormone.ESTRADIOL,
                    route = PkRoute.PATCH_APPLY,
                    timeH = 168.0,
                    doseMg = 0.0,
                    compound = PkCompound.E2,
                    releaseRateMcgPerDay = releaseRate,
                ),
            ),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = 70.0,
            startTimeH = 0.0,
            endTimeH = 400.0,
            numberOfSteps = 401,
        ).run(sampleTimeH = listOf(0.0, 200.0, 400.0)).concentrationAt(400.0) ?: 0.0

        assertTrue(firstStaysOn > removedAt168)
    }

    @Test
    fun simulateMainEstradiolProjection_windowMatchesHomeSnapshotChartBounds() {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val generatedAt = LocalDateTime.of(2026, 5, 8, 14, 27)
        val futureDays = 14L

        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = emptyList(),
            bodyWeightKg = 70.0,
            generatedAt = generatedAt,
            zoneId = zoneId,
            futureDays = futureDays,
        )

        val expectedWindowStart = generatedAt.toLocalDate().atStartOfDay()
            .minusDays(PkMedicationSimulation.mainChartPastDays)
            .atZone(zoneId)
            .toInstant()
        val expectedWindowEnd = generatedAt.toLocalDate().plusDays(futureDays)
            .atStartOfDay()
            .atZone(zoneId)
            .toInstant()
        assertEquals(expectedWindowStart, projection.windowStart)
        assertEquals(expectedWindowEnd, projection.windowEnd)
    }
}
