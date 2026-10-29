package com.mkx.hrttracker.model.medication

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

fun MedicationGroup.previousScheduledForBefore(
    scheduledFor: LocalDateTime,
    lookbackDays: Long = MaxLookbackForAdjacentDoseDays,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDateTime? {
    return occurrencesBetweenInPlanWindow(
        startDate = scheduledFor.toLocalDate().minusDays(lookbackDays),
        endDate = scheduledFor.toLocalDate(),
        zoneId = zoneId,
    )
        .asSequence()
        .map(MedicationGroupSlotOccurrence::scheduledFor)
        .filter { occurrence -> occurrence.isBefore(scheduledFor) }
        .maxOrNull()
}

fun MedicationGroup.nextScheduledForAfter(
    scheduledFor: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDateTime? {
    return nextOccurrencesInPlanWindowFrom(
        start = scheduledFor.plusNanos(1),
        limit = 1,
        zoneId = zoneId,
    ).firstOrNull()?.scheduledFor
}

fun scheduleFulfillmentAllowedOffset(
    scheduledFor: LocalDateTime,
    adjacentScheduledFor: LocalDateTime?,
): Duration {
    val halfTimeToAdjacentDose = adjacentScheduledFor
        ?.let { adjacentDose -> Duration.between(scheduledFor, adjacentDose).abs() }
        ?.takeIf { duration -> !duration.isZero }
        ?.dividedBy(2)

    return if (halfTimeToAdjacentDose != null && halfTimeToAdjacentDose < ScheduleFulfillmentMaxOffset) {
        halfTimeToAdjacentDose
    } else {
        ScheduleFulfillmentMaxOffset
    }
}

fun isWithinScheduleFulfillmentWindow(
    scheduledFor: LocalDateTime,
    appliedAt: LocalDateTime,
    previousScheduledFor: LocalDateTime?,
    nextScheduledFor: LocalDateTime?,
): Boolean {
    val offset = Duration.between(scheduledFor, appliedAt)
    return if (offset.isNegative) {
        offset.abs() < scheduleFulfillmentAllowedOffset(
            scheduledFor = scheduledFor,
            adjacentScheduledFor = previousScheduledFor ?: nextScheduledFor
        )
    } else {
        offset < scheduleFulfillmentAllowedOffset(
            scheduledFor = scheduledFor,
            adjacentScheduledFor = nextScheduledFor
        )
    }
}

private const val MaxLookbackForAdjacentDoseDays = 90L
private val ScheduleFulfillmentMaxOffset = Duration.ofHours(24)
