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
                pinnedSection.contains(
                    "val pinnedTitle = stringResource(R.string.journal_pinned_section)",
                ) &&
                pinnedSection.contains("text = pinnedTitle") &&
                pinnedSection.contains("trailing = if (uiState.hero != null)"),
        )
        assertFalse(
            "The hero background wand should live in the pinned section's header, not in " +
                "HrtSection.headerTrailing.",
            pinnedSection.contains("headerTrailing ="),
        )
    }

    @Test
    fun pinnedHeaderWandUsesStockSectionActionSizing() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalScreens.kt",
        ).readText()
        val pinnedSection = source.substringAfter("item(key = \"milestones-pinned\"")
            .substringBefore("item(key = \"milestones-pinned-timeline-spacer\"")

        assertTrue(
            "The hero background wand should suppress Material's default minimum touch target, " +
                "matching StockSection's compact header action sizing.",
            pinnedSection.contains("LocalMinimumInteractiveComponentSize provides Dp.Unspecified"),
        )
        assertTrue(
            "The wand button should be sized to the section title line height.",
            pinnedSection.contains("IconButton(") &&
                pinnedSection.contains(".size(iconSize)") &&
                pinnedSection.contains("Icon("),
        )
        assertFalse(
            "Using a fixed 48.dp button makes the journal header taller than StockSection.",
            pinnedSection.contains("Modifier.size(48.dp)"),
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
