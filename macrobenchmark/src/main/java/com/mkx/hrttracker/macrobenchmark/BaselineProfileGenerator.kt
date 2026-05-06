package com.mkx.hrttracker.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private lateinit var device: UiDevice

    @Before
    fun seedFixture() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.seedBenchmarkFixture()
    }

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 3,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("Home")), WAIT_TIMEOUT_MILLIS)
            device.findObject(By.text("Plan"))?.click()
            device.waitForIdle()
            device.findObject(By.text("Settings"))?.click()
            device.waitForIdle()
        }
    }
}

private const val WAIT_TIMEOUT_MILLIS = 5_000L
