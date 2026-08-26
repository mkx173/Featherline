package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.system.measureNanoTime

class PkCalibrationRendererTest {
    @Test
    fun chartDomain_hasExactInclusiveSamplingAndCanonicalProtectedKnots() {
        val first = requireNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = 0L,
                rangeEndEpochMillis = 10L,
                projectionEndEpochMillis = 7L,
                samplingIntervalMillis = 4L,
                chartGridVersion = GridVersion,
                protectedKnotEpochMillis = listOf(9L, 3L, 3L),
            )
        )
        val reordered = requireNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = 0L,
                rangeEndEpochMillis = 10L,
                projectionEndEpochMillis = 7L,
                samplingIntervalMillis = 4L,
                chartGridVersion = GridVersion,
                protectedKnotEpochMillis = listOf(3L, 9L),
            )
        )

        assertEquals(listOf(3L, 9L), first.protectedKnotEpochMillis)
        assertEquals(listOf(0L, 3L, 4L, 7L, 8L, 9L, 10L), first.knotEpochMillis)
        assertEquals(first, reordered)
        assertNull(
            PkChartDomain.create(1L, 1L, samplingIntervalMillis = 1L, chartGridVersion = GridVersion)
        )
        assertNull(
            PkChartDomain.create(0L, 1L, samplingIntervalMillis = 0L, chartGridVersion = GridVersion)
        )
        assertNull(
            PkChartDomain.create(
                0L,
                10L,
                projectionEndEpochMillis = 11L,
                samplingIntervalMillis = 1L,
                chartGridVersion = GridVersion,
            )
        )
        assertNull(
            PkChartDomain.create(
                0L,
                10L,
                samplingIntervalMillis = 1L,
                chartGridVersion = "grid version with spaces",
            )
        )
    }

    @Test
    fun readyPopulationRendersPopulation_nonReadyEvaluationIsOmitted() {
        val events = listOf(event(PkRoute.ORAL, 0.0, doseMg = 2.0))
        val readyPopulation = evaluation(events = events)
        val readyRender = render(readyPopulation, domain(hours = 24, intervalHours = 6))
        val failedResult = requireNotNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.SHARED_INPUT_INVALID,
                globalReasons = setOf(PkCalibrationReason.SHARED_INPUT_INVALID),
                forwardModelVersion = ForwardModelVersion,
                calibrationModelVersion = CalibrationModelVersion,
            )
        )
        val failedEvaluation = PkCalibrationEngineEvaluation.notReady(failedResult)

        assertEquals(PkCalibrationRenderState.POPULATION, readyRender.renderState)
        assertEquals(PkCalibrationBandState.NOT_APPLICABLE_POPULATION, readyRender.bandState)
        assertPopulationParity(readyRender, readyPopulation)
        assertNull(PkCalibrationRenderer.render(failedEvaluation, domain(hours = 24, intervalHours = 6)))
        assertNull(failedEvaluation.renderFor(domain(hours = 24, intervalHours = 6)))
    }

    @Test
    fun rendererApi_acceptsOnlyOneEngineIssuedEvaluationAndDomain() {
        val renderMethods = PkCalibrationRenderer::class.java.declaredMethods
            .filter { method -> method.name == "render" }
        assertEquals(1, renderMethods.size)
        assertEquals(
            listOf(PkCalibrationEngineEvaluation::class.java, PkChartDomain::class.java),
            renderMethods.single().parameterTypes.toList(),
        )
        val constructors = PkCalibrationEngineEvaluation::class.java.declaredConstructors
        assertTrue(constructors.any { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertTrue(
            constructors.filterNot { constructor -> Modifier.isPrivate(constructor.modifiers) }
                .all { constructor ->
                    constructor.isSynthetic &&
                            constructor.parameterTypes.last().name ==
                            "kotlin.jvm.internal.DefaultConstructorMarker"
                }
        )
    }

    @Test
    fun boundEvaluation_ownsTreatmentWeightOriginScopeReviewAndConfig() {
        val baseEventId = uuid(50)
        val baseEvents = listOf(
            event(PkRoute.ORAL, 0.0, 2.0, id = baseEventId)
        )
        val base = evaluation(
            events = baseEvents,
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val treatmentChanged = evaluation(
            events = baseEvents + event(PkRoute.ORAL, 12.0, 1.0),
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val weightChanged = evaluation(
            events = baseEvents,
            weightKg = 71.0,
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val changedOrigin = OriginMillis + HourMillis
        val originChanged = evaluation(
            events = listOf(
                event(
                    route = PkRoute.ORAL,
                    timeH = -1.0,
                    doseMg = 2.0,
                    originMillis = changedOrigin,
                    id = baseEventId,
                )
            ),
            originMillis = changedOrigin,
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val rLogChanged = evaluation(
            events = baseEvents,
            config = config(rLog = 0.05),
            promotedQByRoute = mapOf(PkCalibrationRoute.ORAL to ln(1.1)),
        )
        val renderDomain = domain(startHours = 1, hours = 24, intervalHours = 6)
        val baseRender = render(base, renderDomain)

        assertNotEquals(baseRender.domainDigest, render(treatmentChanged, renderDomain).domainDigest)
        assertNotEquals(baseRender.domainDigest, render(weightChanged, renderDomain).domainDigest)
        assertNotEquals(baseRender.domainDigest, render(originChanged, renderDomain).domainDigest)
        assertNotEquals(
            render(base, renderDomain).bandKnots,
            render(rLogChanged, renderDomain).bandKnots,
        )
        assertEquals(Config, requireReady(base).config)
        assertEquals(0.05, requireReady(rLogChanged).config.rLog, 0.0)
        assertEquals(OriginMillis, requireReady(base).forwardTimeOriginEpochMillis)
        assertEquals(changedOrigin, requireReady(originChanged).forwardTimeOriginEpochMillis)
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

        val canonical = requireReady(evaluation)
        val forward = forward(canonical)
        val last = actual.centralCurve.last()
        val population = requireNotNull(
            forward.breakdownAt(hoursBetween(last.epochMillis, canonical.forwardTimeOriginEpochMillis))
        )
        val expected = population.byRouteDrugPgml.getValue(PkCalibrationRoute.INJECTION) *
                result.displayParams.scaleFor(PkCalibrationRoute.INJECTION) +
                population.byRouteDrugPgml.getValue(PkCalibrationRoute.ORAL) *
                result.displayParams.scaleFor(PkCalibrationRoute.ORAL)
        assertNear(expected, last.concentrationPgMl)
        // The extreme injection fit is shown, with its warning, not hidden.
        val injection = result.routeResults[PkCalibrationRoute.INJECTION.ordinal]
        assertEquals(PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL, injection.displayState)
        assertTrue(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED in injection.reasons)
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
        assertTrue(actual.routeRenderFallbacks.isEmpty())
        assertCentralForwardParity(actual, evaluation)
        assertEquals(PkCalibrationBandState.READY, actual.bandState)
    }

    @Test
    fun scaledOverflow_fallsBackOnlyThatRouteForTheRequestedRange() {
        val events = listOf(
            event(PkRoute.INJECTION, 0.0, 1.0, PkCompound.EV),
            event(PkRoute.ORAL, 0.0, 1.55e306),
        )
        val evaluation = evaluation(
            events = events,
            promotedQByRoute = mapOf(
                PkCalibrationRoute.INJECTION to ln(1.1),
                PkCalibrationRoute.ORAL to ln(1.9),
            ),
        )
        val actual = render(
            evaluation,
            domain(startHours = 1, hours = 2, intervalHours = 1),
        )

        assertEquals(PkCalibrationRenderState.PERSONALIZED, actual.renderState)
        assertEquals(listOf(PkCalibrationRoute.INJECTION), actual.effectivePromotedRoutes)
        assertEquals(listOf(PkCalibrationRoute.ORAL), actual.routeRenderFallbacks)
        assertEquals(
            setOf(PkCalibrationRoute.INJECTION),
            actual.effectiveDisplayParams.routeLogScale.keys,
        )
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            evaluation.result.promotedRoutes,
        )

        val laterRange = render(
            evaluation,
            domain(startHours = 100, hours = 101, intervalHours = 1),
        )
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            laterRange.effectivePromotedRoutes,
        )
        assertTrue(laterRange.routeRenderFallbacks.isEmpty())
    }

    @Test
    fun sharedPopulationFailure_isValidatedNumericUnavailableWithoutChangingFit() {
        val evaluation = evaluation(
            events = listOf(event(PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV)),
            promotedQByRoute = mapOf(PkCalibrationRoute.INJECTION to ln(1.1)),
        )
        val actual = render(evaluation, domain(hours = 6, intervalHours = 1))

        assertEquals(PkCalibrationRenderState.NUMERIC_UNAVAILABLE, actual.renderState)
        assertEquals(setOf(PkCalibrationReason.NUMERIC_FAILURE), actual.renderReasons)
        assertTrue(actual.centralCurve.isEmpty())
        assertTrue(actual.effectivePromotedRoutes.isEmpty())
        assertTrue(actual.routeRenderFallbacks.isEmpty())
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
        assertEquals(setOf(PkCalibrationReason.BAND_NUMERIC_FAILURE), actual.bandReasons)
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
            assertEquals(
                requireNotNull(routeResult.laplaceVarianceBeta).toBits(),
                requireNotNull(
                    covariance.covariance(routeResult.route, routeResult.route)
                ).toBits(),
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
        val canonical = requireReady(evaluation)
        val forward = forward(canonical)
        val centralTimes = actual.centralCurve.map(PkCurvePoint::epochMillis)
        val populationOnlyTimes = centralTimes.filter { epochMillis ->
            val breakdown = requireNotNull(
                forward.breakdownAt(
                    hoursBetween(epochMillis, canonical.forwardTimeOriginEpochMillis)
                )
            )
            breakdown.totalDrugPgml > 0.0 &&
                    breakdown.byRouteDrugPgml.getValue(PkCalibrationRoute.ORAL) == 0.0
        }
        val promotedContributionTimes = centralTimes.filter { epochMillis ->
            requireNotNull(
                forward.breakdownAt(
                    hoursBetween(epochMillis, canonical.forwardTimeOriginEpochMillis)
                )
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
    fun domainDigest_isDeterministicUnderInputReorder_andInvalidatesWithDomainOrModelVersion() {
        val events = allRouteEvents()
        val promoted = mapOf(PkCalibrationRoute.ORAL to ln(1.1))
        val firstEvaluation = evaluation(events = events, promotedQByRoute = promoted)
        val reorderedEvaluation = evaluation(events = events.reversed(), promotedQByRoute = promoted)
        val firstDomain = domain(startHours = 1, hours = 24, intervalHours = 6)

        val first = render(firstEvaluation, firstDomain)
        val reordered = render(reorderedEvaluation, firstDomain)
        assertEquals(first.domainDigest, reordered.domainDigest)
        assertEquals(first.centralCurve, reordered.centralCurve)
        assertNotEquals(
            first.domainDigest,
            render(firstEvaluation, domain(startHours = 1, hours = 25, intervalHours = 6))
                .domainDigest,
        )

        val changedVersion = evaluation(
            events = events,
            promotedQByRoute = promoted,
            forwardModelVersion = "pk-forward:test/v2",
        )
        assertNotEquals(first.domainDigest, render(changedVersion, firstDomain).domainDigest)
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
            "Expected <= ${PkCalibrationDefaults.BAND_RENDER_BUDGET_MS} ms, was " +
                    "${bestNanos / 1_000_000.0} ms",
            bestNanos <= PkCalibrationDefaults.BAND_RENDER_BUDGET_MS * 1_000_000L,
        )
    }

    private fun evaluation(
        events: List<PkCalibrationMedicationEventSource>,
        promotedQByRoute: Map<PkCalibrationRoute, Double> = emptyMap(),
        weightKg: Double = WeightKg,
        originMillis: Long = OriginMillis,
        config: PkCalibrationConfig = Config,
        forwardModelVersion: String = ForwardModelVersion,
        calibrationModelVersion: String = CalibrationModelVersion,
    ): PkCalibrationEngineEvaluation {
        val canonicalInput = canonicalInput(
            events = events,
            weightKg = weightKg,
            originMillis = originMillis,
            config = config,
            forwardModelVersion = forwardModelVersion,
            calibrationModelVersion = calibrationModelVersion,
        )
        val included = PkCalibrationRoute.entries.flatMap { route ->
            val q = promotedQByRoute[route]
            if (q == null) emptyList() else includedEvidence(route, q)
        }
        val pool = requireNotNull(
            PkCalibrationEvidencePool.create(
                canonicalInput = canonicalInput,
                included = included,
                unassigned = emptyList(),
                invalidNonpositive = emptyList(),
                excluded = emptyList(),
            )
        )
        val solution = requireNotNull(PkCalibrationSolver.solveBound(pool))
        val evaluation = requireNotNull(PkCalibrationEngineEvaluation.ready(solution))
        assertEquals(
            PkCalibrationRoute.entries.filter(promotedQByRoute::containsKey),
            evaluation.result.promotedRoutes,
        )
        return evaluation
    }

    private fun canonicalInput(
        events: List<PkCalibrationMedicationEventSource>,
        weightKg: Double,
        originMillis: Long,
        config: PkCalibrationConfig,
        forwardModelVersion: String,
        calibrationModelVersion: String,
    ): PkCalibrationCanonicalInputSnapshot {
        val resultId = uuid(9_000)
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = Instant.EPOCH,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = 100.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 100.0,
        )
        val panel = BloodTestPanel(
            uuid = uuid(9_001),
            collectedAt = Instant.ofEpochMilli(originMillis),
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(result),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val lab = requireNotNull(
            PkCalibrationE2LabSource.create(
                panel = panel,
                result = result,
                analyteId = "hrttracker:analyte/e2/v1",
                unitId = "hrttracker:unit/pg-ml/v1",
            )
        )
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs = listOf(lab),
                medicationEvents = events,
                resolvedCurrentWeightKg = weightKg,
                forwardModelVersion = forwardModelVersion,
            )
        )
        return requireNotNull(
            PkCalibrationCanonicalInputSnapshot.create(
                authorizedLabs = listOf(lab),
                medicationEvents = scopeInput.medicationEvents,
                forwardTimeOriginEpochMillis = originMillis,
                resolvedCurrentWeightKg = weightKg,
                metadata = emptyList(),
                scopeInputSnapshot = scopeInput,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
                config = config,
            )
        )
    }

    /**
     * Three single-route labs whose observed values imply route log-scale q.
     * Single-route breakdowns keep the joint objective decoupled per route, so
     * each route's MAP is determined by its own labs alone.
     */
    private fun includedEvidence(
        route: PkCalibrationRoute,
        q: Double,
    ): List<PkCalibrationLabEvidence> {
        val totals = listOf(10.0, 20.0, 40.0)
        return totals.mapIndexed { index, total ->
            val breakdown = requireNotNull(
                PkForwardBreakdown.create(
                    PkCalibrationRoute.entries.associateWith { entry ->
                        if (entry == route) total else 0.0
                    }
                )
            )
            PkCalibrationLabEvidence(
                resultId = uuid(1_000L + route.ordinal * 100L + index),
                state = PkCalibrationLabEvidenceState.INCLUDED,
                observedPgml = exp(q) * total,
                totalDrugPgml = breakdown.totalDrugPgml,
                breakdown = breakdown,
                effectiveDisposition = PkCalibrationEffectiveDisposition.AUTO,
            )
        }
    }

    private fun render(
        evaluation: PkCalibrationEngineEvaluation,
        domain: PkChartDomain,
    ): PkCalibrationRenderResult {
        return requireNotNull(PkCalibrationRenderer.render(evaluation, domain))
    }

    private fun requireReady(
        evaluation: PkCalibrationEngineEvaluation,
    ): PkCalibrationCanonicalInputSnapshot {
        return requireNotNull(evaluation.readyEvidence).canonicalInput
    }

    private fun forward(input: PkCalibrationCanonicalInputSnapshot): PkE2ForwardModel {
        return requireNotNull(
            PkE2ForwardModel.create(
                events = input.medicationEvents.map(PkCalibrationMedicationEventSource::event),
                bodyWeightKg = input.resolvedCurrentWeightKg,
            )
        )
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
                chartGridVersion = GridVersion,
            )
        )
    }

    private fun event(
        route: PkRoute,
        timeH: Double,
        doseMg: Double,
        compound: PkCompound = PkCompound.E2,
        releaseRateMcgPerDay: Double? = null,
        originMillis: Long = OriginMillis,
        id: UUID = uuid(nextEventId++),
    ): PkCalibrationMedicationEventSource {
        val event = PkDoseEvent(
            id = id,
            sourceGroupUuid = null,
            hormone = PkHormone.ESTRADIOL,
            route = route,
            timeH = timeH,
            doseMg = doseMg,
            compound = compound,
            releaseRateMcgPerDay = releaseRateMcgPerDay,
        )
        return requireNotNull(
            PkCalibrationMedicationEventSource.create(
                event = event,
                epochMillis = originMillis + (timeH * HourMillis).toLong(),
                eventTypeId = "event:${route.name.lowercase()}/v1",
                hormoneId = "hormone:e2/v1",
                routeId = "route:${route.name.lowercase()}/v1",
                compoundId = "compound:${compound.name.lowercase()}/v1",
            )
        )
    }

    private fun allRouteEvents(): List<PkCalibrationMedicationEventSource> {
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
        evaluation: PkCalibrationEngineEvaluation,
    ) {
        val input = requireReady(evaluation)
        val forward = forward(input)
        for (point in render.centralCurve) {
            val expected = requireNotNull(
                forward.breakdownAt(
                    hoursBetween(point.epochMillis, input.forwardTimeOriginEpochMillis)
                )
            ).totalDrugPgml
            assertEquals(expected.toBits(), point.concentrationPgMl.toBits())
        }
    }

    private fun assertCentralForwardParity(
        render: PkCalibrationRenderResult,
        evaluation: PkCalibrationEngineEvaluation,
    ) {
        val input = requireReady(evaluation)
        val forward = forward(input)
        for (point in render.centralCurve) {
            val expected = requireNotNull(
                forward.breakdownAt(
                    hoursBetween(point.epochMillis, input.forwardTimeOriginEpochMillis),
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

    private fun config(rLog: Double): PkCalibrationConfig {
        return requireNotNull(
            PkCalibrationConfig.create(
                drugMinInformativePgml = 1e-12,
                rLog = rLog,
            )
        )
    }

    private fun uuid(value: Long): UUID = UUID(0L, value)

    private var nextEventId = 1L

    private companion object {
        const val OriginMillis = 1_700_000_000_000L
        const val HourMillis = 3_600_000L
        const val WeightKg = 70.0
        const val ForwardModelVersion = "pk-forward:test/v1"
        const val CalibrationModelVersion = "route-calibration:test/v9"
        const val GridVersion = "pk-chart-grid:test/v1"

        val Config = requireNotNull(
            PkCalibrationConfig.create(
                drugMinInformativePgml = 1e-12,
                rLog = 0.04,
            )
        )
    }
}
