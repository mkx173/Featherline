package com.mkx.hrttracker.macrobenchmark

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

internal fun UiDevice.seedBenchmarkFixture() {
    executeShellCommand("am start -W -n $FIXTURE_COMPONENT")
    waitForFixtureReady()
    executeShellCommand("am force-stop $TARGET_PACKAGE")
    waitForIdle()
}

private fun UiDevice.waitForFixtureReady() {
    val deadline = SystemClock.uptimeMillis() + FIXTURE_TIMEOUT_MILLIS
    while (SystemClock.uptimeMillis() < deadline) {
        when {
            hasObject(By.desc(FIXTURE_READY_CONTENT_DESCRIPTION)) -> return
            hasObject(By.desc(FIXTURE_FAILED_CONTENT_DESCRIPTION)) -> {
                error("Benchmark fixture seeding failed.")
            }
        }
        SystemClock.sleep(FIXTURE_POLL_INTERVAL_MILLIS)
    }
    error("Benchmark fixture did not report ready within $FIXTURE_TIMEOUT_MILLIS ms.")
}

private const val FIXTURE_READY_CONTENT_DESCRIPTION = "benchmark-fixture-ready"
private const val FIXTURE_FAILED_CONTENT_DESCRIPTION = "benchmark-fixture-failed"
private const val FIXTURE_TIMEOUT_MILLIS = 30_000L
private const val FIXTURE_POLL_INTERVAL_MILLIS = 100L
