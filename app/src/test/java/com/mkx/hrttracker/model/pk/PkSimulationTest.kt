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
        assertEquals(170, trend.chartConcentrations.size)
        assertEquals(1, trend.chartSampleIntervalHours)
        assertEquals(168, trend.chartWindowHours)
        assertEquals(84.0, trend.predictionStartTimeH, 1e-9)
        assertTrue(trend.chartTimeH.contains(82.5034))
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
}
