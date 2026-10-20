package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Fixed Home/widget buckets. Midnight is the first bucket when the day starts at 00:00 and the last when it starts at 04:00. */
enum class MedicationDayTimeRange(val startHour: Int, val endHour: Int) {
    MIDNIGHT(0, 4),
    MORNING(4, 12),
    AFTERNOON(12, 18),
    EVENING(18, 24);

    companion object {
        fun of(time: LocalTime): MedicationDayTimeRange = when (time.hour) {
            in MIDNIGHT.startHour until MIDNIGHT.endHour -> MIDNIGHT
            in MORNING.startHour until MORNING.endHour -> MORNING
            in AFTERNOON.startHour until AFTERNOON.endHour -> AFTERNOON
            else -> EVENING
        }
    }
}

private val LastNightEndTime = LocalTime.NOON
private val TonightStartTime = LocalTime.of(18, 0)

/** Home and the widget share these section windows. */
fun MedicationDayBoundary.isLastNightVisible(now: LocalDateTime): Boolean =
    now.toLocalTime().isBefore(LastNightEndTime)

fun MedicationDayBoundary.lastNightMedicationDay(now: LocalDateTime): LocalDate =
    medicationDay(now, this).minusDays(1)

fun MedicationDayBoundary.isTonightVisible(now: LocalDateTime): Boolean =
    !endsAtFourAm && !now.toLocalTime().isBefore(TonightStartTime)

/** Midnight boundary only: the tomorrow whose Midnight doses Tonight shows. */
fun MedicationDayBoundary.tonightMedicationDay(now: LocalDateTime): LocalDate =
    medicationDay(now, this).plusDays(1)

/** Evening is always last night; the 04:00 boundary adds its trailing Midnight. */
fun MedicationDayBoundary.includesInLastNight(dateTime: LocalDateTime): Boolean {
    return when (MedicationDayTimeRange.of(dateTime.toLocalTime())) {
        MedicationDayTimeRange.EVENING -> true
        MedicationDayTimeRange.MIDNIGHT -> endsAtFourAm
        MedicationDayTimeRange.MORNING,
        MedicationDayTimeRange.AFTERNOON -> false
    }
}
