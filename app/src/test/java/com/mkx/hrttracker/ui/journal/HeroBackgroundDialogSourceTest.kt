package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HeroBackgroundDialogSourceTest {
    @Test
    fun dateColorSwatchIsPlainPrimaryColorWithoutIcon() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/HeroBackgroundDialog.kt",
        ).readText()
        val dateColorSwatch = source.substringAfter("fun DateColorSwatch(")
            .substringBefore("\n@OptIn")

        assertTrue(
            "The date-color selector should use the date palette primary color directly.",
            dateColorSwatch.contains("val fill = colorScheme.primary") &&
                dateColorSwatch.contains(".background(fill)"),
        )
        assertFalse(
            "The date-color selector should be a plain color swatch, not an icon chip.",
            dateColorSwatch.contains("Icon(") ||
                dateColorSwatch.contains("painterResource(R.drawable.ic_palette)") ||
                dateColorSwatch.contains("primaryContainer"),
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
