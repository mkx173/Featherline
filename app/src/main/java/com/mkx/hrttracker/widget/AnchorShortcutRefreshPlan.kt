package com.mkx.hrttracker.widget

// Pure split of the currently-pinned anchor shortcut ids against the live anchor ids:
//  - toUpdate  = pinned ids that still map to a live anchor → regenerate the bitmap.
//  - toDisable = pinned ids whose anchor was deleted → disable (grey out) so they stop
//                showing a stale count.
// No Android dependencies; the refresh manager (Task 7) supplies the two id sets.
data class AnchorShortcutRefreshPlan(
    val toUpdate: Set<String>,
    val toDisable: Set<String>,
)

fun anchorShortcutRefreshPlan(
    pinnedIds: Set<String>,
    liveAnchorIds: Set<String>,
): AnchorShortcutRefreshPlan = AnchorShortcutRefreshPlan(
    toUpdate = pinnedIds intersect liveAnchorIds,
    toDisable = pinnedIds - liveAnchorIds,
)
