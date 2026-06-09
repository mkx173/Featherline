package com.mkx.hrttracker.ui.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HistoryScreenFabAnimationSourceTest {
    @Test
    fun selectionFab_usesMaterialFabAnimationModifier() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/history/HistoryScreen.kt"
        ).readText()
        val fabSlot = source.substringAfter("floatingActionButton = {")
            .substringBefore("topBar = {")

        assertTrue(
            "History selection FAB should use animateFloatingActionButton so its shadow is " +
                    "included in the scale/alpha layer.",
            fabSlot.contains(".animateFloatingActionButton("),
        )
        assertFalse(
            "AnimatedVisibility clips/animates the FAB content separately from the Material FAB " +
                    "shadow layer.",
            fabSlot.contains("AnimatedVisibility("),
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
