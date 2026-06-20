package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.journal.dayCount
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.time.LocalDate

data class AnchorRowUiState(
    val id: String,
    val name: String,
    val icon: AnchorIcon,
    val palette: MedicationGroupColorKey?,
    val date: LocalDate,
    val dayMagnitude: Long,
    val isFuture: Boolean,
    val heroBackground: HeroBackground = HeroBackground.DateColor,
)

data class JournalUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val hasTrackedDates: Boolean = false,
    val pinnedAnchors: List<AnchorRowUiState> = emptyList(),
    val recentNotes: List<Note> = emptyList(),
    val todayNote: Note? = null,
    val olderNotesCount: Int = 0,
) {
    val hasAnchors: Boolean get() = pinnedAnchors.isNotEmpty()
}

internal fun TrackedDate.toAnchorRowUiState(today: LocalDate): AnchorRowUiState {
    val count = dayCount(date, today)
    return AnchorRowUiState(
        id = id,
        name = name,
        icon = icon,
        palette = palette,
        date = date,
        dayMagnitude = count.magnitude,
        isFuture = count.isFuture,
        heroBackground = heroBackground,
    )
}
