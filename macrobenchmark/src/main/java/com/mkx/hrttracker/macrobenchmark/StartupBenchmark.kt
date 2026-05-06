package com.mkx.hrttracker.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private lateinit var device: UiDevice

    @Before
    fun seedFixture() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.seedBenchmarkFixture()
    }

    @Test
    fun coldHomeStart_noCompilation() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) {
            pressHome()
            startActivityAndWait()
            waitForHomeFullDisplay()
        }
    }

    @Test
    fun coldHomeStart_partialCompilation() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) {
            pressHome()
            startActivityAndWait()
            waitForHomeFullDisplay()
        }
    }

    private fun waitForHomeFullDisplay() {
        device.wait(Until.hasObject(By.text("7-day trend")), WAIT_TIMEOUT_MILLIS)
        device.waitForIdle()
    }
}

internal const val TARGET_PACKAGE = "com.mkx.hrttracker.benchmark"
internal const val FIXTURE_COMPONENT = "$TARGET_PACKAGE/com.mkx.hrttracker.benchmark.StartupFixtureActivity"
private const val WAIT_TIMEOUT_MILLIS = 5_000L
