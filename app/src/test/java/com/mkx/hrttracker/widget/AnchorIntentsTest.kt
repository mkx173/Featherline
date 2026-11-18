package com.mkx.hrttracker.widget

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The anchor widget and pinned shortcut open Milestones in the existing MainActivity even with the
 * backup file picker or a settings page over it (checked on an API 34 emulator): without CLEAR_TOP
 * a second MainActivity opened above the picker, or, in a task the anchor itself started, the task
 * only came forward and the link was lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnchorIntentsTest {
    @Test
    fun opensMilestonesInTheExistingMainActivity_closingWhatIsOverIt() {
        val intent = anchorOpenMilestonesIntent(ApplicationProvider.getApplicationContext())

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(flags, intent.flags and flags)
        assertTrue(intent.getBooleanExtra(EXTRA_OPEN_MILESTONES, false))
    }
}
