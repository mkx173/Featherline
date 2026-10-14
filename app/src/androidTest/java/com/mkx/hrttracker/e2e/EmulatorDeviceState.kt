package com.mkx.hrttracker.e2e

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Controls the real Android clock, so application lifecycle and alarm receivers also run. */
internal class EmulatorDeviceState : AutoCloseable {
    private val settings = linkedMapOf(
        "global auto_time" to "0",
        "global auto_time_zone" to "0",
        "system time_12_24" to "24",
    )
    private val originals = mutableMapOf<String, String>()
    private var originalZone: String? = null
    private var originalTime: Long? = null
    private var originalElapsed = 0L

    fun captureAndConfigure() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) {
            "These E2E tests reset Debug app data and change device time. Use an Android emulator."
        }
        originalTime = System.currentTimeMillis()
        originalElapsed = SystemClock.elapsedRealtime()
        originalZone = shell("getprop persist.sys.timezone").trim()
        settings.forEach { (key, value) ->
            originals[key] = shell("settings get $key").trim()
            shell("settings put $key $value")
        }
        setZone("Asia/Shanghai")
    }

    fun setZone(zone: String) {
        require(ZoneId.getAvailableZoneIds().contains(zone))
        shell("cmd alarm set-timezone $zone")
        waitFor("timezone $zone") { ZoneId.systemDefault().id == zone }
    }

    fun setTime(time: LocalDateTime) {
        val epoch = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val result = shell("cmd alarm set-time $epoch")
        check(kotlin.math.abs(Instant.now().toEpochMilli() - epoch) < 5_000L) {
            "Could not set emulator time: $result"
        }
    }

    override fun close() {
        try {
            originalZone?.let { shell("cmd alarm set-timezone $it") }
            originalTime?.let { time ->
                shell("cmd alarm set-time ${time + SystemClock.elapsedRealtime() - originalElapsed}")
            }
        } finally {
            originals.forEach { (key, value) ->
                if (value == "null") shell("settings delete $key")
                else shell("settings put $key $value")
            }
        }
    }

    companion object {
        fun shell(command: String): String {
            val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand(command)
            return fd.use { FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() } }
        }

        fun waitFor(description: String, timeoutMillis: Long = 20_000L, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            while (!condition()) {
                check(SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $description" }
                SystemClock.sleep(100)
            }
        }
    }
}
