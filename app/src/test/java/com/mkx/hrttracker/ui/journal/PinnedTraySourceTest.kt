package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinnedTraySourceTest {
    @Test
    fun pinnedTray_letsReorderableColumnOwnGapsDuringDrag() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalComponents.kt",
        ).readText()
        val pinnedTrayRows = source.substringAfter("private fun PinnedTrayRows(")
            .substringBefore("\nprivate fun MutableTransitionState<Boolean>.isPinnedTrayRowPresent")

        assertTrue(
            "Settled edit-mode rows should let ReorderableColumn own gaps so drag " +
                "offsets include the same spacing the user sees.",
            pinnedTrayRows.contains("val reorderOwnsGaps = isEditMode && !hasTransitioningRows") &&
                pinnedTrayRows.contains("verticalArrangement = if (reorderOwnsGaps)") &&
                pinnedTrayRows.contains("Arrangement.spacedBy(gap)"),
        )
        assertTrue(
            "Rows should own leading gaps while add/remove transitions are active so " +
                "a middle row's gap collapses with the exiting row instead of leaving " +
                "two fixed arrangement gaps.",
            pinnedTrayRows.contains("val hasLeadingGap = !reorderOwnsGaps &&"),
        )
        assertFalse(
            "Changing ReorderableColumn's arrangement when the drag gesture starts can " +
                "recompose the reorder container under the active pointer and cancel drag.",
            pinnedTrayRows.contains("verticalArrangement = if (isDraggingAny)"),
        )
        assertFalse(
            "Keeping Arrangement.Top for every state makes gaps belong entirely to rows, " +
                "which blinks when ReorderableColumn translates rows during drag.",
            pinnedTrayRows.contains("verticalArrangement = Arrangement.Top"),
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
