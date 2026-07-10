package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinFolderOverflowSourceTest {
    @Test
    fun pinFolderOverflowIsGatedOnShortcutSupport() {
        val source = File(
            projectRoot(),
            "app/src/main/java/com/mkx/hrttracker/ui/journal/JournalScreens.kt",
        ).readText()

        // The overflow lives inside the top app bar's actions block. Scope the assertion to it
        // so we're checking the milestones overflow, not some other menu.
        val actions = source.substringAfter("actions = {").substringBefore("scrollBehavior = ")

        val supportCheck = actions.indexOf("AnchorShortcutManager.isSupported(context)")
        val pinItem = actions.indexOf("R.string.anchor_pin_folder_icon")

        assertTrue(
            "Pinning is a silent no-op where ShortcutManager.isRequestPinShortcutSupported is " +
                "false, so the pin-folder overflow item must be gated behind " +
                "AnchorShortcutManager.isSupported and not offered unconditionally.",
            supportCheck != -1 && pinItem != -1 && supportCheck < pinItem,
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
