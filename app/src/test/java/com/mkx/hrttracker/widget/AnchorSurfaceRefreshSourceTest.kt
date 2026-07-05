package com.mkx.hrttracker.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Guards the stale anchor-list fix: the config window is singleTop in its own task, so a
// retained instance can be resumed without recreation. The anchor picker must observe the
// live list after its one-shot seed or it shows "No tracked dates yet." forever for a user
// who added their first date after the window was first opened.
class AnchorSurfaceRefreshSourceTest {
    @Test
    fun widgetConfigActivity_keepsAnchorListLiveAfterSeed() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )
        val produceBlock = source.substringAfter("produceState<LoadedConfigState?>")
        assertTrue(
            "The anchor config load must keep collecting the live tracked-dates flow " +
                "after the one-shot seed, so a resumed (not recreated) config window " +
                "picks up dates added since it was first opened.",
            produceBlock.contains("observeLoadedTrackedDates().collect") &&
                produceBlock.contains("value = value?.copy(anchors ="),
        )
    }

    private fun source(relativePath: String): String {
        return File(projectRoot(), relativePath).readText()
    }

    private fun projectRoot(): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = requireNotNull(dir.parentFile).canonicalFile
        }
        return dir
    }
}
