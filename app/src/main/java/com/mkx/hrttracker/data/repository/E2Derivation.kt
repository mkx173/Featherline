package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderer
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvent
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvents
import com.mkx.hrttracker.model.pk.buildEstradiolPkSimulationEntries
import com.mkx.hrttracker.model.pk.projectionFutureDays
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Bumped with any change to PK math or calibration. The E2 inputs part is
 * valid only from this engine, so a bump drops it and the next run re-solves.
 * It is also part of every estimate key and of the fit's identity, so nothing
 * an older engine derived or solved is reused.
 */
const val PK_ENGINE_VERSION = 2

/** SHA-256 of a canonical [HomeSnapshotCodec] encoding; hex, so a key holding it compares by value. */
data class Digest(val hex: String) {
    companion object {
        fun of(bytes: ByteArray): Digest =
            Digest(MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
    }
}

/** One calibration solve and what it was solved from. */
data class E2FitRecord(
    /** [E2Derivation.fitId] of the input it was solved from. */
    val fitId: Digest,
    /** The requested fit epoch it satisfies; Try again raises it. */
    val epoch: Int,
    val outcome: FitOutcome,
)

sealed interface FitOutcome {
    /** The engine's result; the solve placed its dose events relative to [originEpochMillis]. */
    data class Solved(
        val result: PkCalibrationResult,
        val originEpochMillis: Long,
    ) : FitOutcome

    /** The solve threw: the estimates show the population curve with no status. */
    data object Failed : FitOutcome
}

/** What every E2 estimate is derived from. The clock comes in only through the key. */
data class E2Inputs(
    /** Logged estradiol entries; each derivation reads its own PK lookback of them. */
    val estradiolEntries: List<MedicationLogEntry>,
    /** Active groups: their estradiol slots after now are Home's planned doses. */
    val plannedSources: List<MedicationGroup>,
    /** Current Weight; unset falls back to the app default. */
    val weightKg: Double?,
    val option: HomeE2ChartWindowOption,
    /** Null when no calibration ran. */
    val fit: E2FitRecord?,
)

/** Home's curve, band and calibration status. */
data class HomeE2Estimate(
    /** The chart window it covers: [HomeEstimateKey.option]. */
    val option: HomeE2ChartWindowOption,
    val projection: PkProjectionResult,
    /** The READY band over the projection window; empty otherwise. */
    val bandKnots: List<HomePkBandKnotRecord>,
    /** Null without a solved fit. */
    val calibration: HomePkCalibrationRecord?,
)

/** The last Home estimate and the key it was derived for. */
data class HomeE2EstimateRecord(
    val key: HomeEstimateKey,
    val estimate: HomeE2Estimate,
)

/** Exactly what [E2Derivation.deriveHomeEstimate] reads. */
data class HomeEstimateKey(
    val engine: Int,
    /** [E2Derivation.fitKey]; null when no calibration ran. */
    val fitKey: Digest?,
    /** Resolved: the default when unset. */
    val weightKg: Double,
    /** The PK dose events of the logged entries, in absolute time. */
    val logged: Digest,
    /** The PK dose events of the planned slots after now; changes when a slot passes or a log fills it. */
    val planned: Digest,
    val option: HomeE2ChartWindowOption,
    val windowDate: LocalDate,
    val zoneId: String,
)

/** Exactly what [E2Derivation.deriveWidgetEstimate] reads: logged doses only, a fixed window. */
data class WidgetEstimateKey(
    val engine: Int,
    val fitKey: Digest?,
    val weightKg: Double,
    val logged: Digest,
    val windowDate: LocalDate,
    val zoneId: String,
)

/**
 * Exactly what [E2Derivation.deriveCalibrationRender] reads: logged doses
 * only; [windowDate] places the domain and [option] the status range.
 */
data class CalibrationRenderKey(
    val engine: Int,
    val fitKey: Digest?,
    val weightKg: Double,
    val logged: Digest,
    val option: HomeE2ChartWindowOption,
    val windowDate: LocalDate,
    val zoneId: String,
)

/**
 * The E2 estimates as pure functions of [E2Inputs] and the clock. A
 * derivation reads the clock only through its key's planned slots and window
 * date, so two derivations with the same key are identical.
 */
object E2Derivation {
    /**
     * Logged plus planned doses over the option's window. A thrown band
     * render goes to [onBandFailure] and leaves the curve without its band,
     * the status naming the supported routes.
     */
    fun deriveHomeEstimate(
        inputs: E2Inputs,
        now: LocalDateTime,
        zoneId: ZoneId,
        onBandFailure: (Throwable) -> Unit,
    ): HomeE2Estimate {
        val logged = loggedSince(inputs, chartStart(now, zoneId, inputs.option))
        val planned = plannedEntries(inputs, now, zoneId)
        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = logged,
            plannedEntries = planned,
            bodyWeightKg = inputs.weightKg,
            windowDate = now.toLocalDate(),
            zoneId = zoneId,
            option = inputs.option,
            personalParams = displayParams(inputs.fit),
        )
        val solved = inputs.fit?.outcome as? FitOutcome.Solved
            ?: return HomeE2Estimate(inputs.option, projection, bandKnots = emptyList(), calibration = null)
        // The band follows the same projected curve as the chart: the same
        // logged plus planned doses, over the projection window. The fit
        // itself used the full dose history.
        val render = runCatching {
            val windowStartMillis = projection.windowStart.toEpochMilli()
            val windowEndMillis = projection.windowEnd.toEpochMilli()
            // Sample the band exactly where the curve is sampled (dense around
            // doses), so its edges follow the peaks instead of cutting chords
            // across them.
            PkChartDomain.create(
                rangeStartEpochMillis = windowStartMillis,
                rangeEndEpochMillis = windowEndMillis,
                samplingIntervalMillis = BAND_SAMPLING_INTERVAL_MILLIS,
                protectedKnotEpochMillis = projection.timeH
                    .map { hours -> windowStartMillis + (hours * 3_600_000.0).roundToLong() }
                    .filter { millis -> millis in windowStartMillis..windowEndMillis },
            )?.let { domain ->
                renderFit(
                    solved, inputs, domain, logged, planned,
                    statusRange = HomePkChartWindow.forNow(now, zoneId, inputs.option).range,
                )
            }
        }.onFailure(onBandFailure).getOrNull()
        return HomeE2Estimate(
            option = inputs.option,
            projection = projection,
            bandKnots = render
                ?.takeIf { it.bandState == PkCalibrationBandState.READY }
                ?.bandKnots
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
                .orEmpty(),
            calibration = pkCalibrationStatus(solved.result, render),
        )
    }

