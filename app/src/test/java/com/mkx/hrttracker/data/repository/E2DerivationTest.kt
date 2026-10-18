package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.DenseSamplePolicy
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderer
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkDoseEvent
import com.mkx.hrttracker.model.pk.PkHormone
import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.PkRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import com.mkx.hrttracker.model.pk.PkSimulationEngine
import com.mkx.hrttracker.model.pk.PkSimulationResult
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvents
import com.mkx.hrttracker.model.pk.buildEstradiolPkSimulationEntries
import com.mkx.hrttracker.model.pk.projectionFutureDays
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToLong

class E2DerivationTest {
    private val zoneId: ZoneId = ZoneId.of("Asia/Tokyo")

    // Between samples of either option's grid, and between the 08:00 and
    // 20:00 slots.
    private val now: LocalDateTime = LocalDateTime.of(2026, 5, 20, 10, 7)
    private val weightKg = 62.0
    private val estradiol = testMedicine(
        uuid = UUID.fromString("e2e2e2e2-0000-0000-0000-000000000000"),
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
    )
    private val group = MedicationGroup(
        uuid = UUID.fromString("11111111-2222-3333-4444-555555555555"),
        name = "E2",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 3, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                uuid = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                medicine = estradiol,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            ),
        ),
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )
    private val eveningSlot = group.schedule.timeSlots.last()

    /** Every slot logged on time, from the plan's start through this morning. */
    private val logged: List<MedicationLogEntry> =
        (0..ChronoUnit.DAYS.between(group.schedule.since, now.toLocalDate()))
            .flatMap { day ->
                group.schedule.timeSlots.map { slot ->
                    slotDose(group.schedule.since.plusDays(day).atTime(slot.time), slot)
                }
            }
            .filter { entry -> entry.scheduledFor!!.isBefore(now) }

    // An hour after a morning dose. Oral is the only route they touch, so it
    // is the one adjusted, with a band.
    private val labs = listOf(21L to 130.0, 14L to 150.0, 7L to 140.0).mapIndexed { index, (daysAgo, value) ->
        PkCalibrationLab(
            resultId = UUID(0L, index + 1L),
            collectedAtEpochMillis = now.toLocalDate().minusDays(daysAgo).atTime(9, 0)
                .atZone(zoneId).toInstant().toEpochMilli(),
            valuePgml = value,
        )
    }

    @Test
    fun homeAndWidget_matchTodaysBuild_lessItsTwoSamplesAtTheBuildTime() {
        val input = calibrationInput()
        val evaluation = PkCalibrationEngine.evaluate(input)
        assertEquals(listOf(PkCalibrationRoute.ORAL), evaluation.result.supportedPromotedRoutes)
        val solved = E2FitRecord(
            fitId = E2Derivation.fitId(input),
            epoch = 0,
            outcome = FitOutcome.Solved(evaluation.result, input.originEpochMillis),
        )
        val noLabsInput = buildPkCalibrationInput(emptyList(), logged, weightKg, metadata = emptyList())
        val noLabs = PkCalibrationEngine.evaluate(noLabsInput)
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, noLabs.result.globalState)
        val notReady = E2FitRecord(
            fitId = E2Derivation.fitId(noLabsInput),
            epoch = 0,
            outcome = FitOutcome.Solved(noLabs.result, noLabsInput.originEpochMillis),
        )
        for (option in HomeE2ChartWindowOption.entries) {
            val planned = buildEstradiolPkSimulationEntries(
                realEntries = logged,
                activeGroups = listOf(group),
                now = now,
                horizon = now.toLocalDate().plusDays(option.projectionFutureDays()).atStartOfDay(),
                zoneId = zoneId,
            ).planned
            assertTrue(planned.isNotEmpty())

            val calibrated = inputs(option, solved)
            val curve = todaysCurve(calibrated, planned, evaluation.result.displayParams)
            val home = homeEstimate(calibrated)
            curve.assertSameCurve(home.projection)
            val (bandKnots, status) = todaysBand(curve, calibrated, planned, evaluation, input)
            assertTrue(home.bandKnots.isNotEmpty())
            assertEquals(bandKnots, home.bandKnots)
            assertEquals(status, home.calibration)

            // A failed solve: the population curve, no band, no status.
            val failed = inputs(option, solved.copy(outcome = FitOutcome.Failed))
            val population = homeEstimate(failed)
            todaysCurve(failed, planned, PkPersonalParams.population()).assertSameCurve(population.projection)
            assertTrue(population.bandKnots.isEmpty())
            assertNull(population.calibration)

            // A solve without labs is not READY: no band, and the build's
            // non-null, all-false hero record.
            val unlabelled = inputs(option, notReady)
            val unlabelledCurve = todaysCurve(unlabelled, planned, noLabs.result.displayParams)
            val uncalibrated = homeEstimate(unlabelled)
            unlabelledCurve.assertSameCurve(uncalibrated.projection)
            assertEquals(
                todaysBand(unlabelledCurve, unlabelled, planned, noLabs, noLabsInput),
                uncalibrated.bandKnots to uncalibrated.calibration,
            )
            assertTrue(uncalibrated.bandKnots.isEmpty())
            assertEquals(
                HomePkCalibrationRecord(
                    adjusted = false,
                    limitedConfidence = false,
                    renderUnavailable = false,
                    bandUnavailable = false,
                    effectiveRoutes = emptyList(),
                ),
                uncalibrated.calibration,
            )
        }
        // Today's widget simulated Home's window; at Home's seven days that
        // is the same curve, from the logged doses only.
        todaysCurve(inputs(fit = solved), planned = emptyList(), params = evaluation.result.displayParams)
            .assertSameCurve(E2Derivation.deriveWidgetEstimate(inputs(fit = solved), now, zoneId))
    }

    @Test
    fun sameKey_derivesIdenticalOutputs_atAnyMinute() {
        val inputs = inputs()
        // Both before the 20:00 slot.
        val morning = now
        val evening = now.withHour(19).withMinute(41)
        assertEquals(
            E2Derivation.homeEstimateKey(inputs, morning, zoneId),
            E2Derivation.homeEstimateKey(inputs, evening, zoneId),
        )
        fun homeBytes(at: LocalDateTime): ByteArray {
            val estimate = homeEstimate(inputs, at)
            assertTrue(estimate.bandKnots.isNotEmpty())
            return HomeSnapshotCodec.encodeHomeEstimate(
                HomeE2EstimateRecord(E2Derivation.homeEstimateKey(inputs, at, zoneId), estimate)
            )
        }
        assertArrayEquals(homeBytes(morning), homeBytes(evening))

        // The widget and the calibration page move only with the date.
        val night = now.withHour(23).withMinute(59)
        assertEquals(
            E2Derivation.widgetEstimateKey(inputs, morning, zoneId),
            E2Derivation.widgetEstimateKey(inputs, night, zoneId),
        )
        assertEquals(
            E2Derivation.deriveWidgetEstimate(inputs, morning, zoneId),
            E2Derivation.deriveWidgetEstimate(inputs, night, zoneId),
        )
        assertEquals(
            E2Derivation.calibrationRenderKey(inputs, morning, zoneId),
            E2Derivation.calibrationRenderKey(inputs, night, zoneId),
        )
        val render = E2Derivation.deriveCalibrationRender(inputs, morning, zoneId)
        assertEquals(PkCalibrationBandState.READY, render?.bandState)
        assertEquals(render, E2Derivation.deriveCalibrationRender(inputs, night, zoneId))
    }

    @Test
    fun keys_reuseTheInputsDigests_untilTheFitOrTheEntriesAreNewObjects() {
        val inputs = inputs()
        val home = E2Derivation.homeEstimateKey(inputs, now, zoneId)
        val page = E2Derivation.calibrationRenderKey(inputs, now, zoneId)

        // The next minute, from the same published inputs: the same digests,
        // not re-encoded, for every observer at once.
        val nextHome = E2Derivation.homeEstimateKey(inputs, now.plusMinutes(1), zoneId)
        assertSame(home.fitKey, nextHome.fitKey)
        assertSame(home.logged, nextHome.logged)
        assertSame(page.logged, E2Derivation.calibrationRenderKey(inputs, now.plusMinutes(1), zoneId).logged)
        assertSame(home.fitKey, E2Derivation.widgetEstimateKey(inputs, now, zoneId).fitKey)

        // A publish: equal contents in new objects are digested again, to the same value.
        val republished = inputs.copy(
            estradiolEntries = inputs.estradiolEntries.toList(),
            fit = checkNotNull(inputs.fit).copy(),
        )
        val again = E2Derivation.homeEstimateKey(republished, now, zoneId)
        assertNotSame(home.fitKey, again.fitKey)
        assertNotSame(home.logged, again.logged)
        assertEquals(home, again)
        // A new day moves the window: the same entries are digested for it.
        assertNotSame(page.logged, E2Derivation.calibrationRenderKey(inputs, now.plusDays(1), zoneId).logged)
    }

    @Test
    fun keys_ignoreWhatTheMathDoesNotRead() {
        val inputs = inputs()
        val key = E2Derivation.homeEstimateKey(inputs, now, zoneId)

        // A stock recount and a rename reach every embedded copy of the medicine.
        val edited = estradiol.copy(
            displayName = "My E2",
            updatedAt = Instant.parse("2026-05-20T00:30:00Z"),
            stock = MedicineStock(trackingEnabled = true, unitsRemaining = 12.0),
        )
        val editedInputs = inputs.copy(
            estradiolEntries = inputs.estradiolEntries.map { entry -> entry.copy(medicine = edited) },
            plannedSources = inputs.plannedSources.map { source ->
                source.copy(medications = source.medications.map { it.copy(medicine = edited) })
            },
        )
        assertEquals(key, E2Derivation.homeEstimateKey(editedInputs, now, zoneId))

        // An antiandrogen dose, and a group of nothing but antiandrogen.
        val spironolactone = testMedicine(
            key = MedicationKey.SPIRONOLACTONE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
        )
        val withAntiandrogen = inputs.copy(
            estradiolEntries = inputs.estradiolEntries + testMedicationLogEntry(
                medicine = spironolactone,
                sourceGroupUuid = null,
                appliedAt = now.minusHours(1).atZone(zoneId).toInstant(),
            ),
            plannedSources = inputs.plannedSources + group.copy(
                uuid = UUID(1L, 1L),
                medications = listOf(testMedicationGroupMedication(medicine = spironolactone)),
            ),
        )
        assertEquals(key, E2Derivation.homeEstimateKey(withAntiandrogen, now, zoneId))

        // Minute ticks that cross no slot; an unset and an invalid weight
        // both resolve to the default.
        assertEquals(key, E2Derivation.homeEstimateKey(inputs, now.plusMinutes(1), zoneId))
        assertEquals(key, E2Derivation.homeEstimateKey(inputs, now.withHour(19).withMinute(59), zoneId))
        assertEquals(
            E2Derivation.homeEstimateKey(inputs.copy(weightKg = null), now, zoneId),
            E2Derivation.homeEstimateKey(inputs.copy(weightKg = -1.0), now, zoneId),
        )
        // The widget doesn't read Home's option.
        assertEquals(
            E2Derivation.widgetEstimateKey(inputs, now, zoneId),
            E2Derivation.widgetEstimateKey(inputs.copy(option = HomeE2ChartWindowOption.THIRTY_DAYS), now, zoneId),
        )
    }

    @Test
    fun keys_moveWithEverythingTheMathReads() {
        val inputs = inputs()
        val key = E2Derivation.homeEstimateKey(inputs, now, zoneId)
        assertEquals(PK_ENGINE_VERSION, key.engine)
        assertEquals(weightKg, key.weightKg, 0.0)

        // Logging an E2 dose.
        val extraDose = inputs.copy(
            estradiolEntries = inputs.estradiolEntries + slotDose(now.minusMinutes(30), slot = null),
        )
        assertNotEquals(key.logged, E2Derivation.homeEstimateKey(extraDose, now, zoneId).logged)

        // The 20:00 slot passing: only the planned digest moves.
        val beforeSlot = E2Derivation.homeEstimateKey(inputs, now.withHour(19).withMinute(59), zoneId)
        val afterSlot = E2Derivation.homeEstimateKey(inputs, now.withHour(20).withMinute(1), zoneId)
        assertNotEquals(beforeSlot.planned, afterSlot.planned)
        assertEquals(beforeSlot.copy(planned = afterSlot.planned), afterSlot)

        // Logging the 20:00 dose early fills its slot.
        val slotTime = now.toLocalDate().atTime(eveningSlot.time)
        val filled = inputs.copy(
            estradiolEntries = inputs.estradiolEntries + slotDose(slotTime, eveningSlot, appliedAt = now),
        )
        val later = now.plusMinutes(10)
        assertNotEquals(
            E2Derivation.homeEstimateKey(inputs, later, zoneId).planned,
            E2Derivation.homeEstimateKey(filled, later, zoneId).planned,
        )

        // Option, zone, date, weight, and the fit's outcome and epoch.
        val fit = checkNotNull(inputs.fit)
        listOf(
            E2Derivation.homeEstimateKey(inputs.copy(option = HomeE2ChartWindowOption.THIRTY_DAYS), now, zoneId),
            E2Derivation.homeEstimateKey(inputs, now, ZoneId.of("Europe/Berlin")),
            E2Derivation.homeEstimateKey(inputs, now.plusDays(1).withHour(9), zoneId),
            E2Derivation.homeEstimateKey(inputs.copy(weightKg = 63.0), now, zoneId),
            E2Derivation.homeEstimateKey(inputs.copy(fit = fit.copy(outcome = FitOutcome.Failed)), now, zoneId),
            E2Derivation.homeEstimateKey(inputs.copy(fit = fit.copy(epoch = 1)), now, zoneId),
            E2Derivation.homeEstimateKey(inputs.copy(fit = null), now, zoneId),
        ).forEach { moved -> assertNotEquals(key, moved) }

        // The widget and the calibration page move with the date; the page's
        // status range moves with Home's option.
        assertNotEquals(
            E2Derivation.widgetEstimateKey(inputs, now, zoneId),
            E2Derivation.widgetEstimateKey(inputs, now.plusDays(1), zoneId),
        )
        assertNotEquals(
            E2Derivation.calibrationRenderKey(inputs, now, zoneId),
            E2Derivation.calibrationRenderKey(inputs, now.plusDays(1), zoneId),
        )
        assertNotEquals(
            E2Derivation.calibrationRenderKey(inputs, now, zoneId),
            E2Derivation.calibrationRenderKey(inputs.copy(option = HomeE2ChartWindowOption.THIRTY_DAYS), now, zoneId),
        )
    }

    @Test
    fun loggedDoses_olderThanEachCurvesLookback_changeNeitherItsKeyNorItsOutput() {
        val inputs = inputs()
        // 180 days before each window: Home's and the widget's seven days
        // start three days back, the calibration page's domain seventeen.
        val homeBound = now.toLocalDate().minusDays(3).atStartOfDay(zoneId).toInstant().minus(Duration.ofDays(180))
        val pageBound = now.toLocalDate().minusDays(17).atStartOfDay(zoneId).toInstant().minus(Duration.ofDays(180))
        fun withDoseAt(at: Instant) = inputs.copy(
            estradiolEntries = inputs.estradiolEntries + slotDose(LocalDateTime.ofInstant(at, zoneId), slot = null),
        )
        fun keys(of: E2Inputs) = listOf(
            E2Derivation.homeEstimateKey(of, now, zoneId),
            E2Derivation.widgetEstimateKey(of, now, zoneId),
            E2Derivation.calibrationRenderKey(of, now, zoneId),
        )
        fun outputs(of: E2Inputs) = listOf(
            homeEstimate(of),
            E2Derivation.deriveWidgetEstimate(of, now, zoneId),
            E2Derivation.deriveCalibrationRender(of, now, zoneId),
        )

        // Older than every bound: nothing reads it.
        val ancient = withDoseAt(pageBound.minusMillis(1))
        assertEquals(keys(inputs), keys(ancient))
        assertEquals(outputs(inputs), outputs(ancient))
        // Between the bounds: only the page's longer domain reads it.
        val between = withDoseAt(homeBound.minusMillis(1))
        assertEquals(keys(inputs).take(2), keys(between).take(2))
        assertEquals(outputs(inputs).take(2), outputs(between).take(2))
        assertNotEquals(keys(inputs)[2], keys(between)[2])
        // On Home's bound it is read.
        assertNotEquals(keys(inputs)[0], keys(withDoseAt(homeBound))[0])
    }

    @Test
    fun fitId_digestsWhatTheSolveReads_andNotTheClockOrTheOrigin() {
        val input = calibrationInput()
        val id = E2Derivation.fitId(input)

        // The origin only places the events: the same instants from another one.
        val earlierOrigin = input.copy(
            originEpochMillis = input.originEpochMillis - 5L * 3_600_000L,
            doseEvents = input.doseEvents.map { event -> event.copy(timeH = event.timeH + 5.0) },
        )
        assertEquals(id, E2Derivation.fitId(earlierOrigin))
        // A medicine's stock or name, the labs' order, a review's timestamp
        // or an AUTO review.
        val edited = estradiol.copy(
            displayName = "My E2",
            stock = MedicineStock(trackingEnabled = true, unitsRemaining = 3.0),
        )
        assertEquals(id, E2Derivation.fitId(calibrationInput(logged.map { it.copy(medicine = edited) })))
        assertEquals(id, E2Derivation.fitId(input.copy(labs = input.labs.reversed())))
        fun review(disposition: E2CalibrationDisposition, at: Instant = Instant.EPOCH) =
            input.copy(metadata = listOf(E2CalibrationMetadata(labs.first().resultId, disposition, at)))
        assertEquals(
            E2Derivation.fitId(review(E2CalibrationDisposition.REVIEWED)),
            E2Derivation.fitId(review(E2CalibrationDisposition.REVIEWED, Instant.parse("2026-05-20T00:00:00Z"))),
        )
        assertEquals(id, E2Derivation.fitId(review(E2CalibrationDisposition.AUTO)))

        listOf(
            input.copy(weightKg = 63.0),
            input.copy(labs = input.labs.dropLast(1)),
            input.copy(labs = input.labs.map { lab -> lab.copy(valuePgml = lab.valuePgml + 1.0) }),
            input.copy(doseEvents = input.doseEvents.drop(1)),
            review(E2CalibrationDisposition.REVIEWED),
            review(E2CalibrationDisposition.EXCLUDED),
            input.copy(config = PkCalibrationConfig(drugMinInformativePgml = 5.0, rLog = 0.03)),
        ).forEach { changed -> assertNotEquals(id, E2Derivation.fitId(changed)) }

        // No lab and no dose: nothing anchors the origin, and the clock doesn't
        // either, so the stored fit (and fitKey) is the same on every build.
        val empty = buildPkCalibrationInput(emptyList(), emptyList(), weightKg = null, metadata = emptyList())
        assertEquals(0L, empty.originEpochMillis)
    }

    @Test
    fun fitKey_isTheWholeRecord_comparedByValue() {
        val fit = fit()
        val key = E2Derivation.fitKey(fit)
        assertEquals(key, E2Derivation.fitKey(fit.copy(fitId = Digest(fit.fitId.hex))))
        listOf(
            fit.copy(epoch = 1),
            fit.copy(outcome = FitOutcome.Failed),
            fit.copy(fitId = Digest.of(byteArrayOf(1))),
            fit.copy(outcome = (fit.outcome as FitOutcome.Solved).copy(originEpochMillis = 0L)),
        ).forEach { changed -> assertNotEquals(key, E2Derivation.fitKey(changed)) }
        assertEquals(Digest.of(byteArrayOf(1, 2)), Digest.of(byteArrayOf(1, 2)))
        assertEquals(64, key.hex.length)
    }

    @Test
    fun pkCalibrationStatus_reproducesTheBuildsHeroRecord() {
        val input = calibrationInput()
        val evaluations = listOf(
            PkCalibrationEngine.evaluate(input.copy(doseEvents = emptyList())),
            PkCalibrationEngine.evaluate(input.copy(labs = emptyList())),
            PkCalibrationEngine.evaluate(input.copy(weightKg = Double.NaN)),
            PkCalibrationEngine.evaluate(input),
        )
        // What the build read as isReady, the fit stores as READY.
        assertEquals(
            PkCalibrationGlobalState.entries.toSet(),
            evaluations.map { evaluation -> evaluation.result.globalState }.toSet(),
        )
        for (evaluation in evaluations) {
            assertEquals(evaluation.isReady, evaluation.result.globalState == PkCalibrationGlobalState.READY)
        }
        val ready = evaluations.last()
        val provisional = PkCalibrationResult(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = PkCalibrationRoute.entries.map { route ->
                when (route) {
                    PkCalibrationRoute.ORAL -> PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                        reasons = setOf(PkCalibrationReason.UNCERTAIN),
                        fittedBeta = 0.3,
                        betaPosteriorSd = 0.25,
                        supportingLabCount = 1,
                    )

                    PkCalibrationRoute.INJECTION -> PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                        fittedBeta = 0.1,
                        betaPosteriorSd = 0.05,
                        supportingLabCount = 3,
                    )

                    else -> PkRouteCalibrationResult(route, PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL)
                }
            },
        )
        val numericUnavailable = PkCalibrationRenderResult(
            renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
            centralCurve = emptyList(),
            bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
        )
        val bandFailed = PkCalibrationRenderResult(
            renderState = PkCalibrationRenderState.PERSONALIZED,
            effectivePromotedRoutes = listOf(PkCalibrationRoute.ORAL),
            centralCurve = emptyList(),
            bandState = PkCalibrationBandState.NUMERIC_UNAVAILABLE,
        )
        val renders = listOf(
            null,
            numericUnavailable,
            bandFailed,
            bandFailed.copy(bandState = PkCalibrationBandState.READY),
            bandFailed.copy(effectivePromotedRoutes = listOf(PkCalibrationRoute.INJECTION)),
            numericUnavailable.copy(renderState = PkCalibrationRenderState.POPULATION),
            evaluationRender(
                ready, input,
                checkNotNull(PkChartDomain.create(input.originEpochMillis, labs.last().collectedAtEpochMillis, 21_600_000L)),
            ),
        )
        val cases = evaluations.map { evaluation -> evaluation.isReady to evaluation.result } +
                (true to provisional)
        val records = cases.flatMap { (isReady, result) ->
            renders.map { render ->
                E2Derivation.pkCalibrationStatus(result, render).also { record ->
                    assertEquals(todaysStatus(isReady, result, render), record)
                }
            }
        }
        // Not vacuous: every flag is exercised both ways.
        for (flag in listOf<(HomePkCalibrationRecord) -> Boolean>(
            HomePkCalibrationRecord::adjusted,
            HomePkCalibrationRecord::limitedConfidence,
            HomePkCalibrationRecord::renderUnavailable,
            HomePkCalibrationRecord::bandUnavailable,
        )) {
            assertEquals(setOf(true, false), records.map(flag).toSet())
        }
    }

    @Test
    fun calibrationRender_isTheFitsRender_overTheDateAnchoredDomain_withHomesStatusRange() {
        val inputs = inputs()
        val solved = checkNotNull(inputs.fit).outcome as FitOutcome.Solved
        val today = now.toLocalDate()
        val domain = checkNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = today.minusDays(17).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                rangeEndEpochMillis = today.plusDays(14).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                samplingIntervalMillis = 6L * 3_600_000L,
            )
        )
        val page = E2Derivation.deriveCalibrationRender(inputs, now, zoneId)
        assertEquals(
            PkCalibrationRenderer.render(
                result = solved.result,
                originEpochMillis = solved.originEpochMillis,
                weightKg = weightKg,
                config = PkCalibrationConfig.Default,
                domain = domain,
                doseEvents = buildEstradiolPkDoseEvents(
                    Instant.ofEpochMilli(solved.originEpochMillis), logged, emptyList(),
                ),
                statusRange = HomePkChartWindow.forNow(now, zoneId, inputs.option).range,
            ),
            page,
        )
        assertEquals(domain.rangeStartEpochMillis, checkNotNull(page).centralCurve.first().epochMillis)
        assertEquals(domain.rangeEndEpochMillis, page.centralCurve.last().epochMillis)

        // Home and the page draw the same band where they overlap: before
        // now, where Home's planned doses add nothing.
        val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()
        val pageKnots = page.bandKnots.filter { knot -> knot.epochMillis < nowMillis }.associateBy { it.epochMillis }
        val shared = homeEstimate(inputs).bandKnots
            .filter { knot -> knot.epochMillis in pageKnots }
        assertTrue(shared.size >= 12)
        for (knot in shared) {
            val pageKnot = pageKnots.getValue(knot.epochMillis)
            assertEquals(
                with(pageKnot) { listOf(p025Pgml, p158655254Pgml, p50Pgml, p841344746Pgml, p975Pgml) },
                with(knot) { listOf(p025Pgml, p158655254Pgml, p50Pgml, p841344746Pgml, p975Pgml) },
            )
        }

        assertNull(E2Derivation.deriveCalibrationRender(inputs.copy(fit = null), now, zoneId))
        assertNull(
            E2Derivation.deriveCalibrationRender(
                inputs.copy(fit = inputs.fit.copy(outcome = FitOutcome.Failed)), now, zoneId,
            )
        )
    }

    @Test
    fun calibrationRender_matchesTheOldPagesRender_withinWhatTheLookbackLeavesOut() {
        // The page rendered the build's evaluation over the solve's whole
        // dose history, on a domain anchored to the clock: 17 days before
        // now to 14 days after. It now renders the stored fit over Home's
        // 197-day lookback, on a domain anchored to the date. At noon the two
        // 6 h grids share their knots. A lab 60 days back takes the solve's
        // history to 240 days; the 43 days the page no longer reads end more
        // than 180 days before its domain and contribute almost nothing.
        val noon = now.withHour(12).withMinute(0)
        val history = (1L..365L).flatMap { daysAgo ->
            listOf(8, 20).map { hour -> slotDose(noon.toLocalDate().minusDays(daysAgo).atTime(hour, 0), slot = null) }
        }
        val labs = listOf(60L to 135.0, 21L to 130.0, 7L to 145.0).mapIndexed { index, (daysAgo, value) ->
            PkCalibrationLab(
                resultId = UUID(0L, index + 1L),
                collectedAtEpochMillis = noon.toLocalDate().minusDays(daysAgo).atTime(9, 0)
                    .atZone(zoneId).toInstant().toEpochMilli(),
                valuePgml = value,
            )
        }
        val input = buildPkCalibrationInput(labs = labs, entries = history, weightKg = weightKg, metadata = emptyList())
        val evaluation = PkCalibrationEngine.evaluate(input)
        assertEquals(listOf(PkCalibrationRoute.ORAL), evaluation.result.supportedPromotedRoutes)
        val nowMillis = noon.atZone(zoneId).toInstant().toEpochMilli()
        val old = checkNotNull(
            evaluationRender(
                evaluation, input,
                domain = checkNotNull(
                    PkChartDomain.create(
                        rangeStartEpochMillis = nowMillis - 17L * 86_400_000L,
                        rangeEndEpochMillis = nowMillis + 14L * 86_400_000L,
                        samplingIntervalMillis = 6L * 3_600_000L,
                    )
                ),
                statusRange = HomePkChartWindow.forNow(noon, zoneId, HomeE2ChartWindowOption.SEVEN_DAYS).range,
            )
        )
        val page = checkNotNull(
            E2Derivation.deriveCalibrationRender(
                inputs().copy(
                    estradiolEntries = history,
                    fit = E2FitRecord(
                        fitId = E2Derivation.fitId(input),
                        epoch = 0,
                        outcome = FitOutcome.Solved(evaluation.result, input.originEpochMillis),
                    ),
                ),
                noon,
                zoneId,
            )
        )

        assertEquals(old.renderState, page.renderState)
        assertEquals(old.bandState, page.bandState)
        assertEquals(PkCalibrationBandState.READY, page.bandState)
        assertEquals(old.effectivePromotedRoutes, page.effectivePromotedRoutes)
        assertEquals(old.effectiveDisplayParams, page.effectiveDisplayParams)
        fun assertClose(expected: Double, actual: Double) =
            assertEquals(expected, actual, 1e-6 * maxOf(1.0, kotlin.math.abs(expected)))
        val pageKnots = page.bandKnots.associateBy { knot -> knot.epochMillis }
        val sharedKnots = old.bandKnots.filter { knot -> knot.epochMillis in pageKnots }
        // The overlap, 17 days from noon 17 days back to midnight 14 days on,
        // less half a day: about 120 grid knots, plus the doses in it.
        assertTrue(sharedKnots.size >= 120)
        for (knot in sharedKnots) {
            val pageKnot = pageKnots.getValue(knot.epochMillis)
            assertEquals(knot.segment, pageKnot.segment)
            assertClose(knot.p025Pgml, pageKnot.p025Pgml)
            assertClose(knot.p158655254Pgml, pageKnot.p158655254Pgml)
            assertClose(knot.p50Pgml, pageKnot.p50Pgml)
            assertClose(knot.p841344746Pgml, pageKnot.p841344746Pgml)
            assertClose(knot.p975Pgml, pageKnot.p975Pgml)
        }
        val pageCurve = page.centralCurve.associate { point -> point.epochMillis to point.concentrationPgMl }
        val sharedCurve = old.centralCurve.filter { point -> point.epochMillis in pageCurve }
        assertTrue(sharedCurve.size >= 120)
        for (point in sharedCurve) {
            assertClose(point.concentrationPgMl, pageCurve.getValue(point.epochMillis))
        }
    }

    @Test
    fun widgetProjection_servesTheWidgetsSevenDays_onEveryDayTheSnapshotIsValid() {
        // Home's 30-day option no longer reaches the widget: its projection is
        // the seven-day one, and HrtWidget's seven-day trend reads from it on
        // each of the snapshot's ten valid days.
        val thirtyDays = inputs(option = HomeE2ChartWindowOption.THIRTY_DAYS)
        val projection = E2Derivation.deriveWidgetEstimate(thirtyDays, now, zoneId)
        assertEquals(projection, E2Derivation.deriveWidgetEstimate(inputs(), now, zoneId))
        for (day in 0L..10L) {
            assertNotNull(projection.toMainEstradiolTrend(now.plusDays(day).withHour(12), zoneId))
        }
    }

    // A thrown band render fails the test instead of passing as a missing band.
    private fun homeEstimate(inputs: E2Inputs, at: LocalDateTime = now): HomeE2Estimate =
        E2Derivation.deriveHomeEstimate(inputs, at, zoneId, onBandFailure = { throw it })

    private fun calibrationInput(entries: List<MedicationLogEntry> = logged): PkCalibrationInput =
        buildPkCalibrationInput(labs = labs, entries = entries, weightKg = weightKg, metadata = emptyList())

    private fun fit(input: PkCalibrationInput = calibrationInput()) = E2FitRecord(
        fitId = E2Derivation.fitId(input),
        epoch = 0,
        outcome = FitOutcome.Solved(PkCalibrationEngine.evaluate(input).result, input.originEpochMillis),
    )

    private fun inputs(
        option: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        fit: E2FitRecord? = fit(),
    ) = E2Inputs(
        estradiolEntries = logged,
        plannedSources = listOf(group),
        weightKg = weightKg,
        option = option,
        fit = fit,
    )

    private fun slotDose(
        scheduledFor: LocalDateTime,
        slot: MedicationGroupScheduleTime?,
        appliedAt: LocalDateTime = scheduledFor,
    ) = testMedicationLogEntry(
        uuid = UUID.nameUUIDFromBytes("dose:$scheduledFor:${slot?.uuid}:$appliedAt".toByteArray()),
        medicine = estradiol,
        doseInstruction = DoseInstruction.TabletFraction(1, 1),
        sourceGroupUuid = slot?.let { group.uuid },
        appliedAt = appliedAt.atZone(zoneId).toInstant(),
        appliedAtTimeZoneId = zoneId.id,
        scheduledFor = slot?.let { scheduledFor },
        scheduleTimeUuid = slot?.uuid,
    )

    /** Today's build's curve: its sample grid, which also took the build time and a day before. */
    private class TodaysCurve(
        val windowStart: Instant,
        val windowEnd: Instant,
        val grid: List<Double>,
        val buildTimeSamples: List<Double>,
        val result: PkSimulationResult,
    ) {
        fun assertSameCurve(derived: PkProjectionResult) {
            assertEquals(windowStart, derived.windowStart)
            assertEquals(windowEnd, derived.windowEnd)
            val kept = result.timeH.indices.filter { index -> result.timeH[index] !in buildTimeSamples }
            assertEquals(result.timeH.size - 2, kept.size)
            assertEquals(kept.map(result.timeH::get), derived.timeH)
            assertEquals(kept.map(result.concentrations::get), derived.concentrations)
            assertTrue(derived.doseMarkers.isNotEmpty())
            for (marker in derived.doseMarkers) {
                assertEquals(checkNotNull(result.concentrationAt(marker.timeH)).toBits(), marker.concentration.toBits())
            }
        }
    }

    private fun todaysCurve(
        inputs: E2Inputs,
        planned: List<MedicationLogEntry>,
        params: PkPersonalParams,
    ): TodaysCurve {
        val option = inputs.option
        val windowStart = now.toLocalDate().atStartOfDay().minusDays(option.pastDays).atZone(zoneId).toInstant()
        val windowEnd = now.toLocalDate().plusDays(option.projectionFutureDays()).atStartOfDay()
            .atZone(zoneId).toInstant()
        val windowHours = Duration.between(windowStart, windowEnd).toMillis() / 3_600_000.0
        val buildTimeH = Duration.between(windowStart, now.atZone(zoneId).toInstant()).toMillis() / 3_600_000.0
        val buildTimeSamples = listOf(chartX(buildTimeH), chartX(buildTimeH - 24.0))
        val events = buildEstradiolPkDoseEvents(windowStart, inputs.estradiolEntries, planned)
        val grid = (denseGrid(windowHours, option) + buildTimeSamples + events.map(PkDoseEvent::timeH) +
                postDoseOffsets(events, option))
            .map(::chartX)
            .filter { timeH -> timeH.isFinite() && timeH in 0.0..windowHours }
            .distinct()
            .sorted()
        val result = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = checkNotNull(inputs.weightKg),
            startTimeH = 0.0,
            endTimeH = windowHours,
            numberOfSteps = ceil(windowHours).toInt().coerceAtLeast(1) + 1,
            personalParams = params,
        ).run(sampleTimeH = grid)
        return TodaysCurve(windowStart, windowEnd, grid, buildTimeSamples, result)
    }

    /** Today's band over its grid (the evaluation's render), less the build-time knots, and its hero record. */
    private fun todaysBand(
        curve: TodaysCurve,
        inputs: E2Inputs,
        planned: List<MedicationLogEntry>,
        evaluation: PkCalibrationEvaluation,
        input: PkCalibrationInput,
    ): Pair<List<HomePkBandKnotRecord>, HomePkCalibrationRecord> {
        val startMillis = curve.windowStart.toEpochMilli()
        val endMillis = curve.windowEnd.toEpochMilli()
        fun millisOf(hours: Double) = startMillis + (hours * 3_600_000.0).roundToLong()
        val render = evaluationRender(
            evaluation, input,
            domain = checkNotNull(
                PkChartDomain.create(
                    rangeStartEpochMillis = startMillis,
                    rangeEndEpochMillis = endMillis,
                    samplingIntervalMillis = 6L * 3_600_000L,
                    protectedKnotEpochMillis = curve.grid.map(::millisOf).filter { it in startMillis..endMillis },
                )
            ),
            doseEvents = buildEstradiolPkDoseEvents(
                Instant.ofEpochMilli(input.originEpochMillis), inputs.estradiolEntries, planned,
            ),
            statusRange = HomePkChartWindow.forNow(now, zoneId, inputs.option).range,
        )
        val buildTimeKnots = curve.buildTimeSamples.map(::millisOf)
        val bandKnots = render
            ?.takeIf { it.bandState == PkCalibrationBandState.READY }
            ?.bandKnots
            ?.filter { knot -> knot.epochMillis !in buildTimeKnots }
            ?.map { knot ->
                HomePkBandKnotRecord(
                    epochMillis = knot.epochMillis,
                    p025Pgml = knot.p025Pgml,
                    p158655254Pgml = knot.p158655254Pgml,
                    p50Pgml = knot.p50Pgml,
                    p841344746Pgml = knot.p841344746Pgml,
                    p975Pgml = knot.p975Pgml,
                    segment = knot.segment,
                )
            }
            .orEmpty()
        return bandKnots to todaysStatus(evaluation.isReady, evaluation.result, render)
    }

    // The evaluation's render as the build and the old page drew it: its fit,
    // with the input it was solved from. Null unless READY.
    private fun evaluationRender(
        evaluation: PkCalibrationEvaluation,
        input: PkCalibrationInput,
        domain: PkChartDomain,
        doseEvents: List<PkDoseEvent> = input.doseEvents,
        statusRange: LongRange = domain.rangeStartEpochMillis..domain.rangeEndEpochMillis,
    ): PkCalibrationRenderResult? = PkCalibrationRenderer.render(
        result = evaluation.result,
        originEpochMillis = input.originEpochMillis,
        weightKg = input.weightKg,
        config = input.config,
        domain = domain,
        doseEvents = doseEvents,
        statusRange = statusRange,
    )

    // The hero record exactly as buildAndWriteHomeSnapshot built it inline,
    // from the evaluation's isReady.
    private fun todaysStatus(
        isReady: Boolean,
        result: PkCalibrationResult,
        render: PkCalibrationRenderResult?,
    ): HomePkCalibrationRecord {
        val effective = render?.effectivePromotedRoutes ?: result.supportedPromotedRoutes
        val provisional = result.routeResults
            .filter { row -> row.displayState == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL }
            .map { row -> row.route }
        return HomePkCalibrationRecord(
            adjusted = effective.isNotEmpty(),
            limitedConfidence = effective.any(provisional::contains),
            renderUnavailable = isReady && render?.renderState == PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
            bandUnavailable = effective.isNotEmpty() && render?.bandState != PkCalibrationBandState.READY,
            effectiveRoutes = effective.map(PkCalibrationRoute::stableId),
        )
    }

    // mainProjectionSampleTimeH's dense grid and post-dose offsets.
    private fun denseGrid(windowHours: Double, option: HomeE2ChartWindowOption): List<Double> =
        when (val policy = option.densePolicy) {
            is DenseSamplePolicy.Interval -> (0..ceil(windowHours / policy.hours).toInt())
                .map { index -> (policy.hours * index).coerceAtMost(windowHours) }

            is DenseSamplePolicy.Budget -> (0..policy.segmentCount)
                .map { index -> (windowHours / policy.segmentCount * index).coerceAtMost(windowHours) }
        }

    private fun postDoseOffsets(events: List<PkDoseEvent>, option: HomeE2ChartWindowOption): List<Double> {
        if (!option.includesPostDoseOffsets) return emptyList()
        return events
            .filter { event ->
                event.route != PkRoute.PATCH_REMOVE && (event.doseMg > 0.0 || event.releaseRateMcgPerDay != null)
            }
            .flatMap { event -> PostDoseOffsetsHours.map { offset -> event.timeH + offset } }
    }

    private fun chartX(hours: Double): Double = (hours * 10_000.0).roundToLong() / 10_000.0

    private companion object {
        val PostDoseOffsetsHours = listOf(0.25, 0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 12.0, 24.0, 48.0)
    }
}
