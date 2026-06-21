package com.mkx.hrttracker.model.journal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A derived, never-stored milestone for a past anchor. */
data class Milestone(val dayCount: Long, val label: String)

object Milestones {
    val ROUND_DAYS = listOf(100L, 200L, 300L, 500L, 800L, 1000L, 1500L)
    val ANNIVERSARY_MONTHS = listOf(6, 12, 18, 24, 30, 36)

    fun next(date: LocalDate, today: LocalDate): Milestone? {
        val current = dayCount(date = date, today = today)
        if (current.isFuture) return null

        val roundMilestones = ROUND_DAYS.map { days ->
            Milestone(dayCount = days, label = "$days days")
        }
        val anniversaryMilestones = ANNIVERSARY_MONTHS.map { months ->
            val anniversaryDate = date.plusMonths(months.toLong())
            Milestone(
                dayCount = ChronoUnit.DAYS.between(date, anniversaryDate),
                label = "$months months",
            )
        }

        return (roundMilestones + anniversaryMilestones)
            .sortedBy { it.dayCount }
            .firstOrNull { it.dayCount > current.magnitude }
    }
}
