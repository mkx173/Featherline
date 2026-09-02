package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.system.measureNanoTime

class PkCalibrationRendererTest {
    @Test
    fun chartDomain_hasExactInclusiveSamplingAndProtectedKnots() {
        val first = requireNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = 0L,
                rangeEndEpochMillis = 10L,
                samplingIntervalMillis = 4L,
                protectedKnotEpochMillis = listOf(9L, 3L, 3L),
            )
        )
        val reordered = requireNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = 0L,
                rangeEndEpochMillis = 10L,
                samplingIntervalMillis = 4L,
                protectedKnotEpochMillis = listOf(3L, 9L),
            )
        )

        assertEquals(listOf(0L, 3L, 4L, 8L, 9L, 10L), first.knotEpochMillis)
        assertEquals(first, reordered)
        assertNull(PkChartDomain.create(1L, 1L, samplingIntervalMillis = 1L))
        assertNull(PkChartDomain.create(0L, 1L, samplingIntervalMillis = 0L))
        assertNull(
            PkChartDomain.create(0L, 10L, samplingIntervalMillis = 1L, protectedKnotEpochMillis = listOf(11L))
        )
    }

    @Test
    fun readyPopulationRendersPopulation_nonReadyEvaluationIsOmitted() {
        val events = listOf(event(PkRoute.ORAL, 0.0, doseMg = 2.0))
        val readyPopulation = evaluation(events = events)
        val readyRender = render(readyPopulation, domain(hours = 24, intervalHours = 6))
        val failedEvaluation = PkCalibrationEvaluation(
            PkCalibrationResult(PkCalibrationGlobalState.NUMERIC_FAILURE),
            null,
        )

        assertEquals(PkCalibrationRenderState.POPULATION, readyRender.renderState)
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, readyRender.bandState)
        assertPopulationParity(readyRender, readyPopulation)
        assertNull(PkCalibrationRenderer.render(failedEvaluation, domain(hours = 24, intervalHours = 6)))
        assertNull(failedEvaluation.renderFor(domain(hours = 24, intervalHours = 6)))
    }

    @Test
    fun rLog_isReadFromTheBoundInput() {
        val events = listOf(event(PkRoute.ORAL, 0.0, 2.0))
        val promoted = mapOf(PkCalibrationRoute.ORAL to ln(1.1))
        val base = evaluation(events = events, promotedQByRoute = promoted)
        val rLogChanged = evaluation(events = events, config = config(rLog = 0.05), promotedQByRoute = promoted)
        val renderDomain = domain(startHours = 1, hours = 24, intervalHours = 6)

        assertEquals(0.05, requireReady(rLogChanged).config.rLog, 0.0)
        assertTrue(render(base, renderDomain).bandKnots != render(rLogChanged, renderDomain).bandKnots)
    }

    @Test
    fun centralCurve_usesDisplayParamsForEveryFittedRoute_andMatchesForwardModel() {
        // Warn-only: every route with a supporting lab is promoted, so the
        // central curve scales each fitted route by its display beta.
        val events = listOf(
            event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV),
            event(PkRoute.ORAL, 0.0, 2.0),
        )
        val evaluation = evaluation(
            events = events,
            promotedQByRoute = mapOf(
                PkCalibrationRoute.INJECTION to ln(4.0),
                PkCalibrationRoute.ORAL to ln(1.2),
            ),
        )
        val actual = render(
            evaluation,
            domain(startHours = 1, hours = 24, intervalHours = 4),
        )
        val result = evaluation.result

        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            actual.effectivePromotedRoutes,
        )
        assertEquals(result.displayParams, actual.effectiveDisplayParams)
        assertCentralForwardParity(actual, evaluation)

        val input = requireReady(evaluation)
        val forward = forward(input)
        val last = actual.centralCurve.last()
        val population = requireNotNull(
            forward.breakdownAt(hoursBetween(last.epochMillis, input.originEpochMillis))
        )
        val expected = population.byRouteDrugPgml.getValue(PkCalibrationRoute.INJECTION) *
                result.displayParams.scaleFor(PkCalibrationRoute.INJECTION) +
                population.byRouteDrugPgml.getValue(PkCalibrationRoute.ORAL) *
                result.displayParams.scaleFor(PkCalibrationRoute.ORAL)
        assertNear(expected, last.concentrationPgMl)
        // The extreme injection fit is shown, with its warning, not hidden.
        val injection = result.routeResults[PkCalibrationRoute.INJECTION.ordinal]
        assertEquals(PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL, injection.displayState)
        assertTrue(PkCalibrationReason.SCALE_OUTSIDE_USUAL_RANGE in injection.reasons)
    }

    @Test
    fun mixedPersonalization_supportsAllFiveRoutesInCanonicalOrder() {
        val evaluation = evaluation(
            events = allRouteEvents(),
            promotedQByRoute = PkCalibrationRoute.entries.associateWith { route ->
                if (route == PkCalibrationRoute.PATCH) 0.0 else ln(1.1)
            },
        )
        val actual = render(
            evaluation,
            domain(startHours = 1, hours = 24, intervalHours = 6),
        )

        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
        assertEquals(PkCalibrationRoute.entries, actual.effectivePromotedRoutes)
        assertCentralForwardParity(actual, evaluation)
        assertEquals(PkCalibrationBandState.READY, actual.bandState)
    }

    @Test
    fun promotedRouteWithNoContributionInRange_isAbsentFromRenderPromotion() {
        val events = listOf(
            event(PkRoute.INJECTION, 0.0, 1.0, PkCompound.EV),
            event(PkRoute.ORAL, 100.0, 2.0),
        )
        val evaluation = evaluation(
            events = events,
            promotedQByRoute = mapOf(
                PkCalibrationRoute.INJECTION to ln(1.1),
                PkCalibrationRoute.ORAL to ln(1.2),
            ),
        )
        val early = render(evaluation, domain(startHours = 1, hours = 24, intervalHours = 6))
        assertEquals(listOf(PkCalibrationRoute.INJECTION), early.effectivePromotedRoutes)
        assertEquals(setOf(PkCalibrationRoute.INJECTION), early.effectiveDisplayParams.routeLogScale.keys)

        val late = render(evaluation, domain(startHours = 100, hours = 124, intervalHours = 6))
        assertEquals(listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL), late.effectivePromotedRoutes)
        assertEquals(listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL), evaluation.result.promotedRoutes)
    }

    @Test
    fun sharedPopulationFailure_isValidatedNumericUnavailableWithoutChangingFit() {
        val evaluation = evaluation(
            events = listOf(event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV)),
            promotedQByRoute = mapOf(PkCalibrationRoute.INJECTION to ln(1.1)),
        )
        val actual = render(evaluation, domain(hours = 6, intervalHours = 1))

        assertEquals(PkCalibrationRenderState.NUMERIC_UNAVAILABLE, actual.renderState)
        assertTrue(actual.centralCurve.isEmpty())
        assertTrue(actual.effectivePromotedRoutes.isEmpty())
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, actual.bandState)
        assertEquals(listOf(PkCalibrationRoute.INJECTION), evaluation.result.promotedRoutes)
    }

    @Test
    fun aggregateBandFailure_preservesPersonalizedCentralAndFit() {
        val evaluation = evaluation(
            events = listOf(event(PkRoute.ORAL, 0.0, 1.45e306)),
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val actual = render(evaluation, domain(startHours = 1, hours = 12, intervalHours = 3))

        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
        assertFalse(actual.centralCurve.isEmpty())
        assertEquals(listOf(PkCalibrationRoute.ORAL), actual.effectivePromotedRoutes)
        assertEquals(evaluation.result.displayParams, actual.effectiveDisplayParams)
        assertEquals(PkCalibrationBandState.NUMERIC_UNAVAILABLE, actual.bandState)
        assertTrue(actual.bandKnots.isEmpty())
        assertEquals(listOf(PkCalibrationRoute.ORAL), evaluation.result.promotedRoutes)
    }

    @Test
    fun renderedBand_isPositiveOrderedAndCenteredOnTheCentralMedian() {
        val evaluation = evaluation(
            events = listOf(
                event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV),
                event(PkRoute.ORAL, 0.0, 2.0),
            ),
            promotedQByRoute = mapOf(
                PkCalibrationRoute.INJECTION to ln(1.1),
                PkCalibrationRoute.ORAL to ln(0.9),
            ),
        )
        val actual = render(
            evaluation,
            domain(startHours = 1, hours = 24, intervalHours = 3),
        )
        val centralByTime = actual.centralCurve.associateBy(PkCurvePoint::epochMillis)

        assertEquals(PkCalibrationBandState.READY, actual.bandState)
        // The band now reads the joint covariance block; its diagonal must
        // bit-match each promoted row's marginal Laplace variance.
        val covariance = requireNotNull(evaluation.result.promotedBetaCovariance)
        assertEquals(evaluation.result.promotedRoutes, covariance.routes)
        for (routeResult in evaluation.result.routeResults) {
            if (routeResult.route !in covariance.routes) continue
            val sd = requireNotNull(routeResult.betaPosteriorSd)
            assertEquals(
                sd * sd,
                requireNotNull(covariance.covariance(routeResult.route, routeResult.route)),
                1e-15,
            )
        }
        for (knot in actual.bandKnots) {
            val values = listOf(
                knot.p025Pgml,
                knot.p158655254Pgml,
                knot.p50Pgml,
                knot.p841344746Pgml,
                knot.p975Pgml,
            )
            assertTrue(values.all { value -> value.isFinite() && value > 0.0 })
            assertTrue(values.zipWithNext().all { (left, right) -> left < right })
            assertNear(
                centralByTime.getValue(knot.epochMillis).concentrationPgMl,
                knot.p50Pgml,
                relativeTolerance = 1e-7,
            )
        }
    }

    @Test
    fun bandKnots_omitPopulationOnlyTimesButKeepThemInTheCentralCurve() {
        val evaluation = evaluation(
            events = listOf(
                event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV),
                event(PkRoute.ORAL, 12.0, 2.0),
            ),
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val actual = render(
            evaluation,
            domain(startHours = 1, hours = 24, intervalHours = 6),
        )
        val input = requireReady(evaluation)
        val forward = forward(input)
        val centralTimes = actual.centralCurve.map(PkCurvePoint::epochMillis)
        val populationOnlyTimes = centralTimes.filter { epochMillis ->
            val breakdown = requireNotNull(
                forward.breakdownAt(hoursBetween(epochMillis, input.originEpochMillis))
            )
            breakdown.totalDrugPgml > 0.0 &&
                    breakdown.byRouteDrugPgml.getValue(PkCalibrationRoute.ORAL) == 0.0
        }
        val promotedContributionTimes = centralTimes.filter { epochMillis ->
            requireNotNull(
                forward.breakdownAt(hoursBetween(epochMillis, input.originEpochMillis))
            ).byRouteDrugPgml.getValue(PkCalibrationRoute.ORAL) > 0.0
        }
        val bandTimes = actual.bandKnots.map(PkPredictiveBandKnot::epochMillis)

        assertEquals(PkCalibrationBandState.READY, actual.bandState)
        assertTrue(populationOnlyTimes.isNotEmpty())
        assertTrue(promotedContributionTimes.isNotEmpty())
        assertTrue(populationOnlyTimes.all(centralTimes::contains))
        assertTrue(populationOnlyTimes.none(bandTimes::contains))
        assertEquals(promotedContributionTimes, bandTimes)
    }

    @Test
    fun centralCurve_isDeterministicUnderEventReorder() {
        val events = allRouteEvents()
        val promoted = mapOf(PkCalibrationRoute.ORAL to ln(1.1))
        val renderDomain = domain(startHours = 1, hours = 24, intervalHours = 6)
        assertEquals(
            render(evaluation(events = events, promotedQByRoute = promoted), renderDomain).centralCurve,
            render(evaluation(events = events.reversed(), promotedQByRoute = promoted), renderDomain).centralCurve,
        )
    }

    @Test
    fun thirtyDayAllFiveRouteRender_meetsCombinedFiftyMillisecondBudgetAfterWarmup() {
        val evaluation = evaluation(
            events = allRouteEvents(),
            promotedQByRoute = PkCalibrationRoute.entries.associateWith { ln(1.1) },
        )
        val domain = domain(startHours = 1, hours = 30 * 24, intervalHours = 24)
        repeat(2) { render(evaluation, domain) }

        val bestNanos = (0 until 3).minOf {
            measureNanoTime {
                val result = render(evaluation, domain)
                assertEquals(PkCalibrationRenderState.PERSONALIZED, result.renderState)
                assertEquals(PkCalibrationBandState.READY, result.bandState)
                assertEquals(PkCalibrationRoute.entries, result.effectivePromotedRoutes)
            }
        }

        assertTrue(
            "Expected <= $RenderBudgetMs ms, was ${bestNanos / 1_000_000.0} ms",
            bestNanos <= RenderBudgetMs * 1_000_000L,
        )
    }

    private fun evaluation(
        events: List<PkDoseEvent>,
        promotedQByRoute: Map<PkCalibrationRoute, Double> = emptyMap(),
        weightKg: Double = WeightKg,
        originMillis: Long = OriginMillis,
        config: PkCalibrationConfig = Config,
    ): PkCalibrationEvaluation {
        val input = PkCalibrationInput(
            labs = listOf(PkCalibrationLab(uuid(9_000), originMillis, 100.0)),
            doseEvents = events,
            originEpochMillis = originMillis,
            weightKg = weightKg,
            config = config,
        )
        val included = PkCalibrationRoute.entries.flatMap { route ->
            val q = promotedQByRoute[route]
            if (q == null) emptyList() else includedEvidence(route, q)
        }
        val pool = PkCalibrationEvidencePool(
            input = input,
            forwardModel = forward(input),
            included = included,
            ignored = emptyMap(),
        )
        val evaluation = PkCalibrationEvaluation(
            PkCalibrationSolver.solve(pool),
            pool,
        )
        assertEquals(
            PkCalibrationRoute.entries.filter(promotedQByRoute::containsKey),
            evaluation.result.promotedRoutes,
        )
        return evaluation
    }

    /**
     * Three single-route labs whose observed values imply route log-scale q.
     * Single-route breakdowns keep the joint objective decoupled per route, so
     * each route's MAP is determined by its own labs alone.
     */
    private fun includedEvidence(
        route: PkCalibrationRoute,
        q: Double,
    ): List<PkCalibrationIncludedLab> {
        val totals = listOf(10.0, 20.0, 40.0)
        return totals.mapIndexed { index, total ->
            val breakdown = requireNotNull(
                PkForwardBreakdown.create(
                    PkCalibrationRoute.entries.associateWith { entry ->
                        if (entry == route) total else 0.0
                    }
                )
            )
            PkCalibrationIncludedLab(
                resultId = uuid(1_000L + route.ordinal * 100L + index),
                observedPgml = exp(q) * total,
                breakdown = breakdown,
            )
        }
    }

    private fun render(
        evaluation: PkCalibrationEvaluation,
        domain: PkChartDomain,
    ): PkCalibrationRenderResult {
        return requireNotNull(PkCalibrationRenderer.render(evaluation, domain))
    }

    private fun requireReady(evaluation: PkCalibrationEvaluation): PkCalibrationInput {
        return requireNotNull(evaluation.evidence).input
    }

    private fun forward(input: PkCalibrationInput): PkE2ForwardModel {
        return requireNotNull(PkE2ForwardModel.create(input.doseEvents, input.weightKg))
    }

    private fun domain(
        startHours: Int = 0,
        hours: Int,
        intervalHours: Int,
        originMillis: Long = OriginMillis,
    ): PkChartDomain {
        return requireNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = originMillis + startHours * HourMillis,
                rangeEndEpochMillis = originMillis + hours * HourMillis,
                samplingIntervalMillis = intervalHours * HourMillis,
            )
        )
    }

    private fun event(
        route: PkRoute,
        timeH: Double,
        doseMg: Double,
        compound: PkCompound = PkCompound.E2,
        releaseRateMcgPerDay: Double? = null,
        id: UUID = uuid(nextEventId++),
    ): PkDoseEvent = PkDoseEvent(
        id = id,
        sourceGroupUuid = null,
        hormone = PkHormone.ESTRADIOL,
        route = route,
        timeH = timeH,
        doseMg = doseMg,
        compound = compound,
        releaseRateMcgPerDay = releaseRateMcgPerDay,
    )

    private fun allRouteEvents(): List<PkDoseEvent> {
        return listOf(
            event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV),
            event(PkRoute.PATCH_APPLY, 0.0, 0.0, releaseRateMcgPerDay = 100.0),
            event(PkRoute.GEL, 0.0, 1.0),
            event(PkRoute.ORAL, 0.0, 2.0),
            event(PkRoute.SUBLINGUAL, 0.0, 1.0),
            event(PkRoute.PATCH_REMOVE, 18.0, 0.0),
        )
    }

    private fun assertPopulationParity(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEvaluation,
    ) {
        val input = requireReady(evaluation)
        val forward = forward(input)
        for (point in render.centralCurve) {
            val expected = requireNotNull(
                forward.breakdownAt(hoursBetween(point.epochMillis, input.originEpochMillis))
            ).totalDrugPgml
            assertEquals(expected.toBits(), point.concentrationPgMl.toBits())
        }
    }

    private fun assertCentralForwardParity(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEvaluation,
    ) {
        val input = requireReady(evaluation)
        val forward = forward(input)
        for (point in render.centralCurve) {
            val expected = requireNotNull(
                forward.breakdownAt(
                    hoursBetween(point.epochMillis, input.originEpochMillis),
                    render.effectiveDisplayParams,
                )
            ).totalDrugPgml
            assertNear(expected, point.concentrationPgMl)
        }
    }

    private fun assertNear(
        expected: Double,
        actual: Double,
        relativeTolerance: Double = 1e-12,
    ) {
        val tolerance = max(1e-10, abs(expected) * relativeTolerance)
        assertEquals(expected, actual, tolerance)
    }

    private fun hoursBetween(epochMillis: Long, originMillis: Long): Double {
        return (epochMillis - originMillis).toDouble() / HourMillis.toDouble()
    }

    private fun config(rLog: Double): PkCalibrationConfig =
        PkCalibrationConfig(drugMinInformativePgml = 1e-12, rLog = rLog)

    private fun uuid(value: Long): UUID = UUID(0L, value)

    private var nextEventId = 1L

    private companion object {
        const val RenderBudgetMs = 50L
        const val OriginMillis = 1_700_000_000_000L
        const val HourMillis = 3_600_000L
        const val WeightKg = 70.0

        val Config = PkCalibrationConfig(drugMinInformativePgml = 1e-12, rLog = 0.04)
    }
}
