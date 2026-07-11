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

    @Test
    fun widgetDateReceiver_guardsHomeRefreshIndependentlyOfAnchorRefreshes() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetDateReceiver.kt"
        )
        assertTrue(
            "The home snapshot refresh must be guarded like the anchor refreshes: an " +
                "unguarded throw from this root coroutine crashes the process on every " +
                "midnight/boot/timezone broadcast while the failure holds, and skips the " +
                "anchor widget/shortcut refreshes behind it.",
            source.contains("runCatching {") &&
                source.substringAfter("runCatching {")
                    .substringBefore("}").contains("refreshHomeSnapshotIfNeeded"),
        )
    }

    @Test
    fun composeAnchorRemoteViews_forwardsAppWidgetIdToContent() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/AnchorWidget.kt"
        )
        val body = source.substringAfter("internal suspend fun composeAnchorRemoteViews")
        assertTrue(
            "The background-push compose path must forward the real appWidgetId into " +
                "AnchorWidgetContent: the default INVALID id makes a pushed empty state " +
                "tap through to Milestones instead of reconfiguring the affected instance.",
            body.substringAfter("AnchorWidgetContent(")
                .contains("appWidgetId = appWidgetId"),
        )
    }

    @Test
    fun widgetConfigActivity_boundsTheAnchorSeedAwait() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )
        assertTrue(
            "The config window's anchor seed must use the bounded await: the unbounded " +
                "awaitTrackedDates suspends indefinitely through a persistent read-error " +
                "window, stranding the whole config UI blank with no cancel path.",
            source.contains("awaitTrackedDatesOrSnapshot(ANCHOR_WIDGET_AWAIT_TIMEOUT_MS)") &&
                !source.contains("awaitTrackedDates()"),
        )
    }

    @Test
    fun widgetConfigScreen_rekeysPreviewOnLiveAnchorChanges() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigScreen.kt"
        )
        val keys = source
            .substringAfter("produceState<WidgetConfigPreviewRender?>")
            .substringBefore(") {")
        assertTrue(
            "The preview producer must key on the live anchor list, not just the " +
                "selected id: it captures the resolved anchor in its closure, so a " +
                "rename/delete with an unchanged id would keep rendering the stale " +
                "capture even on later appearance emissions.",
            keys.contains("anchors"),
        )
    }

    @Test
    fun widgetConfigActivity_retargetsOnNewIntentForADifferentWidget() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )
        val onNewIntent = source.substringAfter("override fun onNewIntent")
        assertTrue(
            "A singleTop redelivery for a different widget must retarget the retained " +
                "instance: without this, Save writes the new widget's anchor selection " +
                "and RESULT_OK against the appWidgetId captured in the first onCreate.",
            onNewIntent.contains("if (newAppWidgetId != appWidgetId)") &&
                onNewIntent.contains("appWidgetId = newAppWidgetId") &&
                onNewIntent.contains("RESULT_CANCELED"),
        )
        assertTrue(
            "The retarget must discard the previous widget's UI state: the load rekeys " +
                "on appWidgetId (dropping its stale value first, so the screen unmounts " +
                "instead of seeding from the old widget's initialAnchorId) and the " +
                "screen is wrapped in key(appWidgetId) so rememberSaveable cannot " +
                "restore the old selection on remount.",
            source.contains(
                "produceState<LoadedConfigState?>(initialValue = null, appWidgetId)"
            ) &&
                source.contains("value = null") &&
                source.contains("key(appWidgetId) {"),
        )
    }

    @Test
    fun databaseHolder_signalsOpenOutcomeFromEveryWarmupPath() {
        val holder = source(
            "app/src/main/java/com/mkx/hrttracker/data/local/DatabaseHolder.kt"
        )
        val openAndSignal = holder.substringAfter("fun openAndSignal")
        assertTrue(
            "The shared open must flip openFailed on failure and clear it on success — a " +
                "flag set only by warmUp() leaves the journal's seeded flow (Milestones " +
                "with no snapshot) spinning forever when the in-app cold-start open path " +
                "was the one that failed.",
            openAndSignal.contains("_openFailed.value = true") &&
                openAndSignal.contains("_openFailed.value = false"),
        )
        val preloader = source(
            "app/src/main/java/com/mkx/hrttracker/startup/StartupPreloader.kt"
        )
        assertTrue(
            "The in-app cold-start open must route through the signalling open, not a " +
                "bare get(), so its failure reaches DatabaseHolder.openFailed.",
            preloader.contains("databaseHolder.openAndSignal()") &&
                !preloader.contains("databaseHolder.get()"),
        )
    }

    @Test
    fun awaitTrackedDates_neverBlocksTheTimedPath() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/data/repository/JournalRepository.kt"
        )
        val body = source
            .substringAfter("suspend fun awaitTrackedDates()")
            .substringBefore("suspend fun awaitTrackedDatesOrNull")
        assertTrue(
            "awaitTrackedDates must not run the blocking database open on its own " +
                "coroutine: withTimeoutOrNull cannot interrupt a blocking frame, so the " +
                "bounded helpers' budgets were illusory when the SQLCipher open stalled. " +
                "The open belongs on the holder's scope (warmUp) with a cancellable flow " +
                "await racing the terminal openFailed signal.",
            body.contains("databaseHolder.warmUp()") &&
                body.contains("databaseHolder.openFailed") &&
                !body.contains("databaseHolder.get()"),
        )
    }

    @Test
    fun anchorWidgetManager_guardsWidgetAndShortcutRefreshSeparately() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/AnchorWidgetManager.kt"
        )
        assertTrue(
            "The journal collector must guard the widget repaint and the shortcut " +
                "refresh independently (like WidgetDateReceiver): a repaint throw from " +
                "the trailing Glance reconcile must not skip the shortcut refresh, or " +
                "orphaned/renamed shortcuts stay stale until an unrelated broadcast.",
            source.contains("anchor_widget_refresh_failed") &&
                source.contains("anchor_shortcut_refresh_failed") &&
                !source.contains("\"anchor_refresh_failed\""),
        )
    }

    @Test
    fun widgetConfigActivity_confirmsAnchorPlacementOnlyAfterThePersistLands() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )
        val saveBlock = source
            .substringAfter("onSaveAnchor = {")
            .substringBefore("onSave = {")
        assertTrue(
            "The save must capture its target id before launching: onNewIntent can " +
                "retarget the mutable appWidgetId while the writes are in flight, which " +
                "would write widget A's anchor into widget B's Glance state — and " +
                "finishing after a retarget would close the window now hosting B's config.",
            saveBlock.contains("val targetWidgetId = appWidgetId") &&
                saveBlock.contains("if (appWidgetId == targetWidgetId)"),
        )
        assertTrue(
            "RESULT_OK must be reported only after the anchor id persists (the anchor IS " +
                "the required configuration): confirming before the write leaves a failed " +
                "first placement as a confirmed-but-unconfigured widget stuck on the " +
                "empty state, instead of letting RESULT_CANCELED remove it for a retry.",
            !saveBlock.substringBefore("appScope.launch").contains("RESULT_OK") &&
                saveBlock.contains("if (persisted)"),
        )
    }

    @Test
    fun widgetConfigActivity_repaintsAnchorWidgetsThroughTheSynchronousPush() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt"
        )
        val saveBlock = source
            .substringAfter("onSaveAnchor = {")
            .substringBefore("onSave = {")
        assertTrue(
            "The anchor config save must repaint through updateAllAnchorWidgets (the " +
                "synchronous RemoteViews push): finish() backgrounds the process right " +
                "after the save, exactly where a bare Glance session update stalls, so " +
                "the launcher kept the old anchor date until an unrelated broadcast.",
            saveBlock.contains("updateAllAnchorWidgets(") &&
                !saveBlock.contains(".update(this@WidgetConfigActivity"),
        )
    }

    @Test
    fun updateAllHrtWidgets_routesThroughTheSynchronousPush() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/widget/HrtWidget.kt"
        )
        val body = source
            .substringAfter("suspend fun updateAllHrtWidgets")
            .substringBefore("suspend fun pushHrtWidgets")
        assertTrue(
            "updateAllHrtWidgets must read the stored snapshot and delegate to " +
                "pushHrtWidgets: all of its callers (quick-log callback, daily refresh " +
                "worker) run backgrounded, where a bare session updateAll stalls until " +
                "the app next draws a frame.",
            body.contains("readSnapshot()") &&
                body.contains("pushHrtWidgets(context, record)") &&
                !body.contains("glanceUpdateAll"),
        )
    }

    @Test
    fun mainActivity_parsesDeepLinksAfterProcessDeathButNeverHistoryReplays() {
        val source = source(
            "app/src/main/java/com/mkx/hrttracker/MainActivity.kt"
        )
        assertTrue(
            "The launch-intent parse must gate on the ViewModel-lifetime marker plus the " +
                "history flag, not savedInstanceState: a widget tap that recreates a " +
                "killed task lands in onCreate with non-null savedInstanceState (a dead " +
                "instance can't receive onNewIntent) and must still navigate, while a " +
                "recents relaunch redelivers the original intent's stale extras and must " +
                "not.",
            source.contains("Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY") &&
                source.contains("if (parseLaunchIntent)") &&
                source.contains("mainViewModel.launchIntentParsed = true"),
        )
        assertTrue(
            "The milestones splash hold must share the parse's exact gate: a hold " +
                "without a parse waits on a navigation that never comes (stuck splash), " +
                "and a parse without a hold releases the splash on the pre-navigation " +
                "frame (restored-task deep links flash the old frame).",
            source.contains("awaitingMilestonesEntry = parseLaunchIntent &&"),
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
