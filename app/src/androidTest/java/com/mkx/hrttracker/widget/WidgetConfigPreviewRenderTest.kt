package com.mkx.hrttracker.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Guards the WidgetConfigActivity live-preview render path: the helper must produce an
// inflatable RemoteViews for BOTH widget sizes even with no persisted snapshot (fresh
// install / first placement falls back to previewSnapshot), because the reconfigure
// activity is exported and must never strand the user on a blank preview.
// Uses org.junit.Assert because kotlin-test is not on the androidTest classpath.
@RunWith(AndroidJUnit4::class)
class WidgetConfigPreviewRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun mediumPreviewRendersWithoutPersistedSnapshot() {
        val render = runBlocking {
            composeWidgetPreviewRemoteViews(
                context = context,
                isMedium = true,
                contentScale = 1.0f,
                backgroundAlpha = 0.8f,
                forcedDark = null,
                snapshot = null,
            )
        }
        assertNotNull(render.remoteViews.apply(context, FrameLayout(context)))
    }

    @Test
    fun largePreviewRendersWithLiveValuesApplied() {
        val render = runBlocking {
            composeWidgetPreviewRemoteViews(
                context = context,
                isMedium = false,
                contentScale = 1.5f,
                backgroundAlpha = 0.5f,
                forcedDark = true,
                snapshot = null,
            )
        }
        val inflated = render.remoteViews.apply(context, FrameLayout(context))
        assertNotNull(inflated)
        // WHY this assertion matters: the large widget's dose list is collection-backed
        // (Glance LazyColumn → RemoteCollectionItems / setRemoteAdapter). Collections only
        // bind inside a real AppWidget host; under the config preview's host-free
        // RemoteViews.apply() the platform silently refuses to bind them, so the LazyColumn
        // path renders a BLANK list — only the header chrome inflates, with no error. The
        // host-free preview must therefore render the same rows as a plain Column
        // (LocalHostFreePreview = true). This test pins that: it asserts a dose ROW actually
        // materialized in the inflated view tree, so a regression back to the LazyColumn path
        // (silent blank list) fails the build instead of shipping an empty preview.
        //
        // The estradiol medication name is resolved from the SAME context the helper renders
        // with, so the assertion is locale-consistent (the device/test locale doesn't matter).
        // previewSnapshot's morning row carries medicationName = estradiol_valerate, which
        // contains the estradiol string; that name appears only in a dose row's title, never
        // in the header chrome — so its presence proves the list rows inflated, not the header.
        val estradiolName = context.getString(R.string.medication_name_estradiol)
        val texts = collectTextViewTexts(inflated)
        assertTrue(
            "Large preview list rendered blank: no dose-row TextView contained " +
                "\"$estradiolName\" (the host-free Column path failed to materialize rows). " +
                "Collected texts=$texts",
            texts.any { it.contains(estradiolName) },
        )
    }

    // Pins the sizing contract for the fallback path: with no (default INVALID)
    // appWidgetId the helper cannot read live launcher options, so it must compose at the
    // fixed reference preview size. The literals tie to the private reference constants
    // (MEDIUM_WIDGET_PREVIEW_WIDTH_DP=306, WIDGET_PREVIEW_HEIGHT_DP=276) — asserted as
    // literal dp because the constants are private; that pinning is intentional. A live
    // real-appWidgetId sizing assertion needs a bound widget and is covered on-device.
    @Test
    fun invalidWidgetIdFallsBackToReferenceSize() {
        val render = runBlocking {
            composeWidgetPreviewRemoteViews(
                context = context,
                isMedium = true,
                contentScale = 1.0f,
                backgroundAlpha = 1.0f,
                forcedDark = null,
                snapshot = null,
            )
        }
        assertEquals(DpSize(306.dp, 276.dp), render.sizeDp)
    }
}

// Collects the text of every TextView in [root]'s view hierarchy, depth-first.
private fun collectTextViewTexts(root: View): List<String> {
    val out = mutableListOf<String>()
    fun walk(view: View) {
        if (view is TextView) out += view.text?.toString().orEmpty()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
    }
    walk(root)
    return out
}
