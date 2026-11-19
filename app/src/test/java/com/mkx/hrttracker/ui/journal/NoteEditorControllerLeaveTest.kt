package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.ui.components.UnsavedChangesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * All Notes' Back arrow is `askFirstToLeave(onNavigateBack)`, and onNavigateBack is
 * `popBackStackSafely()`, which does nothing while the entry isn't RESUMED. Discard stops
 * tracking the draft only after a pop that left.
 */
class NoteEditorControllerLeaveTest {
    @Test
    fun discardWhosePopWasRefused_stillAsksOnTheNextBack() {
        val guard = UnsavedChangesState()
        val controller = NoteEditorController(initialActiveEditorId = "note-1")
        controller.unsavedChanges = guard
        guard.hostInBackStack(controller)
        controller.reportUnsaved("note-1", unsaved = true)
        guard.update(controller, true)

        // Back with a dirty draft: the dialog asks. Discard, and the pop is refused mid-transition.
        controller.askFirstToLeave { false }
        assertNotNull("The first Back did not ask", guard.pendingLeave)
        guard.discard()

        // The page is still up with its editor and draft. More typing, then Back again.
        guard.update(controller, true)
        var pops = 0
        controller.askFirstToLeave { pops++; true }

        assertEquals("The second Back left without asking", 0, pops)
        assertNotNull("The second Back did not ask", guard.pendingLeave)
    }
}
