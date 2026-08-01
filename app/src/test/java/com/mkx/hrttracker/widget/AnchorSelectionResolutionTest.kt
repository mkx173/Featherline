package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.TrackedDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// The config screen seeds selectedAnchorId straight from the persisted per-widget prefs
// with no membership check. If the anchor was deleted in-app after the widget was
// configured, that id is DANGLING: it must resolve to null so Save stays disabled — a
// "successful" save that rewrote the stale id would leave the widget stuck on its empty
// state. resolveSelectedAnchor is the seam Save's enable predicate gates on, so it must
// return null for a dangling id and never falsely re-enable Save.
class AnchorSelectionResolutionTest {

    private fun anchor(id: String) = TrackedDate(
        id = id,
        name = "Anchor $id",
        icon = AnchorIcon.entries.first(),
        date = LocalDate.of(2026, 6, 1),
        palette = null,
        pinnedOrder = null,
    )

    @Test
    fun danglingIdInNonEmptyListDoesNotResolve() {
        val anchors = listOf(anchor("a1"), anchor("a2"))
        assertNull(resolveSelectedAnchor(anchors, "deleted"))
    }

    @Test
    fun presentIdResolvesToItsAnchor() {
        val anchors = listOf(anchor("a1"), anchor("a2"))
        assertEquals("a2", resolveSelectedAnchor(anchors, "a2")?.id)
    }

    @Test
    fun nullIdDoesNotResolve() {
        assertNull(resolveSelectedAnchor(listOf(anchor("a1")), null))
    }

    @Test
    fun anyIdAgainstEmptyListDoesNotResolve() {
        assertNull(resolveSelectedAnchor(emptyList(), "a1"))
    }

    @Test
    fun singleAnchorIsAutomaticallySelectedInAnchorConfig() {
        assertEquals(
            "a1",
            autoSelectSingleAnchorId(
                configType = WidgetConfigType.ANCHOR,
                anchors = listOf(anchor("a1")),
                selectedAnchorId = null,
            ),
        )
    }

    @Test
    fun automaticSelectionDoesNotOverrideExistingChoice() {
        assertEquals(
            "selected",
            autoSelectSingleAnchorId(
                configType = WidgetConfigType.ANCHOR,
                anchors = listOf(anchor("a1")),
                selectedAnchorId = "selected",
            ),
        )
    }

    @Test
    fun singleAnchorIsNotAutomaticallySelectedForDoseWidgetConfig() {
        assertNull(
            autoSelectSingleAnchorId(
                configType = WidgetConfigType.MEDIUM,
                anchors = listOf(anchor("a1")),
                selectedAnchorId = null,
            )
        )
    }
}
