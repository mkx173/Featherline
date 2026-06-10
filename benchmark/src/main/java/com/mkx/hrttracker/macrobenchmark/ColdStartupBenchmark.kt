package com.mkx.hrttracker.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing for the release-equivalent `benchmark` build.
 *
 * Reports `timeToInitialDisplayMs` (process start → first frame) and, where the app
 * calls `ReportDrawnWhen` (the home screen's `splashReady`), `timeToFullDisplayMs`.
 *
 * Run on a connected device:
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 *
 * The target is the id-suffixed `com.mkx.hrttracker.benchmark` install, so a
 * production install on the same device is untouched. For numbers representative of
 * a real session (home screen with data, not onboarding), complete onboarding in the
 * benchmark app once before running — COLD iterations kill the process but keep app
 * data.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.mkx.hrttracker.benchmark",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
