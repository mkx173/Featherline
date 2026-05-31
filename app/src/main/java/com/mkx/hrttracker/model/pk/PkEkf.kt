package com.mkx.hrttracker.model.pk

import kotlin.math.exp
import kotlin.math.pow

data class EkfState(
    val mean: DoubleArray, // [logAmplitude, logClearance]
    val covariance: Array<DoubleArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EkfState
        if (!mean.contentEquals(other.mean)) return false
        if (!covariance.contentDeepEquals(other.covariance)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = mean.contentHashCode()
        result = 31 * result + covariance.contentDeepHashCode()
        return result
    }
}

class PkEkf(
    private val meanReversionHalfLifeDays: Double = 7.0,
    private val steadyStateVariance: Double = 0.1,
    private val measurementCv: Double = 0.20
) {
    private val kMeanReversion = kotlin.math.ln(2.0) / meanReversionHalfLifeDays

    fun predict(state: EkfState, dtDays: Double): EkfState {
        if (dtDays <= 0.0) return state

        // Ornstein-Uhlenbeck transition: mean reverts to 0
        val decay = exp(-kMeanReversion * dtDays)
        val newMean = DoubleArray(2) { i -> state.mean[i] * decay }

        // Process noise
        val q = steadyStateVariance * (1.0 - exp(-2.0 * kMeanReversion * dtDays))

        val newCovariance = Array(2) { DoubleArray(2) }
        for (i in 0..1) {
            for (j in 0..1) {
                newCovariance[i][j] = state.covariance[i][j] * decay * decay
            }
            newCovariance[i][i] += q
        }

        return EkfState(newMean, newCovariance)
    }

    // Jacobian of the measurement function h(x) = predicted_conc(x)
    private fun computeJacobian(
        state: EkfState,
        predictFn: (logAmp: Double, logClr: Double) -> Double
    ): DoubleArray {
        val eps = 1e-4
        val h0 = predictFn(state.mean[0], state.mean[1])

        val h1a = predictFn(state.mean[0] + eps, state.mean[1])
        val dh_damp = (h1a - h0) / eps

        val h1c = predictFn(state.mean[0], state.mean[1] + eps)
        val dh_dclr = (h1c - h0) / eps

        return doubleArrayOf(dh_damp, dh_dclr)
    }

    fun update(
        state: EkfState,
        measuredConc: Double,
        predictFn: (logAmp: Double, logClr: Double) -> Double
    ): EkfState {
        val h = computeJacobian(state, predictFn)
        val predictedConc = predictFn(state.mean[0], state.mean[1])

        // Measurement variance
        val r = (predictedConc * measurementCv).pow(2)

        // Innovation
        val y = measuredConc - predictedConc

        // Innovation covariance S = H * P * H^T + R
        var s = r
        for (i in 0..1) {
            var rowSum = 0.0
            for (j in 0..1) {
                rowSum += state.covariance[i][j] * h[j]
            }
            s += h[i] * rowSum
        }

        if (s <= 0.0) return state

        // Kalman gain K = P * H^T / S
        val k = DoubleArray(2)
        for (i in 0..1) {
            var sum = 0.0
            for (j in 0..1) {
                sum += state.covariance[i][j] * h[j]
            }
            k[i] = sum / s
        }

        // Updated mean
        val newMean = DoubleArray(2) { i -> state.mean[i] + k[i] * y }

        // Updated covariance P = (I - K * H) * P
        val newCovariance = Array(2) { DoubleArray(2) }
        for (i in 0..1) {
            for (j in 0..1) {
                var sum = state.covariance[i][j]
                for (l in 0..1) {
                    sum -= k[i] * h[l] * state.covariance[l][j]
                }
                newCovariance[i][j] = sum
            }
        }

        return EkfState(newMean, newCovariance)
    }

    fun rtsSmooth(
        forwardStates: List<EkfState>,
        dtDays: List<Double>
    ): List<EkfState> {
        if (forwardStates.isEmpty()) return emptyList()

        val smoothed = MutableList(forwardStates.size) { forwardStates.last() }

        for (i in forwardStates.size - 2 downTo 0) {
            val dt = dtDays[i] // dt to the next state
            val decay = exp(-kMeanReversion * dt)
            val q = steadyStateVariance * (1.0 - exp(-2.0 * kMeanReversion * dt))

            val curState = forwardStates[i]
            val nextStateSmoothed = smoothed[i + 1]

            // Predict step from i to i+1
            val predMean = DoubleArray(2) { j -> curState.mean[j] * decay }
            val predCov = Array(2) { DoubleArray(2) }
            for (r in 0..1) {
                for (c in 0..1) {
                    predCov[r][c] = curState.covariance[r][c] * decay * decay
                }
                predCov[r][r] += q
            }

            val cGain = Array(2) { DoubleArray(2) }
            val det = predCov[0][0] * predCov[1][1] - predCov[0][1] * predCov[1][0]
            if (det > 1e-12) {
                val invCov = Array(2) { DoubleArray(2) }
                invCov[0][0] = predCov[1][1] / det
                invCov[1][1] = predCov[0][0] / det
                invCov[0][1] = -predCov[0][1] / det
                invCov[1][0] = -predCov[1][0] / det

                for (r in 0..1) {
                    for (c in 0..1) {
                        var sum = 0.0
                        for (l in 0..1) {
                            sum += curState.covariance[r][l] * decay * invCov[l][c]
                        }
                        cGain[r][c] = sum
                    }
                }
            } else {
                smoothed[i] = curState
                continue
            }

            val diffMean = DoubleArray(2) { j -> nextStateSmoothed.mean[j] - predMean[j] }
            val smMean = DoubleArray(2)
            for (r in 0..1) {
                var sum = curState.mean[r]
                for (c in 0..1) {
                    sum += cGain[r][c] * diffMean[c]
                }
                smMean[r] = sum
            }

            val diffCov = Array(2) { DoubleArray(2) }
            for (r in 0..1) {
                for (c in 0..1) {
                    diffCov[r][c] = nextStateSmoothed.covariance[r][c] - predCov[r][c]
                }
            }

            val temp = Array(2) { DoubleArray(2) }
            for (r in 0..1) {
                for (c in 0..1) {
                    var sum = 0.0
                    for (l in 0..1) {
                        sum += cGain[r][l] * diffCov[l][c]
                    }
                    temp[r][c] = sum
                }
            }

            val smCov = Array(2) { DoubleArray(2) }
            for (r in 0..1) {
                for (c in 0..1) {
                    var sum = curState.covariance[r][c]
                    for (l in 0..1) {
                        sum += temp[r][l] * cGain[c][l]
                    }
                    smCov[r][c] = sum
                }
            }

            smoothed[i] = EkfState(smMean, smCov)
        }

        return smoothed
    }
}
