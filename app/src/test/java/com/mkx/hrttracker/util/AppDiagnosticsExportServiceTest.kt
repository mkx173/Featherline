package com.mkx.hrttracker.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.BuildConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId

class AppDiagnosticsExportServiceTest {
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val destinationUri: Uri = mockk()
    private val logStore: AppDiagnosticsLogStore = mockk()
    private val logcatReader: AppDiagnosticsLogcatReader = mockk()

    @Test
    fun exportLogsWritesAppLogsAndCurrentProcessLogcat() = runTest {
        val output = ByteArrayOutputStream()
        every { context.contentResolver } returns contentResolver
        every { context.packageName } returns "com.mkx.hrttracker"
        every { contentResolver.openOutputStream(destinationUri) } returns output
        every { logStore.readText() } returns "INFO/App: app-owned log\n"
        every {
            logcatReader.readCurrentProcessLogs(maxChars = any())
        } returns AppDiagnosticsLogcatCapture(
            text = "05-07 12:00:00.000 I/App: logcat line",
            unavailableReason = null,
        )
        val service = AppDiagnosticsExportService(
            context = context,
            logStore = logStore,
            logcatReader = logcatReader,
        )

        service.exportLogs(destinationUri = destinationUri, exportedAt = Instant.parse("2026-05-07T03:04:05Z"))

        val exportedText = output.toString(Charsets.UTF_8.name())
        assertTrue(exportedText.contains("Featherline diagnostic logs"))
        assertTrue(exportedText.contains("Package: com.mkx.hrttracker"))
        assertTrue(exportedText.contains("Build type: ${BuildConfig.BUILD_TYPE}"))
        assertTrue(exportedText.contains("INFO/App: app-owned log"))
        assertTrue(exportedText.contains("05-07 12:00:00.000 I/App: logcat line"))
    }

    @Test
    fun exportLogsIncludesUnavailableNoteWhenLogcatFails() = runTest {
        val output = ByteArrayOutputStream()
        every { context.contentResolver } returns contentResolver
        every { context.packageName } returns "com.mkx.hrttracker"
        every { contentResolver.openOutputStream(destinationUri) } returns output
        every { logStore.readText() } returns ""
        every {
            logcatReader.readCurrentProcessLogs(maxChars = any())
        } returns AppDiagnosticsLogcatCapture(
            text = "",
            unavailableReason = "logcat unavailable",
        )
        val service = AppDiagnosticsExportService(
            context = context,
            logStore = logStore,
            logcatReader = logcatReader,
        )

        service.exportLogs(destinationUri = destinationUri, exportedAt = Instant.parse("2026-05-07T03:04:05Z"))

        val exportedText = output.toString(Charsets.UTF_8.name())
        assertTrue(exportedText.contains("No app diagnostics captured."))
        assertTrue(exportedText.contains("Package logcat unavailable: logcat unavailable"))
    }

    @Test
    fun buildExportFileNameUsesLocalTimestamp() {
        val service = AppDiagnosticsExportService(
            context = context,
            logStore = logStore,
            logcatReader = logcatReader,
        )

        assertEquals(
            "featherline-diagnostics-2026-05-07_12-04-05.txt",
            service.buildExportFileName(
                exportedAt = Instant.parse("2026-05-07T03:04:05Z"),
                zoneId = ZoneId.of("Asia/Tokyo"),
            )
        )
    }
}
