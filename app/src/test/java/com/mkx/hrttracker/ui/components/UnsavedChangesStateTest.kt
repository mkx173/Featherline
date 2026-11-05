package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedChangesStateTest {

    @Test
    fun leave_runsAtOnceWithoutUnsavedChanges() {
        val state = UnsavedChangesState()
        var left = 0
        state.update(Any(), dirty = false)
        state.leave { left++; true }
        assertEquals(1, left)
        assertNull(state.pendingLeave)
    }

    @Test
    fun leave_waitsForDiscard() {
        val state = UnsavedChangesState()
        var left = 0
        state.update(Any(), dirty = true)
        state.leave { left++; true }
        assertEquals(0, left)
        state.discard()
        assertEquals(1, left)
        assertNull(state.pendingLeave)
    }

    @Test
    fun keepEditing_dropsTheLeave() {
        val state = UnsavedChangesState()
        var left = 0
        state.update(Any(), dirty = true)
        state.leave { left++; true }
        state.keepEditing(state.pendingLeave!!)
        state.discard()
        assertEquals(0, left)
    }

    @Test
    fun keepEditing_leavesALeaveThatReplacedIt() {
        // Back's dialog is open when a Home deep link asks again: the old dialog's dismissal
        // must not cancel the deep link's leave.
        val state = UnsavedChangesState()
        var wentHome = 0
        state.update(Any(), dirty = true)
        state.leave { true }
        val backLeave = state.pendingLeave!!
        state.leave { wentHome++; true }
        state.keepEditing(backLeave)
        state.discard()
        assertEquals(1, wentHome)
    }

    @Test
    fun pendingLeave_isDroppedWhenItsEditorFinishes() {
        // Back tapped while saving: the save leaves the editor, and nothing is left to discard.
        val state = UnsavedChangesState()
        val editor = Any()
        var left = 0
        state.update(editor, dirty = true)
        state.leave { left++; true }
        state.update(editor, dirty = false)
        assertNull(state.pendingLeave)
        state.remove(editor)
        state.discard()
        assertEquals(0, left)
    }

    @Test
    fun discardedEditor_stopsCountingWhileItAnimatesOut() {
        // Its ViewModel lives through the exit, still reporting its edits.
        val state = UnsavedChangesState()
        val editor = Any()
        var left = 0
        state.update(editor, dirty = true)
        state.leave { left++; true }
        state.discard()
        assertEquals(1, left)
        state.update(editor, dirty = true)
        assertFalse(state.hasUnsavedChanges)
        state.leave { left++; true }
        assertEquals(2, left)
        state.remove(editor)
        state.update(editor, dirty = true)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun discardThatDidNotLeave_keepsAsking() {
        // The pop refused mid-transition: the editor is still there, edits and all.
        val state = UnsavedChangesState()
        val editor = Any()
        state.update(editor, dirty = true)
        state.leave { false }
        state.discard()
        assertTrue(state.asks(editor))
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun editorOutsideTheBackStack_stopsCountingOnceGone() {
        // Onboarding's editor ViewModel outlives it, under the activity.
        val state = UnsavedChangesState()
        val editor = Any()
        state.update(editor, dirty = true)
        state.leave { true }
        state.stopTracking(editor)
        state.update(editor, dirty = true)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.pendingLeave)
    }
}
