package com.mkx.hrttracker.model.pk

/**
 * Drug-only E2 forward evaluator used by calibration.
 *
 * Unlike Home's presentation wrapper, construction requires an explicit,
 * valid Current Weight. Calibration must never substitute the chart's 70 kg
 * presentation fallback.
 */
class PkE2ForwardModel private constructor(
    private val eventModels: List<PkForwardEventModel>,
    private val concentrationScale: Double,
) {
    /**
     * Direct event-order evaluation. This is the population-parity reference;
     * dominance uses [breakdownAt] and its canonical ordered total instead.
     */
    fun directDrugPgmlAt(
        timeH: Double,
        personalParams: PkPersonalParams = PkPersonalParams.population(),
    ): Double? {
        if (!timeH.isFinite()) return null

        var totalAmountMg = 0.0
        for (model in eventModels) {
            val amountMg = model.amountAt(timeH)
            if (!amountMg.isFinite() || amountMg < 0.0) return null
            val route = model.event.calibrationRoute() ?: return null
            val beta = personalParams.routeLogScale[route]
            val scaledAmountMg = if (beta == null) {
                amountMg
            } else {
                amountMg * personalParams.scaleFor(route)
            }
            if (!scaledAmountMg.isFinite() || scaledAmountMg < 0.0) return null
            totalAmountMg += scaledAmountMg
            if (!totalAmountMg.isFinite() || totalAmountMg < 0.0) return null
        }

        val concentration = totalAmountMg * concentrationScale
        return concentration.takeIf { value -> value.isFinite() && value >= 0.0 }
            ?.normalizePositiveZero()
    }

    /**
     * Five-route decomposition. Both route and total accumulation are simple
     * left-to-right binary64 additions in canonical route order.
     */
    fun breakdownAt(
        timeH: Double,
        personalParams: PkPersonalParams = PkPersonalParams.population(),
    ): PkForwardBreakdown? {
        if (!timeH.isFinite()) return null

        val byRoute = linkedMapOf<PkCalibrationRoute, Double>()
        for (route in PkCalibrationRoute.entries) {
            var routeAmountMg = 0.0
            val beta = personalParams.routeLogScale[route]
            val scale = if (beta == null) 1.0 else personalParams.scaleFor(route)
            for (model in eventModels) {
                if (model.event.calibrationRoute() != route) continue
                val amountMg = model.amountAt(timeH)
                if (!amountMg.isFinite() || amountMg < 0.0) return null
                val scaledAmountMg = if (beta == null) amountMg else amountMg * scale
                if (!scaledAmountMg.isFinite() || scaledAmountMg < 0.0) return null
                routeAmountMg += scaledAmountMg
                if (!routeAmountMg.isFinite() || routeAmountMg < 0.0) return null
            }
            val routeDrugPgml = routeAmountMg * concentrationScale
            if (!routeDrugPgml.isFinite() || routeDrugPgml < 0.0) return null
            byRoute[route] = routeDrugPgml.normalizePositiveZero()
        }
        return PkForwardBreakdown.create(byRoute)
    }

    companion object {
        fun create(
            events: List<PkDoseEvent>,
            bodyWeightKg: Double,
        ): PkE2ForwardModel? {
            if (!bodyWeightKg.isFinite() || bodyWeightKg <= 0.0) return null
            val core = PkCatalog.coreParams(PkHormone.ESTRADIOL)
            val plasmaVolumeMl = core.vdPerKg * bodyWeightKg * 1000.0
            if (!plasmaVolumeMl.isFinite() || plasmaVolumeMl <= 0.0) return null
            val concentrationScale =
                PkConcentrationUnit.PG_PER_ML.concentrationScale(PkHormone.ESTRADIOL) /
                        plasmaVolumeMl
            if (!concentrationScale.isFinite() || concentrationScale <= 0.0) return null

            val sortedEvents = events
                .filter { event -> event.hormone == PkHormone.ESTRADIOL }
                .takeIf { e2Events -> e2Events.all(PkDoseEvent::hasValidCalibrationInput) }
                ?.sortedBy(PkDoseEvent::timeH)
                ?: return null
            val eventModels = sortedEvents
                .asSequence()
                .filter { event ->
                    event.calibrationRoute() != null
                }
                .map { event -> PkForwardEventModel(event, sortedEvents) }
                .toList()
            return PkE2ForwardModel(eventModels, concentrationScale)
        }
    }
}

private fun PkDoseEvent.hasValidCalibrationInput(): Boolean {
    if (compound !in CalibrationEstradiolCompounds) return false
    if (!timeH.isFinite()) return false
    if (!doseMg.isFinite() || doseMg < 0.0) return false
    if (releaseRateMcgPerDay != null &&
        (!releaseRateMcgPerDay.isFinite() || releaseRateMcgPerDay < 0.0)
    ) {
        return false
    }
    if (sublingualTheta != null && !sublingualTheta.isFinite()) return false
    return true
}

private val CalibrationEstradiolCompounds = setOf(
    PkCompound.E2,
    PkCompound.EB,
    PkCompound.EV,
    PkCompound.EC,
    PkCompound.EN,
    PkCompound.EU,
)

