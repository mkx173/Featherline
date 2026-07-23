package com.mkx.hrttracker.macrobenchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Black-box parity gate against the minified, release-equivalent benchmark target. */
@RunWith(AndroidJUnit4::class)
class PkCalibrationR8ParityTest {
    @Test
    fun minifiedBenchmark_executesForwardSolverAndRendererOracles() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val launch = device.executeShellCommand(
            "am start -W -S -a $Action -n $PackageName/$ActivityName"
        )
        assertFalse(launch, launch.contains("Error", ignoreCase = true))

        val appeared = device.wait(
            Until.hasObject(By.res(PackageName, ResultResourceName)),
            TimeoutMillis,
        )
        assertEquals("parity result view did not appear; launch=$launch", true, appeared)
        val result = device.findObject(By.res(PackageName, ResultResourceName))
        assertNotNull(result)
        assertEquals(SuccessText, result.text)
        assertEquals(ResultTag, result.contentDescription)
    }

    private companion object {
        const val PackageName = "com.mkx.hrttracker.benchmark"
        const val ActivityName =
            "com.mkx.hrttracker.benchmark.PkCalibrationR8ParityActivity"
        const val Action = "com.mkx.hrttracker.action.RUN_PK_CALIBRATION_R8_PARITY"
        const val ResultResourceName = "pk_calibration_r8_parity_result"
        const val ResultTag = "pk_calibration_r8_parity_result"
        const val SuccessText = "PK_CALIBRATION_R8_PARITY_OK:v1"
        const val TimeoutMillis = 120_000L
    }
}
