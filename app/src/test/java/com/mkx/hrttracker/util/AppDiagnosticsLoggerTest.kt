package com.mkx.hrttracker.util

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Test

class AppDiagnosticsLoggerTest {
    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun disabledLoggerSuppressesInfoAndWarningLogs() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        val logStore: AppDiagnosticsLogStore = mockk(relaxed = true)
        val logger = AppDiagnosticsLogger(enabled = false, logStore = logStore)

        logger.info("Diagnostics", "health-sensitive info")
        logger.warning("Diagnostics", "health-sensitive warning")
        logger.warning("Diagnostics", "health-sensitive failure", RuntimeException("boom"))

        verify(exactly = 0) { Log.i(any(), any()) }
        verify(exactly = 0) { Log.w(any<String>(), any<String>()) }
        verify(exactly = 0) { Log.w(any<String>(), any<String>(), any()) }
        verify(exactly = 0) { logStore.record(any(), any(), any(), any()) }
    }

    @Test
    fun enabledLoggerCapturesInfoAndWarningLogs() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        val logStore: AppDiagnosticsLogStore = mockk(relaxed = true)
        val throwable = RuntimeException("boom")
        val logger = AppDiagnosticsLogger(enabled = true, logStore = logStore)

        logger.info("Diagnostics", "captured info")
        logger.warning("Diagnostics", "captured warning", throwable)

        verify {
            logStore.record(AppDiagnosticsLogLevel.INFO, "Diagnostics", "captured info", null)
            logStore.record(
                AppDiagnosticsLogLevel.WARNING,
                "Diagnostics",
                "captured warning",
                throwable
            )
        }
    }
}
