package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToLong

enum class PkHormone {
    ESTRADIOL,
    TESTOSTERONE,
}

enum class PkRoute {
    INJECTION,
    PATCH_APPLY,
    PATCH_REMOVE,
    GEL,
    ORAL,
    SUBLINGUAL,
}

enum class PkCompound {
    E2,
    EB,
    EV,
    EC,
    EN,
    EU,
    T,
    TC,
    TE,
    TU,
}

enum class PkConcentrationUnit(val symbol: String) {
    PG_PER_ML("pg/mL"),
    PMOL_PER_L("pmol/L"),
    NG_PER_DL("ng/dL"),
    NG_PER_ML("ng/mL"),
    NMOL_PER_L("nmol/L");

    fun concentrationScale(hormone: PkHormone): Double {
        return when (this) {
            PG_PER_ML -> 1e9
            PMOL_PER_L -> 1e12 / PkCatalog.molecularWeight(hormone)
            NG_PER_DL -> 1e8
            NG_PER_ML -> 1e6
            NMOL_PER_L -> 1e9 / PkCatalog.molecularWeight(hormone)
        }
    }
}

data class PkDoseEvent(
    val id: UUID,
    val sourceGroupUuid: UUID?,
    val hormone: PkHormone,
    val route: PkRoute,
    val timeH: Double,
    val doseMg: Double,
    val compound: PkCompound,
    val releaseRateMcgPerDay: Double? = null,
    val areaCm2: Double? = null,
    val sublingualTheta: Double? = null,
    // True when this event came from a synthesized future-schedule slot rather
    // than a user-logged entry. Drives the dose marker color in the UI.
    val isPlanned: Boolean = false,
)

data class PkParams(
    val fracFast: Double,
    val k1Fast: Double,
    val k1Slow: Double,
    val k2: Double,
    val k3: Double,
    val bioavailability: Double,
    val rateMgH: Double,
    val fastBioavailability: Double,
    val slowBioavailability: Double,
)

data class PkSimulationMetadata(
    val hormone: PkHormone,
    val concentrationUnit: PkConcentrationUnit,
)

data class PkSimulationResult(
    val timeH: List<Double>,
    val concentrations: List<Double>,
    val auc: Double,
    val metadata: PkSimulationMetadata,
) {
    fun concentrationAt(hour: Double): Double? {
        if (timeH.isEmpty() || timeH.size != concentrations.size) {
            return null
        }
        if (hour <= timeH.first()) {
            return concentrations.first()
        }
        if (hour >= timeH.last()) {
            return concentrations.last()
        }

        var low = 0
        var high = timeH.lastIndex
        while (high - low > 1) {
            val mid = (low + high) / 2
            when {
                timeH[mid] == hour -> return concentrations[mid]
                timeH[mid] < hour -> low = mid
                else -> high = mid
            }
        }

        val t0 = timeH[low]
        val t1 = timeH[high]
        val c0 = concentrations[low]
        val c1 = concentrations[high]
        if (t1 <= t0) {
            return c0
        }
        val ratio = (hour - t0) / (t1 - t0)
        return c0 + (c1 - c0) * ratio
    }
}

data class PkDoseMarker(
    val timeH: Double,
    val concentration: Double,
    val isPlanned: Boolean = false,
)

data class PkTrendResult(
    val currentConcentration: Double,
    val previousDayConcentration: Double,
    val dailyConcentrations: List<Double>,
    val concentrationUnit: PkConcentrationUnit,
    val chartConcentrations: List<Double> = dailyConcentrations,
    val chartSampleIntervalHours: Int = 24,
    val chartTimeH: List<Double> = chartConcentrations.indices.map { index ->
        index * chartSampleIntervalHours.toDouble()
    },
    val doseMarkers: List<PkDoseMarker> = emptyList(),
    val chartWindowHours: Int = chartSampleIntervalHours * (chartConcentrations.size - 1).coerceAtLeast(
        0
    ),
    val predictionStartTimeH: Double = chartWindowHours.toDouble(),
) {
    val changeSincePreviousDay: Double
        get() = currentConcentration - previousDayConcentration
}

data class PkProjectionResult(
    val windowStart: Instant,
    val windowEnd: Instant,
    val concentrationUnit: PkConcentrationUnit,
    val timeH: List<Double>,
    val concentrations: List<Double>,
    val doseMarkers: List<PkDoseMarker>,
) {
    fun toMainEstradiolTrend(
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
        option: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    ): PkTrendResult? {
        if (timeH.isEmpty() || timeH.size != concentrations.size) {
            return null
        }

        val chartWindowStart = now
            .toLocalDate()
            .atStartOfDay()
            .minusDays(option.pastDays)
            .atZone(zoneId)
            .toInstant()
        val chartWindowEnd = chartWindowStart.plus(
            Duration.ofHours(option.chartWindowHours)
        )
        if (chartWindowStart.isBefore(windowStart) || chartWindowEnd.isAfter(windowEnd)) {
            return null
        }

        val chartWindowStartH = hoursFromWindowStart(chartWindowStart)
        val chartVisibleEndH = chartWindowStartH + pkChartVisibleEndHours(
            option.chartWindowHours.toDouble()
        )
        val predictionStartTimeH = hoursBetween(chartWindowStart, now.atZone(zoneId).toInstant())
            .toProjectionChartXHour()
        val currentTimeH = chartWindowStartH + predictionStartTimeH
        val previousDayTimeH = currentTimeH - PkMedicationSimulation.hoursPerDay

        val chartTimeH = (
                timeH.filter { time -> time in chartWindowStartH..chartVisibleEndH } +
                        listOf(chartWindowStartH, chartVisibleEndH, currentTimeH, previousDayTimeH)
                )
            .filter { time -> time.isFinite() && time in chartWindowStartH..chartVisibleEndH }
            .map { time -> time.toProjectionChartXHour() }
            .distinct()
            .sorted()
        val chartConcentrations = chartTimeH.map { time -> concentrationAt(time) ?: 0.0 }
        val dailyConcentrations = (0..option.chartWindowDays.toInt()).map { index ->
            concentrationAt(chartWindowStartH + index * PkMedicationSimulation.hoursPerDay) ?: 0.0
        }

        return PkTrendResult(
            currentConcentration = concentrationAt(currentTimeH) ?: 0.0,
            previousDayConcentration = concentrationAt(previousDayTimeH) ?: 0.0,
            dailyConcentrations = dailyConcentrations,
            concentrationUnit = concentrationUnit,
            chartConcentrations = chartConcentrations,
            chartSampleIntervalHours = PkMedicationSimulation.mainChartSampleIntervalHours,
            chartTimeH = chartTimeH.map { time -> (time - chartWindowStartH).toProjectionChartXHour() },
            doseMarkers = doseMarkers
                .filter { marker -> marker.timeH in chartWindowStartH..chartVisibleEndH }
                .map { marker ->
                    marker.copy(timeH = (marker.timeH - chartWindowStartH).toProjectionChartXHour())
                },
            chartWindowHours = option.chartWindowHours.toInt(),
            predictionStartTimeH = predictionStartTimeH,
        )
    }

    private fun hoursFromWindowStart(instant: Instant): Double {
        return hoursBetween(windowStart, instant)
    }

    private fun hoursBetween(start: Instant, end: Instant): Double {
        return Duration.between(start, end).toMillis() / 3_600_000.0
    }

    private fun concentrationAt(hour: Double): Double? {
        if (timeH.isEmpty() || timeH.size != concentrations.size) {
            return null
        }
        if (hour <= timeH.first()) {
            return concentrations.first()
        }
        if (hour >= timeH.last()) {
            return concentrations.last()
        }

        var low = 0
        var high = timeH.lastIndex
        while (high - low > 1) {
            val mid = (low + high) / 2
            when {
                timeH[mid] == hour -> return concentrations[mid]
                timeH[mid] < hour -> low = mid
                else -> high = mid
            }
        }

        val t0 = timeH[low]
        val t1 = timeH[high]
        val c0 = concentrations[low]
        val c1 = concentrations[high]
        if (t1 <= t0) {
            return c0
        }
        val ratio = (hour - t0) / (t1 - t0)
        return c0 + (c1 - c0) * ratio
    }
}

