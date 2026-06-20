package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HeroBackgroundRenderGuardTest {
    private val source = File(
        projectRoot(),
        "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalComponents.kt",
    ).readText()

    @Test
    fun heroViewRendersDateColorAndFlagBackgrounds() {
        assertTrue(
            "HeroColorBackground must still take a PrideFlag for flag selections.",
            source.contains("fun HeroColorBackground(flag: PrideFlag"),
        )
        assertTrue(
            "HeroDateColorBackground must render the date palette selection.",
            source.contains("fun HeroDateColorBackground(colorScheme: ColorScheme"),
        )
        val heroView = source.substringAfter("private fun HeroViewLayout(")
            .substringBefore("private fun ")
        assertTrue(
            "HeroViewLayout must capture the hero background selection before rendering the wash.",
            heroView.contains("val heroBackground = anchor.heroBackground"),
        )
        assertTrue(
            "Date color should render from the row's medication-group palette.",
            heroView.contains("HeroBackground.DateColor ->") &&
                heroView.contains("HeroDateColorBackground(") &&
                heroView.contains("colorScheme = colorScheme"),
        )
        assertTrue(
            "Flag selections should still render through HeroColorBackground.",
            heroView.contains("is HeroBackground.Flag ->") &&
                heroView.contains("flag = heroBackground.flag"),
        )
        assertTrue(
            "Explicit None should skip the background wash.",
            heroView.contains("HeroBackground.None -> Unit"),
        )
        assertTrue(
            "The frosted watermark must be gated on any active background and haze support.",
            heroView.contains("val drawsHeroBackground = heroBackground != HeroBackground.None") &&
                heroView.contains("if (drawsHeroBackground && hazeBlurSupported)"),
        )
    }

    @Test
    fun compactRowNeverRendersTheWash() {
        val compact = source.substringAfter("private fun PinnedCompactLayout(")
            .substringBefore("\nprivate fun ")
        assertFalse(
            "The compact/edit row must never draw the hero wash.",
            compact.contains("HeroColorBackground("),
        )
    }

    @Test
    fun hardCodedTransSeedsAreGone() {
        assertFalse(
            "The prototype's hard-coded trans seeds must be replaced by PrideFlag seeds.",
            source.contains("HeroBackgroundSeeds"),
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
