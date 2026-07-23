package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.ln

class PkE2ForwardModelTest {
    @Test
    fun calibrationRoute_mapsAllRoutesOnceForRouteAndEventCallers() {
        val expected = linkedMapOf(
            PkRoute.INJECTION to PkCalibrationRoute.INJECTION,
            PkRoute.PATCH_APPLY to PkCalibrationRoute.PATCH,
            PkRoute.PATCH_REMOVE to null,
            PkRoute.GEL to PkCalibrationRoute.GEL,
            PkRoute.ORAL to PkCalibrationRoute.ORAL,
            PkRoute.SUBLINGUAL to PkCalibrationRoute.SUBLINGUAL,
        )

        for ((route, calibrationRoute) in expected) {
            assertEquals(calibrationRoute, route.calibrationRoute())
            assertEquals(calibrationRoute, event(route = route).calibrationRoute())
        }
    }

    @Test
    fun populationBreakdown_matchesDirectEngineAndDirectEvaluator() {
        val events = mixedRouteEvents()
        val timeH = 24.0
        val engineResult = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = BodyWeightKg,
            startTimeH = 0.0,
            endTimeH = timeH,
            numberOfSteps = 2,
        ).run(sampleTimeH = listOf(0.0, timeH))
        val expected = requireNotNull(engineResult.concentrationAt(timeH))
        val forward = requireNotNull(PkE2ForwardModel.create(events, BodyWeightKg))
        val direct = requireNotNull(forward.directDrugPgmlAt(timeH))
        val breakdown = requireNotNull(forward.breakdownAt(timeH))

