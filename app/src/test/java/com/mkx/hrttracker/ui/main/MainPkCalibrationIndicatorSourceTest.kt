package com.mkx.hrttracker.ui.main

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainPkCalibrationIndicatorSourceTest {
    @Test
    fun rawCalibrationContractIndicator_isBuildConfigDebugGatedAtRenderBoundary() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/main/MainContentComponents.kt",
        ).readText()
        val indicatorBody = source
            .substringAfter("private fun MainPkCalibrationIndicator(")
            .substringBefore("internal data class MainE2ChartVisibleXRange")

        assertTrue(
            "Raw schema/beta diagnostics must remain unreachable in production Home builds.",
            indicatorBody.contains("if (!BuildConfig.DEBUG) return"),
        )
        assertTrue(
            "The production guard must run before raw calibration text is constructed.",
            indicatorBody.indexOf("if (!BuildConfig.DEBUG) return") <
                    indicatorBody.indexOf("rawPkCalibrationIndicatorText(display)"),
        )
    }

    private fun projectRoot(): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = requireNotNull(dir.parentFile).canonicalFile
        }
        return dir
    }
}
