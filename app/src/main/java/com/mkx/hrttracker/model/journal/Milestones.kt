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
    /** Early day-count markers, before the schedule switches to yearly anniversaries. */
    val DAY_MARKERS = listOf(7L, 30L, 60L, 90L, 100L, 180L)

    fun current(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        // A day marker (e.g. day 100) wins when today lands exactly on it; all day
        // markers fall before the first anniversary.
        DAY_MARKERS.firstOrNull { it == current.magnitude }?.let { days ->
            return Milestone(dayCount = days, value = days, unit = MilestoneUnit.DAYS)
        }
        // Otherwise it is a milestone only if today is exactly a yearly anniversary.
        val years = completedYears(date, today)
        if (years >= 1 && date.plusYears(years) == today) {
            return Milestone(dayCount = current.magnitude, value = years, unit = MilestoneUnit.YEARS)
        }
        return null
    }

    fun next(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        // The next early marker, while any remains before the yearly cadence begins.
        DAY_MARKERS.firstOrNull { it > current.magnitude }?.let { days ->
            return Milestone(dayCount = days, value = days, unit = MilestoneUnit.DAYS)
        }
        // Past the last day marker the schedule is yearly anniversaries, computed on
        // demand so there is always an upcoming goal no matter how old the anchor is.
        val nextYear = completedYears(date, today) + 1
        val anniversary = date.plusYears(nextYear)
        return Milestone(
            dayCount = ChronoUnit.DAYS.between(date, anniversary),
            value = nextYear,
            unit = MilestoneUnit.YEARS,
        )
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
