package com.mkx.hrttracker.widget

import com.mkx.hrttracker.R

// Today-panel count/state derivation for the home widgets. Kept as pure functions
// (no Glance/Compose) so the "no plan, only a manual record" cases that the inline
// composable logic previously mishandled can be locked down with unit tests.

/**
 * Medium widget top-panel count, split into a large "hero" number and a smaller suffix.
 * Mirrors the home screen's compact "x/y (z)" logic: the planned "x/y" only appears when
 * something is scheduled, the manual "(z)" only when manual records exist. A day with no
 * plan but a manual record falls back to "x MANUAL" rather than a lone "(z)".
 *
 * - plan present:    hero = doneCount,   suffix = "/total[ (manual)] DONE"  ("2" + "/3 (1) DONE")
 * - manual-only day: hero = manualCount, suffix = " MANUAL"                 ("1" + " MANUAL")
 * - nothing at all:  null — the caller hides the count row entirely.
 */
internal data class WidgetMediumCount(val hero: String, val suffix: String)

internal fun widgetMediumCount(
    doneCount: Int,
    totalCount: Int,
    manualCount: Int,
    doneLabel: String,
    manualLabel: String,
): WidgetMediumCount? = when {
    totalCount > 0 -> {
        val manualPart = if (manualCount > 0) " ($manualCount)" else ""
        WidgetMediumCount(
            hero = doneCount.toString(),
            suffix = "/$totalCount$manualPart $doneLabel",
        )
    }

    manualCount > 0 -> WidgetMediumCount(hero = manualCount.toString(), suffix = " $manualLabel")
    else -> null
}

/**
 * Large widget header count fragment — "2/3 (1) DONE", "2/3 DONE", "1 MANUAL", or null
 * when there is nothing to count (no plan and no manual records). Shares
 * [widgetMediumCount]'s rules so both widgets read identically (the medium widget just
 * renders the leading number larger), so a plan-less day never shows a meaningless "0/0".
 */
internal fun widgetLargeCountLabel(
    doneCount: Int,
    totalCount: Int,
    manualCount: Int,
    doneLabel: String,
    manualLabel: String,
): String? = widgetMediumCount(doneCount, totalCount, manualCount, doneLabel, manualLabel)
    ?.let { it.hero + it.suffix }

/**
 * True when the medium widget should treat today as having no plan — i.e. there are no
 * scheduled (non-manual) rows to surface. Manual records are deliberately ignored: a day
 * with only manual records is still a "no doses scheduled today" day for the bottom panel
 * (the manual activity shows in the top count instead), matching a plan-less day with no
 * manual records. Scheduled last-night carry-overs and tonight's coming-up entries are
 * not manual records, so they keep this false.
 */
internal fun mediumNothingScheduledToday(doseRows: List<WidgetDoseRow>): Boolean =
    doseRows.none { !it.isManualRecord }

/**
 * Icon for the medium widget's bottom final-state badge. The emphatic "done_all" (✓✓) mark
 * is reserved for the best outcome — every dose taken within its window ([allInWindow]) — so
 * a perfect day reads more strongly than a lesser one. An empty day ([nothingScheduledToday])
 * and an everything-logged-but-off-window day ([everythingLogged]) share the plain check (✓);
 * anything else means a missed slot.
 *
 * Order matters: [allInWindow] implies [everythingLogged], so it must be checked first to win.
 */
internal fun mediumBadgeIconRes(
    allInWindow: Boolean,
    nothingScheduledToday: Boolean,
    everythingLogged: Boolean,
): Int = when {
    allInWindow -> R.drawable.ic_done_all
    nothingScheduledToday || everythingLogged -> R.drawable.ic_check
    else -> R.drawable.ic_exclamation
}
