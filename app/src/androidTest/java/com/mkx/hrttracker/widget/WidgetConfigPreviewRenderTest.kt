package com.mkx.hrttracker.widget

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
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
        val remoteViews = runBlocking {
            composeWidgetPreviewRemoteViews(
                context = context,
                isMedium = true,
                contentScale = 1.0f,
                backgroundAlpha = 0.8f,
                forcedDark = null,
                snapshot = null,
            )
        }
        assertNotNull(remoteViews.apply(context, FrameLayout(context)))
    }

    @Test
    fun largePreviewRendersWithLiveValuesApplied() {
        val remoteViews = runBlocking {
            composeWidgetPreviewRemoteViews(
                context = context,
                isMedium = false,
                contentScale = 1.5f,
                backgroundAlpha = 0.5f,
                forcedDark = true,
                snapshot = null,
            )
        }
        assertNotNull(remoteViews.apply(context, FrameLayout(context)))
    }
}