private fun Double.toProjectionChartXHour(): Double {
    return (this * PkProjectionChartXPrecisionScale).roundToLong() / PkProjectionChartXPrecisionScale
}

private fun pkChartVisibleEndHours(windowEndHours: Double): Double {
    // The 7-day chart's midnight end is exclusive; render the last minute
    // inside the window so the UI doesn't expose the following day at 00:00.
    return (windowEndHours - PkChartEndInsetHours).coerceAtLeast(0.0).toProjectionChartXHour()
}

private const val PkProjectionChartXPrecisionScale = 10_000.0
private const val PkChartEndInsetHours = 1.0 / 60.0

// Post-dose offsets injected by the sampler when the selected
// HomeE2ChartWindowOption sets includesPostDoseOffsets = true. At the budget
// sampler's grid spacing (~26 min for THIRTY_DAYS) the absorption-phase Cmax
// of fast-Tmax routes (oral, sublingual) lands between grid samples; without
// these offsets the chart polyline would underplot the peak.
private val MainChartPostDoseSampleOffsetsHours =
    listOf(0.25, 0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 12.0, 24.0, 48.0)

object PkMedicationSimulation {
    private const val HoursPerDay = 24.0
    private const val MainChartWindowDays = 7L
    private const val MainChartPastDays = 3L
    private const val MainChartSampleIntervalHours = 1
    private const val MainChartWindowHours = MainChartWindowDays * 24
    private const val MainChartFallbackSteps = MainChartWindowHours + 1
    private const val MainChartDenseSampleIntervalHours = 0.1
    /** App-wide fallback when Current Weight is unset; calibration uses it too. */
    const val DefaultBodyWeightKg = 70.0

    /** Current Weight as the PK math reads it: unset or invalid is [DefaultBodyWeightKg]. */
    internal fun resolvedBodyWeightKg(bodyWeightKg: Double?): Double =
        bodyWeightKg?.takeIf { it.isFinite() && it > 0 } ?: DefaultBodyWeightKg
    private const val ChartXPrecisionScale = 10_000.0

    internal const val hoursPerDay = HoursPerDay
    internal const val mainChartWindowDays = MainChartWindowDays
    internal const val mainChartPastDays = MainChartPastDays
    internal const val mainChartSampleIntervalHours = MainChartSampleIntervalHours
    internal const val mainChartWindowHours = MainChartWindowHours

    fun simulateMainEstradiolTrend(
        entries: List<MedicationLogEntry>,
        bodyWeightKg: Double?,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
        plannedEntries: List<MedicationLogEntry> = emptyList(),
        option: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        personalParams: PkPersonalParams = PkPersonalParams.population(),
    ): PkTrendResult? {
        val nowInstant = now.atZone(zoneId).toInstant()
        val startInstant = now
            .toLocalDate()
            .atStartOfDay()
            .minusDays(option.pastDays)
            .atZone(zoneId)
            .toInstant()
        val predictionStartTimeH = Duration.between(startInstant, nowInstant)
            .toMillis() / 3_600_000.0
        val events = buildEstradiolPkDoseEvents(
            anchor = startInstant,
            realEntries = entries,
            plannedEntries = plannedEntries,
        )
        val windowEndHours = option.chartWindowHours.toDouble()
        val chartVisibleEndHours = pkChartVisibleEndHours(windowEndHours)
        val chartSampleTimeH = mainChartSampleTimeH(
            events = events,
            predictionStartTimeH = predictionStartTimeH,
            windowEndHours = windowEndHours,
            chartVisibleEndHours = chartVisibleEndHours,
            option = option,
        )

        val result = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = resolvedBodyWeightKg(bodyWeightKg),
            startTimeH = 0.0,
            endTimeH = windowEndHours,
            numberOfSteps = (option.chartWindowHours.toInt() + 1).coerceAtLeast(2),
            personalParams = personalParams,
        ).run(sampleTimeH = chartSampleTimeH)

