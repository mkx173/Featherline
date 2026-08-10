package com.mkx.hrttracker.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Top-level destinations share one chrome contract on wide and foldable windows:
 * their titles use the centered Material3 app bar while nested destinations keep
 * the leading-aligned bar for back navigation.
 */
class FoldableLayoutConsistencyTest {
    @Test
    fun topLevelDestinationTitlesUseCenteredTopAppBars() {
        listOf(
            "app/src/main/java/com/mkx/hrttracker/ui/main/MainScreen.kt",
            "app/src/main/java/com/mkx/hrttracker/ui/plan/PlanScreen.kt",
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalScreens.kt",
            "app/src/main/java/com/mkx/hrttracker/ui/settings/SettingsScreen.kt",
        ).forEach { relativePath ->
            val source = File(projectRoot(), relativePath).readText()
            assertTrue(
                "$relativePath must use the centered top-level app bar",
                source.contains("HazeCenterAlignedTopAppBar("),
            )
        }
    }

    @Test
    fun planSelectedDayHeaderBalancesCalendarAndEmptyStateGaps() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/plan/PlanScreenComponents.kt",
        ).readText()

        assertTrue(
            "The plan date pill needs a matching outer bottom inset so the empty-state " +
                "card is spaced like the calendar above it.",
            source.contains("modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)"),
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
