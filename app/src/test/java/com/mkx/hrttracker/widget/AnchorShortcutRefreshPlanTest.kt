package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorShortcutRefreshPlanTest {
    @Test fun `updates the intersection of pinned and live`() {
        val plan = anchorShortcutRefreshPlan(
            pinnedIds = setOf("a", "b", "c"),
            liveAnchorIds = setOf("b", "c", "d"),
        )
        assertEquals(setOf("b", "c"), plan.toUpdate)
    }

    @Test fun `disables pinned ids whose anchor no longer exists`() {
        // Intent: a deleted anchor must not keep a stale day count on the home screen —
        // its orphaned shortcut is greyed out instead.
        val plan = anchorShortcutRefreshPlan(
            pinnedIds = setOf("a", "b"),
            liveAnchorIds = setOf("b"),
        )
        assertEquals(setOf("a"), plan.toDisable)
    }

    @Test fun `no pinned shortcuts yields empty plan`() {
        val plan = anchorShortcutRefreshPlan(emptySet(), setOf("a", "b"))
        assertEquals(emptySet<String>(), plan.toUpdate)
        assertEquals(emptySet<String>(), plan.toDisable)
    }

    @Test fun `all anchors deleted disables every pinned shortcut`() {
        val plan = anchorShortcutRefreshPlan(setOf("a", "b"), emptySet())
        assertEquals(emptySet<String>(), plan.toUpdate)
        assertEquals(setOf("a", "b"), plan.toDisable)
    }
}