        val dailyConcentrations = (0..option.chartWindowDays.toInt()).map { index ->
            result.concentrationAt(index * HoursPerDay) ?: 0.0
        }
        val chartSamples = result.timeH
            .zip(result.concentrations)
            .filter { (timeH, _) -> timeH in 0.0..chartVisibleEndHours }
        val doseMarkers = buildDoseMarkers(
            events = events,
            windowEndHours = chartVisibleEndHours,
            concentrationAt = result::concentrationAt,
        )
        return PkTrendResult(
            currentConcentration = result.concentrationAt(predictionStartTimeH) ?: 0.0,
            previousDayConcentration = result.concentrationAt(predictionStartTimeH - HoursPerDay)
                ?: 0.0,
            dailyConcentrations = dailyConcentrations,
            concentrationUnit = result.metadata.concentrationUnit,
            chartConcentrations = chartSamples.map { (_, concentration) -> concentration },
            chartSampleIntervalHours = MainChartSampleIntervalHours,
            chartTimeH = chartSamples.map { (timeH, _) -> timeH },
            doseMarkers = doseMarkers,
            chartWindowHours = option.chartWindowHours.toInt(),
            predictionStartTimeH = predictionStartTimeH.toChartXHour(),
        )
    }

    // Returned `windowStart` and `windowEnd` MUST equal
    // `windowDate.atStartOfDay().minusDays(mainChartPastDays)` and
    // `windowDate.plusDays(futureDays).atStartOfDay()` (in `zoneId`).
    // `PkProjectionResult.toMainEstradiolTrend` recomputes those bounds
    // independently and slices the chart only out of a projection that covers
    // it (a held estimate relies on that across midnight) — so changing this
    // rounding silently blanks Home's chart.
    fun simulateMainEstradiolProjection(
        entries: List<MedicationLogEntry>,
        bodyWeightKg: Double?,
        windowDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        plannedEntries: List<MedicationLogEntry> = emptyList(),
        option: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        futureDays: Long = option.projectionFutureDays(),
        personalParams: PkPersonalParams = PkPersonalParams.population(),
    ): PkProjectionResult {
        val windowStart = windowDate
            .atStartOfDay()
            .minusDays(option.pastDays)
            .atZone(zoneId)
            .toInstant()
        val windowEnd = windowDate
            .plusDays(futureDays)
            .atStartOfDay()
            .atZone(zoneId)
            .toInstant()
        val windowHours = Duration.between(windowStart, windowEnd)
            .toMillis() / 3_600_000.0
        val events = buildEstradiolPkDoseEvents(
            anchor = windowStart,
            realEntries = entries,
            plannedEntries = plannedEntries,
        )
        val sampleTimeH = mainProjectionSampleTimeH(
            events = events,
            windowEndHours = windowHours,
            option = option,
        )
        val result = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = resolvedBodyWeightKg(bodyWeightKg),
            startTimeH = 0.0,
            endTimeH = windowHours,
            numberOfSteps = ceil(windowHours).toInt().coerceAtLeast(1) + 1,
            personalParams = personalParams,
        ).run(sampleTimeH = sampleTimeH)
        val doseMarkers = buildDoseMarkers(
            events = events,
            windowEndHours = windowHours,
            concentrationAt = result::concentrationAt,
        )

        return PkProjectionResult(
            windowStart = windowStart,
            windowEnd = windowEnd,
            concentrationUnit = result.metadata.concentrationUnit,
            timeH = result.timeH,
            concentrations = result.concentrations,
            doseMarkers = doseMarkers,
        )
    }

    private fun Double.toChartXHour(): Double {
        return (this * ChartXPrecisionScale).roundToLong() / ChartXPrecisionScale
    }

    // Groups dose-marker-candidate events by their chart-rounded time. A marker
    // at a given instant is treated as "planned" only when every event at that
    // instant is itself planned — so a real entry that fulfills a planned slot
    // flips the marker color back to "logged".
    private fun buildDoseMarkers(
        events: List<PkDoseEvent>,
        windowEndHours: Double,
        concentrationAt: (Double) -> Double?,
    ): List<PkDoseMarker> {
        return events
            .asSequence()
            .filter(PkDoseEvent::isDoseMarkerCandidate)
            .mapNotNull { event ->
                val time = event.timeH.toChartXHour()
                if (time.isFinite() && time in 0.0..windowEndHours) time to event else null
            }
            .groupBy({ (time, _) -> time }, { (_, event) -> event })
            .toSortedMap()
            .mapNotNull { (time, eventsAtTime) ->
                concentrationAt(time)?.let { concentration ->
                    PkDoseMarker(
                        timeH = time,
                        concentration = concentration,
                        isPlanned = eventsAtTime.all { it.isPlanned },
                    )
                }
            }
    }

    private fun mainChartSampleTimeH(
        events: List<PkDoseEvent>,
        predictionStartTimeH: Double,
        windowEndHours: Double,
        chartVisibleEndHours: Double,
        option: HomeE2ChartWindowOption,
    ): List<Double> {
        val windowStart = 0.0
        val previousDayTimeH = predictionStartTimeH - HoursPerDay
        val denseSampleTimeH = mainChartDenseSampleTimeH(
            startTimeH = windowStart,
            endTimeH = windowEndHours,
            policy = option.densePolicy,
        )
        val loggedEventTimeH = events
            .asSequence()
            .map { event -> event.timeH.toChartXHour() }
            .filter { timeH -> timeH.isFinite() && timeH in windowStart..windowEndHours }
            .toList()
        val offsetSampleTimeH = postDoseOffsetSampleTimeH(
            events = events,
            windowStart = windowStart,
            windowEndHours = windowEndHours,
            option = option,
        )
        val exactSampleTimeH = (
                listOf(predictionStartTimeH.toChartXHour()) +
                        listOf(chartVisibleEndHours.toChartXHour()) +
                        listOf(previousDayTimeH.toChartXHour()).filter { timeH ->
                            timeH.isFinite() && timeH in windowStart..windowEndHours
                        } +
                        loggedEventTimeH +
                        offsetSampleTimeH
                )
            .filter { timeH -> timeH.isFinite() && timeH in windowStart..windowEndHours }

        return (denseSampleTimeH + exactSampleTimeH)
            .map { timeH -> timeH.toChartXHour() }
            .distinct()
            .sorted()
    }

    // No exact samples at the current time and a day before, unlike the trend:
    // the grid depends only on the window, the events and the option. A
    // reading between samples interpolates (toMainEstradiolTrend).
    private fun mainProjectionSampleTimeH(
        events: List<PkDoseEvent>,
        windowEndHours: Double,
        option: HomeE2ChartWindowOption,
    ): List<Double> {
        val denseSampleTimeH = mainChartDenseSampleTimeH(
            startTimeH = 0.0,
            endTimeH = windowEndHours,
            policy = option.densePolicy,
        )
        val loggedEventTimeH = events
            .asSequence()
            .map { event -> event.timeH.toChartXHour() }
            .filter { timeH -> timeH.isFinite() && timeH in 0.0..windowEndHours }
            .toList()
        val offsetSampleTimeH = postDoseOffsetSampleTimeH(
            events = events,
            windowStart = 0.0,
            windowEndHours = windowEndHours,
            option = option,
        )

        return (denseSampleTimeH + loggedEventTimeH + offsetSampleTimeH)
            .map { timeH -> timeH.toChartXHour() }
            .distinct()
            .sorted()
    }

    private fun postDoseOffsetSampleTimeH(
        events: List<PkDoseEvent>,
        windowStart: Double,
        windowEndHours: Double,
        option: HomeE2ChartWindowOption,
    ): List<Double> {
        if (!option.includesPostDoseOffsets) return emptyList()
        return events
            .asSequence()
            .filter(PkDoseEvent::isDoseMarkerCandidate)
            .flatMap { event ->
                MainChartPostDoseSampleOffsetsHours.asSequence().map { offset ->
                    (event.timeH + offset).toChartXHour()
                }
            }
            .filter { timeH -> timeH.isFinite() && timeH in windowStart..windowEndHours }
            .toList()
    }

    private fun mainChartDenseSampleTimeH(
        startTimeH: Double,
        endTimeH: Double,
        policy: DenseSamplePolicy,
    ): List<Double> {
        val span = endTimeH - startTimeH
        if (span <= 0.0) {
            return emptyList()
        }
        return when (policy) {
            is DenseSamplePolicy.Interval -> {
                val segmentCount = ceil(span / policy.hours).toInt().coerceAtLeast(1)
                (0..segmentCount).map { index ->
                    (startTimeH + policy.hours * index)
                        .coerceAtMost(endTimeH)
                        .toChartXHour()
                }
            }

            is DenseSamplePolicy.Budget -> {
                val segmentCount = policy.segmentCount.coerceAtLeast(1)
                val interval = span / segmentCount
                (0..segmentCount).map { index ->
                    (startTimeH + interval * index)
                        .coerceAtMost(endTimeH)
                        .toChartXHour()
                }
            }
        }
    }
}

