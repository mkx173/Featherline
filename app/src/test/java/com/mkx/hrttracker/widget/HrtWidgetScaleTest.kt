package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrtWidgetScaleTest {
    @Test
    fun resolveWidgetBaselineHeightDp_floorsFirstSmallResizeHeightToReference() {
        assertEquals(276f, resolveWidgetBaselineHeightDp(storedDp = 0f, currentHeightDp = 110f))
    }

    @Test
    fun resolveWidgetBaselineHeightDp_recoversStoredSmallResizeHeight() {
        assertEquals(276f, resolveWidgetBaselineHeightDp(storedDp = 110f, currentHeightDp = 110f))
    }

    @Test
    fun resolveWidgetBaselineHeightDp_preservesStoredTargetCellHeightAcrossResize() {
        assertEquals(320f, resolveWidgetBaselineHeightDp(storedDp = 320f, currentHeightDp = 110f))
    }

    @Test
    fun resolveWidgetBaselineHeightDp_ignoresTransientZeroHeight() {
        assertEquals(276f, resolveWidgetBaselineHeightDp(storedDp = 0f, currentHeightDp = 0f))
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_repairsSmallStoredBaselineOnSaneFrame() {
        assertTrue(
            shouldPersistWidgetBaselineHeight(
                storedDp = 110f,
                currentHeightDp = 110f,
                resolvedDp = 276f,
            )
        )
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_doesNotPersistFirstSmallResizeHeight() {
        assertFalse(
            shouldPersistWidgetBaselineHeight(
                storedDp = 0f,
                currentHeightDp = 110f,
                resolvedDp = 276f,
            )
        )
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_persistsFirstTargetCellHeight() {
        assertTrue(
            shouldPersistWidgetBaselineHeight(
                storedDp = 0f,
                currentHeightDp = 320f,
                resolvedDp = 320f,
            )
        )
    }

    @Test
    fun shouldPersistWidgetBaselineHeight_doesNotPersistTransientFallback() {
        assertFalse(
            shouldPersistWidgetBaselineHeight(
                storedDp = 0f,
                currentHeightDp = 0f,
                resolvedDp = 276f,
            )
        )
    }
}
