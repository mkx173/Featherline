package com.mkx.hrttracker.util

import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class AppDiagnosticsExportedFile(
    val displayName: String,
)

@Singleton
open class AppDiagnosticsExportService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logStore: AppDiagnosticsLogStore,
    private val logcatReader: AppDiagnosticsLogcatReader,
) {
    open suspend fun exportLogs(
        destinationUri: Uri,
        exportedAt: Instant = Instant.now(),
    ): AppDiagnosticsExportedFile {
        check(BuildConfig.DEBUG) {
            "Diagnostics export is only available in debug builds."
        }
        val displayName = buildExportFileName(exportedAt = exportedAt)
        withContext(Dispatchers.IO) {
            val exportText = buildExportText(
                exportedAt = exportedAt,
                appDiagnosticsText = logStore.readText(),
                logcatCapture = logcatReader.readCurrentProcessLogs(maxChars = MAX_LOGCAT_CHARS),
            )
            val outputStream =
                checkNotNull(context.contentResolver.openOutputStream(destinationUri)) {
                    "Unable to open diagnostics export destination."
                }
            outputStream.use { stream ->
                stream.write(exportText.toByteArray(Charsets.UTF_8))
            }
        }
        return AppDiagnosticsExportedFile(displayName = displayName)
    }

    fun buildExportFileName(
        exportedAt: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val timestamp = FILE_NAME_TIMESTAMP_FORMATTER.format(exportedAt.atZone(zoneId))
        return "featherline-diagnostics-$timestamp.txt"
    }

    private fun buildExportText(
        exportedAt: Instant,
        appDiagnosticsText: String,
        logcatCapture: AppDiagnosticsLogcatCapture,
    ): String {
        return buildString {
            appendLine("Featherline diagnostic logs")
            appendLine("Exported at: $exportedAt")
            appendLine("Package: ${context.packageName}")
            appendLine("Application ID: ${BuildConfig.APPLICATION_ID}")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("== App diagnostics ==")
            if (appDiagnosticsText.isBlank()) {
                appendLine("No app diagnostics captured.")
            } else {
                append(appDiagnosticsText.trimEnd())
                appendLine()
            }
            appendLine()
            appendLine("== Current process logcat ==")
            if (logcatCapture.unavailableReason != null) {
                appendLine("Package logcat unavailable: ${logcatCapture.unavailableReason}")
            } else if (logcatCapture.text.isBlank()) {
                appendLine("No current-process logcat lines captured.")
            } else {
                append(logcatCapture.text.trimEnd())
                appendLine()
            }
        }
    }

    private companion object {
        const val MAX_LOGCAT_CHARS = 200_000
        val FILE_NAME_TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}

data class AppDiagnosticsLogcatCapture(
    val text: String,
    val unavailableReason: String?,
)

open class AppDiagnosticsLogcatReader @Inject constructor() {
    open fun readCurrentProcessLogs(
        maxChars: Int,
    ): AppDiagnosticsLogcatCapture {
        return runCatching {
            val process = ProcessBuilder(
                "logcat",
                "-d",
                "--pid=${android.os.Process.myPid()}",
                "-v",
                "time",
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                AppDiagnosticsLogcatCapture(
                    text = "",
                    unavailableReason = output.lineSequence().firstOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?: "logcat exited with code $exitCode",
                )
            } else {
                AppDiagnosticsLogcatCapture(
                    text = output.takeLast(maxChars),
                    unavailableReason = null,
                )
            }
        }.getOrElse { throwable ->
            AppDiagnosticsLogcatCapture(
                text = "",
                unavailableReason = throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }
}
