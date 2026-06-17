package com.mkx.hrttracker.model.journal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class MilestoneUnit {
    DAYS,
    MONTHS,
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
            MilestoneUnit.MONTHS -> "$value months"
        }
}

object Milestones {
    val ROUND_DAYS = listOf(100L, 200L, 300L, 500L, 800L, 1000L, 1500L)
    val ANNIVERSARY_MONTHS = listOf(6, 12, 18, 24, 30, 36)

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
        val roundMilestones = ROUND_DAYS.map { days ->
            Milestone(dayCount = days, value = days, unit = MilestoneUnit.DAYS)
        }
        val anniversaryMilestones = ANNIVERSARY_MONTHS.map { months ->
            val anniversaryDate = date.plusMonths(months.toLong())
            Milestone(
                dayCount = ChronoUnit.DAYS.between(date, anniversaryDate),
                value = months.toLong(),
                unit = MilestoneUnit.MONTHS,
            )
        }

        return (roundMilestones + anniversaryMilestones)
            .sortedBy { it.dayCount }
    }
}
