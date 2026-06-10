package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards that the migrated LazyColumn screens flatten their sections through
 * [hrtSection] rather than packing a whole Composable [HrtSection] into a single
 * lazy item -- the scroll-jank regression these screens were fixed for.
 */
class LazySectionJankGuardTest {

    private fun source(relPath: String): String =
        File(projectRoot(), relPath).readText()

    @Test
    fun medicinesScreen_usesLazyHrtSection() {
        val src = source("app/src/main/java/com/mkx/hrttracker/ui/catalog/MedicinesScreen.kt")
        assertTrue("MedicinesScreen should flatten sections via hrtSection.", src.contains("hrtSection("))
        assertFalse(
            "MedicinesScreen should no longer pack a Composable HrtSection into a lazy item.",
            src.contains("HrtSection("),
        )
    }

    @Test
    fun archivedGroupsScreen_usesLazyHrtSection() {
        val src = source("app/src/main/java/com/mkx/hrttracker/ui/plan/ArchivedMedicationGroupsScreen.kt")
        assertTrue("ArchivedMedicationGroupsScreen should flatten via hrtSection.", src.contains("hrtSection("))
        assertFalse(
            "ArchivedMedicationGroupsScreen should no longer use a Composable HrtSection.",
            src.contains("HrtSection("),
        )
    }

    @Test
    fun planBatchAddScreen_groupSectionUsesLazyHrtSection() {
        // Range/preview sub-sections legitimately keep the Composable HrtSection
        // (they use animatedItem), so only assert the group list is flattened.
        val src = source("app/src/main/java/com/mkx/hrttracker/ui/plan/PlanBatchAddScreen.kt")
        assertTrue(
            "PlanBatchAdd group section should flatten via hrtSection.",
            src.contains("hrtSection(\n                    key = \"group-section\""),
        )
    }

    @Test
    fun planScreen_regimenSectionUsesLazyHrtSection() {
        val src = source("app/src/main/java/com/mkx/hrttracker/ui/plan/PlanScreen.kt")
        val regimenFn = src.substringAfter("fun LazyListScope.regimenSectionItems")
            .substringBefore("\nprivate fun ")
        assertTrue(
            "regimenSectionItems should flatten via hrtSection.",
            regimenFn.contains("hrtSection("),
        )
        assertFalse(
            "regimenSectionItems should not hand-roll LocalSegmentPosition; the helper provides it.",
            regimenFn.contains("LocalSegmentPosition provides"),
        )
    }

    private fun projectRoot(): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