    /**
     * Logged doses only, seven days whatever Home's option: a planned dose
     * 30 min from now must not inflate the widget's "current" reading.
     */
    fun deriveWidgetEstimate(
        inputs: E2Inputs,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): PkProjectionResult = PkMedicationSimulation.simulateMainEstradiolProjection(
        entries = loggedSince(inputs, chartStart(now, zoneId, HomeE2ChartWindowOption.SEVEN_DAYS)),
        bodyWeightKg = inputs.weightKg,
        windowDate = now.toLocalDate(),
        zoneId = zoneId,
        option = HomeE2ChartWindowOption.SEVEN_DAYS,
        personalParams = displayParams(inputs.fit),
    )

    /**
     * The calibration page's render: logged doses only, over a domain
     * anchored to the local date. Its status range is Home's visible window,
     * so the page and Home draw the same band where they overlap. Null
     * without a solved, READY fit.
     */
    fun deriveCalibrationRender(
        inputs: E2Inputs,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): PkCalibrationRenderResult? {
        val solved = inputs.fit?.outcome as? FitOutcome.Solved ?: return null
        val domainStart = calibrationDomainStart(now, zoneId)
        val domain = PkChartDomain.create(
            rangeStartEpochMillis = domainStart.toEpochMilli(),
            rangeEndEpochMillis = now.toLocalDate().plusDays(CALIBRATION_DOMAIN_FUTURE_DAYS)
                .atStartOfDay(zoneId).toInstant().toEpochMilli(),
            samplingIntervalMillis = BAND_SAMPLING_INTERVAL_MILLIS,
        ) ?: return null
        return renderFit(
            solved, inputs, domain,
            logged = loggedSince(inputs, domainStart),
            planned = emptyList(),
            statusRange = HomePkChartWindow.forNow(now, zoneId, inputs.option).range,
        )
    }

    fun homeEstimateKey(inputs: E2Inputs, now: LocalDateTime, zoneId: ZoneId): HomeEstimateKey {
        return HomeEstimateKey(
            engine = PK_ENGINE_VERSION,
            fitKey = inputs.fit?.let(::fitKey),
            weightKg = PkMedicationSimulation.resolvedBodyWeightKg(inputs.weightKg),
            logged = loggedDigest(inputs, chartStart(now, zoneId, inputs.option)),
            planned = doseEventsDigest(plannedEntries(inputs, now, zoneId), isPlanned = true),
            option = inputs.option,
            windowDate = now.toLocalDate(),
            zoneId = zoneId.id,
        )
    }

