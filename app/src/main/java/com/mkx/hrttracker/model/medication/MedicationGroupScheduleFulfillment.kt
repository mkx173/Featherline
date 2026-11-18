package com.mkx.hrttracker.model.medication

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

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

/**
 * Half the real time to [adjacentScheduledFor] in [zoneId], capped at a day. Two slots the
 * spring-forward gap puts on one instant (02:30 and 03:30) keep their wall-clock gap.
 */
fun scheduleFulfillmentAllowedOffset(
    scheduledFor: LocalDateTime,
    adjacentScheduledFor: LocalDateTime?,
    zoneId: ZoneId,
): Duration {
    val halfTimeToAdjacentDose = adjacentScheduledFor
        ?.let { adjacentDose ->
            Duration.between(scheduledFor.atZone(zoneId), adjacentDose.atZone(zoneId)).abs()
                .takeIf { duration -> !duration.isZero }
                ?: Duration.between(scheduledFor, adjacentDose).abs()
        }
        ?.takeIf { duration -> !duration.isZero }
        ?.dividedBy(2)

    return if (halfTimeToAdjacentDose != null && halfTimeToAdjacentDose < ScheduleFulfillmentMaxOffset) {
        halfTimeToAdjacentDose
    } else {
        ScheduleFulfillmentMaxOffset
    }
}

/**
 * Whether [appliedAt] is nearer [scheduledFor] than its neighbours, in real minutes as the
 * offset badge counts them: the slots are resolved in [appliedAt]'s zone ([scheduleOffsetMinutes]).
 */
fun isWithinScheduleFulfillmentWindow(
    scheduledFor: LocalDateTime,
    appliedAt: ZonedDateTime,
    previousScheduledFor: LocalDateTime?,
    nextScheduledFor: LocalDateTime?,
): Boolean {
    val zoneId = appliedAt.zone
    val offset = Duration.between(scheduledFor.atZone(zoneId), appliedAt)
    return if (offset.isNegative) {
        offset.abs() < scheduleFulfillmentAllowedOffset(
            scheduledFor = scheduledFor,
            adjacentScheduledFor = previousScheduledFor ?: nextScheduledFor,
            zoneId = zoneId,
        )
    } else {
        offset < scheduleFulfillmentAllowedOffset(
            scheduledFor = scheduledFor,
            adjacentScheduledFor = nextScheduledFor,
            zoneId = zoneId,
        )
    }
}

private const val MaxLookbackForAdjacentDoseDays = 90L
private val ScheduleFulfillmentMaxOffset = Duration.ofHours(24)
