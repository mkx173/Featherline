package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrtWidgetScaleTest {
    @Test
    fun resolveWidgetBaselineHeightDp_locksFirstSaneRenderToItsRealHeight() {
        // The first render is the launcher's target-cell allocation; it becomes the
        // device baseline verbatim so content scales to the device's actual cell size.
        assertEquals(210f, resolveWidgetBaselineHeightDp(storedDp = 0f, currentHeightDp = 210f))
    }

    @Test
    fun resolveWidgetBaselineHeightDp_locksFirstSaneRenderEvenBelowReference() {
        // Regression: a sub-reference cell must still lock. Otherwise the baseline never
        // persists and every resize re-derives the scale from the live size.
        assertEquals(150f, resolveWidgetBaselineHeightDp(storedDp = 0f, currentHeightDp = 150f))
    }

    @Test
    fun resolveWidgetBaselineHeightDp_keepsStoredBaselineWhenResized() {
        // A stored baseline is reused forever, so resizing relayouts the frame without
        // rescaling the content.
        assertEquals(320f, resolveWidgetBaselineHeightDp(storedDp = 320f, currentHeightDp = 110f))
    }

    @Test
    fun resolveWidgetBaselineHeightDp_fallsBackToReferenceForTransientZeroHeight() {
        // A 0dp loading frame must not lock the baseline; fall back to reference until
        // a sane render arrives.
        assertEquals(276f, resolveWidgetBaselineHeightDp(storedDp = 0f, currentHeightDp = 0f))
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_persistsFirstSaneRender() {
        assertTrue(shouldPersistWidgetBaselineHeight(storedDp = 0f, currentHeightDp = 210f))
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_persistsFirstSaneRenderBelowReference() {
        // The bug fix: sub-reference first renders must persist so the baseline locks
        // instead of every render re-deriving the scale from the live (resized) size.
        assertTrue(shouldPersistWidgetBaselineHeight(storedDp = 0f, currentHeightDp = 150f))
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_neverOverwritesAStoredBaseline() {
        // Once locked, a resize must not re-persist — that is what keeps scale stable.
        assertFalse(shouldPersistWidgetBaselineHeight(storedDp = 320f, currentHeightDp = 110f))
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_doesNotPersistTransientZeroHeight() {
        assertFalse(shouldPersistWidgetBaselineHeight(storedDp = 0f, currentHeightDp = 0f))
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_doesNotPersistOutOfRangeHeight() {
        assertFalse(shouldPersistWidgetBaselineHeight(storedDp = 0f, currentHeightDp = 999f))
    }

    @Test
    fun mergeWidgetBaselineHeightDp_keepsTallerOfStoredAndCurrent() {
        assertEquals(226f, mergeWidgetBaselineHeightDp(existingDp = 0f, currentHeightDp = 226f))
        assertEquals(226f, mergeWidgetBaselineHeightDp(existingDp = 226f, currentHeightDp = 112f))
        assertEquals(300f, mergeWidgetBaselineHeightDp(existingDp = 226f, currentHeightDp = 300f))
    }

    @Test
    fun widgetBaselineScaleRatio_leavesNormalCellRatiosUntouched() {
        // A normally placed cell (sub-reference but sane) keeps its real ratio so content
        // fits the device's actual cell size — the floor must not oversize it.
        assertEquals(150f / 276f, widgetBaselineScaleRatio(150f), 1e-6f)
        assertEquals(1f, widgetBaselineScaleRatio(276f), 1e-6f)
    }

    @Test
    fun widgetBaselineScaleRatio_floorsPathologicallySmallBaseline() {
        // Defensive guard: an unexpectedly tiny captured baseline would collapse content
        // to an illegible size, so the device-baseline component is floored.
        assertEquals(0.35f, widgetBaselineScaleRatio(50f), 1e-6f)
        assertEquals(0.35f, widgetBaselineScaleRatio(80f), 1e-6f)
    }

    @Test
    fun firstUpdateBatch_locksBaselineToTallestSizeComposition() {
        // SizeMode.Exact composes the widget once per size (portrait + landscape) in a
        // single update; every composition reads storedDp before any persists, so the
        // persists must merge by max. Otherwise whichever composition's SideEffect runs
        // last wins — and the short landscape composition makes the widget render tiny.
        val portraitShort = 225f
        val portraitTall = 226f
        val landscape = 112f
        var stored = 0f
        for (height in listOf(portraitShort, portraitTall, landscape)) {
            // Each composition in the batch reads the same storedDp = 0.
            assertTrue(shouldPersistWidgetBaselineHeight(storedDp = 0f, currentHeightDp = height))
            // Their SideEffects then run in order, each merging into the stored value.
            stored = mergeWidgetBaselineHeightDp(existingDp = stored, currentHeightDp = height)
        }
        assertEquals(portraitTall, stored)
    }
}
