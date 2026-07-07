package com.mkx.hrttracker.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Guards the stale anchor-list fix: the config window is singleTop in its own task, so a
// retained instance can be resumed without recreation. The anchor picker must observe the
// live list after its one-shot seed or it shows "No tracked dates yet." forever for a user
// who added their first date after the window was first opened.
class AnchorSurfaceRefreshSourceTest {
    @Test
    fun widgetConfigActivity_keepsAnchorListLiveAfterSeed() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )
        val produceBlock = source.substringAfter("produceState<LoadedConfigState?>")
        assertTrue(
            "The anchor config load must keep collecting the live tracked-dates flow " +
                "after the one-shot seed, so a resumed (not recreated) config window " +
                "picks up dates added since it was first opened.",
            produceBlock.contains("observeLoadedTrackedDates().collect") &&
                produceBlock.contains("value = value?.copy(anchors ="),
        )
    }

    @Test
    fun updateAllAnchorWidgets_pushesRemoteViewsSynchronouslyAndReconciles() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/AnchorWidget.kt"
        )
        val body = source.substringAfter("suspend fun updateAllAnchorWidgets")
        assertTrue(
            "updateAllAnchorWidgets must compose and push RemoteViews synchronously: a " +
                "bare session update stalls in a backgrounded process (where the " +
                "midnight/date-change receiver runs) and skips recomposition on a date " +
                "tick (LocalDate.now() is not observable state), leaving yesterday's day " +
                "count on the widget until the app is reopened.",
            body.contains("composeAnchorRemoteViews(") &&
                body.contains("appWidgetManager.updateAppWidget("),
        )
        assertTrue(
            "After the push the session must be reconciled (glanceUpdateAll) so a " +
                "launcher re-attach can't re-assert a stale composition.",
            body.contains("glanceUpdateAll(context)"),
        )
    }

    @Test
    fun anchorWidgetManager_repaintsWidgetsOnWidgetFacingSettingsChanges() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/AnchorWidgetManager.kt"
        )
        val settingsCollector = source.substringAfter("settingsRepository.settingsState")
        assertTrue(
            "AnchorWidgetManager must observe the same widget-facing settings the anchor " +
                "render consumes.",
            settingsCollector.contains("settings.adaptiveColorEnabled") &&
                settingsCollector.contains("settings.appLanguageOption"),
        )
        assertTrue(
            "Widget-facing settings changes affect widget RemoteViews only; shortcuts do " +
                "not render localized text.",
            settingsCollector.contains("updateAllAnchorWidgets(context)") &&
                !settingsCollector.contains("AnchorShortcutManager.refreshAll(context)"),
        )
    }

    @Test
    fun anchorWidget_collectsWidgetSettingsInsideCompositionAndBakesDisplaySnapshot() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/AnchorWidget.kt"
        )
        val composition = source
            .substringAfter("override suspend fun provideGlance")
            .substringAfter("provideContent {")
        assertTrue(
            "Widget-facing settings must be a reactive source inside provideContent: " +
                "glanceUpdateAll on a live session only recomposes, so values captured " +
                "before the composition stay stale until the session dies.",
            composition.contains("settingsRepository.settingsState") &&
                composition.contains("settings.adaptiveColorEnabled") &&
                composition.contains("settings.appLanguageOption.languageTag") &&
                composition.contains(".collectAsState(initial = initialWidgetSettings)") &&
                composition.contains("adaptiveColorEnabled = widgetSettings.adaptiveColorEnabled") &&
                composition.contains("appLanguageTag = widgetSettings.appLanguageTag"),
        )
        assertTrue(
            "The anchor render should consume baked display text, matching the dose " +
                "snapshot convention of preformatted widget strings.",
            composition.contains("buildAnchorWidgetDisplayText(") &&
                composition.contains("AnchorWidgetContent(") &&
                composition.contains("displayText ="),
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
