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
                "offsets include the same spacing the user sees. The gaps also hold " +
                "through the post-drop settle (settlingAfterDrag) so exiting edit mode " +
                "before onSettle lands doesn't change spacing, rebuild the reorder state " +
                "from the stale list, and snap the order back.",
            pinnedTrayRows.contains(
                "val reorderOwnsGaps = (isEditMode || settlingAfterDrag) && !hasTransitioningRows"
            ) &&
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

    @Test
    fun pinnedTray_hasNoDragHintFloorAndUsesSupportMessageForEmptyState() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalComponents.kt",
        ).readText()

        assertFalse(
            "Dragging a pinned row should not reveal a Home-slot hint floor behind it.",
            source.contains("PinnedHomeSlotFloor("),
        )
        assertFalse(
            "The old drag hint copy should no longer be referenced by PinnedTray.",
            source.contains("journal_home_slot_hint"),
        )
        assertTrue(
            "The empty pinned state should use the standard support-message row.",
            source.contains("SupportMessageListItem(") &&
                source.contains("text = stringResource(R.string.journal_home_slot_empty)") &&
                source.contains("painter = painterResource(R.drawable.ic_info)"),
        )
    }

    @Test
    fun pinnedTray_emptyMorphFadesOnlyInnerContents() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalComponents.kt",
        ).readText()
        val pinnedTray = source.substringAfter("fun PinnedTray(")
            .substringBefore("\n@Composable\nprivate fun PinnedTrayRows")

        assertTrue(
            "The empty-to-rows morph should keep the outer card fully opaque and animate size only.",
            pinnedTray.contains("targetContentEnter = EnterTransition.None") &&
                pinnedTray.contains("initialContentExit = ExitTransition.None") &&
                pinnedTray.contains("sizeTransform = SizeTransform"),
        )
        assertFalse(
            "Fading the AnimatedContent target fades the card surface along with its contents.",
            pinnedTray.contains("targetContentEnter = fadeIn") ||
                pinnedTray.contains("initialContentExit = fadeOut"),
        )
        assertTrue(
            "Only the inner empty/row contents should fade during the morph.",
            pinnedTray.contains("val contentFadeModifier = Modifier.animateEnterExit(") &&
                pinnedTray.contains("contentModifier = contentFadeModifier"),
        )
    }

    @Test
    fun pinnedTray_emptyMorphClipsToRoundedCardShape() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalComponents.kt",
        ).readText()
        val pinnedTray = source.substringAfter("fun PinnedTray(")
            .substringBefore("\n@Composable\nprivate fun PinnedTrayRows")

        assertTrue(
            "The empty-to-rows morph should clip to the card corner shape so the size animation " +
                "cannot briefly reveal square bottom corners.",
            pinnedTray.contains(".clip(MaterialTheme.shapes.large)") &&
                pinnedTray.indexOf(".fillMaxWidth()") < pinnedTray.indexOf(".clip(MaterialTheme.shapes.large)"),
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
