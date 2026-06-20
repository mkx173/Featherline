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
    fun heroColorBackgroundTakesAFlagAndIsGatedOnNonNull() {
        assertTrue(
            "HeroColorBackground must take a PrideFlag (no more hard-coded seeds).",
            source.contains("fun HeroColorBackground(flag: PrideFlag"),
        )
        val heroView = source.substringAfter("private fun HeroViewLayout(")
            .substringBefore("private fun ")
        assertTrue(
            "HeroViewLayout must capture the hero's flag before rendering the wash.",
            heroView.contains("val heroBackground = anchor.heroBackground"),
        )
        val heroBackgroundGate = heroView.blockAfter("if (heroBackground != null)")
        assertTrue(
            "The wash must be gated on the hero's flag being set.",
            heroBackgroundGate.contains("HeroColorBackground("),
        )
        assertTrue(
            "HeroColorBackground must be driven by the captured flag.",
            heroBackgroundGate.contains("flag = heroBackground"),
        )
        assertTrue(
            "The frosted watermark must be gated on both a flag and haze support.",
            heroView.contains("if (heroBackground != null && hazeBlurSupported)"),
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

    private fun String.blockAfter(marker: String): String {
        val markerIndex = indexOf(marker)
        assertTrue("Expected to find `$marker`.", markerIndex >= 0)
        val openBrace = indexOf('{', startIndex = markerIndex)
        assertTrue("Expected `$marker` to open a block.", openBrace >= 0)

        var depth = 0
        for (index in openBrace until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(openBrace + 1, index)
                }
            }
        }
        throw AssertionError("Expected `$marker` block to close.")
    }
}
