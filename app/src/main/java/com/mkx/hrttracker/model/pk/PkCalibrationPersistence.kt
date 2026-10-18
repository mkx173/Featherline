package com.mkx.hrttracker.model.pk

import java.time.Instant
import java.util.UUID

enum class E2CalibrationDisposition {
    AUTO,
    /** Included normally; the user accepted the result and hid its review tip. */
    REVIEWED,
    EXCLUDED,
}

/** Result-owned inclusion and review preference; REVIEWED never changes the fit. */
data class E2CalibrationMetadata(
    val resultId: UUID,
    val disposition: E2CalibrationDisposition,
    val updatedAt: Instant,
)

/** The results reviewed with [disposition]. */
fun List<E2CalibrationMetadata>.resultIdsWith(disposition: E2CalibrationDisposition): Set<UUID> =
    filter { it.disposition == disposition }
        .map { it.resultId }
        .toSet()
