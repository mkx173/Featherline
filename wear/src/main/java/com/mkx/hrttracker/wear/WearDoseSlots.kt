package com.mkx.hrttracker.wear

import com.mkx.hrttracker.wear.protocol.WearDoseRow
import com.mkx.hrttracker.wear.protocol.WearDoseSnapshot
import com.mkx.hrttracker.wear.protocol.WearDoseStatus
import com.mkx.hrttracker.wear.protocol.WearEstradiolSnapshot
import java.time.LocalDate
import java.time.LocalDateTime

data class WearDoseSlot(
    val groupUuid: String,
    val scheduleTimeUuid: String?,
    val scheduledAt: String,
    val groupName: String,
    val medicationSummary: String,
    val status: WearDoseStatus,
) {
    val actionToken: String
        get() = listOf(groupUuid, scheduleTimeUuid.orEmpty(), scheduledAt)
            .joinToString(ACTION_TOKEN_SEPARATOR)
}

fun WearDoseSnapshot.doseSlots(): List<WearDoseSlot> =
    rows
        .filter { it.groupUuid != null }
        .groupBy { row ->
            Triple(row.groupUuid.orEmpty(), row.scheduleTimeUuid, row.scheduledAt)
        }
        .map { (key, rows) ->
            WearDoseSlot(
                groupUuid = key.first,
                scheduleTimeUuid = key.second,
                scheduledAt = key.third,
                groupName = rows.first().groupName,
                medicationSummary = rows.joinToString(separator = " · ") {
                    buildMedicationSummary(it)
                },
                status = rows.minBy(::statusPriority).status,
            )
        }
        .sortedBy(WearDoseSlot::scheduledAt)

fun WearDoseSnapshot.todayDoseSlots(): List<WearDoseSlot> {
    val anchorDate = LocalDate.ofEpochDay(anchorDateEpochDay)
    return doseSlots().filter { slot ->
        runCatching { LocalDateTime.parse(slot.scheduledAt).toLocalDate() }
            .getOrNull() == anchorDate
    }
}

fun WearDoseSnapshot.nextActionableSlot(
    excludedActionTokens: Set<String> = emptySet(),
): WearDoseSlot? {
    val slots = doseSlots().filter { it.actionToken !in excludedActionTokens }
    return slots.firstOrNull { it.status == WearDoseStatus.DUE_SOON }
        ?: slots.lastOrNull { it.status == WearDoseStatus.OVERDUE }
        ?: slots.firstOrNull { it.status == WearDoseStatus.UPCOMING }
}

fun WearDoseSnapshot.nextPlanSlots(
    limit: Int = 5,
    excludedActionTokens: Set<String> = emptySet(),
): List<WearDoseSlot> =
    doseSlots()
        .filter { slot ->
            slot.actionToken !in excludedActionTokens && slot.isQuickLoggable()
        }
        .sortedBy(WearDoseSlot::scheduledAt)
        .take(limit)

fun WearDoseSnapshot.findSlot(actionToken: String): WearDoseSlot? =
    doseSlots().firstOrNull { it.actionToken == actionToken }

fun WearDoseSlot.isQuickLoggable(): Boolean =
    status == WearDoseStatus.DUE_SOON ||
            status == WearDoseStatus.OVERDUE ||
            status == WearDoseStatus.UPCOMING

fun WearDoseSlot.scheduledTimeText(): String =
    runCatching {
        LocalDateTime.parse(scheduledAt).toLocalTime().toString().take(5)
    }.getOrDefault(scheduledAt)

fun WearDoseSlot.scheduledDateTimeText(anchorDateEpochDay: Long): String =
    runCatching {
        val scheduled = LocalDateTime.parse(scheduledAt)
        if (scheduled.toLocalDate().toEpochDay() == anchorDateEpochDay) {
            scheduled.toLocalTime().toString().take(5)
        } else {
            "%02d-%02d %s".format(
                scheduled.monthValue,
                scheduled.dayOfMonth,
                scheduled.toLocalTime().toString().take(5),
            )
        }
    }.getOrDefault(scheduledAt)

fun WearEstradiolSnapshot.normalizedSamples(): List<Float> {
    if (samples.isEmpty()) return emptyList()
    val minimum = samples.min()
    val maximum = samples.max()
    val range = maximum - minimum
    if (!range.isFinite() || range <= 0.0) return List(samples.size) { 0.5f }
    return samples.map { value ->
        ((value - minimum) / range).coerceIn(0.0, 1.0).toFloat()
    }
}

private fun buildMedicationSummary(row: WearDoseRow): String =
    listOf(row.medicationName, row.doseText)
        .filter(String::isNotBlank)
        .joinToString(" ")

private fun statusPriority(row: WearDoseRow): Int = when (row.status) {
    WearDoseStatus.DUE_SOON -> 0
    WearDoseStatus.OVERDUE -> 1
    WearDoseStatus.UPCOMING -> 2
    WearDoseStatus.DONE -> 3
    WearDoseStatus.LOGGED_OUT_OF_WINDOW -> 4
}

private const val ACTION_TOKEN_SEPARATOR = "\u001f"
