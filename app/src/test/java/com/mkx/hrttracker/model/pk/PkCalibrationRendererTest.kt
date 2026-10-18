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
    fun readyPopulationRendersPopulation_nonReadyResultIsOmitted() {
        val events = listOf(event(PkRoute.ORAL, 0.0, doseMg = 2.0))
        val readyPopulation = evaluation(events = events)
        val renderDomain = domain(hours = 24, intervalHours = 6)
        val readyRender = render(readyPopulation, renderDomain)

        assertEquals(PkCalibrationRenderState.POPULATION, readyRender.renderState)
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, readyRender.bandState)
        assertPopulationParity(readyRender, readyPopulation)
        for (state in PkCalibrationGlobalState.entries - PkCalibrationGlobalState.READY) {
            assertNull(
                PkCalibrationRenderer.render(
                    PkCalibrationResult(state), OriginMillis, WeightKg, Config, renderDomain, doseEvents = events,
                )
            )
        }
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
        val renderDomain = domain(startHours = 1, hours = 24, intervalHours = 4)
        val actual = render(evaluation, renderDomain)
        val result = evaluation.result

        // Both fitted routes shape the curve; the status names those at least
        // the support share somewhere in the range.
        assertEquals(result.displayParams, actual.effectiveDisplayParams)
        assertEquals(
            rangeShapingRoutes(actual, evaluation),
            actual.effectivePromotedRoutes,
        )
        assertTrue(actual.effectivePromotedRoutes.isNotEmpty())
        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
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
        val renderDomain = domain(startHours = 1, hours = 24, intervalHours = 6)
        val actual = render(evaluation, renderDomain)

        assertEquals(PkCalibrationRoute.entries, evaluation.result.supportedPromotedRoutes)
        assertEquals(
            rangeShapingRoutes(actual, evaluation),
            actual.effectivePromotedRoutes,
        )
        assertTrue(actual.effectivePromotedRoutes.isNotEmpty())
        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
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

        val lateDomain = domain(startHours = 100, hours = 124, intervalHours = 6)
        val late = render(evaluation, lateDomain)
        assertEquals(
            setOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            late.effectiveDisplayParams.routeLogScale.keys,
        )
        assertEquals(
            rangeShapingRoutes(late, evaluation),
            late.effectivePromotedRoutes,
        )
        // At the oral dose its contribution is exactly zero, but it shapes
        // the range once absorbed: the status holds for the whole range
        // instead of following the clock.
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            late.effectivePromotedRoutes,
        )
        assertEquals(listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL), evaluation.result.promotedRoutes)
    }

    @Test
    fun oldRouteTail_doesNotCallTheCurrentRouteCalibrated_orShadeIt() {
        // Oral, then a switch to gel; only oral is calibrated. A week later the
        // oral tail is still positive, so "any contribution" would label the
        // gel curve calibrated and draw the band over it.
        val events = listOf(event(PkRoute.ORAL, 0.0, 2.0)) +
            (48..168 step 24).map { hour -> event(PkRoute.GEL, hour.toDouble(), 1.0) }
        val evaluation = evaluation(
            events = events,
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.3)),
        )

        val gelWeek = domain(startHours = 120, hours = 168, intervalHours = 6)
        val actual = render(evaluation, gelWeek)
        // The tail is applied (so the curve matches Home, and the render
        // state says so) but shapes nothing: no effective route, no band.
        assertEquals(setOf(PkCalibrationRoute.ORAL), actual.effectiveDisplayParams.routeLogScale.keys)
        assertTrue(actual.effectivePromotedRoutes.isEmpty())
        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, actual.bandState)
        assertTrue(actual.bandKnots.isEmpty())
        assertCentralForwardParity(actual, evaluation)

        // While oral was the only dose the same fit is calibrated, with a band.
        val oralDay = render(evaluation, domain(startHours = 1, hours = 24, intervalHours = 6))
        assertEquals(listOf(PkCalibrationRoute.ORAL), oralDay.effectivePromotedRoutes)
        assertEquals(PkCalibrationRenderState.PERSONALIZED, oralDay.renderState)
        assertEquals(PkCalibrationBandState.READY, oralDay.bandState)

        // A range spanning the switch is calibrated by oral, shaded only over
        // the oral part: the status says calibrated exactly when a band shows.
        val acrossSwitch = render(evaluation, domain(startHours = 1, hours = 168, intervalHours = 6))
        assertEquals(listOf(PkCalibrationRoute.ORAL), acrossSwitch.effectivePromotedRoutes)
        assertEquals(PkCalibrationBandState.READY, acrossSwitch.bandState)
        assertTrue(acrossSwitch.bandKnots.size < acrossSwitch.centralCurve.size)
        assertTrue(acrossSwitch.bandKnots.last().epochMillis < acrossSwitch.centralCurve.last().epochMillis)
        listOf(actual, oralDay, acrossSwitch).forEach { render ->
            assertEquals(render.bandKnots.isNotEmpty(), render.effectivePromotedRoutes.isNotEmpty())
        }
    }

    @Test
    fun statusRange_ignoresCalibratedRoutesOnlyInTheHiddenForecast() {
        // Home caches days of forecast past its visible chart. A calibrated
        // oral dose planned out there is banded in the cache, but the badge
        // describes the visible chart, where only the uncalibrated gel shows.
        val events = (0..72 step 24).map { hour -> event(PkRoute.GEL, hour.toDouble(), 1.0) } +
            event(PkRoute.ORAL, 120.0, 2.0)
        val evaluation = evaluation(
            events = events,
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.3)),
        )
        val cached = domain(startHours = 1, hours = 168, intervalHours = 6)
        val visible = cached.rangeStartEpochMillis..OriginMillis + 96 * HourMillis
        val actual = render(evaluation, cached, statusRange = visible)

        assertTrue(actual.effectivePromotedRoutes.isEmpty())
        assertEquals(PkCalibrationBandState.READY, actual.bandState)
        assertTrue(actual.bandKnots.isNotEmpty())
        assertTrue(actual.bandKnots.none { knot -> knot.epochMillis in visible })
        // Over the whole cache the same render would call it calibrated.
        assertEquals(listOf(PkCalibrationRoute.ORAL), render(evaluation, cached).effectivePromotedRoutes)
    }

    @Test
    fun unsupportedAdjustedRoute_isNotAppliedToTheCurve() {
        // The hero and Home name only supported routes. If the renderer still
        // applied a route adjusted from a negligible lab share, the hero would
        // call a personalized curve and band "population".
        val events = listOf(event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV))
        val input = PkCalibrationInput(
            labs = emptyList(),
            doseEvents = events,
            originEpochMillis = OriginMillis,
            weightKg = WeightKg,
            config = Config,
        )
        val pool = PkCalibrationEvidencePool(input, emptyList(), emptyMap())
        val result = PkCalibrationResult(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = PkCalibrationRoute.entries.map { route ->
                if (route == PkCalibrationRoute.INJECTION) {
                    PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                        reasons = setOf(PkCalibrationReason.UNCERTAIN),
                        fittedBeta = ln(1.5),
                        betaPosteriorSd = 0.3,
                        supportingLabCount = 0,
                    )
                } else {
                    PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                    )
                }
            },
            promotedBetaCovariance = PkCalibrationPromotedCovariance(
                routes = listOf(PkCalibrationRoute.INJECTION),
                values = listOf(listOf(0.09)),
            ),
        )
        val evaluation = PkCalibrationEvaluation(result, pool)

        val actual = render(evaluation, domain(startHours = 1, hours = 24, intervalHours = 6))

        assertEquals(listOf(PkCalibrationRoute.INJECTION), result.promotedRoutes)
        assertEquals(PkCalibrationRenderState.POPULATION, actual.renderState)
        assertTrue(actual.effectivePromotedRoutes.isEmpty())
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, actual.bandState)
        assertPopulationParity(actual, evaluation)
    }

    @Test
    fun sharedPopulationFailure_isValidatedNumericUnavailableWithoutChangingFit() {
        // The dose overflows the shared population evaluation at every
        // sample after the injection instant.
        val evaluation = evaluation(
            events = listOf(event(PkRoute.INJECTION, 0.0, Double.MAX_VALUE, PkCompound.EV)),
            promotedQByRoute = mapOf(PkCalibrationRoute.INJECTION to ln(1.1)),
        )
        val actual = render(evaluation, domain(startHours = 1, hours = 6, intervalHours = 1))

        assertEquals(PkCalibrationRenderState.NUMERIC_UNAVAILABLE, actual.renderState)
        assertTrue(actual.centralCurve.isEmpty())
        assertTrue(actual.effectivePromotedRoutes.isEmpty())
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, actual.bandState)
        assertEquals(listOf(PkCalibrationRoute.INJECTION), evaluation.result.promotedRoutes)
    }

    @Test
    fun aggregateBandFailure_preservesPersonalizedCentralAndFit() {
        val evaluation = evaluation(
            events = listOf(event(PkRoute.ORAL, 0.0, 5.9e306)),
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
        // The band covers the knots where oral is at least the support share
        // of the drawn signal, not every knot with a positive oral tail.
        val shapedTimes = centralTimes.filter { epochMillis ->
            shapingRoutes(actual, evaluation, epochMillis).isNotEmpty()
        }
        val bandTimes = actual.bandKnots.map(PkPredictiveBandKnot::epochMillis)

        assertEquals(PkCalibrationBandState.READY, actual.bandState)
        assertTrue(populationOnlyTimes.isNotEmpty())
        assertTrue(shapedTimes.isNotEmpty())
        assertTrue(populationOnlyTimes.all(centralTimes::contains))
        assertTrue(populationOnlyTimes.none(bandTimes::contains))
        assertEquals(shapedTimes, bandTimes)
        assertSegmentsBreakExactlyAtUnshapedCentralKnots(actual, evaluation)
    }

    @Test
    fun bandSegments_breakAtOneAndTwoKnotHoles_thatSpacingCannotReveal() {
        // Calibrated daily oral under a population injection: around the
        // injection peak the oral share dips under the support share for a
        // knot or two at a time. Those knots leave the band, and with 6h
        // sampling the hole is only 2-3x the usual spacing, so the run tag
        // is the only thing that can keep the drawer from bridging it.
        val shaped = listOf(true, true, false, true, true, false, false, true, false)
        val tagged = PkCalibrationRenderer.segmentShapedKnots(shaped.indices.toList()) { shaped[it] }
        assertEquals(listOf(0 to 0, 1 to 0, 3 to 1, 4 to 1, 7 to 2), tagged)
        assertEquals(emptyList<Pair<Int, Int>>(), PkCalibrationRenderer.segmentShapedKnots(listOf(1, 2)) { false })

        val evaluation = evaluation(
            events = listOf(event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV)) +
                (0..240 step 24).map { hour -> event(PkRoute.ORAL, hour.toDouble(), 2.0) },
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val actual = render(evaluation, domain(startHours = 1, hours = 240, intervalHours = 6))

        assertEquals(PkCalibrationBandState.READY, actual.bandState)
        val holes = holeSizes(actual, evaluation)
        assertTrue("expected short holes, got $holes", holes.any { size -> size in 1..2 })
        assertSegmentsBreakExactlyAtUnshapedCentralKnots(actual, evaluation)
    }

    /** Sizes of the runs of unshaped central knots between band knots. */
    private fun holeSizes(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEvaluation,
    ): List<Int> {
        val central = render.centralCurve.map(PkCurvePoint::epochMillis)
        val band = render.bandKnots.map(PkPredictiveBandKnot::epochMillis)
        return band.zipWithNext { a, b ->
            central.count { epochMillis -> epochMillis in (a + 1) until b }
        }.filter { size -> size > 0 }
    }

    /**
     * Consecutive band knots share a segment exactly when no central knot
     * between them was left out of the band.
     */
    private fun assertSegmentsBreakExactlyAtUnshapedCentralKnots(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEvaluation,
    ) {
        val central = render.centralCurve.map(PkCurvePoint::epochMillis)
        val band = render.bandKnots
        assertEquals(0, band.first().segment)
        band.zipWithNext { a, b ->
            val hole = central.any { epochMillis -> epochMillis in (a.epochMillis + 1) until b.epochMillis }
            assertEquals(
                "segment break between ${a.epochMillis} and ${b.epochMillis}",
                if (hole) a.segment + 1 else a.segment,
                b.segment,
            )
        }
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

    /** The evaluation's fit over [domain], with the dose history and input it was solved from. */
    private fun render(
        evaluation: PkCalibrationEvaluation,
        domain: PkChartDomain,
        statusRange: LongRange = domain.rangeStartEpochMillis..domain.rangeEndEpochMillis,
    ): PkCalibrationRenderResult {
        val input = requireReady(evaluation)
        return requireNotNull(
            PkCalibrationRenderer.render(
                result = evaluation.result,
                originEpochMillis = input.originEpochMillis,
                weightKg = input.weightKg,
                config = input.config,
                domain = domain,
                doseEvents = input.doseEvents,
                statusRange = statusRange,
            )
        )
    }

    /** The status contract: routes that are [shapingRoutes] at some central knot. */
    private fun rangeShapingRoutes(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEvaluation,
    ): List<PkCalibrationRoute> {
        val shaping = render.centralCurve.flatMapTo(HashSet()) { point ->
            shapingRoutes(render, evaluation, point.epochMillis)
        }
        return evaluation.result.supportedPromotedRoutes.filter(shaping::contains)
    }

    /**
     * Supported promoted routes at least the support share of the drawn
     * (scaled) signal at [epochMillis].
     */
    private fun shapingRoutes(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEvaluation,
        epochMillis: Long,
    ): List<PkCalibrationRoute> {
        val input = requireReady(evaluation)
        val breakdown = requireNotNull(
            forward(input).breakdownAt(
                hoursBetween(epochMillis, input.originEpochMillis),
                render.effectiveDisplayParams,
            )
        )
        return evaluation.result.supportedPromotedRoutes.filter { route ->
            breakdown.byRouteDrugPgml.getValue(route) >=
                PkCalibrationDefaults.PROMOTION_SUPPORT_SHARE_MIN * breakdown.totalDrugPgml
        }
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
        const val OriginMillis = 1_700_000_000_000L
        const val HourMillis = 3_600_000L
        const val WeightKg = 70.0

        val Config = PkCalibrationConfig(drugMinInformativePgml = 1e-12, rLog = 0.04)
    }
}
