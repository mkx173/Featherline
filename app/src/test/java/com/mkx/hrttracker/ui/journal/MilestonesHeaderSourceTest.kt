package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MilestonesHeaderSourceTest {
    @Test
    fun pinnedSectionOwnsHeaderTrailingLikeStockSection() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalScreens.kt",
        ).readText()
        val pinnedSection = source.substringAfter("item(key = \"milestones-pinned\"")
            .substringBefore("item(key = \"milestones-pinned-timeline-spacer\"")

        assertTrue(
            "The pinned section should own an explicit HrtSectionHeader, matching StockSection's " +
                "header-then-content structure.",
            pinnedSection.contains("HrtSectionHeader(") &&
                pinnedSection.contains("text = stringResource(R.string.journal_pinned_section)") &&
                pinnedSection.contains("trailing = if (uiState.hero != null)"),
        )
        assertFalse(
            "The hero background wand should live in the pinned section's header, not in " +
                "HrtSection.headerTrailing.",
            pinnedSection.contains("headerTrailing ="),
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
