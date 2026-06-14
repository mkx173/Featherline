package com.mkx.hrttracker.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import com.mkx.hrttracker.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetConfigActivityManifestTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun activityInfo(activityClass: Class<*>): ActivityInfo =
        context.packageManager.getActivityInfo(
            ComponentName(context, activityClass),
            PackageManager.GET_META_DATA,
        )

    @Test
    fun widgetConfigActivityDoesNotShareLauncherTaskAffinity() {
        val mainInfo = activityInfo(MainActivity::class.java)
        val configInfo = activityInfo(WidgetConfigActivity::class.java)

        assertEquals(
            "MainActivity should keep the app task affinity used by launcher icon launches",
            context.packageName,
            mainInfo.taskAffinity,
        )
        assertTrue(
            "WidgetConfigActivity must not share the app task affinity, otherwise a " +
                    "launcher reconfigure task can be resumed when the app icon is opened",
            configInfo.taskAffinity.isNullOrEmpty(),
        )
    }
}
