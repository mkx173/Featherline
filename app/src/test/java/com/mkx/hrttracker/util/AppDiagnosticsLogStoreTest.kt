package com.mkx.hrttracker.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppDiagnosticsLogStoreTest {
    private lateinit var tempDir: File
    private lateinit var logFile: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("app-diagnostics-log-store-test-").toFile()
        logFile = File(tempDir, "diagnostics.log")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun recordPersistsFormattedLogLines() {
        val store = AppDiagnosticsLogStore(logFile = logFile)

        store.record(AppDiagnosticsLogLevel.INFO, "HomeSnapshotRepository", "home_snapshot_loaded", null)
        store.record(AppDiagnosticsLogLevel.WARNING, "StartupPreloader", "warmup_failed", RuntimeException("boom"))

        val text = store.readText()
        assertTrue(text.contains("INFO/HomeSnapshotRepository: home_snapshot_loaded"))
        assertTrue(text.contains("WARN/StartupPreloader: warmup_failed"))
        assertTrue(text.contains("RuntimeException: boom"))
    }

    @Test
    fun recordTrimsOldestContentWhenFileExceedsLimit() {
        val store = AppDiagnosticsLogStore(logFile = logFile, maxBytes = 160)

        repeat(20) { index ->
            store.record(AppDiagnosticsLogLevel.INFO, "Diagnostics", "message-$index-abcdefghijklmnopqrstuvwxyz", null)
        }

        val text = store.readText()
        assertFalse(text.contains("message-0-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(text.contains("message-19-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(logFile.length() <= 160L)
    }
}
