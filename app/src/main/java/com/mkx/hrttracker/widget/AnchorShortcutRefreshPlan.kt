package com.mkx.hrttracker.widget

// Pure split of the currently-pinned anchor shortcut ids against the live anchor ids:
//  - toUpdate  = pinned ids that still map to a live anchor → regenerate the bitmap.
//  - toEnable  = disabled pinned ids whose anchor is live → re-enable. disableShortcuts
//                is sticky and updateShortcuts never re-enables, so this heals pins
//                wrongly disabled in the past.
//  - toDisable = pinned ids whose anchor was deleted → disable (grey out) so they stop
//                showing a stale count.
// No Android dependencies; the refresh manager (Task 7) supplies the id sets.
data class AnchorShortcutRefreshPlan(
    val toUpdate: Set<String>,
    val toEnable: Set<String>,
    val toDisable: Set<String>,
)

fun anchorShortcutRefreshPlan(
    pinnedIds: Set<String>,
    liveAnchorIds: Set<String>,
    disabledPinnedIds: Set<String> = emptySet(),
): AnchorShortcutRefreshPlan = AnchorShortcutRefreshPlan(
    toUpdate = pinnedIds intersect liveAnchorIds,
    toEnable = disabledPinnedIds intersect liveAnchorIds,
    toDisable = pinnedIds - liveAnchorIds,
)
