package com.mkx.hrttracker.model.medication

import java.time.LocalDate

sealed interface RunwayProjection {
    data object NoSchedule : RunwayProjection

    data class Days(
        val days: Int,
        val lastFulfillable: LocalDate,
    ) : RunwayProjection

    data object BeyondHorizon : RunwayProjection
}
