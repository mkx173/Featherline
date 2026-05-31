package com.mkx.hrttracker.widget

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrtWidgetPushModeTest {
    @Test
    fun belowAndroid13_usesSessionBackedUpdatePath() {
        assertFalse(shouldUseSynchronousWidgetPush(sdkInt = Build.VERSION_CODES.S_V2))
    }

    @Test
    fun android13_usesSynchronousUpdatePath() {
        assertTrue(shouldUseSynchronousWidgetPush(sdkInt = Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun aboveAndroid13_usesSynchronousUpdatePath() {
        assertTrue(shouldUseSynchronousWidgetPush(sdkInt = Build.VERSION_CODES.TIRAMISU + 1))
    }
}
