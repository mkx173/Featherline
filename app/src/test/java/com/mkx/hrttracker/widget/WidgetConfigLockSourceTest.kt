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
        assertTrue(
            "The lock the screen hides under must be the app lock's own decision.",
            content.contains("val locked = appLockUiState.shouldShowLockScreen"),
        )
        val hiddenIndex = content.indexOf("HiddenUnderAppLock(locked = locked)")
        val screenIndex = content.indexOf("WidgetConfigScreen(")
        assertTrue(
            "WidgetConfigScreen must compose under HiddenUnderAppLock, as MainActivity's home " +
                "shell: hidden while locked, and kept composed so a relock keeps its drafts.",
            hiddenIndex in 0 until screenIndex,
        )
        // After the loaded gate closes, so a prompt cancelled during the load shows the Unlock
        // button, not a blank window.
        val afterScreen = content.substringAfter("onCancel = { finish() },")
        val lockScreenIndex = afterScreen.indexOf("AppLockScreen(")
        assertTrue(
            "AppLockScreen must be drawn over the screen, outside the loaded gate.",
            lockScreenIndex >= 0 &&
                afterScreen.substring(0, lockScreenIndex)
                    .contains("if (appLockUiState.isReady && locked) {"),
        )

        assertTrue(
            "Foreground/background transitions must reach the lock ViewModel or the " +
                "grace-period re-lock never happens for this window.",
            source.contains("appLockViewModel.onForegrounded()") &&
                source.contains("appLockViewModel.onBackgrounded()"),
        )
    }

    @Test
    fun widgetConfigActivity_hidesScreenContentFromTheFirstFrame() {
        val content = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        ).substringAfter("setContent {")

        val secureIndex = content.indexOf(
            "applyHideScreenContent(enabled = appLockUiState.hideScreenContentEnabled)"
        )
        assertTrue(
            "WidgetConfigActivity shows anchors and doses, so hide screen content must set " +
                "FLAG_SECURE on it as on MainActivity, from the lock's fail-closed value.",
            secureIndex >= 0,
        )
        assertTrue(
            "FLAG_SECURE must apply before the settings load gate, so the loading window and " +
                "the sheets over it are covered too.",
            secureIndex < content.indexOf("loadedState?.takeIf"),
        )
    }

    // ponytail: a source guard, as above; a Robolectric run of this @AndroidEntryPoint activity
    // needs Hilt's testing setup, which the project doesn't have.
    @Test
    fun widgetConfigActivity_showsEverythingInTheAppLanguage_theUnlockPromptIncluded() {
        val source = source("app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt")
        val onCreate = source.substringAfter("override fun onCreate(").substringBefore("setContent {")
        assertTrue(
            "Like MainActivity, re-read the app language at onCreate: it may have changed while " +
                "the process lived on.",
            onCreate.contains("settingsRepository.refreshAppLanguageOption()"),
        )

        val content = source.substringAfter("setContent {")
        val providerIndex = content.indexOf("LocalContext provides localizedContext")
        assertTrue(
            "The content must be provided the app-language context before the unlock prompt, " +
                "which reads its strings from LocalContext, and before the screen.",
            providerIndex in 0 until content.indexOf("AppAuthenticationPromptEffect("),
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