class PkSimulationEngine(
    events: List<PkDoseEvent>,
    private val hormone: PkHormone,
    private val bodyWeightKg: Double,
    private val startTimeH: Double,
    private val endTimeH: Double,
    private val numberOfSteps: Int,
    private val personalParams: PkPersonalParams = PkPersonalParams.population(),
) {
    private val sortedEvents = events.sortedBy(PkDoseEvent::timeH)
    private val eventModels = sortedEvents
        .asSequence()
        .filter { event -> event.hormone == hormone && event.route != PkRoute.PATCH_REMOVE }
        .map { event -> PkForwardEventModel(event, sortedEvents) }
        .toList()
    private val metadata = PkSimulationMetadata(
        hormone = hormone,
        concentrationUnit = PkCatalog.defaultConcentrationUnit(hormone),
    )
    private val plasmaVolumeMl = PkCatalog.coreParams(hormone).vdPerKg * bodyWeightKg * 1000.0

    fun run(sampleTimeH: List<Double>? = null): PkSimulationResult {
        if (startTimeH >= endTimeH || numberOfSteps <= 1 || plasmaVolumeMl <= 0.0) {
            return PkSimulationResult(
                timeH = emptyList(),
                concentrations = emptyList(),
                auc = 0.0,
                metadata = metadata,
            )
        }

        val sampleTimes = sampleTimeH
            ?.asSequence()
            ?.filter { timeH -> timeH.isFinite() && timeH in startTimeH..endTimeH }
            ?.distinct()
            ?.sorted()
            ?.toList()
            ?.takeIf { timeH -> timeH.size > 1 }
            ?: fixedSampleTimes()
        val concentrationScale =
            metadata.concentrationUnit.concentrationScale(hormone) / plasmaVolumeMl
        val time = ArrayList<Double>(sampleTimes.size)
        val concentrations = ArrayList<Double>(sampleTimes.size)
        var previousConcentration = 0.0
        var previousTimeH = startTimeH
        var auc = 0.0

        sampleTimes.forEachIndexed { index, t ->
            var totalAmountMg = 0.0
            if (personalParams.routeLogScale.isEmpty() || hormone != PkHormone.ESTRADIOL) {
                // Keep the population path byte-for-byte equivalent to the pre-calibration loop.
                for (model in eventModels) {
                    totalAmountMg += model.amountAt(t)
                }
            } else {
                for (model in eventModels) {
                    val amountMg = model.amountAt(t)
                    val route = model.event.calibrationRoute()
                    val beta = route?.let(personalParams.routeLogScale::get)
                    totalAmountMg += if (route == null || beta == null) {
                        amountMg
                    } else {
                        amountMg * personalParams.scaleFor(route)
                    }
                }
            }
            val concentration = totalAmountMg * concentrationScale

            time += t
            concentrations += concentration
            if (index > 0) {
                auc += 0.5 * (concentration + previousConcentration) * (t - previousTimeH)
            }
            previousConcentration = concentration
            previousTimeH = t
        }

        return PkSimulationResult(
            timeH = time,
            concentrations = concentrations,
            auc = auc,
            metadata = metadata,
        )
    }

    private fun fixedSampleTimes(): List<Double> {
        val stepSize = (endTimeH - startTimeH) / (numberOfSteps - 1)
        return List(numberOfSteps) { index -> startTimeH + index * stepSize }
    }
}

internal class PkForwardEventModel(
    val event: PkDoseEvent,
    allEvents: List<PkDoseEvent>,
) {
    private val startTime = event.timeH
    private val dose = event.doseMg
    private val params = PkParameterResolver.resolve(event)
    private val oneCompParams = PkParams(
        fracFast = 1.0,
        k1Fast = params.k1Fast,
        k1Slow = 0.0,
        k2 = params.k2,
        k3 = params.k3,
        bioavailability = params.bioavailability,
        rateMgH = params.rateMgH,
        fastBioavailability = params.bioavailability,
        slowBioavailability = params.bioavailability,
    )

    private val amountAt: (Double) -> Double = when (event.route) {
        PkRoute.INJECTION -> { timeH ->
            val tau = timeH - startTime
            if (tau < 0.0) 0.0 else ThreeCompartmentModel.injectionAmount(tau, dose, params)
        }

        PkRoute.GEL,
        PkRoute.ORAL -> { timeH ->
            val tau = timeH - startTime
            if (tau < 0.0) 0.0 else ThreeCompartmentModel.oneCompartmentAmount(
                tau,
                dose,
                oneCompParams
            )
        }

        PkRoute.SUBLINGUAL -> { timeH ->
            val tau = timeH - startTime
            if (tau < 0.0) 0.0 else ThreeCompartmentModel.dualAbsorptionAmount(
                tau,
                dose,
                params,
                fastKe = PkCatalog.kElimSublingual,
            )
        }

        PkRoute.PATCH_APPLY -> {
            // PATCH_OFF means "remove all currently-on patches of this compound",
            // independent of the group that scheduled the apply. The strict `>`
            // filter ensures a remove logged at the same instant as an apply is
            // ordered before the apply — it pairs with the previous patch, not
            // the one being applied right now.
            val removeEvent = allEvents
                .asSequence()
                .filter { candidate ->
                    candidate.hormone == event.hormone &&
                            candidate.compound == event.compound &&
                            candidate.route == PkRoute.PATCH_REMOVE &&
                            candidate.timeH > startTime
                }
                .minByOrNull(PkDoseEvent::timeH)
            val wearH = (removeEvent?.timeH ?: Double.POSITIVE_INFINITY) - startTime
            { timeH ->
                val tau = timeH - startTime
                if (tau < 0.0) {
                    0.0
                } else {
                    ThreeCompartmentModel.patchAmount(tau, dose, wearH, params)
                }
            }
        }

        PkRoute.PATCH_REMOVE -> { _ -> 0.0 }
    }

    fun amountAt(timeH: Double): Double = amountAt.invoke(timeH)
}

