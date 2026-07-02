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

    @Test fun `re-enables disabled pinned ids whose anchor still lives`() {
        // Intent: disableShortcuts is sticky and updateShortcuts never re-enables, so a
        // shortcut wrongly disabled (e.g. by a past cold-start race) must heal on the
        // next refresh once its anchor is confirmed live.
        val plan = anchorShortcutRefreshPlan(
            pinnedIds = setOf("a", "b"),
            liveAnchorIds = setOf("a", "b"),
            disabledPinnedIds = setOf("a"),
        )
        assertEquals(setOf("a"), plan.toEnable)
    }

    @Test fun `disabled pin whose anchor was deleted stays disabled`() {
        val plan = anchorShortcutRefreshPlan(
            pinnedIds = setOf("a"),
            liveAnchorIds = emptySet(),
            disabledPinnedIds = setOf("a"),
        )
        assertEquals(emptySet<String>(), plan.toEnable)
        assertEquals(setOf("a"), plan.toDisable)
    }
}
