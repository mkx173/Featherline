package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Guards the res/xml vs res/xml-v31 provider-info split. On API 31+ the widgets must advertise
// WidgetConfigActivity + reconfigurable|configuration_optional (xml-v31) so the launcher's
// long-press settings affordance appears; on API 26-30 the base res/xml variant must OMIT that
// wiring so older launchers never try to launch a configure activity the feature can't support.
// Both directions are asserted (no assumeTrue skip) so neither the v31 wiring nor the base
// omission can silently regress on whichever API the test device runs. Uses org.junit.Assert
// because kotlin-test is not on the androidTest classpath.
@RunWith(AndroidJUnit4::class)
class WidgetReconfigureProviderInfoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(context)

    private fun providerInfo(receiver: Class<*>): AppWidgetProviderInfo {
        val target = ComponentName(context, receiver)
        val info = manager.getInstalledProvidersForPackage(context.packageName, null)
            .firstOrNull { it.provider == target }
        assertNotNull("No installed provider info found for $target", info)
        return info!!
    }

    private fun assertReconfigureWiringMatchesApiLevel(receiver: Class<*>) {
        val info = providerInfo(receiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        } else {
            assertNull(
                "base res/xml provider info must omit configure below API 31",
                info.configure,
            )
            assertEquals(
                "WIDGET_FEATURE_RECONFIGURABLE must NOT be set below API 31",
                0,
                info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE,
            )
        }
    }

    @Test
    fun mediumWidget_reconfigureWiring_matchesApiLevel() {
        assertReconfigureWiringMatchesApiLevel(HrtWidgetMediumReceiver::class.java)
    }

    @Test
    fun largeWidget_reconfigureWiring_matchesApiLevel() {
        assertReconfigureWiringMatchesApiLevel(HrtWidgetLargeReceiver::class.java)
    }
}
