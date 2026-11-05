package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedChangesStateTest {

    private fun UnsavedChangesState.editorInBackStack(dirty: Boolean = true): Any =
        Any().also { editor ->
            hostInBackStack(editor)
            update(editor, dirty)
        }

    @Test
    fun leave_runsAtOnceWithoutUnsavedChanges() {
        val state = UnsavedChangesState()
        val editor = state.editorInBackStack(dirty = false)
        var left = 0
        assertNull(state.leave(setOf(editor)) { left++; true })
        assertEquals(1, left)
        assertNull(state.pendingLeave)
    }

    @Test
    fun leave_waitsForDiscard() {
        val state = UnsavedChangesState()
        val editor = state.editorInBackStack()
        var left = 0
        assertNotNull(state.leave(setOf(editor)) { left++; true })
        assertEquals(0, left)
        state.discard()
        assertEquals(1, left)
        assertNull(state.pendingLeave)
    }

    @Test
    fun keepEditing_dropsTheLeave() {
        val state = UnsavedChangesState()
        val editor = state.editorInBackStack()
        var left = 0
        state.keepEditing(state.leave(setOf(editor)) { left++; true }!!)
        state.discard()
        assertEquals(0, left)
    }

    @Test
    fun keepEditing_leavesALeaveThatReplacedIt() {
        // Back's dialog is open when a Home deep link asks again: the old dialog's dismissal
        // must not cancel the deep link's leave.
        val state = UnsavedChangesState()
        val editor = state.editorInBackStack()
        var wentHome = 0
        val backLeave = state.leave(setOf(editor)) { true }!!
        state.leaveBackStack { wentHome++; true }
        state.keepEditing(backLeave)
        state.discard()
        assertEquals(1, wentHome)
    }

    @Test
    fun pendingLeave_isDroppedWhenItsEditorFinishes() {
        // Back tapped while saving: the save leaves the editor, and nothing is left to discard.
        val state = UnsavedChangesState()
        val editor = state.editorInBackStack()
        var left = 0
        state.leave(setOf(editor)) { left++; true }
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
        val editor = state.editorInBackStack()
        var left = 0
        state.leaveBackStack { left++; true }
        state.discard()
        assertEquals(1, left)
        state.update(editor, dirty = true)
        assertFalse(state.hasUnsavedChanges)
        state.leaveBackStack { left++; true }
        assertEquals(2, left)
        state.remove(editor)
        state.update(editor, dirty = true)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun backThatDidNotNavigate_keepsAsking() {
        // The editor's back, as unsavedChangesBack leaves it: the pop refused mid-transition.
        val state = UnsavedChangesState()
        val editor = state.editorInBackStack()
        state.leave(setOf(editor)) { false }
        state.discard()
        assertTrue(state.asks(editor))
        assertNotNull(state.leave(setOf(editor)) { true })
    }

    @Test
    fun discard_marksOnlyTheEditorsItLeft() {
        // Back from the calibration editor leaves the plan editor beneath it asking.
        val state = UnsavedChangesState()
        val beneath = state.editorInBackStack()
        val top = state.editorInBackStack()
        state.leave(setOf(top)) { true }
        state.discard()
        assertFalse(state.asks(top))
        assertTrue(state.asks(beneath))
    }

    @Test
    fun backStackLeave_leavesOnboardingsEditorAlone() {
        // A Home deep link under onboarding pops the NavHost, not onboarding's open editor.
        val state = UnsavedChangesState()
        val onboarding = Any().also { state.update(it, dirty = true) }
        var wentHome = 0
        assertFalse(state.backStackHasUnsavedChanges)
        assertNull(state.leaveBackStack { wentHome++; true })
        assertEquals(1, wentHome)
        val inBackStack = state.editorInBackStack()
        state.leaveBackStack { wentHome++; true }
        state.discard()
        assertEquals(2, wentHome)
        assertFalse(state.asks(inBackStack))
        assertTrue(state.asks(onboarding))
    }

    @Test
    fun editorOutsideTheBackStack_stopsCountingOnceGone() {
        // Onboarding's editor ViewModel outlives it, under the activity.
        val state = UnsavedChangesState()
        val editor = Any()
        state.update(editor, dirty = true)
        state.leave(setOf(editor)) { true }
        state.stopTracking(editor)
        state.update(editor, dirty = true)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.pendingLeave)
    }
}
