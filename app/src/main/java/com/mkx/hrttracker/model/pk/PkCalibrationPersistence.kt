package com.mkx.hrttracker.model.pk

import java.time.Instant
import java.util.Collections
import java.util.UUID
import kotlin.math.exp

const val PK_CALIBRATION_OUTLIER_REVIEW_DIGEST_SCHEMA =
    "hrttracker.calibration-outlier-review/v1"
const val PK_CALIBRATION_DISPLAY_SCHEMA =
    "hrttracker.calibration-display/v9"

/**
 * Supplies the one calibration-model identity currently approved to reach Home.
 *
 * Production deliberately defaults to [Unavailable]: until the final model identity is
 * explicitly bound, durable artifacts remain available to calibration storage but Home
 * stays population-only. Tests and research builds can use [fixed] with an explicit,
 * validated identity.
 */
fun interface PkCalibrationModelIdentityProvider {
    fun expectedCalibrationModelVersion(): String?

    companion object {
        val Unavailable = PkCalibrationModelIdentityProvider { null }

        fun fixed(calibrationModelVersion: String): PkCalibrationModelIdentityProvider {
            require(calibrationModelVersion.isStableAsciiIdentity())
            return PkCalibrationModelIdentityProvider { calibrationModelVersion }
        }
    }
}

enum class E2CalibrationDisposition {
    AUTO,
    ACCEPTED,
    EXCLUDED,
}

@ConsistentCopyVisibility
data class E2CalibrationMetadata private constructor(
    val resultId: UUID,
    val disposition: E2CalibrationDisposition,
    val acceptedReviewDigest: CanonicalDigest?,
    val updatedAt: Instant,
) {
    companion object {
        fun create(
            resultId: UUID,
            disposition: E2CalibrationDisposition,
            acceptedReviewDigest: CanonicalDigest?,
            updatedAt: Instant,
        ): E2CalibrationMetadata? {
            when (disposition) {
                E2CalibrationDisposition.ACCEPTED -> {
                    if (acceptedReviewDigest?.schema !=
                        PK_CALIBRATION_OUTLIER_REVIEW_DIGEST_SCHEMA
                    ) {
                        return null
                    }
                }

                E2CalibrationDisposition.AUTO,
                E2CalibrationDisposition.EXCLUDED,
                -> if (acceptedReviewDigest != null) return null
            }
            return E2CalibrationMetadata(
                resultId = resultId,
                disposition = disposition,
                acceptedReviewDigest = acceptedReviewDigest,
                updatedAt = updatedAt,
            )
        }
    }
}

/**
 * The one durable, atomic display artifact defined by the v9 calibration contract.
 * Promotion identity is kept separately from the non-zero beta map so an exactly
 * zero fitted beta remains distinguishable from an unpromoted route.
 */
@ConsistentCopyVisibility
data class PersistedPkCalibrationDisplay private constructor(
    val schema: String,
    val calibrationModelVersion: String,
    val resultInputDigest: CanonicalDigest,
    val promotedRoutes: List<PkCalibrationRoute>,
    val displayRouteLogScaleByRoute: Map<PkCalibrationRoute, Double>,
) {
    companion object {
        fun create(
            schema: String = PK_CALIBRATION_DISPLAY_SCHEMA,
            calibrationModelVersion: String,
            resultInputDigest: CanonicalDigest,
            promotedRoutes: List<PkCalibrationRoute>,
            displayRouteLogScaleByRoute: Map<PkCalibrationRoute, Double>,
        ): PersistedPkCalibrationDisplay? {
            if (schema != PK_CALIBRATION_DISPLAY_SCHEMA) return null
            if (calibrationModelVersion.isBlank()) return null
            if (promotedRoutes.distinct().size != promotedRoutes.size) return null
            if (promotedRoutes != PkCalibrationRoute.entries.filter(promotedRoutes::contains)) {
                return null
            }
            if (displayRouteLogScaleByRoute.keys.any { route -> route !in promotedRoutes }) {
                return null
            }

            val canonicalBetas = linkedMapOf<PkCalibrationRoute, Double>()
            for (route in PkCalibrationRoute.entries) {
                val beta = displayRouteLogScaleByRoute[route] ?: continue
                if (!beta.isFinite() || beta == 0.0) return null
                val scale = exp(beta)
                if (!scale.isFinite() || scale <= 0.0) return null
                canonicalBetas[route] = beta
            }
            if (canonicalBetas.size != displayRouteLogScaleByRoute.size) return null

            return PersistedPkCalibrationDisplay(
                schema = schema,
                calibrationModelVersion = calibrationModelVersion,
                resultInputDigest = resultInputDigest,
                promotedRoutes = Collections.unmodifiableList(ArrayList(promotedRoutes)),
                displayRouteLogScaleByRoute =
                    Collections.unmodifiableMap(LinkedHashMap(canonicalBetas)),
            )
        }
    }
}

/**
 * Converts the durable display-only artifact into the exact immutable parameters consumed by
 * forward simulation. Promotion identity deliberately remains on the artifact: an exactly-zero
 * promoted route maps to population math while still remaining observable to presentation.
 */
fun PersistedPkCalibrationDisplay.toValidatedPersonalParams(): PkPersonalParams? {
    if (schema != PK_CALIBRATION_DISPLAY_SCHEMA ||
        !calibrationModelVersion.isStableAsciiIdentity() ||
        promotedRoutes.distinct().size != promotedRoutes.size ||
        promotedRoutes != PkCalibrationRoute.entries.filter(promotedRoutes::contains) ||
        displayRouteLogScaleByRoute.keys.any { route -> route !in promotedRoutes }
    ) {
        return null
    }
    return PkPersonalParams.create(displayRouteLogScaleByRoute)
}
