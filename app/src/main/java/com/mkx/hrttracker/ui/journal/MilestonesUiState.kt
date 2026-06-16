package com.mkx.hrttracker.ui.journal

import java.time.LocalDate

data class TimelineNodeUiState(
    val anchor: AnchorRowUiState,
    val isPinned: Boolean,
)

data class MilestonesUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val hero: AnchorRowUiState? = null,
    val heroNextMilestoneLabel: String? = null,
    val pinnedTray: List<AnchorRowUiState> = emptyList(),
    val timeline: List<TimelineNodeUiState> = emptyList(),
    val todayDividerIndex: Int = 0,
    val isEditMode: Boolean = false,
)
