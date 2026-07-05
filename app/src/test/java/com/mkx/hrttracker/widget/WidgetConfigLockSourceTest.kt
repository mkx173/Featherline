package com.mkx.hrttracker.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Guard for the app-lock gate on the widget config window: the Activity is exported and
// launcher-reachable and renders journal data (anchor names/dates, the dose snapshot
// preview), so it must honor the in-app lock like MainActivity. A refactor that composes
// WidgetConfigScreen without consulting the lock state silently reopens the leak.
class WidgetConfigLockSourceTest {
    @Test
    fun widgetConfigActivity_gatesContentBehindAppLock() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )

        assertTrue(
            "WidgetConfigActivity should own an AppLockViewModel like MainActivity.",
            source.contains("appLockViewModel: AppLockViewModel by viewModels()"),
        )
        assertTrue(
            "The biometric prompt effect must be installed so the lock can be cleared.",
            source.contains("AppAuthenticationPromptEffect("),
        )

        val content = source.substringAfter("setContent {")
        val lockGateIndex = content.indexOf("appLockUiState.shouldShowLockScreen ->")
        val screenIndex = content.indexOf("WidgetConfigScreen(")
        assertTrue(
            "Content must branch on shouldShowLockScreen before composing " +
                "WidgetConfigScreen, so locked launches show AppLockScreen instead of " +
                "anchor data.",
            lockGateIndex in 0 until screenIndex,
        )
        assertTrue(
            "The lock branch should render AppLockScreen.",
            content.contains("appLockUiState.shouldShowLockScreen -> AppLockScreen("),
        )

        assertTrue(
            "Foreground/background transitions must reach the lock ViewModel or the " +
                "grace-period re-lock never happens for this window.",
            source.contains("appLockViewModel.onForegrounded()") &&
                source.contains("appLockViewModel.onBackgrounded()"),
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