private object PkParameterResolver {
    fun resolve(event: PkDoseEvent): PkParams {
        val core = PkCatalog.coreParams(event.hormone)
        val k3 = if (event.route == PkRoute.INJECTION && event.compound != PkCompound.EU) {
            core.kClearInjection
        } else {
            core.kClear
        }

        return when (event.route) {
            PkRoute.INJECTION -> {
                val depot = PkCatalog.twoPartDepotParams(event.compound)
                val k1Fast = (depot?.k1Fast ?: 0.0) * core.depotK1Correction
                val k1Slow = (depot?.k1Slow ?: 0.0) * core.depotK1Correction
                val formationFraction = PkCatalog.formationFraction(event.compound) ?: 1.0
                PkParams(
                    fracFast = depot?.fracFast ?: 1.0,
                    k1Fast = k1Fast,
                    k1Slow = k1Slow,
                    k2 = PkCatalog.hydrolysisK2(event.compound) ?: 0.0,
                    k3 = k3,
                    bioavailability = formationFraction,
                    rateMgH = 0.0,
                    fastBioavailability = formationFraction,
                    slowBioavailability = formationFraction,
                )
            }

            PkRoute.PATCH_APPLY -> {
                val releaseRateMcgPerDay = event.releaseRateMcgPerDay
                if (releaseRateMcgPerDay != null) {
                    PkParams(
                        fracFast = 1.0,
                        k1Fast = 0.0,
                        k1Slow = 0.0,
                        k2 = 0.0,
                        k3 = k3,
                        bioavailability = 1.0,
                        rateMgH = releaseRateMcgPerDay / 24_000.0,
                        fastBioavailability = 1.0,
                        slowBioavailability = 1.0,
                    )
                } else {
                    PkParams(
                        fracFast = 1.0,
                        k1Fast = core.patchFallbackK1,
                        k1Slow = 0.0,
                        k2 = 0.0,
                        k3 = k3,
                        bioavailability = 1.0,
                        rateMgH = 0.0,
                        fastBioavailability = 1.0,
                        slowBioavailability = 1.0,
                    )
                }
            }

            PkRoute.GEL -> {
                PkParams(
                    fracFast = 1.0,
                    k1Fast = core.gelK1,
                    k1Slow = 0.0,
                    k2 = 0.0,
                    k3 = k3,
                    bioavailability = core.gelFMax,
                    rateMgH = 0.0,
                    fastBioavailability = core.gelFMax,
                    slowBioavailability = core.gelFMax,
                )
            }

            PkRoute.ORAL -> {
                val bioavailability = PkCatalog.oralBioavailability(event.compound) ?: 0.0
                PkParams(
                    fracFast = 1.0,
                    k1Fast = PkCatalog.oralKAbs(event.compound) ?: 0.0,
                    k1Slow = 0.0,
                    k2 = 0.0,
                    k3 = k3,
                    bioavailability = bioavailability,
                    rateMgH = 0.0,
                    fastBioavailability = bioavailability,
                    slowBioavailability = bioavailability,
                )
            }

            PkRoute.SUBLINGUAL -> {
                if (event.hormone != PkHormone.ESTRADIOL) {
                    PkParams(0.0, 0.0, 0.0, 0.0, k3, 0.0, 0.0, 0.0, 0.0)
                } else {
                    val theta = event.sublingualTheta?.coerceIn(0.0, 1.0)
                        ?: PkCatalog.standardSublingualTheta
                    val oralF = PkCatalog.oralBioavailability(event.compound) ?: 0.0
                    // The recycling share is a fraction of the whole dose; the swallowed limb
                    // carries (1 - theta) of it, so divide to express it per swallowed mg.
                    val recycled = if (theta < 1.0) {
                        PkCatalog.sublingualRecyclingShare / (1.0 - theta)
                    } else {
                        0.0
                    }
                    PkParams(
                        fracFast = theta,
                        k1Fast = PkCatalog.kAbsSublingual,
                        k1Slow = PkCatalog.oralKAbs(event.compound) ?: 0.0,
                        k2 = 0.0,
                        k3 = k3,
                        bioavailability = 1.0,
                        rateMgH = 0.0,
                        fastBioavailability = 1.0,
                        slowBioavailability = oralF + recycled,
                    )
                }
            }

            PkRoute.PATCH_REMOVE -> PkParams(0.0, 0.0, 0.0, 0.0, k3, 0.0, 0.0, 0.0, 0.0)
        }
    }
}

object ThreeCompartmentModel {
    fun dualAbsorptionThreeCompartmentAmount(tau: Double, doseMg: Double, p: PkParams): Double {
        if (tau < 0.0 || doseMg <= 0.0) {
            return 0.0
        }
        val fraction = p.fracFast.coerceIn(0.0, 1.0)
        return analyticThreeCompartmentAmount(
            tau = tau,
            doseMg = doseMg * fraction,
            bioavailability = p.fastBioavailability,
            k1 = p.k1Fast,
            k2 = p.k2,
            k3 = p.k3,
        ) + analyticThreeCompartmentAmount(
            tau = tau,
            doseMg = doseMg * (1.0 - fraction),
            bioavailability = p.slowBioavailability,
            k1 = p.k1Slow,
            k2 = p.k2,
            k3 = p.k3,
        )
    }

    fun dualAbsorptionAmount(tau: Double, doseMg: Double, p: PkParams, fastKe: Double): Double {
        if (tau < 0.0 || doseMg <= 0.0) {
            return 0.0
        }
        val fraction = p.fracFast.coerceIn(0.0, 1.0)
        return batemanAmount(
            doseMg = doseMg * fraction,
            bioavailability = p.fastBioavailability,
            ka = p.k1Fast,
            ke = fastKe,
            t = tau,
        ) + batemanAmount(
            doseMg = doseMg * (1.0 - fraction),
            bioavailability = p.slowBioavailability,
            ka = p.k1Slow,
            ke = p.k3,
            t = tau,
        )
    }

    fun injectionAmount(tau: Double, doseMg: Double, p: PkParams): Double {
        if (tau < 0.0 || doseMg <= 0.0) {
            return 0.0
        }
        val fraction = p.fracFast.coerceIn(0.0, 1.0)
        return analyticThreeCompartmentAmount(
            tau = tau,
            doseMg = doseMg * fraction,
            bioavailability = p.bioavailability,
            k1 = p.k1Fast,
            k2 = p.k2,
            k3 = p.k3,
        ) + analyticThreeCompartmentAmount(
            tau = tau,
            doseMg = doseMg * (1.0 - fraction),
            bioavailability = p.bioavailability,
            k1 = p.k1Slow,
            k2 = p.k2,
            k3 = p.k3,
        )
    }

    fun oneCompartmentAmount(tau: Double, doseMg: Double, p: PkParams): Double {
        return batemanAmount(
            doseMg = doseMg,
            bioavailability = p.bioavailability,
            ka = p.k1Fast,
            ke = p.k3,
            t = tau,
        )
    }

    fun patchAmount(tau: Double, doseMg: Double, wearH: Double, p: PkParams): Double {
        if (tau < 0.0 || p.k3 <= 0.0) {
            return 0.0
        }

        if (p.rateMgH > 0.0) {
            return if (tau <= wearH) {
                p.rateMgH / p.k3 * (1.0 - exp(-p.k3 * tau))
            } else {
                val amountAtRemoval = p.rateMgH / p.k3 * (1.0 - exp(-p.k3 * wearH))
                amountAtRemoval * exp(-p.k3 * (tau - wearH))
            }
        }

        val oneCompParams = p.copy(fracFast = 1.0, k1Slow = 0.0)
        val amountUnderPatch = oneCompartmentAmount(tau, doseMg, oneCompParams)
        return if (tau > wearH) {
            val amountAtRemoval = oneCompartmentAmount(wearH, doseMg, oneCompParams)
            amountAtRemoval * exp(-p.k3 * (tau - wearH))
        } else {
            amountUnderPatch
        }
    }

    private fun analyticThreeCompartmentAmount(
        tau: Double,
        doseMg: Double,
        bioavailability: Double,
        k1: Double,
        k2: Double,
        k3: Double,
    ): Double {
        if (tau < 0.0 || doseMg <= 0.0 || bioavailability <= 0.0 || k1 <= 0.0 || k2 <= 0.0 || k3 <= 0.0) {
            return 0.0
        }

        analyticThreeCompartmentRepeatedRates(
            tau = tau,
            doseMg = doseMg,
            bioavailability = bioavailability,
            k1 = k1,
            k2 = k2,
            k3 = k3,
            tolerance = 1e-9,
        )?.let { return it }

        val k1k2 = k1 - k2
        val k1k3 = k1 - k3
        val k2k3 = k2 - k3
        val term1 = exp(-k1 * tau) / (k1k2 * k1k3)
        val term2 = exp(-k2 * tau) / (-k1k2 * k2k3)
        val term3 = exp(-k3 * tau) / (k1k3 * k2k3)
        // The partial fractions cancel exactly at tau = 0 but round to ~-1e-17;
        // an absorbed amount is never negative.
        return (doseMg * bioavailability * k1 * k2 * (term1 + term2 + term3)).coerceAtLeast(0.0)
    }

