package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import java.time.Duration
import java.time.Instant
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
    val chartWindowHours: Int = chartSampleIntervalHours * (chartConcentrations.size - 1).coerceAtLeast(0),
    val predictionStartTimeH: Double = chartWindowHours.toDouble(),
) {
    val changeSincePreviousDay: Double
        get() = currentConcentration - previousDayConcentration
}

object PkMedicationSimulation {
    private const val HoursPerDay = 24.0
    private const val MainChartWindowDays = 7L
    private const val MainChartPastDays = 3L
    private const val MainChartSampleIntervalHours = 1
    private const val MainChartWindowHours = MainChartWindowDays * 24
    private const val MainChartFallbackSteps = MainChartWindowHours + 1
    private const val MainChartDenseSampleIntervalHours = 0.1
    private const val DefaultBodyWeightKg = 70.0
    private const val ChartXPrecisionScale = 10_000.0
    private val MainChartPostDoseSampleOffsetsHours = listOf(
        0.25,
        0.5,
        1.0,
        2.0,
        4.0,
        6.0,
        8.0,
        12.0,
        24.0,
        48.0,
    )

    fun simulateMainEstradiolTrend(
        entries: List<MedicationLogEntry>,
        bodyWeightKg: Double?,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PkTrendResult? {
        val resolvedBodyWeightKg = bodyWeightKg?.takeIf { it.isFinite() && it > 0 } ?: DefaultBodyWeightKg
        val nowInstant = now.atZone(zoneId).toInstant()
        val startInstant = now
            .toLocalDate()
            .atStartOfDay()
            .minusDays(MainChartPastDays)
            .atZone(zoneId)
            .toInstant()
        val predictionStartTimeH = Duration.between(startInstant, nowInstant)
            .toMillis() / 3_600_000.0
        val events = entries
            .asSequence()
            .filterNot { entry -> entry.appliedAt.isAfter(nowInstant) }
            .mapNotNull { entry -> entry.toEstradiolPkDoseEvent(anchor = startInstant) }
            .toList()
        val doseMarkerTimeH = events
            .asSequence()
            .filter(PkDoseEvent::isDoseMarkerCandidate)
            .map { event -> event.timeH.toChartXHour() }
            .filter { timeH -> timeH.isFinite() && timeH in 0.0..MainChartWindowHours.toDouble() }
            .distinct()
            .sorted()
            .toList()
        val chartSampleTimeH = mainChartSampleTimeH(
            events = events,
            predictionStartTimeH = predictionStartTimeH,
        )

        val result = PkSimulationEngine(
            events = events,
            hormone = PkHormone.ESTRADIOL,
            bodyWeightKg = resolvedBodyWeightKg,
            startTimeH = 0.0,
            endTimeH = MainChartWindowHours.toDouble(),
            numberOfSteps = MainChartFallbackSteps.toInt(),
        ).run(sampleTimeH = chartSampleTimeH)

        val dailyConcentrations = (0..MainChartWindowDays.toInt()).map { index ->
            result.concentrationAt(index * HoursPerDay) ?: 0.0
        }
        val doseMarkers = doseMarkerTimeH.mapNotNull { timeH ->
            result.concentrationAt(timeH)?.let { concentration ->
                PkDoseMarker(
                    timeH = timeH,
                    concentration = concentration,
                )
            }
        }
        return PkTrendResult(
            currentConcentration = result.concentrationAt(predictionStartTimeH) ?: 0.0,
            previousDayConcentration = result.concentrationAt(predictionStartTimeH - HoursPerDay) ?: 0.0,
            dailyConcentrations = dailyConcentrations,
            concentrationUnit = result.metadata.concentrationUnit,
            chartConcentrations = result.concentrations,
            chartSampleIntervalHours = MainChartSampleIntervalHours,
            chartTimeH = result.timeH,
            doseMarkers = doseMarkers,
            chartWindowHours = MainChartWindowHours.toInt(),
            predictionStartTimeH = predictionStartTimeH.toChartXHour(),
        )
    }

    private fun Double.toChartXHour(): Double {
        return (this * ChartXPrecisionScale).roundToLong() / ChartXPrecisionScale
    }

    private fun mainChartSampleTimeH(
        events: List<PkDoseEvent>,
        predictionStartTimeH: Double,
    ): List<Double> {
        val windowStart = 0.0
        val windowEnd = MainChartWindowHours.toDouble()
        val previousDayTimeH = predictionStartTimeH - HoursPerDay
        val denseSampleTimeH = mainChartDenseSampleTimeH(
            startTimeH = windowStart,
            endTimeH = windowEnd,
        )
        val loggedEventTimeH = events
            .asSequence()
            .map { event -> event.timeH.toChartXHour() }
            .filter { timeH -> timeH.isFinite() && timeH in windowStart..windowEnd }
            .toList()
        val exactSampleTimeH = (
            listOf(predictionStartTimeH.toChartXHour()) +
                listOf(previousDayTimeH.toChartXHour()).filter { timeH ->
                    timeH.isFinite() && timeH in windowStart..windowEnd
                } +
                loggedEventTimeH
            )
            .filter { timeH -> timeH.isFinite() && timeH in windowStart..windowEnd }
        val postDoseSampleTimeH = events
            .asSequence()
            .filter(PkDoseEvent::isDoseMarkerCandidate)
            .flatMap { event ->
                MainChartPostDoseSampleOffsetsHours.asSequence().map { offsetHours ->
                    (event.timeH + offsetHours).toChartXHour()
                }
            }
            .filter { timeH -> timeH.isFinite() && timeH in windowStart..windowEnd }
            .toList()

        return (denseSampleTimeH + exactSampleTimeH + postDoseSampleTimeH)
            .map { timeH -> timeH.toChartXHour() }
            .distinct()
            .sorted()
    }

    private fun mainChartDenseSampleTimeH(
        startTimeH: Double,
        endTimeH: Double,
    ): List<Double> {
        val span = endTimeH - startTimeH
        if (span <= 0.0) {
            return emptyList()
        }
        val segmentCount = ceil(span / MainChartDenseSampleIntervalHours).toInt().coerceAtLeast(1)
        return (0..segmentCount).map { index ->
            (startTimeH + MainChartDenseSampleIntervalHours * index)
                .coerceAtMost(endTimeH)
                .toChartXHour()
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
) {
    private val sortedEvents = events.sortedBy(PkDoseEvent::timeH)
    private val eventModels = sortedEvents
        .asSequence()
        .filter { event -> event.hormone == hormone && event.route != PkRoute.PATCH_REMOVE }
        .map { event -> PrecomputedPkEventModel(event, sortedEvents) }
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
        val concentrationScale = metadata.concentrationUnit.concentrationScale(hormone) / plasmaVolumeMl
        val time = ArrayList<Double>(sampleTimes.size)
        val concentrations = ArrayList<Double>(sampleTimes.size)
        var previousConcentration = 0.0
        var previousTimeH = startTimeH
        var auc = 0.0

        sampleTimes.forEachIndexed { index, t ->
            val totalAmountMg = eventModels.sumOf { model -> model.amountAt(t) }
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

private class PrecomputedPkEventModel(
    event: PkDoseEvent,
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
            if (tau < 0.0) 0.0 else ThreeCompartmentModel.oneCompartmentAmount(tau, dose, oneCompParams)
        }

        PkRoute.SUBLINGUAL -> { timeH ->
            val tau = timeH - startTime
            when {
                tau < 0.0 -> 0.0
                params.k2 > 0.0 -> ThreeCompartmentModel.dualAbsorptionMixedAmount(tau, dose, params)
                else -> ThreeCompartmentModel.dualAbsorptionAmount(tau, dose, params)
            }
        }

        PkRoute.PATCH_APPLY -> {
            val removeEvent = allEvents
                .asSequence()
                .filter { candidate ->
                    candidate.hormone == event.hormone &&
                        candidate.route == PkRoute.PATCH_REMOVE &&
                        candidate.compound == event.compound &&
                        candidate.matchesPatchIdentity(event) &&
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
        val k3 = if (event.route == PkRoute.INJECTION) {
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
                    k2 = if (event.hormone == PkHormone.ESTRADIOL && event.compound == PkCompound.EV) {
                        PkCatalog.hydrolysisK2(PkCompound.EV) ?: 0.0
                    } else {
                        0.0
                    },
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
                    PkParams(
                        fracFast = theta,
                        k1Fast = PkCatalog.kAbsSublingual,
                        k1Slow = PkCatalog.oralKAbs(event.compound) ?: 0.0,
                        k2 = if (event.compound == PkCompound.EV) {
                            PkCatalog.hydrolysisK2(PkCompound.EV) ?: 0.0
                        } else {
                            0.0
                        },
                        k3 = k3,
                        bioavailability = 1.0,
                        rateMgH = 0.0,
                        fastBioavailability = 1.0,
                        slowBioavailability = PkCatalog.oralBioavailability(event.compound) ?: 0.0,
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

    fun dualAbsorptionMixedAmount(tau: Double, doseMg: Double, p: PkParams): Double {
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
        ) + batemanAmount(
            doseMg = doseMg * (1.0 - fraction),
            bioavailability = p.slowBioavailability,
            ka = p.k1Slow,
            ke = p.k3,
            t = tau,
        )
    }

    fun dualAbsorptionAmount(tau: Double, doseMg: Double, p: PkParams): Double {
        if (tau < 0.0 || doseMg <= 0.0) {
            return 0.0
        }
        val fraction = p.fracFast.coerceIn(0.0, 1.0)
        return batemanAmount(
            doseMg = doseMg * fraction,
            bioavailability = p.fastBioavailability,
            ka = p.k1Fast,
            ke = p.k3,
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
        return doseMg * bioavailability * k1 * k2 * (term1 + term2 + term3)
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

private fun MedicationLogEntry.toEstradiolPkDoseEvent(anchor: Instant): PkDoseEvent? {
    if (category != MedicationCategory.ESTRADIOL) {
        return null
    }

    val compound = details.toEstradiolPkCompound() ?: return null
    val route = applicationType.toPkRoute()
    val doseMg = when (route) {
        PkRoute.PATCH_REMOVE -> 0.0
        PkRoute.PATCH_APPLY -> when (dose) {
            is MedicationDose.PatchReleaseRateMcgPerDay -> 0.0
            else -> activeEstradiolDoseMg()
        }
        PkRoute.INJECTION -> medicineDoseMg()
        else -> activeEstradiolDoseMg()
    } * count.coerceAtLeast(1)

    val releaseRateMcgPerDay = (dose as? MedicationDose.PatchReleaseRateMcgPerDay)
        ?.valueMcgPerDay
        ?.times(count.coerceAtLeast(1))

    return PkDoseEvent(
        id = uuid,
        sourceGroupUuid = sourceGroupUuid,
        hormone = PkHormone.ESTRADIOL,
        route = route,
        timeH = Duration.between(anchor, appliedAt).toMillis() / 3_600_000.0,
        doseMg = doseMg,
        compound = compound,
        releaseRateMcgPerDay = releaseRateMcgPerDay,
        areaCm2 = DefaultGelAreaCm2,
    )
}

private fun PkDoseEvent.matchesPatchIdentity(patchApply: PkDoseEvent): Boolean {
    return sourceGroupUuid == null ||
        patchApply.sourceGroupUuid == null ||
        sourceGroupUuid == patchApply.sourceGroupUuid
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

private fun com.mkx.hrttracker.model.medication.MedicationDetails.toEstradiolPkCompound(): PkCompound? {
    val medicationKey = (selection as? MedicationSelection.Catalog)?.medicationKey ?: return null
    return when (medicationKey) {
        MedicationKey.ESTRADIOL -> PkCompound.E2
        MedicationKey.ESTRADIOL_VALERATE -> PkCompound.EV
        MedicationKey.ESTRADIOL_BENZOATE -> PkCompound.EB
        MedicationKey.ESTRADIOL_CYPIONATE -> PkCompound.EC
        MedicationKey.ESTRADIOL_ENANTHATE -> PkCompound.EN
        MedicationKey.ESTRADIOL_GEL -> PkCompound.E2
        MedicationKey.ESTRADIOL_PATCH -> PkCompound.E2
        else -> null
    }
}

private fun MedicationLogEntry.activeEstradiolDoseMg(): Double {
    val storedDose = dosageMgAsEstradiol
    if (storedDose != null && storedDose.isFinite() && storedDose >= 0.0) {
        return storedDose
    }

    val medicationKey = (selection as? MedicationSelection.Catalog)?.medicationKey ?: return 0.0
    val valueMg = when (val resolvedDose = dose) {
        is MedicationDose.MgAsMedicine -> resolvedDose.valueMg
        is MedicationDose.GelEquivalentEstradiolMg -> resolvedDose.valueMg
        is MedicationDose.GelPercentAndWeight -> resolvedDose.percent * resolvedDose.weightGrams * 10.0
        is MedicationDose.PatchTotalMg -> resolvedDose.valueMg
        is MedicationDose.PatchReleaseRateMcgPerDay,
        MedicationDose.None -> return 0.0
    }

    return valueMg * when (medicationKey) {
        MedicationKey.ESTRADIOL -> 1.0
        MedicationKey.ESTRADIOL_VALERATE -> PkCatalog.activeFactor(PkCompound.EV)
        MedicationKey.ESTRADIOL_BENZOATE -> PkCatalog.activeFactor(PkCompound.EB)
        MedicationKey.ESTRADIOL_CYPIONATE -> PkCatalog.activeFactor(PkCompound.EC)
        MedicationKey.ESTRADIOL_ENANTHATE -> PkCatalog.activeFactor(PkCompound.EN)
        MedicationKey.ESTRADIOL_GEL -> 1.0
        MedicationKey.ESTRADIOL_PATCH -> 1.0
        else -> 0.0
    }
}

private fun MedicationLogEntry.medicineDoseMg(): Double {
    val resolvedDose = dose
    return when (resolvedDose) {
        is MedicationDose.MgAsMedicine -> resolvedDose.valueMg
        is MedicationDose.GelEquivalentEstradiolMg -> resolvedDose.valueMg
        is MedicationDose.GelPercentAndWeight -> resolvedDose.percent * resolvedDose.weightGrams * 10.0
        is MedicationDose.PatchTotalMg -> resolvedDose.valueMg
        is MedicationDose.PatchReleaseRateMcgPerDay,
        MedicationDose.None -> 0.0
    }
}

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
    const val standardSublingualTheta: Double = 0.11
    const val kAbsSublingual: Double = 1.8

    private val coreParams = mapOf(
        PkHormone.ESTRADIOL to HormoneCoreParams(
            vdPerKg = 2.0,
            kClear = 0.41,
            kClearInjection = 0.041,
            depotK1Correction = 1.0,
            patchFallbackK1 = 0.0075,
            gelK1 = 0.022,
            gelFMax = 0.06,
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
        PkCompound.T to CompoundInfo(PkHormone.TESTOSTERONE, 288.42, 288.42),
        PkCompound.TC to CompoundInfo(PkHormone.TESTOSTERONE, 412.61, 288.42),
        PkCompound.TE to CompoundInfo(PkHormone.TESTOSTERONE, 400.59, 288.42),
        PkCompound.TU to CompoundInfo(PkHormone.TESTOSTERONE, 456.7, 288.42),
    )

    private val twoPartDepot = mapOf(
        PkCompound.EB to TwoPartDepotParams(fracFast = 0.9, k1Fast = 0.144, k1Slow = 0.114),
        PkCompound.EV to TwoPartDepotParams(fracFast = 0.4, k1Fast = 0.0216, k1Slow = 0.0138),
        PkCompound.EC to TwoPartDepotParams(fracFast = 0.229164549, k1Fast = 0.005035046, k1Slow = 0.004510574),
        PkCompound.EN to TwoPartDepotParams(fracFast = 0.05, k1Fast = 0.001, k1Slow = 0.005),
        PkCompound.TC to TwoPartDepotParams(fracFast = 0.4, k1Fast = 0.018, k1Slow = 0.0036),
        PkCompound.TE to TwoPartDepotParams(fracFast = 0.45, k1Fast = 0.02, k1Slow = 0.0069),
        PkCompound.TU to TwoPartDepotParams(fracFast = 0.25, k1Fast = 0.008, k1Slow = 0.00145),
    )

    private val formationFraction = mapOf(
        PkCompound.EB to 0.10922376473734707,
        PkCompound.EV to 0.062258288229969413,
        PkCompound.EC to 0.117255838,
        PkCompound.EN to 0.12,
        PkCompound.TC to 1.0,
        PkCompound.TE to 1.0,
        PkCompound.TU to 1.0,
    )

    private val hydrolysisK2 = mapOf(
        PkCompound.EB to 0.09,
        PkCompound.EV to 0.07,
        PkCompound.EC to 0.045,
        PkCompound.EN to 0.015,
        PkCompound.TC to 0.09,
        PkCompound.TE to 0.12,
        PkCompound.TU to 0.03,
    )

    private val oralKAbs = mapOf(
        PkCompound.E2 to 0.32,
        PkCompound.EV to 0.05,
        PkCompound.TU to 0.04,
    )

    private val oralBioavailability = mapOf(
        PkCompound.E2 to 0.03,
        PkCompound.EV to 0.03,
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
