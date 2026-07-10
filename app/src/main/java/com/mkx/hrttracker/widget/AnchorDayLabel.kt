package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.journal.dayCount
import java.time.LocalDate

// The label drawn on the pinned shortcut icon: always the unsigned day count, even past
// a year — the renderer measures the string and shrinks the font to fit the safe zone.
// Sign is intentionally omitted — the colour carries identity and there is no room for
// a "-" on the icon.
fun anchorIconLabel(date: LocalDate, today: LocalDate): String =
    dayCount(date = date, today = today).magnitude.toString()