    private fun analyticThreeCompartmentRepeatedRates(
        tau: Double,
        doseMg: Double,
        bioavailability: Double,
        k1: Double,
        k2: Double,
        k3: Double,
        tolerance: Double,
    ): Double? {
        val scaledDose = doseMg * bioavailability
        val k1EqualsK2 = abs(k1 - k2) < tolerance
        val k1EqualsK3 = abs(k1 - k3) < tolerance
        val k2EqualsK3 = abs(k2 - k3) < tolerance

        if (k1EqualsK2 && k1EqualsK3) {
            return scaledDose * k1 * k1 * tau * tau * 0.5 * exp(-k1 * tau)
        }

        if (k2EqualsK3) {
            val delta = k1 - k2
            if (abs(delta) < tolerance) {
                return null
            }
            return scaledDose * k1 * k2 *
                    (exp(-k1 * tau) + exp(-k2 * tau) * (delta * tau - 1.0)) /
                    (delta * delta)
        }

        if (k1EqualsK2) {
            val delta = k1 - k3
            if (abs(delta) < tolerance) {
                return null
            }
            return scaledDose * k1 * k1 *
                    (exp(-k3 * tau) - exp(-k1 * tau) * (1.0 + delta * tau)) /
                    (delta * delta)
        }

        if (k1EqualsK3) {
            val delta = k2 - k1
            if (abs(delta) < tolerance) {
                return null
            }
            return scaledDose * k1 * k2 *
                    (exp(-k2 * tau) + exp(-k1 * tau) * (delta * tau - 1.0)) /
                    (delta * delta)
        }

        return null
    }

    private fun batemanAmount(
        doseMg: Double,
        bioavailability: Double,
        ka: Double,
        ke: Double,
        t: Double,
    ): Double {
        if (t < 0.0 || doseMg <= 0.0 || bioavailability <= 0.0 || ka <= 0.0 || ke <= 0.0) {
            return 0.0
        }
        return if (abs(ka - ke) < 1e-9) {
            doseMg * bioavailability * ka * t * exp(-ke * t)
        } else {
            doseMg * bioavailability * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
        }
    }
}

private data class PkDoseAmounts(val doseMg: Double, val releaseRateMcgPerDay: Double?)

private fun pkDoseAmounts(
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
    route: PkRoute,
    snapshotEquivalentE2Mg: Double?,
    count: Int,
): PkDoseAmounts {
    val multiplier = count.coerceAtLeast(1)
    // PATCH_OFF carries the PatchOff singleton medicine; the simulator still
    // routes patch removals on applicationType alone (handled by the
    // PATCH_REMOVE branch below). Legacy log rows from before the singleton
    // existed pass medicine == null here; both paths fall through cleanly.
    val patchSpec = (medicine?.preparation as? MedicinePreparation.Patch)?.specification
    val perUnitDoseMg = when (route) {
        PkRoute.PATCH_REMOVE -> 0.0
        PkRoute.PATCH_APPLY -> when (patchSpec) {
            is MedicinePreparation.PatchSpecification.TotalMg -> patchSpec.valueMg
            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> 0.0
            null -> 0.0
        }
        // Non-patch routes: prefer the log snapshot; re-derive for planned entries.
        else -> snapshotEquivalentE2Mg
            ?: medicine?.let {
                DoseInstructionCalculator.perUnitEquivalentE2Mg(
                    it,
                    doseInstruction
                )
            }
            ?: 0.0
    }
    val releaseRateMcgPerDay =
        (patchSpec as? MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay)
            ?.takeIf { route == PkRoute.PATCH_APPLY }
            ?.valueMcgPerDay
            ?.times(multiplier)
    return PkDoseAmounts(
        doseMg = perUnitDoseMg * multiplier,
        releaseRateMcgPerDay = releaseRateMcgPerDay,
    )
}

/** Shared medication-entry conversion used by population and calibration paths. */
internal fun buildEstradiolPkDoseEvents(
    anchor: Instant,
    realEntries: List<MedicationLogEntry>,
    plannedEntries: List<MedicationLogEntry>,
): List<PkDoseEvent> {
    val realEvents = realEntries.asSequence()
        .mapNotNull { entry ->
            entry.buildEstradiolPkDoseEvent(
                anchor = anchor,
                isPlanned = false,
            )
        }
    val plannedEvents = plannedEntries.asSequence()
        .mapNotNull { entry ->
            entry.buildEstradiolPkDoseEvent(
                anchor = anchor,
                isPlanned = true,
            )
        }
    return (realEvents + plannedEvents).toList()
}

internal fun MedicationLogEntry.buildEstradiolPkDoseEvent(
    anchor: Instant,
    isPlanned: Boolean = false,
): PkDoseEvent? {
    if (category != MedicationCategory.ESTRADIOL) {
        return null
    }
    val route = applicationType.toPkRoute()
    // PATCH_OFF now carries the singleton PatchOff medicine, but its selection
    // is PatchOff (not Catalog), so toEstradiolPkCompound returns null. The
    // simulator only needs *some* compound for the PATCH_REMOVE route and the
    // only patch today is estradiol, so we fall back to E2. Legacy log rows
    // with medicine == null follow the same path.
    val compound = medicine?.toEstradiolPkCompound()
        ?: if (route == PkRoute.PATCH_REMOVE) PkCompound.E2 else return null
    val amounts = pkDoseAmounts(
        medicine = medicine,
        doseInstruction = doseInstruction,
        route = route,
        snapshotEquivalentE2Mg = equivalentE2Mg,
        count = count,
    )
    return PkDoseEvent(
        id = uuid,
        sourceGroupUuid = sourceGroupUuid,
        hormone = PkHormone.ESTRADIOL,
        route = route,
        timeH = Duration.between(anchor, appliedAt).toMillis() / 3_600_000.0,
        doseMg = amounts.doseMg,
        compound = compound,
        releaseRateMcgPerDay = amounts.releaseRateMcgPerDay,
        areaCm2 = DefaultGelAreaCm2,
        isPlanned = isPlanned,
    )
}

private fun PkDoseEvent.isDoseMarkerCandidate(): Boolean {
    return route != PkRoute.PATCH_REMOVE && (doseMg > 0.0 || releaseRateMcgPerDay != null)
}

private fun MedicationApplicationType.toPkRoute(): PkRoute {
    return when (this) {
        MedicationApplicationType.ORAL -> PkRoute.ORAL
        MedicationApplicationType.SUBLINGUAL -> PkRoute.SUBLINGUAL
        MedicationApplicationType.INJECTION -> PkRoute.INJECTION
        MedicationApplicationType.GEL -> PkRoute.GEL
        MedicationApplicationType.PATCH_ON -> PkRoute.PATCH_APPLY
        MedicationApplicationType.PATCH_OFF -> PkRoute.PATCH_REMOVE
    }
}

