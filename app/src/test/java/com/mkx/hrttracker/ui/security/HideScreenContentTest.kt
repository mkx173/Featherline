package com.mkx.hrttracker.ui.security

import android.app.Activity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hide screen content keeps screenshots and recordings out of every window that shows data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HideScreenContentTest {
    @Test
    fun applyHideScreenContent_securesTheWindowOnlyWhileOn() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.applyHideScreenContent(enabled = true)
        assertTrue("Hide screen content left the window open to screenshots", activity.isSecure())

        activity.applyHideScreenContent(enabled = false)
        assertFalse("Hide screen content off still kept FLAG_SECURE", activity.isSecure())
    }

    private fun Activity.isSecure(): Boolean =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