    fun widgetEstimateKey(inputs: E2Inputs, now: LocalDateTime, zoneId: ZoneId): WidgetEstimateKey {
        return WidgetEstimateKey(
            engine = PK_ENGINE_VERSION,
            fitKey = inputs.fit?.let(::fitKey),
            weightKg = PkMedicationSimulation.resolvedBodyWeightKg(inputs.weightKg),
            logged = loggedDigest(inputs, chartStart(now, zoneId, HomeE2ChartWindowOption.SEVEN_DAYS)),
            windowDate = now.toLocalDate(),
            zoneId = zoneId.id,
        )
    }

    fun calibrationRenderKey(inputs: E2Inputs, now: LocalDateTime, zoneId: ZoneId): CalibrationRenderKey {
        return CalibrationRenderKey(
            engine = PK_ENGINE_VERSION,
            fitKey = inputs.fit?.let(::fitKey),
            weightKg = PkMedicationSimulation.resolvedBodyWeightKg(inputs.weightKg),
            logged = loggedDigest(inputs, calibrationDomainStart(now, zoneId)),
            option = inputs.option,
            windowDate = now.toLocalDate(),
            zoneId = zoneId.id,
        )
    }

    /**
     * The earliest logged dose any derivation at [now] reads, whatever the
     * option: the PK lookback before the calibration page's domain, the
     * widest window. Entries read from here serve every option and the page.
     */
    fun loggedEntriesStart(now: LocalDateTime, zoneId: ZoneId): Instant =
        calibrationDomainStart(now, zoneId).minus(PK_CALIBRATION_DOSE_LOOKBACK)

    /**
     * The slots after [now], through the option's projection horizon, that
     * Home's curve assumes are taken. Every logged entry counts toward
     * filling one; the planned digest covers the result.
     */
    fun plannedEntries(inputs: E2Inputs, now: LocalDateTime, zoneId: ZoneId): List<MedicationLogEntry> =
        buildEstradiolPkSimulationEntries(
            realEntries = inputs.estradiolEntries,
            activeGroups = inputs.plannedSources,
            now = now,
            horizon = now.toLocalDate().plusDays(inputs.option.projectionFutureDays()).atStartOfDay(),
            zoneId = zoneId,
        ).planned

    /** The parameters every estimate is simulated with: the fit's, or population without a solved one. */
    fun displayParams(fit: E2FitRecord?): PkPersonalParams =
        (fit?.outcome as? FitOutcome.Solved)?.result?.displayParams ?: PkPersonalParams.population()