private fun Medicine.toEstradiolPkCompound(): PkCompound? {
    return when (val currentPreparation = preparation) {
        is MedicinePreparation.ImportedInjection -> currentPreparation.ester.toEstradiolPkCompound()
        is MedicinePreparation.ImportedGel -> PkCompound.E2
        else -> (selection as? MedicineSelection.Catalog)
            ?.medicationKey
            ?.toEstradiolPkCompound()
    }
}

private fun MedicationKey.toEstradiolPkCompound(): PkCompound? {
    return when (this) {
        MedicationKey.ESTRADIOL -> PkCompound.E2
        MedicationKey.ESTRADIOL_VALERATE -> PkCompound.EV
        MedicationKey.ESTRADIOL_BENZOATE -> PkCompound.EB
        MedicationKey.ESTRADIOL_CYPIONATE -> PkCompound.EC
        MedicationKey.ESTRADIOL_ENANTHATE -> PkCompound.EN
        MedicationKey.ESTRADIOL_UNDECYLATE -> PkCompound.EU
        MedicationKey.ESTRADIOL_GEL -> PkCompound.E2
        MedicationKey.ESTRADIOL_PATCH -> PkCompound.E2
        else -> null
    }
}

// Carried on every gel event but not applied. Jarvinen 1997 gives F = gelFMax * (1 - 0.31 *
// ln(area / 750)) over 200-1,600 cm2 if a per-product area is ever wired (0.1% sachets ~200 cm2,
// 0.06% pump ~750 cm2).
private const val DefaultGelAreaCm2 = 750.0

data class HormoneCoreParams(
    val vdPerKg: Double,
    val kClear: Double,
    val kClearInjection: Double,
    val depotK1Correction: Double,
    val patchFallbackK1: Double,
    val gelK1: Double,
    val gelFMax: Double,
)

private data class CompoundInfo(
    val hormone: PkHormone,
    val molecularWeight: Double,
    val activeMolecularWeight: Double,
) {
    val activeFactor: Double
        get() = activeMolecularWeight / molecularWeight
}

data class TwoPartDepotParams(
    val fracFast: Double,
    val k1Fast: Double,
    val k1Slow: Double,
)

object PkCatalog {
    // Share of a sublingual dose absorbed through the mucosa. 0.08 is a midpoint between the
    // peak-matched 0.058 (Fiet 1982, Casper & Yen 1981, Doll 2022) and the estrone-matched 0.101
    // (Cirrincione 2021); range 0.05-0.09. Only theta / kElimSublingual (exposure) and the peak
    // are identified, so move the pair together; both scale with vdPerKg. See
    // notes/reports/Estradiol PK parameters final papers.md for every constant's derivation.
    const val standardSublingualTheta: Double = 0.08
    // Peak at 0.68 h, the slow edge of the observed 0.25-0.75 h (Casper & Yen 1981, Fiet 1982).
    const val kAbsSublingual: Double = 1.8
    // Lumped post-peak rate of the mucosal part: distribution plus clearance after a bolus
    // (Kuhnz 1993 IV phases 2.3/h and 0.77/h), not conversion to estrone. Direct mucosal fits
    // point lower (0.6-0.9/h). No primary source exists for the often-quoted "11-14 h"
    // sublingual half-life; the ~12 h apparent 4-24 h half-life emerges from the two limbs.
    const val kElimSublingual: Double = 1.2
    // Share of a sublingual dose routed through the swallowed limb at full bioavailability, on
    // top of (1 - theta) x oral F. Keeps the 8-24 h sublingual curve at or above the same-dose
    // oral curve (Doll 2022, Price 1997, Fiet 1982) without overshooting Cirrincione 2021's
    // LC-MS/MS steady-state median. Sized between Burnier 1981 and Fiet 1982, which disagree
    // 20x at 24 h: a convention, not a measurement.
    const val sublingualRecyclingShare: Double = 0.004

    private val coreParams = mapOf(
        PkHormone.ESTRADIOL to HormoneCoreParams(
            // A terminal-slope (V_z-like) volume chosen to pair with kClear: Dusterberg 1985 gives
            // 2.0-2.3 L/kg, steady-state volumes are lower (1.0-1.6). standardSublingualTheta
            // scales with it.
            vdPerKg = 2.0,
            // CL = kClear * vdPerKg = 0.82 L/h/kg, the median of eleven human IV clearances
            // (Kuhnz, Blode & Zimmermann 1999, table 2; core 0.56-0.98). IV terminal half-life
            // 1.7-2.2 h puts 0.41/h at the fast end of 0.32-0.41/h. Every bioavailability and
            // formation fraction in this catalog is really F / CL at this clearance.
            kClear = 0.41,
            // Fitting device for the injection tail, a tenth of kClear: an injection's share of
            // the dose reaching blood is 10 x its formation fraction.
            kClearInjection = 0.041,
            depotK1Correction = 1.0,
            // Total-content patches are in practice Estrana tape: 0.72 mg worn 48 h holds ~52-60
            // pg/mL in postmenopausal women (JP insert), i.e. ~14% of the content delivered.
            patchFallbackK1 = 0.0035,
            // Lumped skin-to-blood rate: steady-state peak 4-5 h on 200-400 cm2 (Jarvinen 1997),
            // single-dose peak 6-8 h (Jarvinen 1999), steady state by the third daily dose.
            gelK1 = 0.04,
            // 0.06% gel on its 750 cm2 label area: Oestrogel UK SmPC day-12 average 76.8 pg/mL and
            // the Melbourne trans cohort median 77.6 at 1.5 mg/day (Maggacis 2025); Jarvinen 1997
            // interpolates 0.057-0.063 at 750 cm2. F falls with application area, not product
            // class: 0.081 on 200 cm2, 0.038 spread over both thighs. areaCm2 is not applied.
            gelFMax = 0.060,
        ),
        PkHormone.TESTOSTERONE to HormoneCoreParams(
            vdPerKg = 2.0,
            kClear = 0.6,
            kClearInjection = 0.03,
            depotK1Correction = 1.0,
            patchFallbackK1 = 0.05,
            gelK1 = 0.03,
            gelFMax = 0.12,
        ),
    )

    private val compounds = mapOf(
        PkCompound.E2 to CompoundInfo(PkHormone.ESTRADIOL, 272.38, 272.38),
        PkCompound.EB to CompoundInfo(PkHormone.ESTRADIOL, 376.5, 272.38),
        PkCompound.EV to CompoundInfo(PkHormone.ESTRADIOL, 356.5, 272.38),
        PkCompound.EC to CompoundInfo(PkHormone.ESTRADIOL, 396.58, 272.38),
        PkCompound.EN to CompoundInfo(PkHormone.ESTRADIOL, 384.56, 272.38),
        PkCompound.EU to CompoundInfo(PkHormone.ESTRADIOL, 440.66, 272.38),
        PkCompound.T to CompoundInfo(PkHormone.TESTOSTERONE, 288.42, 288.42),
        PkCompound.TC to CompoundInfo(PkHormone.TESTOSTERONE, 412.61, 288.42),
        PkCompound.TE to CompoundInfo(PkHormone.TESTOSTERONE, 400.59, 288.42),
        PkCompound.TU to CompoundInfo(PkHormone.TESTOSTERONE, 456.7, 288.42),
    )

