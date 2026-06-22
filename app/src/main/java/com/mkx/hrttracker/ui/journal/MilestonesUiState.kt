package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.journal.Milestones
import com.mkx.hrttracker.model.journal.dayCount
import java.time.LocalDate

data class TimelineNodeUiState(
    val anchor: AnchorRowUiState,
    val isPinned: Boolean,
)

data class MilestonesUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val hero: AnchorRowUiState? = null,
    val heroNextMilestone: NextMilestoneUiState? = null,
    val pinnedTray: List<AnchorRowUiState> = emptyList(),
    val timeline: List<TimelineNodeUiState> = emptyList(),
    val todayDividerIndex: Int = 0,
    val isEditMode: Boolean = false,
)

data class NextMilestoneUiState(
    val remainingDays: Long,
    val value: Long,
    val unit: MilestoneUnit,
)

// The next (or just-reached) milestone for the hero anchor, used by both the Milestones page
// and the top-level Journal page hero card. Returns null when the anchor has no upcoming or
// current milestone.
fun nextMilestoneUiState(anchorDate: LocalDate, today: LocalDate): NextMilestoneUiState? {
    val currentDayCount = dayCount(anchorDate, today)
    return (Milestones.current(anchorDate, today) ?: Milestones.next(anchorDate, today))?.let { milestone ->
        NextMilestoneUiState(
            remainingDays = milestone.dayCount - currentDayCount.magnitude,
            value = milestone.value,
            unit = milestone.unit,
        )
    }
}
