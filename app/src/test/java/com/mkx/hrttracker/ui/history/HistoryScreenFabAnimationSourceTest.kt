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

    @Test
    fun historyEntries_areIndividualLazyItems() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/history/HistoryScreen.kt"
        ).readText()
        val groupedEntriesSlot = source.substringAfter("groupedEntries.entries.forEachIndexed")
            .substringBefore("if (effectiveSelectedDate != null)")

        assertFalse(
            "Each date's entries should not be grouped into one lazy item; that makes " +
                    "scroll-to-top remeasure and reuse a large ListItem subtree in one frame.",
            groupedEntriesSlot.contains("item(key = \"entries-\$date\")"),
        )
        assertTrue(
            "Each entry should be keyed independently so LazyColumn can reuse and precompose " +
                    "entry rows incrementally during animated scroll.",
            groupedEntriesSlot.contains("key = \"entry-\${entry.uuid}\""),
        )
        assertTrue(
            "Entry rows should declare a stable content type for LazyColumn reuse.",
            groupedEntriesSlot.contains("contentType = \"history-entry\""),
        )
    }

    @Test
    fun historyEntryTitleSpacing_doesNotDoubleLeadingGap() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/history/HistoryScreen.kt"
        ).readText()
        val titleItem = source.substringAfter("key = \"history-entry-title\"")
            .substringBefore("if (visibleEntries.isEmpty())")

        assertFalse(
            "The title item already has an explicit leading spacer replacing LazyColumn's " +
                    "global item gap. Keeping Arrangement.spacedBy there adds an extra 4dp " +
                    "before the divider.",
            titleItem.contains("verticalArrangement = Arrangement.spacedBy"),
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