    private val twoPartDepot = mapOf(
        PkCompound.EB to TwoPartDepotParams(fracFast = 0.9, k1Fast = 0.144, k1Slow = 0.114),
        // Not refit: Oriowo 1980 wants a taller, narrower peak (580-670 pg/mL at day 2.2, baseline
        // by day 7-8) and Dusterberg & Nishino 1982 a longer tail (148 pg/mL at day 10); this
        // shape sits between them and peaks at day 2.3 against measured day 2-3.
        PkCompound.EV to TwoPartDepotParams(fracFast = 0.4, k1Fast = 0.0216, k1Slow = 0.0138),
        // Cypionate in oil (Depo-Estradiol, home-brewed vials). Upstream's depot, effectively one
        // rate: 5 mg at 70 kg peaks at 154 pg/mL on day 3.8, against Oriowo 1980 (arachis oil, peak
        // day 3.9, 2.4-6.2) and Aly's four-study oil fit (155 pg/mL at day 4.3). Zhou 1998 and
        // Rahimy 1999 peak on day 1-2, but both used the aqueous microcrystalline suspension
        // (Cyclofem / Lunelle, with MPA), a different release; they inform the formation fraction,
        // not this shape.
        PkCompound.EC to TwoPartDepotParams(
            fracFast = 0.229164549,
            k1Fast = 0.005035046,
            k1Slow = 0.004510574,
        ),
        // Labels are inverted: the 5% "fast" part at 0.001/h is the slower one. Peaks at day 5.9
        // for 10 mg against Schiavon 1988's 6.3 d; back to baseline by day ~30.
        PkCompound.EN to TwoPartDepotParams(fracFast = 0.05, k1Fast = 0.001, k1Slow = 0.005),
        // Transmtf im-depot-v1: EU is a single rate-limiting slow depot.
        PkCompound.EU to TwoPartDepotParams(fracFast = 0.0, k1Fast = 0.00082, k1Slow = 0.00082),
        PkCompound.TC to TwoPartDepotParams(fracFast = 0.4, k1Fast = 0.018, k1Slow = 0.0036),
        PkCompound.TE to TwoPartDepotParams(fracFast = 0.45, k1Fast = 0.02, k1Slow = 0.0069),
        PkCompound.TU to TwoPartDepotParams(fracFast = 0.25, k1Fast = 0.008, k1Slow = 0.00145),
    )

    // Share of the dose reaching blood is 10 x F (kClearInjection = kClear / 10), so no ester can
    // exceed 0.100 at this clearance. One shared value: Oriowo 1980's head-to-head per-mg
    // exposures for EB/EV/EC agree within 7% (shares 0.72/0.82/0.75), Zhou 1998 cypionate
    // 0.065-0.088, Schiavon 1988 enanthate peaks favour 0.078 over 0.089, Garza-Flores 1994
    // 0.071-0.080. EV is bracketed by Aedo 1985's peak (fits 0.062) below and Dusterberg &
    // Nishino 1982's 4 mg curve (needs 0.09-0.10) above. A median of brackets, not a fit to a
    // published AUC. GAHT Plotter is not a vote: its EB and EC imply shares above 1.
    private val formationFraction = mapOf(
        PkCompound.EB to 0.078,
        PkCompound.EV to 0.078,
        PkCompound.EC to 0.078,
        PkCompound.EN to 0.078,
        // EU clears at kClear, so its share is F itself; references span 0.46-0.94.
        PkCompound.EU to 0.542,
        PkCompound.TC to 1.0,
        PkCompound.TE to 1.0,
        PkCompound.TU to 1.0,
    )

    private val hydrolysisK2 = mapOf(
        PkCompound.EB to 0.09,
        PkCompound.EV to 0.07,
        PkCompound.EC to 0.045,
        PkCompound.EN to 0.015,
        PkCompound.EU to 2.0,
        PkCompound.TC to 0.09,
        PkCompound.TE to 0.12,
        PkCompound.TU to 0.03,
    )

    // E2: a slow flip-flop ka stands in for the estrone-sulfate reservoir. 0.045 gives a single-
    // dose peak at 6 h (observed 6-10 h), an apparent half-life of 15 h (13-20 h) and daily
    // accumulation of 1.6x (Aedo 1990 1.53x, Stadberg 1999 1.4-1.6x). Linear to 4 mg; 8 mg
    // absorbs incompletely (Kuhnz 1993). EV is cleaved during absorption and tracks E2 at equal
    // E2 content (Timmer 1999, Wiegratz 2001), so it shares E2's values, sublingual included.
    private val oralKAbs = mapOf(
        PkCompound.E2 to 0.045,
        PkCompound.EV to 0.045,
        PkCompound.TU to 0.04,
    )

    // F is really F / CL at 0.82 L/h/kg: CL/F is 26-31 L/h/kg across Kuhnz 1993, Price 1997,
    // Timmer 1999 and Stadberg 1999; Cattani 2023 (LC-MS/MS, trans women) gives 2.75%, Aedo 1990
    // 2.8-3.6%, Dusterberg 1985 "about 3%". The literature's "about 5%" is Kuhnz's figure against
    // a clearance twice this one. The earlier 0.043 was back-solved from the Zumenon 2 mg SmPC's
    // steady-state average of 62.9 pg/mL (arithmetic mean, not baseline-adjusted, cohort weight
    // unstated), which sits at the top of the range. Range 0.025-0.045. At 70 kg, 2 mg/day gives
    // 57 / 27 / 44 pg/mL (peak / trough / average).
    private val oralBioavailability = mapOf(
        PkCompound.E2 to 0.030,
        PkCompound.EV to 0.030,
        PkCompound.TU to 0.03,
    )

    fun coreParams(hormone: PkHormone): HormoneCoreParams = coreParams.getValue(hormone)

    fun defaultConcentrationUnit(hormone: PkHormone): PkConcentrationUnit {
        return when (hormone) {
            PkHormone.ESTRADIOL -> PkConcentrationUnit.PG_PER_ML
            PkHormone.TESTOSTERONE -> PkConcentrationUnit.NG_PER_DL
        }
    }

    fun molecularWeight(hormone: PkHormone): Double {
        val analyte = when (hormone) {
            PkHormone.ESTRADIOL -> PkCompound.E2
            PkHormone.TESTOSTERONE -> PkCompound.T
        }
        return compounds.getValue(analyte).activeMolecularWeight
    }

    fun activeFactor(compound: PkCompound): Double = compounds.getValue(compound).activeFactor

    fun twoPartDepotParams(compound: PkCompound): TwoPartDepotParams? = twoPartDepot[compound]

    fun formationFraction(compound: PkCompound): Double? = formationFraction[compound]

    fun hydrolysisK2(compound: PkCompound): Double? = hydrolysisK2[compound]

    fun oralKAbs(compound: PkCompound): Double? = oralKAbs[compound]

    fun oralBioavailability(compound: PkCompound): Double? = oralBioavailability[compound]
}