        assertEquals(expected, direct, 0.0)
        assertEquals(expected, breakdown.totalDrugPgml, parityTolerance(expected))
        assertEquals(PkCalibrationRoute.entries, breakdown.byRouteDrugPgml.keys.toList())
        assertTrue(breakdown.byRouteDrugPgml.values.all { contribution -> contribution >= 0.0 })
    }

    @Test
    fun emptyPersonalParams_preservePopulationExactly() {
        val forward = requireNotNull(PkE2ForwardModel.create(mixedRouteEvents(), BodyWeightKg))
        val population = PkPersonalParams.population()
        val explicitEmpty = requireNotNull(PkPersonalParams.create(emptyMap()))

        assertEquals(population, explicitEmpty)
        assertEquals(
            requireNotNull(forward.directDrugPgmlAt(24.0)),
            requireNotNull(forward.directDrugPgmlAt(24.0, explicitEmpty)),
            0.0,
        )
        assertEquals(
            requireNotNull(forward.breakdownAt(24.0)),
            requireNotNull(forward.breakdownAt(24.0, explicitEmpty)),
        )

        val defaultEngine = PkSimulationEngine(
            events = mixedRouteEvents(),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = BodyWeightKg,
            startTimeH = 0.0,
            endTimeH = 24.0,
            numberOfSteps = 2,
        ).run(sampleTimeH = listOf(0.0, 24.0))
        val explicitPopulationEngine = PkSimulationEngine(
            events = mixedRouteEvents(),
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = BodyWeightKg,
            startTimeH = 0.0,
            endTimeH = 24.0,
            numberOfSteps = 2,
            personalParams = explicitEmpty,
        ).run(sampleTimeH = listOf(0.0, 24.0))
        assertEquals(defaultEngine, explicitPopulationEngine)
    }

    @Test
    fun personalizedSimulation_matchesDirectForwardAndPreservesPatchRemovalSemantics() {
        val events = listOf(
            event(route = PkRoute.PATCH_APPLY, timeH = 0.0, doseMg = 4.0),
            event(route = PkRoute.PATCH_REMOVE, timeH = 12.0, doseMg = 0.0),
        )
        val params = requireNotNull(
            PkPersonalParams.create(mapOf(PkCalibrationRoute.PATCH to ln(2.0)))
        )
        val sampleTimeH = listOf(0.0, 12.0, 24.0)
        val population = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = BodyWeightKg,
            startTimeH = 0.0,
            endTimeH = 24.0,
            numberOfSteps = 2,
        ).run(sampleTimeH = sampleTimeH)
        val personalized = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = BodyWeightKg,
            startTimeH = 0.0,
            endTimeH = 24.0,
            numberOfSteps = 2,
            personalParams = params,
        ).run(sampleTimeH = sampleTimeH)
        val forward = requireNotNull(PkE2ForwardModel.create(events, BodyWeightKg))

        sampleTimeH.forEachIndexed { index, timeH ->
            assertEquals(
                requireNotNull(forward.directDrugPgmlAt(timeH, params)),
                personalized.concentrations[index],
                0.0,
            )
            assertEquals(
                population.concentrations[index] * 2.0,
                personalized.concentrations[index],
                parityTolerance(personalized.concentrations[index]),
            )
        }
    }

    @Test
    fun eachRouteScale_changesOnlyThatRouteContribution() {
        val forward = requireNotNull(PkE2ForwardModel.create(mixedRouteEvents(), BodyWeightKg))
        val population = requireNotNull(forward.breakdownAt(24.0))
        assertTrue(
            population.byRouteDrugPgml.values.all { contribution -> contribution > 0.0 }
        )
        for (scaledRoute in PkCalibrationRoute.entries) {
            val personalizedParams = requireNotNull(
                PkPersonalParams.create(
                    mapOf(scaledRoute to ln(2.0))
                )
            )
            val personalized = requireNotNull(forward.breakdownAt(24.0, personalizedParams))
            val direct = requireNotNull(forward.directDrugPgmlAt(24.0, personalizedParams))

            assertEquals(direct, personalized.totalDrugPgml, parityTolerance(direct))
            for (route in PkCalibrationRoute.entries) {
                val populationContribution = requireNotNull(population.byRouteDrugPgml[route])
                val personalizedContribution = requireNotNull(personalized.byRouteDrugPgml[route])
                if (route == scaledRoute) {
                    assertEquals(
                        2.0 * populationContribution,
                        personalizedContribution,
                        parityTolerance(populationContribution),
                    )
                } else {
                    assertEquals(populationContribution, personalizedContribution, 0.0)
                }
            }
        }
    }

    @Test
    fun inactiveRouteScale_hasNoEffect() {
        val events = listOf(event(route = PkRoute.ORAL, doseMg = 2.0))
        val forward = requireNotNull(PkE2ForwardModel.create(events, BodyWeightKg))
        val population = requireNotNull(forward.breakdownAt(8.0))
        val params = requireNotNull(
            PkPersonalParams.create(mapOf(PkCalibrationRoute.GEL to ln(3.0)))
        )

        assertEquals(population, requireNotNull(forward.breakdownAt(8.0, params)))
    }

    @Test
    fun create_requiresExplicitValidCurrentWeight() {
        val events = listOf(event(route = PkRoute.ORAL))

        assertNull(PkE2ForwardModel.create(events, 0.0))
        assertNull(PkE2ForwardModel.create(events, -1.0))
        assertNull(PkE2ForwardModel.create(events, Double.NaN))
        assertNull(PkE2ForwardModel.create(events, Double.POSITIVE_INFINITY))
        assertNotNull(PkE2ForwardModel.create(events, BodyWeightKg))

        val at50Kg = requireNotNull(
            requireNotNull(PkE2ForwardModel.create(events, 50.0)).directDrugPgmlAt(8.0)
        )
        val at100Kg = requireNotNull(
            requireNotNull(PkE2ForwardModel.create(events, 100.0)).directDrugPgmlAt(8.0)
        )
        assertTrue(at100Kg > 0.0)
        assertEquals(2.0 * at100Kg, at50Kg, parityTolerance(at50Kg))
    }

    @Test
    fun create_rejectsInvalidTargetEventsButIgnoresOtherHormones() {
        assertNull(
            PkE2ForwardModel.create(
                listOf(event(route = PkRoute.ORAL, doseMg = Double.NaN)),
                BodyWeightKg,
            )
        )
        assertNull(
            PkE2ForwardModel.create(
                listOf(event(route = PkRoute.ORAL, timeH = Double.POSITIVE_INFINITY)),
                BodyWeightKg,
            )
        )
        assertNull(
            PkE2ForwardModel.create(
                listOf(event(route = PkRoute.INJECTION, compound = PkCompound.TE)),
                BodyWeightKg,
            )
        )
        assertNotNull(
            PkE2ForwardModel.create(
                listOf(
                    event(
                        route = PkRoute.INJECTION,
                        timeH = Double.NaN,
                        hormone = PkHormone.TESTOSTERONE,
                        compound = PkCompound.TE,
                    )
                ),
                BodyWeightKg,
            )
        )
    }

    private fun mixedRouteEvents(): List<PkDoseEvent> {
        return listOf(
            event(route = PkRoute.INJECTION, compound = PkCompound.EV, doseMg = 4.0),
            event(route = PkRoute.PATCH_APPLY, doseMg = 1.0),
            event(route = PkRoute.GEL, doseMg = 1.5),
            event(route = PkRoute.ORAL, doseMg = 2.0),
            event(route = PkRoute.SUBLINGUAL, doseMg = 1.0),
            event(route = PkRoute.PATCH_REMOVE, timeH = 48.0, doseMg = 0.0),
            event(
                route = PkRoute.INJECTION,
                hormone = PkHormone.TESTOSTERONE,
                compound = PkCompound.TE,
                doseMg = 50.0,
            ),
        )
    }

    private fun event(
        route: PkRoute,
        timeH: Double = 0.0,
        doseMg: Double = 1.0,
        hormone: PkHormone = PkHormone.ESTRADIOL,
        compound: PkCompound = PkCompound.E2,
    ): PkDoseEvent {
        return PkDoseEvent(
            id = UUID.randomUUID(),
            sourceGroupUuid = null,
            hormone = hormone,
            route = route,
            timeH = timeH,
            doseMg = doseMg,
            compound = compound,
        )
    }

    private fun parityTolerance(value: Double): Double = value * 1e-12 + 1e-12

    private companion object {
        const val BodyWeightKg = 70.0
    }
}
