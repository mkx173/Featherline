package com.mkx.hrttracker.model.journal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class MilestoneUnit {
    DAYS,
    YEARS,
}

/** A derived, never-stored milestone for a past anchor. */
data class Milestone(
    val dayCount: Long,
    val value: Long,
    val unit: MilestoneUnit,
)

object Milestones {
    /** Early day-count markers; 100-multiples are covered by the every-100-days rule. */
    val DAY_MARKERS = listOf(7L, 30L, 60L, 90L, 180L)

    private const val HUNDRED_DAYS = 100L

    fun current(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        // An early day marker wins when today lands exactly on it.
        DAY_MARKERS.firstOrNull { it == current.magnitude }?.let { days ->
            return Milestone(dayCount = days, value = days, unit = MilestoneUnit.DAYS)
        }
        // A yearly anniversary outranks a 100-day multiple on the rare exact collision.
        val years = completedYears(date, today)
        if (years >= 1 && date.plusYears(years) == today) {
            return Milestone(dayCount = current.magnitude, value = years, unit = MilestoneUnit.YEARS)
        }
        // Every whole hundred days (100, 200, …) is a milestone, forever.
        if (current.magnitude > 0 && current.magnitude % HUNDRED_DAYS == 0L) {
            return Milestone(
                dayCount = current.magnitude,
                value = current.magnitude,
                unit = MilestoneUnit.DAYS,
            )
        }
        return null
    }

    fun next(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        // Three candidate schedules run side by side — early markers, 100-day
        // multiples, and yearly anniversaries — and the soonest one wins. On an
        // exact tie the anniversary outranks the day count.
        val nextYear = completedYears(date, today) + 1
        val anniversary = Milestone(
            dayCount = ChronoUnit.DAYS.between(date, date.plusYears(nextYear)),
            value = nextYear,
            unit = MilestoneUnit.YEARS,
        )
        val nextDays = minOf(
            DAY_MARKERS.firstOrNull { it > current.magnitude } ?: Long.MAX_VALUE,
            (current.magnitude / HUNDRED_DAYS + 1) * HUNDRED_DAYS,
        )
        if (nextDays >= anniversary.dayCount) return anniversary
        return Milestone(dayCount = nextDays, value = nextDays, unit = MilestoneUnit.DAYS)
    }

    // The largest N (>= 0) whose anniversary date.plusYears(N) is on or before today.
    // ChronoUnit.YEARS.between undercounts when an anniversary is shifted earlier — a
    // Feb 29 anchor adjusts to Feb 28 in common years, so between(2024-02-29, 2025-02-28)
    // is 0 even though the first anniversary has arrived. It never overcounts, so start
    // there and advance while the following anniversary has also passed. Future dates
    // (today before date) have completed zero anniversaries.
    fun completedYears(date: LocalDate, today: LocalDate): Long {
        if (today.isBefore(date)) return 0L
        var years = ChronoUnit.YEARS.between(date, today)
        while (!date.plusYears(years + 1).isAfter(today)) {
            years++
        }
        return years
    }
}
