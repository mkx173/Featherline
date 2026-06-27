package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.journal.Milestones
import com.mkx.hrttracker.model.journal.dayCount
import java.time.LocalDate

// The ≤~3-char label drawn on the pinned shortcut icon. Once at least one anniversary
// year has completed it rolls to "${years}y" (space-constrained); otherwise it shows the
// signed-magnitude day count. Sign is intentionally omitted — the glyph/colour carry
// identity and there is no room for a "-" on the icon (spec § Day-count rendering).
fun anchorIconLabel(date: LocalDate, today: LocalDate): String {
    val years = Milestones.completedYears(date, today)
    if (years >= 1L) return "${years}y"
    return dayCount(date = date, today = today).magnitude.toString()
}
