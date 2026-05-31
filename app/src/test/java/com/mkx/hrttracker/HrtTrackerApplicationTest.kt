package com.mkx.hrttracker

import android.app.UiModeManager
import android.os.Build
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class HrtTrackerApplicationTest {
    @Test
    fun applyApplicationNightModeIfAvailable_belowS_doesNotCallApi31Method() {
        val uiModeManager = mockk<UiModeManager>(relaxed = true)

        applyApplicationNightModeIfAvailable(
            uiModeManager = uiModeManager,
            applicationNightMode = UiModeManager.MODE_NIGHT_YES,
            sdkInt = 30,
        )

        verify(exactly = 0) { uiModeManager.setApplicationNightMode(any()) }
    }

    @Test
    fun applyApplicationNightModeIfAvailable_fromS_callsApi31Method() {
        val uiModeManager = mockk<UiModeManager>(relaxed = true)

        applyApplicationNightModeIfAvailable(
            uiModeManager = uiModeManager,
            applicationNightMode = UiModeManager.MODE_NIGHT_YES,
            sdkInt = Build.VERSION_CODES.S,
        )

        verify(exactly = 1) {
            uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        }
    }
}
