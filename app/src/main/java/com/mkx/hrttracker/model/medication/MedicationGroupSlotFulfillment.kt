package com.mkx.hrttracker.model.medication

import java.time.LocalDateTime
import java.util.UUID

/**
 * Query key for slot-level fulfillment lookups. Nullable [scheduleTimeUuid]
 * because callers asking about ad-hoc logged slots (entries that don't match
 * any current schedule entry) pass `null` — the predicate falls back to
 * exact-time matching in that case.
 *
 * Distinct from [MedicationGroupSlotOccurrence], which is what the schedule
 * generator emits (always has a uuid).
 */
internal data class MedicationGroupSlotKey(
    val scheduleTimeUuid: UUID?,
    val scheduledFor: LocalDateTime,
)
