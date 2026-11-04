package com.mkx.hrttracker.ui.components

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedChangesStateTest {

    private class TestEntry : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun leave_runsAtOnceWithoutUnsavedChanges() {
        val state = UnsavedChangesState()
        var left = 0
        state.update(TestEntry(), dirty = false)
        state.leave { left++ }
        assertEquals(1, left)
        assertNull(state.pendingLeave)
    }

    @Test
    fun leave_waitsForDiscard() {
        val state = UnsavedChangesState()
        var left = 0
        state.update(TestEntry(), dirty = true)
        state.leave { left++ }
        assertEquals(0, left)
        state.discard()
        assertEquals(1, left)
        assertNull(state.pendingLeave)
    }

    @Test
    fun keepEditing_dropsTheLeave() {
        val state = UnsavedChangesState()
        var left = 0
        state.update(TestEntry(), dirty = true)
        state.leave { left++ }
        state.keepEditing()
        state.discard()
        assertEquals(0, left)
    }

    @Test
    fun entryUnderAnotherPage_stillCountsUntilDestroyed() {
        // The plan editor stays dirty under the medicine picker it opened, composed or not.
        val state = UnsavedChangesState()
        val editor = TestEntry()
        state.update(editor, dirty = true)
        editor.registry.currentState = Lifecycle.State.CREATED
        assertTrue(state.hasUnsavedChanges)
        editor.registry.currentState = Lifecycle.State.DESTROYED
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun pendingLeave_isDroppedWhenItsEditorFinishes() {
        // Back tapped while saving: the save leaves the editor, and nothing is left to discard.
        val state = UnsavedChangesState()
        val editor = TestEntry()
        var left = 0
        state.update(editor, dirty = true)
        state.leave { left++ }
        state.update(editor, dirty = false)
        assertNull(state.pendingLeave)
        editor.registry.currentState = Lifecycle.State.DESTROYED
        state.discard()
        assertEquals(0, left)
    }

    @Test
    fun editorOutsideTheBackStack_isDroppedWhenRemoved() {
        // Onboarding's editor lives under the activity, which outlives it.
        val state = UnsavedChangesState()
        val editor = Any()
        state.update(editor, dirty = true)
        state.leave { }
        state.remove(editor)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.pendingLeave)
    }
}
