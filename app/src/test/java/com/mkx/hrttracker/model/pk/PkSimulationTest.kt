package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
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
    fun simulateMainEstradiolTrend_convertsInjectionDoseToActiveE2() {
        val now = LocalDateTime.of(2026, 5, 5, 12, 0)
        val zoneId = ZoneId.systemDefault()
        val injectionAt = now.minusHours(48)
        val bodyWeightKg = 70.0
        val medicineDoseMg = 5.0
        val entry = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_VALERATE,
                preparation = MedicinePreparation.InjectionSingleUseVial(
                    strengthMgPerVial = medicineDoseMg,
                ),
            ),
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = medicineDoseMg * PkCatalog.activeFactor(PkCompound.EV),
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
                    doseMg = medicineDoseMg * PkCatalog.activeFactor(PkCompound.EV),
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
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = 2.0,
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
        assertEquals(1682, trend.chartConcentrations.size)
        assertEquals(trend.chartTimeH.size, trend.chartConcentrations.size)
        assertEquals(1, trend.chartSampleIntervalHours)
        assertEquals(168, trend.chartWindowHours)
        assertEquals(84.0, trend.predictionStartTimeH, 1e-9)
        assertTrue(trend.chartTimeH.contains(82.0))
        assertTrue(trend.chartTimeH.contains(82.5))
        assertTrue(trend.chartTimeH.contains(82.5034))
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
    fun simulateMainEstradiolTrend_excludesWindowEndMidnightDoseFromChart() {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val now = LocalDateTime.of(2026, 5, 14, 12, 0)
        val windowEnd = LocalDateTime.of(2026, 5, 18, 0, 0)
        val doseAtWindowEnd = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = windowEnd.atZone(zoneId).toInstant(),
        )

        val trend = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(doseAtWindowEnd),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )

        assertTrue(
            "chart should stay before 168h, got ${trend.chartTimeH.lastOrNull()}",
            trend.chartTimeH.all { timeH ->
                timeH < PkMedicationSimulation.mainChartWindowHours.toDouble()
            },
        )
        assertTrue(
            "window-end dose marker leaked into chart",
            trend.doseMarkers.none { marker ->
                marker.timeH == PkMedicationSimulation.mainChartWindowHours.toDouble()
            },
        )
    }

    @Test
    fun simulateMainEstradiolProjection_canServeLaterMainTrendFromCache() {
        val zoneId = ZoneId.systemDefault()
        val generatedAt = LocalDateTime.of(2026, 5, 5, 12, 0)
        val laterLaunch = generatedAt.plusDays(2).plusHours(3)
        val entries = listOf(
            testMedicationLogEntry(
                medicine = testMedicine(
                    key = MedicationKey.ESTRADIOL_VALERATE,
                    preparation = MedicinePreparation.InjectionSingleUseVial(
                        strengthMgPerVial = 5.0,
                    ),
                ),
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = 5.0 * PkCatalog.activeFactor(PkCompound.EV),
                sourceGroupUuid = null,
                appliedAt = testInstant(generatedAt.minusDays(2)),
            ),
            testMedicationLogEntry(
                medicine = testMedicine(
                    key = MedicationKey.ESTRADIOL,
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                ),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = 2.0,
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
    fun projectionToMainEstradiolTrend_excludesWindowEndMidnightDoseFromChart() {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val generatedAt = LocalDateTime.of(2026, 5, 14, 12, 0)
        val windowEnd = LocalDateTime.of(2026, 5, 18, 0, 0)
        val doseAtWindowEnd = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = windowEnd.atZone(zoneId).toInstant(),
        )
        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = listOf(doseAtWindowEnd),
            bodyWeightKg = 70.0,
            generatedAt = generatedAt,
            zoneId = zoneId,
            futureDays = 4,
        )

        val trend = checkNotNull(
            projection.toMainEstradiolTrend(
                now = generatedAt,
                zoneId = zoneId,
            )
        )

        assertTrue(
            "chart should stay before 168h, got ${trend.chartTimeH.lastOrNull()}",
            trend.chartTimeH.all { timeH ->
                timeH < PkMedicationSimulation.mainChartWindowHours.toDouble()
            },
        )
        assertTrue(
            "window-end dose marker leaked into chart",
            trend.doseMarkers.none { marker ->
                marker.timeH == PkMedicationSimulation.mainChartWindowHours.toDouble()
            },
        )
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

        for (option in HomeE2ChartWindowOption.entries) {
            val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
                entries = emptyList(),
                bodyWeightKg = 70.0,
                generatedAt = generatedAt,
                zoneId = zoneId,
                option = option,
            )

            val expectedWindowStart = generatedAt.toLocalDate().atStartOfDay()
                .minusDays(option.pastDays)
                .atZone(zoneId)
                .toInstant()
            val expectedWindowEnd = generatedAt.toLocalDate()
                .plusDays(option.projectionFutureDays())
                .atStartOfDay()
                .atZone(zoneId)
                .toInstant()
            assertEquals(
                "windowStart mismatch for $option",
                expectedWindowStart,
                projection.windowStart,
            )
            assertEquals(
                "windowEnd mismatch for $option",
                expectedWindowEnd,
                projection.windowEnd,
            )
        }
    }

    @Test
    fun simulateMainEstradiolProjection_thirtyDays_usesBudgetSampler() {
        // 30-day projection spans 40 d (20 past + 10 future + 10 buffer). The
        // budget sampler emits segmentCount + 1 = 2241 dense samples; the
        // returned timeH has more after exact anchors (windowStart, generatedAt,
        // previousDay) are unioned in, but should not balloon to interval-style
        // counts.
        val zoneId = ZoneId.of("Asia/Tokyo")
        val generatedAt = LocalDateTime.of(2026, 5, 8, 14, 27)
        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = emptyList(),
            bodyWeightKg = 70.0,
            generatedAt = generatedAt,
            zoneId = zoneId,
            option = HomeE2ChartWindowOption.THIRTY_DAYS,
        )

        // 2241 budget samples plus a handful of exact anchors; cap well below
        // the interval-grid count of (40*24/0.1)+1 = 9601.
        assertTrue(
            "THIRTY_DAYS dense samples grew beyond budget: ${projection.timeH.size}",
            projection.timeH.size in 2241..2260,
        )
    }

    @Test
    fun simulateMainEstradiolTrend_thirtyDays_injectsPostDoseOffsets() {
        // Oral E2 has a sub-hour Tmax, which sits between budget-sampler grid
        // samples at 26-min spacing. THIRTY_DAYS injects post-dose offsets so
        // the absorption phase is sampled exactly.
        val zoneId = ZoneId.of("Asia/Tokyo")
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
        val doseAt = now.minusHours(72)
        val entries = listOf(
            testMedicationLogEntry(
                medicine = testMedicine(
                    key = MedicationKey.ESTRADIOL,
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                ),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = 2.0,
                sourceGroupUuid = null,
                appliedAt = doseAt.atZone(zoneId).toInstant(),
            )
        )

        val thirtyDayResult = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = entries,
            bodyWeightKg = 70.0,
            now = now,
            zoneId = zoneId,
            option = HomeE2ChartWindowOption.THIRTY_DAYS,
        )
        val sevenDayResult = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = entries,
            bodyWeightKg = 70.0,
            now = now,
            zoneId = zoneId,
            option = HomeE2ChartWindowOption.SEVEN_DAYS,
        )
        assertNotNull(thirtyDayResult)
        assertNotNull(sevenDayResult)

        // The dose is 72 h before `now = midnight + 12h`, so its timeH is
        // (pastDays * 24 + 12) − 72 = (pastDays * 24 − 60) h from the chart
        // window start. THIRTY_DAYS adds exact post-dose offsets; SEVEN_DAYS
        // uses the 0.1 h interval sampler, which lands on multiples of 0.1.
        val thirtyDoseOffsetH = HomeE2ChartWindowOption.THIRTY_DAYS.pastDays * 24.0 - 60.0
        val sevenDoseOffsetH = HomeE2ChartWindowOption.SEVEN_DAYS.pastDays * 24.0 - 60.0
        val thirtyTimes = thirtyDayResult!!.chartTimeH.toSet()
        val sevenTimes = sevenDayResult!!.chartTimeH.toSet()
        for (offset in listOf(0.25, 0.5, 1.0)) {
            val thirtyExpected = round4(thirtyDoseOffsetH + offset)
            assertTrue(
                "THIRTY_DAYS expected post-dose sample at $thirtyExpected h",
                thirtyTimes.contains(thirtyExpected),
            )
        }
        // Only +0.25 h is guaranteed off the SEVEN_DAYS 0.1 h interval grid.
        val sevenContrast = round4(sevenDoseOffsetH + 0.25)
        assertTrue(
            "SEVEN_DAYS unexpectedly contains exact +0.25 h post-dose offset at $sevenContrast h",
            !sevenTimes.contains(sevenContrast),
        )
    }

    @Test
    fun simulateMainEstradiolTrend_totalMgPatchYieldsNonZeroDose() {
        // Regression: a TotalMg patch must drive PK off its patch spec —
        // DoseInstructionCalculator.perUnitEquivalentE2Mg returns null for every
        // patch, so a naive equivalentE2Mg rewrite would silently drop it.
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
        val zoneId = ZoneId.systemDefault()
        val patchEntry = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_PATCH,
                preparation = MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification.TotalMg(valueMg = 4.0),
                ),
            ),
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = now.minusHours(48).atZone(zoneId).toInstant(),
        )

        val trend = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(patchEntry),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )

        assertTrue(
            "TotalMg patch produced no dose marker — its dose was dropped",
            trend.doseMarkers.isNotEmpty(),
        )
        assertTrue(trend.currentConcentration > 0.0)
    }

    @Test
    fun simulateMainEstradiolTrend_releaseRatePatchYieldsNonZeroLevel() {
        // Regression: a ReleaseRateMcgPerDay patch must drive PK off its
        // release-rate spec via PkDoseEvent.releaseRateMcgPerDay.
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
        val zoneId = ZoneId.systemDefault()
        val patchEntry = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_PATCH,
                preparation = MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification
                        .ReleaseRateMcgPerDay(valueMcgPerDay = 100.0),
                ),
            ),
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = now.minusHours(48).atZone(zoneId).toInstant(),
        )

        val trend = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(patchEntry),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )

        assertTrue(
            "ReleaseRate patch produced no dose marker — its release rate was dropped",
            trend.doseMarkers.isNotEmpty(),
        )
        assertTrue(trend.currentConcentration > 0.0)
    }

    @Test
    fun patchOffEntryWithSingletonMedicineStillRemovesPatch() {
        // After the singleton refactor a PATCH_OFF log carries the global
        // PatchOff medicine instead of null. The simulator must still route
        // it as an estradiol patch removal (the route alone determines this).
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
        val applyEntry: MedicationLogEntry = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_PATCH,
                preparation = MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification
                        .ReleaseRateMcgPerDay(valueMcgPerDay = 100.0),
                ),
            ),
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = now.minusHours(72).atZone(zoneId).toInstant(),
        )
        val removeEntry: MedicationLogEntry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = com.mkx.hrttracker.model.medication.testPatchOffMedicine(),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = now.minusHours(24).atZone(zoneId).toInstant(),
        )

        val withRemoval = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(applyEntry, removeEntry),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )
        val withoutRemoval = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(applyEntry),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )

        assertTrue(withRemoval.currentConcentration < withoutRemoval.currentConcentration)
    }

    @Test
    fun patchOffEntryWithoutMedicineIsRemovalOfEstradiol() {
        // A PATCH_OFF log carries no medicine; it must still pair as an
        // estradiol patch removal (not be dropped for lack of a compound).
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
        val applyEntry: MedicationLogEntry = testMedicationLogEntry(
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_PATCH,
                preparation = MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification
                        .ReleaseRateMcgPerDay(valueMcgPerDay = 100.0),
                ),
            ),
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = now.minusHours(72).atZone(zoneId).toInstant(),
        )
        val removeEntry: MedicationLogEntry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = null,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = now.minusHours(24).atZone(zoneId).toInstant(),
        )

        val withRemoval = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(applyEntry, removeEntry),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )
        val withoutRemoval = checkNotNull(
            PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = listOf(applyEntry),
                bodyWeightKg = 70.0,
                now = now,
                zoneId = zoneId,
            )
        )

        // Removing the patch must lower the level vs. leaving it on.
        assertTrue(withRemoval.currentConcentration < withoutRemoval.currentConcentration)
    }

    private fun round4(value: Double): Double {
        // Mirrors PkMedicationSimulation.toChartXHour rounding scale.
        return kotlin.math.round(value * 10_000.0) / 10_000.0
    }
}
