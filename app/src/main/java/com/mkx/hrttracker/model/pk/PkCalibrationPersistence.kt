package com.mkx.hrttracker.model.pk

import java.time.Instant
import java.util.UUID

enum class E2CalibrationDisposition {
    AUTO,
    EXCLUDED,
}

/** Result-owned review state: the user's explicit exclusion of one E2 result from the fit. */
data class E2CalibrationMetadata(
    val resultId: UUID,
    val disposition: E2CalibrationDisposition,
    val updatedAt: Instant,
)
