package com.mkx.hrttracker.model.pk

import java.util.UUID

data class CalibrationConfig(
    val initialMean: DoubleArray = doubleArrayOf(0.0, 0.0), // [logAmplitude, logClearance]
    val initialCovariance: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.1, 0.0),
        doubleArrayOf(0.0, 0.1)
    )
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalibrationConfig

        if (!initialMean.contentEquals(other.initialMean)) return false
        if (!initialCovariance.contentDeepEquals(other.initialCovariance)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initialMean.contentHashCode()
        result = 31 * result + initialCovariance.contentDeepHashCode()
        return result
    }
}

data class LabResult(
    val timeH: Double,
    val concentrationPgMl: Double
)

class PkCalibrationPipeline(
    private val engine: PkSimulationEngine,
    private val config: CalibrationConfig = CalibrationConfig()
) {
    private val ekf = PkEkf()

    fun runCalibration(
        labResults: List<LabResult>,
        events: List<PkDoseEvent>
    ): Map<String, EkfState> {
        val sortedLabs = labResults.sortedBy { it.timeH }
        val sortedEvents = events.sortedBy { it.timeH }

        val forwardStates = mutableListOf<EkfState>()
        val dtDaysList = mutableListOf<Double>()
        var currentState = EkfState(config.initialMean, config.initialCovariance)

        var lastTimeH = sortedEvents.firstOrNull()?.timeH ?: 0.0

        for (lab in sortedLabs) {
            val dtH = lab.timeH - lastTimeH
            val dtDays = dtH / 24.0

            // Predict
            currentState = ekf.predict(currentState, dtDays)
            forwardStates.add(currentState)
            dtDaysList.add(dtDays)

            val pastEvents = sortedEvents.filter { it.timeH <= lab.timeH }
            val tempEngine = PkSimulationEngine(
                events = pastEvents,
                hormone = PkHormone.ESTRADIOL,
                bodyWeightKg = 70.0,
                startTimeH = 0.0,
                endTimeH = lab.timeH,
                numberOfSteps = 1
            )

            val predictFn: (Double, Double) -> Double = { logAmp, logClr ->
                val models = tempEngine.getEventModels(logAmp, logClr)
                val res = tempEngine.run(sampleTimeH = listOf(lab.timeH), customModels = models)
                res.concentrations.firstOrNull() ?: 0.0
            }

            // Update
            currentState = ekf.update(currentState, lab.concentrationPgMl, predictFn)

            // Replace the predicted state with the updated state in forwardStates
            forwardStates[forwardStates.lastIndex] = currentState
            lastTimeH = lab.timeH
        }

        // RTS Smoothing
        val rtsDtDaysList = mutableListOf<Double>()
        for (i in 0 until dtDaysList.size - 1) {
            rtsDtDaysList.add(dtDaysList[i + 1])
        }
        rtsDtDaysList.add(0.0) // No next state for the last one

        val smoothedStates = ekf.rtsSmooth(forwardStates, rtsDtDaysList)

        // Map events to smoothed states based on time
        val timeline = mutableMapOf<String, EkfState>()
        for (event in sortedEvents) {
            val nextLabIndex = sortedLabs.indexOfFirst { it.timeH >= event.timeH }
            if (nextLabIndex != -1 && nextLabIndex < smoothedStates.size) {
                timeline[event.id.toString()] = smoothedStates[nextLabIndex]
            } else if (smoothedStates.isNotEmpty()) {
                timeline[event.id.toString()] = smoothedStates.last()
            } else {
                timeline[event.id.toString()] = EkfState(config.initialMean, config.initialCovariance)
            }
        }

        return timeline
    }
}
