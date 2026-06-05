package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

// Guards the res/xml-v31 provider-info wiring: the widgets must advertise the
// WidgetConfigActivity as their configure activity and the reconfigurable feature on API
// 31+, otherwise the long-press settings affordance silently disappears. assumeTrue skips on
// API 26-30 where the feature does not exist and the res/xml variant intentionally omits
// these attributes. Uses org.junit.Assert because kotlin-test is not on the androidTest
// classpath.
@RunWith(AndroidJUnit4::class)
class WidgetReconfigureProviderInfoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(context)

    private fun providerInfo(receiver: Class<*>): AppWidgetProviderInfo =
        manager.getInstalledProvidersForPackage(context.packageName, null)
            .first { it.provider == ComponentName(context, receiver) }

    private fun assertReconfigurable(receiver: Class<*>) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val info = providerInfo(receiver)
        assertEquals(
            "configure must point at WidgetConfigActivity on API 31+",
            ComponentName(context, WidgetConfigActivity::class.java),
            info.configure,
        )
        assertTrue(
            "WIDGET_FEATURE_RECONFIGURABLE must be set on API 31+",
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0,
        )
        assertTrue(
            "WIDGET_FEATURE_CONFIGURATION_OPTIONAL must be set so placement does not force config",
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0,
        )
    }

    @Test
    fun mediumWidget_isReconfigurable_onApi31Plus() {
        assertReconfigurable(HrtWidgetMediumReceiver::class.java)
    }

    @Test
    fun largeWidget_isReconfigurable_onApi31Plus() {
        assertReconfigurable(HrtWidgetLargeReceiver::class.java)
    }
}
