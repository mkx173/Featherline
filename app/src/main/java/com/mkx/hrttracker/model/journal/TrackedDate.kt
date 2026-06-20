package com.mkx.hrttracker.model.journal

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.time.LocalDate

/**
 * A user-defined date counted up (past) or down (future). `palette` reuses the
 * medication-group hue set and carries no semantic meaning. `pinnedOrder`
 * null = unpinned; ascending non-null values are the tray order.
 */
data class TrackedDate(
    val id: String,
    val name: String,
    val icon: AnchorIcon,
    val date: LocalDate,
    val palette: MedicationGroupColorKey?,
    val heroBackground: HeroBackground = HeroBackground.DateColor,
    val pinnedOrder: Int?,
    val createdAtEpochMillis: Long = 0L,
)
