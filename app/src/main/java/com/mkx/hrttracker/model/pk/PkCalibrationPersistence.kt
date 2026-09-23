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
