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

/** Until noon of the current medication day: never between midnight and the 04:00 boundary. */
fun MedicationDayBoundary.isLastNightVisible(now: LocalDateTime): Boolean =
    now.isBefore(medicationDay(now, this).atTime(LastNightEndTime))

fun MedicationDayBoundary.lastNightMedicationDay(now: LocalDateTime): LocalDate =
    medicationDay(now, this).minusDays(1)

/**
 * Coming up previews the next medication day's first bucket during this one's last: from
 * 18:00 it shows the Midnight doses, and with the day ending at 04:00, from midnight it
 * shows the Morning ones, which would otherwise stay hidden until 04:00.
 */
fun MedicationDayBoundary.isComingUpVisible(now: LocalDateTime): Boolean =
    MedicationDayTimeRange.of(now.toLocalTime()) ==
        if (endsAtFourAm) MedicationDayTimeRange.MIDNIGHT else MedicationDayTimeRange.EVENING

/** The bucket Coming up shows: the next medication day's first. */
val MedicationDayBoundary.comingUpTimeRange: MedicationDayTimeRange
    get() = if (endsAtFourAm) MedicationDayTimeRange.MORNING else MedicationDayTimeRange.MIDNIGHT

fun MedicationDayBoundary.comingUpMedicationDay(now: LocalDateTime): LocalDate =
    medicationDay(now, this).plusDays(1)

fun MedicationDayBoundary.includesInLastNight(dateTime: LocalDateTime): Boolean {
    return when (MedicationDayTimeRange.of(dateTime.toLocalTime())) {
        MedicationDayTimeRange.EVENING -> true
        MedicationDayTimeRange.MIDNIGHT -> endsAtFourAm
        MedicationDayTimeRange.MORNING,
        MedicationDayTimeRange.AFTERNOON -> false
    }
}
