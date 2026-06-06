package com.mkx.hrttracker.widget

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Governs only the large widget, whose LazyColumn is a collection-backed view that a bare
// updateAppWidget() won't rebind below API 33. The medium widget has no collection and always
// takes the synchronous push, so it is not gated by this function.
class HrtWidgetPushModeTest {
    @Test
    fun largeWidgetBelowAndroid13_usesSessionBackedUpdatePath() {
        assertFalse(shouldUseSynchronousWidgetPush(sdkInt = Build.VERSION_CODES.S_V2))
    }

    @Test
    fun largeWidgetOnAndroid13_usesSynchronousUpdatePath() {
        assertTrue(shouldUseSynchronousWidgetPush(sdkInt = Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun largeWidgetAboveAndroid13_usesSynchronousUpdatePath() {
        assertTrue(shouldUseSynchronousWidgetPush(sdkInt = Build.VERSION_CODES.TIRAMISU + 1))
    }
}
