package com.mkx.hrttracker

import android.content.Intent
import android.os.Bundle
import com.mkx.hrttracker.widget.EXTRA_OPEN_MILESTONES
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The gate MainActivity.onCreate puts on its launch intent. Driving MainActivity itself needs a
// Hilt test application and the app's database, which the unit tests don't have.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchIntentParseTest {
    private fun notificationTap() = Intent().apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_HIGHLIGHT_KIND, HIGHLIGHT_KIND_SCHEDULED)
    }

    private fun milestonesTap() = Intent().apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_OPEN_MILESTONES, true)
    }

    @Test
    fun coldStart_parsesTheDeepLink() {
        assertTrue(parsesLaunchIntent(savedInstanceState = null, intent = notificationTap()))
        assertTrue(parsesLaunchIntent(savedInstanceState = null, intent = milestonesTap()))
    }

    @Test
    fun restoreAfterProcessDeath_doesNotReplayTheTasksOriginalIntent() {
        // A launcher or Recents relaunch of a killed task hands the restored instance the
        // intent that started the task, with no history flag. A fresh tap that restores the
        // task comes through onNewIntent instead.
        assertFalse(
            "A restored notification link was replayed",
            parsesLaunchIntent(savedInstanceState = Bundle(), intent = notificationTap()),
        )
        assertFalse(
            "A restored Milestones link was replayed",
            parsesLaunchIntent(savedInstanceState = Bundle(), intent = milestonesTap()),
        )
    }

    @Test
    fun recentsRelaunchOfAnExpiredTask_doesNotReplayTheTasksOriginalIntent() {
        val fromHistory = notificationTap().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertFalse(parsesLaunchIntent(savedInstanceState = null, intent = fromHistory))
    }
}
