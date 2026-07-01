package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnchorSelectorSourceTest {
    @Test
    fun anchorSelectorSheet_usesCalibrationStyleHeaderAndPadding() {
        val source = source("app/src/main/java/com/mkx/hrttracker/ui/journal/AnchorSelectorList.kt")
        val sheet = source.substringAfter("fun AnchorSelectorSheet(")
            .substringBefore("\n// Shared anchor picker content.")
        val content = source.substringAfter("fun AnchorSelectorSheetContent(")
            .substringBefore("\n// Shared anchor picker content.")
        val list = source.substringAfter("fun AnchorSelectorList(")

        assertTrue(
            "The sheet should delegate to a content composable with title/cancel chrome, " +
                "matching CalibrationAddAnalyteSheetContent.",
            sheet.contains("AnchorSelectorSheetContent(") &&
                content.contains("title: String") &&
                content.contains("HrtFilledTonalButton(") &&
                content.contains("text = stringResource(R.string.cancel)") &&
                content.contains("Arrangement.SpaceBetween") &&
                content.contains("navigationBarsPadding()") &&
                content.contains("R.dimen.padding_large") &&
                content.contains("R.dimen.padding_xsmall"),
        )
        assertFalse(
            "Raw selector list content should not own sheet-level navigation-bar padding.",
            list.contains("navigationBarsPadding()"),
        )
    }

    @Test
    fun anchorSelector_reusesMilestoneCardWithoutTimelineRail() {
        val anchorSelectorSource = source(
            "app/src/main/java/com/mkx/hrttracker/ui/journal/AnchorSelectorList.kt"
        )
        val journalComponentsSource = source(
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalComponents.kt"
        )
        val anchorSelectorList = anchorSelectorSource.substringAfter("fun AnchorSelectorList(")
        val timelineMilestoneRow = journalComponentsSource.substringAfter("fun TimelineMilestoneRow(")
            .substringBefore("\n@Composable\nprivate fun TimelineRailCell")

        assertFalse(
            "Anchor selector should not reuse the full timeline row because that brings the rail " +
                "and milestone node into a bottom sheet. It should reuse only the segmented card.",
            anchorSelectorList.contains("TimelineMilestoneRow("),
        )
        assertTrue(
            "Anchor selector and timeline should share the extracted segmented milestone card.",
            anchorSelectorList.contains("AnchorMilestoneCard(") &&
                timelineMilestoneRow.contains("AnchorMilestoneCard("),
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