    /**
     * Hero/status summary of [result] as [render] draws it. The render
     * narrows the supported routes to those shaping the estimate somewhere in
     * the status range. When it is null (it failed, threw, or had no domain)
     * the curve still carries displayParams, so the supported routes are
     * named rather than calling a personalized curve "population".
     */
    fun pkCalibrationStatus(
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
            // READY is what an evaluation's isReady was: the engine attaches
            // evidence exactly when the solver ran, and it always returns READY.
            renderUnavailable = result.globalState == PkCalibrationGlobalState.READY &&
                    render?.renderState == PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
            // Any adjusted curve without a READY band gets the note: a
            // numeric failure, a thrown render, or an unbuildable domain.
            bandUnavailable = effective.isNotEmpty() &&
                    render?.bandState != PkCalibrationBandState.READY,
            effectiveRoutes = effective.map(PkCalibrationRoute::stableId),
        )
    }

    /**
     * What the solve reads (labs, dose events in absolute time, review
     * dispositions, weight, config) and [PK_ENGINE_VERSION]. Not the origin,
     * which only places the events, nor what the math ignores: a medicine's
     * stock or name, a review's timestamp.
     */
    fun fitId(input: PkCalibrationInput): Digest = Digest.of(HomeSnapshotCodec.encodeFitInput(input))

    /** The whole fit record: a new outcome or epoch changes every key built on it. */
    fun fitKey(fit: E2FitRecord): Digest =
        fitKeys.memo({ (record, _) -> record === fit }) { fit to Digest.of(HomeSnapshotCodec.encodeFit(fit)) }.second

    // Every key is rebuilt each minute, from the inputs instance a run
    // published: its fit and logged entries stay the same objects until the
    // next publish, and the windows move only with the date, so their
    // digests are reused rather than re-encoded on the observer's thread.
    // ponytail: four slots each, matched by identity: Home, the calibration
    // page and the widget each read one window. Widen if more observers show up.
    private val fitKeys = ArrayDeque<Pair<E2FitRecord, Digest>>()
    private val loggedDigests = ArrayDeque<Triple<List<MedicationLogEntry>, Instant, Digest>>()

    private fun loggedDigest(inputs: E2Inputs, windowStart: Instant): Digest {
        val entries = inputs.estradiolEntries
        return loggedDigests.memo({ (list, start) -> list === entries && start == windowStart }) {
            Triple(entries, windowStart, doseEventsDigest(loggedSince(inputs, windowStart), isPlanned = false))
        }.third
    }

    /** The slot [matches] finds, or [compute]'s, kept as the newest. Computed outside the lock. */
    private fun <T> ArrayDeque<T>.memo(matches: (T) -> Boolean, compute: () -> T): T =
        synchronized(this) { firstOrNull(matches) }
            ?: compute().also { slot ->
                synchronized(this) {
                    addFirst(slot)
                    if (size > MEMO_SLOTS) removeLast()
                }
            }

    private fun renderFit(
        solved: FitOutcome.Solved,
        inputs: E2Inputs,
        domain: PkChartDomain,
        logged: List<MedicationLogEntry>,
        planned: List<MedicationLogEntry>,
        statusRange: LongRange,
    ): PkCalibrationRenderResult? = PkCalibrationRenderer.render(
        result = solved.result,
        originEpochMillis = solved.originEpochMillis,
        weightKg = PkMedicationSimulation.resolvedBodyWeightKg(inputs.weightKg),
        // Every fit is solved with the default; changing it bumps PK_ENGINE_VERSION.
        config = PkCalibrationConfig.Default,
        domain = domain,
        doseEvents = buildEstradiolPkDoseEvents(
            anchor = Instant.ofEpochMilli(solved.originEpochMillis),
            realEntries = logged,
            plannedEntries = planned,
        ),
        statusRange = statusRange,
    )

    /**
     * The logged doses a curve whose window starts at [windowStart] reads:
     * from [PK_CALIBRATION_DOSE_LOOKBACK] before it, which covers steady state
     * for every route. A derivation and its key both read through this, so
     * older entries change neither, however far back the inputs reach.
     */
    private fun loggedSince(inputs: E2Inputs, windowStart: Instant): List<MedicationLogEntry> {
        val lookbackStart = windowStart.minus(PK_CALIBRATION_DOSE_LOOKBACK)
        return inputs.estradiolEntries.filter { entry -> !entry.appliedAt.isBefore(lookbackStart) }
    }

    private fun chartStart(now: LocalDateTime, zoneId: ZoneId, option: HomeE2ChartWindowOption): Instant =
        Instant.ofEpochMilli(HomePkChartWindow.forNow(now, zoneId, option).windowStartEpochMillis)

    private fun calibrationDomainStart(now: LocalDateTime, zoneId: ZoneId): Instant =
        now.toLocalDate().minusDays(CALIBRATION_DOMAIN_PAST_DAYS).atStartOfDay(zoneId).toInstant()

    private fun doseEventsDigest(entries: List<MedicationLogEntry>, isPlanned: Boolean): Digest =
        Digest.of(
            HomeSnapshotCodec.encodeDoseEvents(
                events = entries.mapNotNull { entry -> entry.buildEstradiolPkDoseEvent(Instant.EPOCH, isPlanned) },
                anchorEpochMillis = 0L,
            )
        )
}

// Visible chart window for the selected option: the range the calibration
// status describes, narrower than the projection window, which runs
// MainChartProjectionFutureBufferDays past it. Reads only the date of `now`.
internal data class HomePkChartWindow(
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
) {
    val range: LongRange get() = windowStartEpochMillis..windowEndEpochMillis

    companion object {
        fun forNow(
            now: LocalDateTime,
            zoneId: ZoneId,
            option: HomeE2ChartWindowOption,
        ): HomePkChartWindow {
            val windowStart = now
                .toLocalDate()
                .atStartOfDay()
                .minusDays(option.pastDays)
                .atZone(zoneId)
                .toInstant()
            val windowEnd = windowStart.plus(
                Duration.ofHours(option.chartWindowHours)
            )
            return HomePkChartWindow(
                windowStartEpochMillis = windowStart.toEpochMilli(),
                windowEndEpochMillis = windowEnd.toEpochMilli(),
            )
        }
    }
}

private const val MEMO_SLOTS = 4

private const val BAND_SAMPLING_INTERVAL_MILLIS = 6L * 60L * 60L * 1_000L

// The calibration page's domain: every option's visible window, plus the day
// of slack the page's clock-anchored domain kept before it.
private val CALIBRATION_DOMAIN_PAST_DAYS = HomeE2ChartWindowOption.entries.maxOf { it.pastDays } + 1L
private val CALIBRATION_DOMAIN_FUTURE_DAYS = HomeE2ChartWindowOption.entries.maxOf { it.futureDays }
