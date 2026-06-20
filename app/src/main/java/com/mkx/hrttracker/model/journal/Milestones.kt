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
) {
    val label: String
        get() = when (unit) {
            MilestoneUnit.DAYS -> "$value days"
            MilestoneUnit.YEARS -> "$value years"
        }
}

object Milestones {
    /** Early day-count markers, before the schedule switches to yearly anniversaries. */
    val DAY_MARKERS = listOf(7L, 30L, 60L, 90L, 100L, 180L)

    /**
     * Yearly anniversaries are generated up to this many years. The cap only needs to
     * stay ahead of any realistic anchor age so [next] always has an upcoming goal.
     */
    const val MAX_ANNIVERSARY_YEARS = 50

    fun current(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        return milestonesFor(date)
            .firstOrNull { it.dayCount == current.magnitude }
    }

    fun next(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        return milestonesFor(date)
            .firstOrNull { it.dayCount > current.magnitude }
    }

    private fun milestonesFor(date: LocalDate): List<Milestone> {
        val dayMilestones = DAY_MARKERS.map { days ->
            Milestone(dayCount = days, value = days, unit = MilestoneUnit.DAYS)
        }
        val anniversaryMilestones = (1..MAX_ANNIVERSARY_YEARS).map { years ->
            val anniversaryDate = date.plusYears(years.toLong())
            Milestone(
                dayCount = ChronoUnit.DAYS.between(date, anniversaryDate),
                value = years.toLong(),
                unit = MilestoneUnit.YEARS,
            )
        }

        return (dayMilestones + anniversaryMilestones)
            .sortedBy { it.dayCount }
    }
}
