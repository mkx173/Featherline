package com.mkx.hrttracker.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class AppDiagnosticsLogLevel(
    val label: String,
) {
    INFO("INFO"),
    WARNING("WARN"),
}

@Singleton
open class AppDiagnosticsLogStore private constructor(
    private val logFileProvider: () -> File?,
    private val maxBytes: Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        logFileProvider = {
            File(
                File(context.filesDir, DIAGNOSTICS_DIRECTORY_NAME),
                DIAGNOSTICS_FILE_NAME,
            )
        },
        maxBytes = DEFAULT_MAX_BYTES,
    )

    internal constructor(
        logFile: File,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ) : this(
        logFileProvider = { logFile },
        maxBytes = maxBytes,
    )

    @Synchronized
    open fun record(
        level: AppDiagnosticsLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val logFile = logFileProvider() ?: return
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText(formatLine(level, tag, message, throwable), Charsets.UTF_8)
            trimIfNeeded(logFile)
        }
    }

    @Synchronized
    open fun readText(): String {
        val logFile = logFileProvider() ?: return ""
        return runCatching {
            if (logFile.exists()) {
                logFile.readText(Charsets.UTF_8)
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun formatLine(
        level: AppDiagnosticsLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ): String {
        return buildString {
            append(Instant.now())
            append(' ')
            append(level.label)
            append('/')
            append(tag)
            append(": ")
            append(message)
            append('\n')
            if (throwable != null) {
                append(throwable.stackTraceString())
                append('\n')
            }
        }
    }

    private fun trimIfNeeded(logFile: File) {
        if (maxBytes <= 0L || logFile.length() <= maxBytes) {
            return
        }

        val lines = logFile.readLines(Charsets.UTF_8)
        val retainedLines = ArrayDeque<String>()
        var retainedBytes = 0L
        for (line in lines.asReversed()) {
            val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1L
            if (retainedLines.isNotEmpty() && retainedBytes + lineBytes > maxBytes) {
                break
            }
            retainedLines.addFirst(line)
            retainedBytes += lineBytes
        }
        val retainedText = if (retainedLines.isEmpty()) {
            logFile.readText(Charsets.UTF_8).takeLast(maxBytes.toInt())
        } else {
            retainedLines.joinToString(separator = "\n", postfix = "\n")
        }
        logFile.writeText(retainedText, Charsets.UTF_8)
    }

    private fun Throwable.stackTraceString(): String {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { writer ->
            printStackTrace(writer)
        }
        return stringWriter.toString().trimEnd()
    }

    companion object {
        private const val DIAGNOSTICS_DIRECTORY_NAME = "diagnostics"
        private const val DIAGNOSTICS_FILE_NAME = "app-diagnostics.log"
        private const val DEFAULT_MAX_BYTES = 256L * 1024L

        fun disabled(): AppDiagnosticsLogStore {
            return AppDiagnosticsLogStore(
                logFileProvider = { null },
                maxBytes = 0L,
            )
        }
    }
}
