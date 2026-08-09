package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.ui.theme.DefaultSeedColor
import com.mkx.hrttracker.ui.theme.systemColorSchemes
import com.mkx.hrttracker.util.currentAppLocale
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityFromIntent
import androidx.glance.appwidget.updateAll as glanceUpdateAll
import androidx.glance.preview.Preview as GlancePreview


// ── Widget ────────────────────────────────────────────────────────────────────

private suspend fun GlanceAppWidget.provideHrtContent(
    context: Context,
    id: GlanceId,
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
    val appearanceRepository = EntryPointAccessors
        .fromApplication(context, WidgetEntryPoint::class.java)
        .widgetAppearanceRepository()
    // Resolve once before composing so the first frame already has the real value
    // (a Default initial would flash default colors on session spin-up).
    val initialAppearance = runCatching {
        appearanceRepository.currentEffective(appWidgetId)
    }.getOrElse { failure ->
        if (failure is CancellationException) throw failure
        WidgetAppearance.Default
    }
    provideContent {
        // Re-read on every composition so the baseline picks up the launcher's portrait
        // cell height as soon as options are reported (Glance recomposes on options change).
        val deviceBaselineHeightDp = appWidgetId?.let { widgetId ->
            runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) }
                .getOrNull()
                ?.let { options -> portraitBaselineHeightDp(options) }
        }
        val state = currentState<WidgetSnapshotState>()
        val snapshot = state.record?.takeIf { it.schemaVersion == WIDGET_SNAPSHOT_SCHEMA_VERSION }
        // Remembered: collectAsState keys on flow identity, so an unremembered
        // effectiveFor() would build a fresh combine flow each recomposition and
        // cancel/restart the underlying DataStore collections.
        val appearanceFlow = remember(appWidgetId) {
            appearanceRepository.effectiveFor(appWidgetId)
        }
        val appearance by appearanceFlow.collectAsState(initial = initialAppearance)
        HrtWidgetThemed(context, snapshot, appearance, deviceBaselineHeightDp) { content(it) }
    }
}

// Shared theme + CompositionLocal scaffold for the widget content. Used both by the
// session-backed provideContent path and by the synchronous GlanceRemoteViews.compose
// path (pushHrtWidgets), so the two render identically.
@Composable
internal fun HrtWidgetThemed(
    context: Context,
    snapshot: WidgetSnapshotRecord?,
    appearance: WidgetAppearance,
    deviceBaselineHeightDp: Float? = null,
    // Adaptive-colour + app-language default to the snapshot's baked values, so the dose
    // call sites (which always carry a snapshot) stay untouched. The anchor paths render
    // with snapshot = null and pass these explicitly, read from settings the same way the
    // snapshot builder bakes them, so the anchor widget honours the disabled-adaptive and
    // in-app-language settings just like the dose widgets.
    adaptiveColorEnabled: Boolean = snapshot?.adaptiveColorEnabled ?: true,
    appLanguageTag: String? = snapshot?.appLanguageTag,
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    val adaptiveEnabled = adaptiveColorEnabled
    val sanitized = appearance.sanitized()
    val alpha = sanitized.backgroundAlpha
    val scale = sanitized.contentScale
    val forcedDark = sanitized.darkMode.toForcedDark()
    // Resolve the live-rendered chrome strings against the snapshot's app language, not
    // the raw widget context. Below API 33 the widget process context stays on the system
    // locale, so without this the chrome ("TODAY", "DONE", E2 label) renders in the system
    // language while the snapshot's baked medication/dose strings are in the app language.
    val localizedContext = remember(context, appLanguageTag) {
        context.withLanguageTag(appLanguageTag)
    }
    // Explicit seed pick wins; null keeps today's source selection (system palette
    // on API 31+ with adaptive on, DefaultSeedColor otherwise). Deriving the schemes
    // (HCT round-trips, plus a full tonal-palette generation for a seeded hue) is the
    // widget's heaviest per-frame work, so memoize it across Glance's frequent
    // recompositions (size/options/state) when the inputs are unchanged. forcedDark is
    // derived from sanitized, so the sanitized key already covers it.
    val widgetColors = remember(sanitized, adaptiveEnabled, localizedContext) {
        val (lightScheme, darkScheme) = when {
            sanitized.seedHue != null ->
                seededWidgetColorSchemes(seedColorFromHue(sanitized.seedHue))
            adaptiveEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                systemColorSchemes(localizedContext)
            else -> seededWidgetColorSchemes(DefaultSeedColor)
        }
        widgetColorScheme(lightScheme, darkScheme, sanitized, forcedDark)
    }
    GlanceTheme {
        CompositionLocalProvider(
            // GlanceRemoteViews.compose does not seed LocalContext the way the session
            // path does, so provide it explicitly; harmless (same value) on the session path.
            LocalContext provides localizedContext,
            LocalWidgetColors provides widgetColors,
            LocalWidgetScale provides scale,
            LocalWidgetAlpha provides alpha,
            LocalWidgetForcedDark provides forcedDark,
            LocalDeviceBaselineHeight provides deviceBaselineHeightDp,
        ) {
            content(snapshot)
        }
    }
}

// Returns a context whose resources resolve strings in [languageTag] (BCP-47). Mirrors
// WidgetSnapshotRepository.withAppLocale: createConfigurationContext + setLocale works on
// every supported API level, unlike reading the per-app locale back from the app context.
// Returns the receiver unchanged when the tag is null/blank or already the active locale.
private fun Context.withLanguageTag(languageTag: String?): Context {
    if (languageTag.isNullOrBlank()) return this
    val locale = Locale.forLanguageTag(languageTag)
    if (locale == currentAppLocale()) return this
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}

private suspend fun GlanceAppWidget.provideHrtPreviewContent(
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    provideContent {
        HrtPreviewContent(content)
    }
}

@Composable
private fun HrtPreviewContent(
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    val context = LocalContext.current
    GlanceTheme {
        CompositionLocalProvider(
            LocalWidgetColors provides widgetColorScheme(DefaultSeedColor),
            LocalWidgetScale provides WIDGET_PREVIEW_CONTENT_SCALE,
            LocalPreviewBaselineHeight provides WIDGET_BASELINE_REFERENCE_DP,
            LocalPreviewE2Text provides formatWidgetE2Text(
                currentConcentration = WIDGET_PREVIEW_E2_PG_PER_ML,
                concentrationUnit = PkConcentrationUnit.PG_PER_ML,
                displayUnit = BloodUnitKey.PG_ML,
            ),
        ) {
            content(previewSnapshot(context))
        }
    }
}

class HrtWidgetMedium : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(MEDIUM_WIDGET_PREVIEW_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideHrtContent(context, id) { snapshot -> MediumWidgetContent(snapshot) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideHrtPreviewContent { snapshot -> MediumWidgetContent(snapshot) }
    }
}

class HrtWidgetLarge : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(LARGE_WIDGET_PREVIEW_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideHrtContent(context, id) { snapshot -> LargeWidgetContent(snapshot) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideHrtPreviewContent { snapshot -> LargeWidgetContent(snapshot) }
    }
}

// Repaint every dose widget from the stored snapshot through the synchronous push. Every
// caller is a background context (quick-log callback, daily refresh worker), where a bare
// session updateAll stalls until the app next draws a frame (see the push note below) —
// the same staleness pushHrtWidgets exists to avoid. A null read renders the empty-setup
// state, exactly what the session path would compose from the same store.
suspend fun updateAllHrtWidgets(context: Context) {
    val record = EntryPointAccessors
        .fromApplication(context, WidgetEntryPoint::class.java)
        .widgetSnapshotStore()
        .readSnapshot()
    pushHrtWidgets(context, record)
}

// Widget push entry point. The synchronous push (GlanceRemoteViews.compose() +
// AppWidgetManager.updateAppWidget) bypasses Glance's lazy session: update()/updateAll() only
// signal the session, whose recomposition is driven by the app process's frame clock —
// backgrounded, no frames are produced and the update stalls until the launcher next draws or
// the app relaunches, and on a foregrounded tap the session spin-up briefly paints the
// initialLayout loading view (the "loading" flash). The one-shot compose pushes finished
// RemoteViews straight to the launcher, immediately and flash-free, even from the background.
//
// The catch is collection-backed views: a bare updateAppWidget() does not rebind a Glance
// LazyColumn below API 33 (only the session update / notifyAppWidgetViewDataChanged does), so
// the list would render stale. We therefore split per widget:
//   • Medium widget — plain views only, no LazyColumn → synchronous push on every API level.
//   • Large widget  — contains a LazyColumn → synchronous push only on API 33+, where the
//     platform rebinds inline collections from updateAppWidget; below 33 it falls back to the
//     session path (which reads the snapshot just written to the DataStore).
//
// Because the synchronous push never runs through the session, it leaves the session's
// in-memory composition at its pre-push state. The session doesn't repaint on its own, but the
// launcher re-asserts that stale composition on the next re-attach (swiping back to the page,
// dismissing the reconfigure activity), briefly reverting the widget to the old content until
// the session finally recomposes. So after each synchronous push we also updateAll() that
// widget: it recomposes from the snapshot we just wrote to the DataStore, putting the session's
// cached composition back in lockstep with what the push painted. The push still owns the
// instant, background-capable paint; the trailing updateAll() only reconciles — it defers
// harmlessly while backgrounded (the push already showed the right content) and is invisible
// while foregrounded (the user is in the app, not watching the widget).
//
// Tradeoff (synchronous path): we compose a single RemoteViews for the current orientation's
// size rather than Glance's automatic portrait/landscape variants. Acceptable here because
// content scale is frozen to the captured per-device baseline.
//
// A null record renders the empty-setup state, so clearWidgetSnapshot can reuse the same
// widget update path.
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushHrtWidgets(context: Context, record: WidgetSnapshotRecord?) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appearanceRepository = EntryPointAccessors
        .fromApplication(context, WidgetEntryPoint::class.java)
        .widgetAppearanceRepository()
    coroutineScope {
        launch {
            pushHrtWidget(
                context,
                appWidgetManager,
                appearanceRepository,
                HrtWidgetMediumReceiver::class.java,
                record,
                isMedium = true
            )
            // Reconcile the session with the snapshot we just pushed so it can't re-assert a
            // stale composition on the next launcher re-attach (see note above).
            HrtWidgetMedium().glanceUpdateAll(context)
        }
        launch {
            if (shouldUseSynchronousWidgetPush()) {
                pushHrtWidget(
                    context,
                    appWidgetManager,
                    appearanceRepository,
                    HrtWidgetLargeReceiver::class.java,
                    record,
                    isMedium = false
                )
                // Reconcile the session after the synchronous push (see note above).
                HrtWidgetLarge().glanceUpdateAll(context)
            } else {
                // Below API 33 the large widget already renders through the session, so its
                // composition is current — no separate reconcile needed.
                HrtWidgetLarge().glanceUpdateAll(context)
            }
        }
    }
}

// Whether the LARGE widget (the only one with a collection-backed LazyColumn) can take the
// synchronous push. Below API 33 a bare updateAppWidget() won't rebind the LazyColumn, so it
// must use the session path; API 33+ rebinds inline collections from updateAppWidget. The
// medium widget has no collection and always takes the synchronous push regardless.
internal fun shouldUseSynchronousWidgetPush(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU

@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
private suspend fun pushHrtWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appearanceRepository: WidgetAppearanceRepository,
    receiverClass: Class<*>,
    record: WidgetSnapshotRecord?,
    isMedium: Boolean,
) {
    val appWidgetIds = runCatching {
        appWidgetManager.getAppWidgetIds(ComponentName(context, receiverClass))
    }.getOrElse { intArrayOf() }
    if (appWidgetIds.isEmpty()) return
    // Resolve every instance against one read of the shared default entry, instead of
    // re-reading the default per instance inside currentEffective.
    val appearances = runCatching { appearanceRepository.currentEffectiveFor(appWidgetIds) }
        .getOrElse { failure ->
            if (failure is CancellationException) throw failure
            emptyMap()
        }
    appWidgetIds.forEach { appWidgetId ->
        // One options read; size and baseline resolve through the same helper the
        // config-screen preview uses.
        val (size, deviceBaselineHeightDp) = resolveWidgetRenderSize(
            context,
            appWidgetManager.getAppWidgetOptions(appWidgetId),
            isMedium,
        )
        val appearance = appearances[appWidgetId] ?: WidgetAppearance.Default
        val result = runCatching {
            GlanceRemoteViews().compose(context = context, size = size) {
                HrtWidgetThemed(context, record, appearance, deviceBaselineHeightDp) { snapshot ->
                    if (isMedium) MediumWidgetContent(snapshot) else LargeWidgetContent(snapshot)
                }
            }
        }.getOrNull() ?: return@forEach
        runCatching { appWidgetManager.updateAppWidget(appWidgetId, result.remoteViews) }
    }
}

// RemoteViews plus the DpSize it was composed at, so the preview host lays the
// AndroidView out at exactly the composed size before fit-scaling it for display.
internal class WidgetConfigPreviewRender(
    val remoteViews: RemoteViews,
    val sizeDp: DpSize,
)

// Single reuse point for WidgetConfigActivity's live preview. Encapsulates the private
// render helpers: preview size selection, the preview baseline, applying the live
// control values onto the snapshot, and the synchronous GlanceRemoteViews compose.
// Falls back to previewSnapshot when no snapshot was ever persisted (fresh install /
// first placement); in that case the preview E2 trend text is provided the same way
// HrtPreviewContent does, since the fabricated snapshot has no real projection.
//
// When [appWidgetId] resolves to live launcher options, the preview is composed at the
// widget's ACTUAL current cell size and resolves scale through the same device baseline
// path the real widget uses (no LocalPreviewBaselineHeight override) — true WYSIWYG.
// Otherwise (invalid id / options read failed) it falls back to the fixed reference
// preview size with the reference baseline, as before.
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
internal suspend fun composeWidgetPreviewRemoteViews(
    context: Context,
    isMedium: Boolean,
    appearance: WidgetAppearance,
    snapshot: WidgetSnapshotRecord?,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
): WidgetConfigPreviewRender {
    val previewE2Text = if (snapshot == null) {
        formatWidgetE2Text(
            currentConcentration = WIDGET_PREVIEW_E2_PG_PER_ML,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML,
            displayUnit = BloodUnitKey.PG_ML,
        )
    } else {
        null
    }
    val record = snapshot ?: previewSnapshot(context)
    val options = widgetOptionsOrNull(context, appWidgetId)
    // Live options → actual size + the widget's real device baseline, resolved through
    // the same helper as the production push so the two paths cannot diverge. No
    // options → fixed reference size + baseline.
    val (size, deviceBaselineHeightDp) = resolveWidgetRenderSize(context, options, isMedium)
    val remoteViews = GlanceRemoteViews().compose(context = context, size = size) {
        HrtWidgetThemed(context, record, appearance, deviceBaselineHeightDp) { themed ->
            CompositionLocalProvider(
                // Only pin the fixed reference baseline on the fallback path; with live
                // options the real device baseline (above) drives scale for true WYSIWYG.
                LocalPreviewBaselineHeight provides
                    if (options != null) null else WIDGET_BASELINE_REFERENCE_DP,
                LocalPreviewE2Text provides previewE2Text,
                LocalHostFreePreview provides true,
            ) {
                if (isMedium) MediumWidgetContent(themed) else LargeWidgetContent(themed)
            }
        }
    }.remoteViews
    return WidgetConfigPreviewRender(remoteViews, size)
}

internal fun widgetOptionsOrNull(context: Context, appWidgetId: Int): Bundle? = appWidgetId
    .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    ?.let { widgetId ->
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) }
            .getOrNull()
    }

// The size the config preview will compose at, resolved synchronously through the same
// path as the render itself, so the config screen can reserve the wallpaper window's
// final footprint on its first frame instead of blinking in once the first async
// render lands.
internal fun widgetPreviewSizeDp(context: Context, isMedium: Boolean, appWidgetId: Int): DpSize =
    resolveWidgetRenderSize(context, widgetOptionsOrNull(context, appWidgetId), isMedium).sizeDp

// The size a widget render composes at plus the device baseline that drives its content
// scale. The production push and the config-screen preview BOTH resolve through
// resolveWidgetRenderSize, so a change to the derivation cannot silently make the
// preview diverge from the real widget.
private data class WidgetRenderSize(
    val sizeDp: DpSize,
    val deviceBaselineHeightDp: Float?,
)

// Null options (a preview without live launcher options) fall back to the fixed
// reference preview size with no device baseline.
private fun resolveWidgetRenderSize(
    context: Context,
    options: Bundle?,
    isMedium: Boolean,
): WidgetRenderSize = if (options != null) {
    // Options not yet reported (e.g. freshly added widget): fall back to a sane size so the
    // one-shot compose still produces a usable layout. The height matches the baseline
    // reference, so if this fallback happens to be the first render to capture the device
    // baseline, it resolves to scale 1.0 rather than a slightly-off value. Widths are
    // nominal — content fills the launcher-allocated cell width regardless.
    val fallbackHeight = WIDGET_BASELINE_REFERENCE_DP.dp
    val fallback = if (isMedium) DpSize(280.dp, fallbackHeight) else DpSize(483.dp, fallbackHeight)
    WidgetRenderSize(
        sizeDp = currentWidgetSizeDp(context, options, fallback),
        deviceBaselineHeightDp = portraitBaselineHeightDp(options),
    )
} else {
    WidgetRenderSize(
        sizeDp = if (isMedium) MEDIUM_WIDGET_PREVIEW_SIZE else LARGE_WIDGET_PREVIEW_SIZE,
        deviceBaselineHeightDp = null,
    )
}

// The widget size for the current orientation, derived from the launcher's options.
// Portrait uses (minWidth, maxHeight); landscape uses (maxWidth, minHeight) — matching
// how SizeMode.Exact picks the size Glance composes against. Zero/absent dimensions
// (options not yet reported) fall back to the caller's reference size.
private fun currentWidgetSizeDp(
    context: Context,
    options: Bundle,
    fallback: DpSize,
): DpSize {
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    val landscape =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = if (landscape) maxWidth else minWidth
    val heightDp = if (landscape) minHeight else maxHeight
    if (widthDp > 0 && heightDp > 0) {
        return DpSize(widthDp.dp, heightDp.dp)
    }
    return fallback
}

// The anchor preview's compose size: the instance's ACTUAL launcher cell size when live
// options are available — the same derivation the dose widgets' preview uses, so the
// config preview matches the placed widget — else the fixed reference preview size.
internal fun anchorWidgetPreviewSizeDp(context: Context, appWidgetId: Int): DpSize =
    widgetOptionsOrNull(context, appWidgetId)
        ?.let { currentWidgetSizeDp(context, it, fallback = ANCHOR_WIDGET_PREVIEW_SIZE) }
        ?: ANCHOR_WIDGET_PREVIEW_SIZE

// The launcher's portrait target-cell height (OPTION_APPWIDGET_MAX_HEIGHT) in dp, used as
// the device baseline. It is the same value regardless of the current orientation, so
// feeding it into baseline capture removes the portrait/landscape ordering hazard that let
// the short landscape pass lock the baseline first. Returns null until options report it.
internal fun portraitBaselineHeightDp(options: Bundle): Float? =
    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat()

// ── Group-aware row collapsing ────────────────────────────────────────────────

// Aggregates several per-medication rows that belong to the same group + scheduled slot
// into a single representative row. The output has medicationUuid = null so the widget's
// quick-log action targets the entire group, and trailingText is sourced from a member
// that still needs attention (so half-fulfilled groups don't display the addressed
// member's empty trailing text).
internal fun collapseToGroupRow(rows: List<WidgetDoseRow>): WidgetDoseRow {
    require(rows.isNotEmpty()) { "collapseToGroupRow requires at least one row." }
    val representativeStatus = when {
        rows.all { it.status == WidgetDoseStatus.DONE } -> WidgetDoseStatus.DONE
        rows.any { it.status == WidgetDoseStatus.OVERDUE } -> WidgetDoseStatus.OVERDUE
        rows.any { it.status == WidgetDoseStatus.DUE_SOON } -> WidgetDoseStatus.DUE_SOON
        rows.any { it.status == WidgetDoseStatus.UPCOMING } -> WidgetDoseStatus.UPCOMING
        else -> WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
    }
    val identitySource = rows.firstOrNull { it.groupUuid != null } ?: rows.first()
    val trailingText = when (representativeStatus) {
        WidgetDoseStatus.DONE, WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> null
        else -> rows.firstOrNull { it.status == representativeStatus }?.trailingText
            ?: rows.firstOrNull { it.trailingText != null }?.trailingText
    }
    return rows.first().copy(
        status = representativeStatus,
        trailingText = trailingText,
        groupUuid = identitySource.groupUuid,
        scheduleTimeUuid = identitySource.scheduleTimeUuid,
        medicationUuid = null,
    )
}

private sealed interface WidgetRowGroupKey {
    data class GroupSlot(val groupUuid: String, val scheduledAt: LocalDateTime) : WidgetRowGroupKey
    data class SingleRow(val index: Int) : WidgetRowGroupKey
}

internal fun groupRowsByScheduledGroupSlot(rows: List<WidgetDoseRow>): List<List<WidgetDoseRow>> =
    rows.withIndex()
        .groupBy { (index, row) ->
            row.groupUuid?.let { WidgetRowGroupKey.GroupSlot(it, row.scheduledAt) }
                ?: WidgetRowGroupKey.SingleRow(index)
        }
        .values
        .map { indexedRows -> indexedRows.map { it.value } }

// Picks the slot group the medium widget's bottom card shows, from groups sorted by
// scheduledAt. Precedence: the earliest slot actionable right now (due soon), else the
// most recently missed slot — kept visible and tappable for an out-of-window log until
// the next slot's window opens — else the earliest upcoming slot. DUE_SOON/OVERDUE/
// UPCOMING all imply the slot is unaddressed (fulfilled slots read DONE or
// LOGGED_OUT_OF_WINDOW), so status alone decides.
internal fun selectActiveScheduledGroup(
    groups: List<List<WidgetDoseRow>>,
): List<WidgetDoseRow>? =
    groups.firstOrNull { rows -> rows.any { it.status == WidgetDoseStatus.DUE_SOON } }
        ?: groups.lastOrNull { rows -> rows.any { it.status == WidgetDoseStatus.OVERDUE } }
        ?: groups.firstOrNull { rows -> rows.any { it.status == WidgetDoseStatus.UPCOMING } }

// The medium widget's bottom-card pipeline: group scheduled rows by (groupUuid,
// scheduledAt) so the single action button logs the entire group — groupName is not
// unique across groups, so only groupUuid collapses true siblings — then pick by the
// selectActiveScheduledGroup precedence. Manual records never drive the card; they
// surface in the top count instead. Last-night carry-overs ARE eligible, so an
// unaddressed evening dose survives midnight until the snapshot drops it at 06:00,
// consistent with the large widget's last-night section.
internal fun selectMediumActiveScheduledGroup(
    doseRows: List<WidgetDoseRow>,
): List<WidgetDoseRow>? = selectActiveScheduledGroup(
    groupRowsByScheduledGroupSlot(doseRows.filterNot { it.isManualRecord })
        .sortedBy { it.first().scheduledAt }
)

// ── State definition ──────────────────────────────────────────────────────────

internal object HrtWidgetStateDefinition : GlanceStateDefinition<WidgetSnapshotState> {
    override suspend fun getDataStore(
        context: Context,
        fileKey: String
    ): DataStore<WidgetSnapshotState> =
        context.widgetSnapshotDataStore

    override fun getLocation(context: Context, fileKey: String): File =
        File(context.filesDir, "datastore/widget_snapshot.pb")
}

// ── Receivers ─────────────────────────────────────────────────────────────────

class HrtWidgetMediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidgetMedium()

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        scheduleDoseWidgetResizeUpdate(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        cleanupAppearance(context, appWidgetIds)
    }
}

class HrtWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidgetLarge()

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        scheduleDoseWidgetResizeUpdate(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        cleanupAppearance(context, appWidgetIds)
    }
}

// Launchers remain free to snap the outer frame to their own grid, but every options-change
// event must be rendered at the exact reported dp size. Without this synchronous repaint,
// some OEM launchers stretch the last RemoteViews throughout a resize gesture and only
// Glance's background session is notified; that session can remain idle until the app next
// draws a frame. Reusing the normal push path keeps the header fit check, text wrapping,
// and row capacity responsive at each size the launcher actually exposes.
private fun scheduleDoseWidgetResizeUpdate(context: Context) {
    val applicationContext = context.applicationContext
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        .appScope()
        .launch {
            runCatching { updateAllHrtWidgets(applicationContext) }
                .onFailure { if (it is CancellationException) throw it }
        }
}

// Best-effort per-instance appearance cleanup (the default entry always survives).
// Launched on the app scope WITHOUT goAsync: GlanceAppWidgetReceiver's contract
// forbids overrides calling goAsync (super already manages the async window, and
// on some OEMs deliberately avoids goAsync). If the process dies before the write
// lands, the orphaned override is invisible (its id never recurs) and costs a few
// bytes. Deliberately no onDisabled sweep: medium/large are separate receivers, so
// a per-provider clear-all would wipe the other provider's overrides.
private fun cleanupAppearance(context: Context, appWidgetIds: IntArray) {
    val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
    val repository = entryPoint.widgetAppearanceRepository()
    entryPoint.appScope().launch {
        runCatching { repository.deleteOverrides(appWidgetIds) }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
    }
}


// ── Per-device baseline scaling ───────────────────────────────────────────────

// Launcher cell sizes vary by device, so a 2-cell-tall widget renders at different
// dp heights on different launchers. We capture the first-render target-cell height
// per widget type and reuse it forever, so resize doesn't change the visual scale.
private const val MEDIUM_WIDGET_PREVIEW_WIDTH_DP = 306
private const val LARGE_WIDGET_PREVIEW_WIDTH_DP = 624
private const val WIDGET_PREVIEW_HEIGHT_DP = 276
// Internal: the anchor widget's picker preview applies the same content scale so all
// generated previews shrink consistently in the launcher's widget list.
internal const val WIDGET_PREVIEW_CONTENT_SCALE = 0.6f
private const val WIDGET_PREVIEW_E2_PG_PER_ML = 120.0
private val MEDIUM_WIDGET_PREVIEW_SIZE =
    DpSize(MEDIUM_WIDGET_PREVIEW_WIDTH_DP.dp, WIDGET_PREVIEW_HEIGHT_DP.dp)
private val LARGE_WIDGET_PREVIEW_SIZE =
    DpSize(LARGE_WIDGET_PREVIEW_WIDTH_DP.dp, WIDGET_PREVIEW_HEIGHT_DP.dp)
private const val WIDGET_BASELINE_PREFS = "hrt_widget_baseline"

// Key suffix is bumped (_v3) when the capture logic changes, so installs carrying a
// baseline persisted by the old buggy logic discard it and re-capture cleanly. _v2 keyed
// off the per-composition LocalSize, which let the short landscape pass lock the baseline
// before the portrait pass; _v3 captures the portrait cell height directly.
private const val WIDGET_BASELINE_KEY_MEDIUM = "medium_height_dp_v3"
private const val WIDGET_BASELINE_KEY_LARGE = "large_height_dp_v3"
internal const val WIDGET_BASELINE_KEY_ANCHOR = "anchor_height_dp_v3"

// Matches the preview viewport height: scale == 1.0 corresponds to the fully
// laid-out widget size seen in @GlancePreview / Live Preview. The anchor widget is
// one cell tall, so it resolves against its own reference (its preview height).
internal const val WIDGET_BASELINE_REFERENCE_DP = 276f

// Reject obviously bogus first-render sizes (e.g. transient 0dp loading frames)
// so we don't permanently lock the device baseline to nonsense.
private const val WIDGET_BASELINE_MIN_SANE_DP = 50f
private const val WIDGET_BASELINE_MAX_SANE_DP = 400f

// Floor for the device-baseline component (baseline / reference) so an unexpectedly
// small captured baseline can't collapse content to an illegible size. Sits below the
// normal placed-widget ratio (~0.4–0.58) so it never oversizes a real cell, while still
// catching pathologically tiny baselines. The user's own scale choice multiplies on top.
private const val WIDGET_MIN_BASELINE_SCALE_RATIO = 0.35f
internal val LocalPreviewBaselineHeight = compositionLocalOf<Float?> { null }

// The launcher's portrait target-cell height (dp), resolved from AppWidgetManager options
// and provided by HrtWidgetThemed. Unlike LocalSize it is the same value on every
// SizeMode.Exact pass, so baseline capture no longer depends on which orientation pass
// composes (and persists) first. Null until the launcher reports options.
private val LocalDeviceBaselineHeight = compositionLocalOf<Float?> { null }

// Pre-formatted E2 trend label shown in previews. Bypasses the real
// PkProjection path (whose windowing is unfriendly to fabricated data) so the
// preview can demonstrate the trend pill without seeding a full projection.
private val LocalPreviewE2Text = compositionLocalOf<String?> { null }

// True only when composing the config activity's live preview, whose RemoteViews are
// applied outside an AppWidget host — a host-free apply() cannot bind collection views
// (setRemoteAdapter / RemoteCollectionItems are AppWidget-only), so the large widget's
// dose list must render as a plain Column there. The home-screen render paths are
// unaffected (default false).
private val LocalHostFreePreview = compositionLocalOf { false }

@Composable
internal fun widgetScale(
    widgetKey: String,
    referenceDp: Float = WIDGET_BASELINE_REFERENCE_DP,
): Float {
    val previewBaselineDp = LocalPreviewBaselineHeight.current
    val baselineDp = previewBaselineDp ?: run {
        val context = LocalContext.current
        // Portrait target-cell height from the launcher options — identical across the
        // portrait/landscape Exact passes. 0f until options report it; in that gap it is out
        // of the sane range, so we don't capture (a fallback frame can't lock a wrong
        // baseline) and render at the reference scale until a real height arrives.
        val currentHeightDp = LocalDeviceBaselineHeight.current ?: 0f
        val prefs = context.getSharedPreferences(WIDGET_BASELINE_PREFS, Context.MODE_PRIVATE)
        val storedDp = prefs.getFloat(widgetKey, 0f)
        if (shouldPersistWidgetBaselineHeight(storedDp, currentHeightDp)) {
            SideEffect {
                val existingDp = prefs.getFloat(widgetKey, 0f)
                val mergedDp = mergeWidgetBaselineHeightDp(existingDp, currentHeightDp)
                if (mergedDp != existingDp) {
                    prefs.edit { putFloat(widgetKey, mergedDp) }
                }
            }
        }
        resolveWidgetBaselineHeightDp(storedDp, currentHeightDp, referenceDp)
    }
    return widgetBaselineScaleRatio(baselineDp, referenceDp) * LocalWidgetScale.current
}

// The device-baseline component of the widget scale, floored so an unexpectedly small
// captured baseline can't collapse content to an illegible size. The user's own scale
// choice (LocalWidgetScale) multiplies on top of this.
internal fun widgetBaselineScaleRatio(
    baselineDp: Float,
    referenceDp: Float = WIDGET_BASELINE_REFERENCE_DP,
): Float = (baselineDp / referenceDp).coerceAtLeast(WIDGET_MIN_BASELINE_SCALE_RATIO)

// Capture now feeds the portrait cell height (OPTION_APPWIDGET_MAX_HEIGHT) on every Exact
// pass, so the persists are already order-independent. Merging by max is kept as a cheap
// guard: if options ever report a height on one pass but not another, the real (taller)
// height still wins over a fallback.
internal fun mergeWidgetBaselineHeightDp(existingDp: Float, currentHeightDp: Float): Float =
    maxOf(existingDp, currentHeightDp)

// The device baseline is the launcher's portrait target-cell height, captured on the
// first update and reused forever. Once stored, later (resized) heights are ignored, so
// a resize relayouts the widget frame without rescaling its content.
internal fun resolveWidgetBaselineHeightDp(
    storedDp: Float,
    currentHeightDp: Float,
    referenceDp: Float = WIDGET_BASELINE_REFERENCE_DP,
): Float {
    if (storedDp > 0f) {
        return storedDp
    }
    if (currentHeightDp !in WIDGET_BASELINE_MIN_SANE_DP..WIDGET_BASELINE_MAX_SANE_DP) {
        return referenceDp
    }
    return currentHeightDp
}

// Register a baseline persist while none is stored yet — i.e. on every size composition
// of the first update. Their persists merge by max, so the tallest sane height wins. A
// bogus frame is skipped so a later sane size still gets captured.
internal fun shouldPersistWidgetBaselineHeight(
    storedDp: Float,
    currentHeightDp: Float,
): Boolean =
    storedDp <= 0f && currentHeightDp in WIDGET_BASELINE_MIN_SANE_DP..WIDGET_BASELINE_MAX_SANE_DP

// ── Medium widget (2×2) ───────────────────────────────────────────────────────

@Composable
private fun MediumWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetScale(WIDGET_BASELINE_KEY_MEDIUM)
    WidgetShell(
        scale = scale,
        contentAlignment = Alignment.Center,
    ) {
        if (isEmptySetup(snapshot)) {
            EmptyWidgetContent(
                iconSize = 22f,
                backgroundColor = colors.secondary,
                foregroundColor = colors.onSecondary
            )
            return@WidgetShell
        }
        val record = checkNotNull(snapshot)
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val e2Trend = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
        val e2DisplayUnit =
            BloodUnitKey.fromStorageValue(record.e2DisplayUnit) ?: BloodUnitKey.PG_ML
        val e2Text = LocalPreviewE2Text.current
            ?: e2Trend?.let {
                formatWidgetE2Text(
                    it.currentConcentration,
                    it.concentrationUnit,
                    e2DisplayUnit
                )
            }
        val doneCount = record.doneCount
        val totalCount = record.totalCount
        val manualCount = record.manualCount
        // A day whose only rows are manual records still reads as "nothing scheduled" for
        // the bottom panel — the manual activity surfaces in the top count instead, so the
        // bottom matches a plan-less day. Scheduled last-night carry-overs and tonight's
        // coming-up entries are not manual records, so they keep this false.
        val nothingScheduledToday = mediumNothingScheduledToday(record.doseRows)

        // Treat LOGGED_OUT_OF_WINDOW as addressed for activeRow/all-done: the slot has an
        // entry attached (even though it's outside the fulfillment window), so prompting
        // the user to log it again would just produce another out-of-window record.
        fun WidgetDoseRow.isAddressed(): Boolean =
            status == WidgetDoseStatus.DONE || status == WidgetDoseStatus.LOGGED_OUT_OF_WINDOW

        // Past the 1-hour grace period — logging from the widget would only create an
        // out-of-window record, so these lose priority to anything still in window but
        // stay visible (and tappable) while nothing else is actionable; see
        // selectActiveScheduledGroup for the precedence.
        fun WidgetDoseRow.isExpired(): Boolean = status == WidgetDoseStatus.OVERDUE

        val activeScheduledGroup: List<WidgetDoseRow>? =
            selectMediumActiveScheduledGroup(record.doseRows)
        val activeRow: WidgetDoseRow? = activeScheduledGroup?.let { collapseToGroupRow(it) }
            ?: record.doseRows.firstOrNull { it.contextChip == WidgetDoseChip.COMING_UP }
        val isMultiMedGroup = (activeScheduledGroup?.size ?: 1) > 1
        val todayRows = record.doseRows.filter {
            it.contextChip != WidgetDoseChip.LAST_NIGHT && it.contextChip != WidgetDoseChip.COMING_UP
        }
        // Nothing left to act on today (every slot is either addressed or expired).
        // The empty-schedule case is handled separately via nothingScheduledToday so
        // we don't conflate it with a perfect-adherence "all in window" finish.
        val noActionableRemaining = totalCount > 0 &&
                todayRows.none { !it.isAddressed() && !it.isExpired() }
        // Three final-state variants:
        //   allInWindow → every slot fulfilled within its window (perfect adherence).
        //   everythingLogged → every slot has a log attached, but at least one is
        //     out-of-window (took the dose, just timed imperfectly). No missed slots.
        //   otherwise → at least one OVERDUE slot with no log (missed).
        val allInWindow =
            noActionableRemaining && todayRows.all { it.status == WidgetDoseStatus.DONE }
        val everythingLogged = noActionableRemaining && todayRows.none { it.isExpired() }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // ── Top panel: progress ───────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetLabel(context.getString(R.string.widget_today))
                if (e2Text != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = e2Text,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (16f * scale).sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            val count = widgetMediumCount(
                doneCount = doneCount,
                totalCount = totalCount,
                manualCount = manualCount,
                doneLabel = context.getString(R.string.main_today_summary_done_label),
                manualLabel = context.getString(R.string.main_today_summary_manual_label),
            )
            if (count != null) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                        .padding(top = (-6 * scale).dp, bottom = (-4 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = count.hero,
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = (42f * scale).sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(GlanceModifier.width(2.dp))
                        Text(
                            text = count.suffix,
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontSize = (18f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                    // The ring tracks plan adherence; with no plan there's nothing to
                    // track, so hide it rather than draw a misleading empty track.
                    if (totalCount > 0) {
                        Spacer(GlanceModifier.defaultWeight())
                        ProgressRing(doneCount = doneCount, totalCount = totalCount)
                    }
                }
            }

            // ── Bottom panel: next dose ───────────────────────────────────────
            if ((noActionableRemaining || nothingScheduledToday) && activeRow == null) {
                val useCelebrationColor = allInWindow || nothingScheduledToday
                val badgeBackground = if (useCelebrationColor) colors.primary else colors.secondary
                val badgeForeground =
                    if (useCelebrationColor) colors.onPrimary else colors.onSecondary
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundedBackgroundBox(
                            modifier = GlanceModifier.size((30f * scale).dp),
                            color = badgeBackground,
                            shape = WidgetRoundedShape.Pill,
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(
                                    mediumBadgeIconRes(
                                        allInWindow = allInWindow,
                                        nothingScheduledToday = nothingScheduledToday,
                                        everythingLogged = everythingLogged,
                                    )
                                ),
                                contentDescription = null,
                                modifier = GlanceModifier.size((22f * scale).dp),
                                colorFilter = ColorFilter.tint(badgeForeground),
                            )
                        }
                        Spacer(GlanceModifier.height((6f * scale).dp))
                        Text(
                            text = context.getString(
                                when {
                                    nothingScheduledToday -> R.string.widget_no_doses_today
                                    allInWindow -> R.string.widget_all_done
                                    everythingLogged -> R.string.widget_all_logged
                                    else -> R.string.widget_nothing_more_today
                                }
                            ),
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = (18f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            } else if (activeRow != null) {
                val displayName = when {
                    record.hideMedicationDetails && activeRow.isManualRecord ->
                        context.getString(R.string.widget_manual_record)

                    isMultiMedGroup || record.hideMedicationDetails -> activeRow.groupName
                    else -> activeRow.medicationName
                }
                // Preserve the underlying medicationUuid only for navigation, only when we're
                // actually showing a single specific medication. activeRow itself keeps
                // medicationUuid = null so taps on the action button log the whole group.
                val highlightIntentRow = if (
                    !isMultiMedGroup &&
                    !record.hideMedicationDetails &&
                    !activeRow.isManualRecord
                ) {
                    activeRow.copy(medicationUuid = activeScheduledGroup?.firstOrNull()?.medicationUuid)
                } else {
                    activeRow
                }
                val highlightIntent = widgetRowHighlightIntent(context, highlightIntentRow)
                val cardClickModifier = if (highlightIntent != null) {
                    GlanceModifier.clickable(
                        onClick = actionStartActivityFromIntent(highlightIntent),
                        rippleOverride = WidgetRoundedShape.Card.rippleRes,
                    )
                } else {
                    GlanceModifier
                }
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    SectionHeader(
                        text = context.getString(
                            if (activeRow.status == WidgetDoseStatus.OVERDUE) {
                                R.string.plan_schedule_entry_past_due
                            } else {
                                R.string.widget_upcoming
                            }
                        ),
                        topPadding = 0.dp
                    )
                    Spacer(modifier = GlanceModifier.height((4 * scale).dp))
                    RoundedBackgroundRow(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height((64f * scale).dp),
                        color = colors.widgetContainer,
                        shape = WidgetRoundedShape.Card,
                        contentModifier = GlanceModifier
                            .padding(horizontal = (16f * scale).dp)
                            .then(cardClickModifier),
                    ) {
                        RoundedBackgroundBox(
                            modifier = GlanceModifier
                                .width((6f * scale).dp)
                                .height((44f * scale).dp),
                            color = groupAccentColor(
                                activeRow.colorKey,
                                LocalWidgetForcedDark.current
                            ),
                            shape = WidgetRoundedShape.Pill,
                        ) {}
                        Spacer(GlanceModifier.width((10f * scale).dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            val supporting = listOfNotNull(
                                activeRow.routeLabel.takeIf(String::isNotBlank),
                                activeRow.doseText.takeIf(String::isNotBlank),
                            ).joinToString(" · ")
                            val showSupporting = !record.hideMedicationDetails &&
                                    !isMultiMedGroup &&
                                    supporting.isNotBlank()
                            Text(
                                text = displayName,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = (18f * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            // Keep this node even when hidden. The synchronous GlanceRemoteViews
                            // path can reuse layout IDs across updates, so privacy toggles must
                            // not change the RemoteViews tree.
                            Text(
                                text = supporting.takeIf { showSupporting }.orEmpty(),
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = ((if (showSupporting) 14f else 1f) * scale).sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (activeRow.isFromArchivedGroup) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_archive),
                                contentDescription = context.getString(R.string.archived_group_record_indicator),
                                modifier = GlanceModifier.size((22f * scale).dp),
                                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                            )
                            Spacer(GlanceModifier.width(8.dp))
                        }
                        val showTrailingText = activeRow.trailingText != null &&
                                !(record.hideMedicationDetails && activeRow.isManualRecord)
                        if (showTrailingText) {
                            Text(
                                text = activeRow.trailingText,
                                style = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = (18f * scale).sp,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(8.dp))
                        }
                        val isActionable = activeRow.groupUuid != null &&
                                (activeRow.status == WidgetDoseStatus.DUE_SOON ||
                                        activeRow.status == WidgetDoseStatus.OVERDUE)
                        TrailingButton(
                            row = activeRow,
                            showLogAction = isActionable,
                            navigateIntent = highlightIntent,
                            buttonSizeDp = 44f,
                            iconSizeDp = 32f,
                            arrowIconSizeDp = 26f,
                        )
                    }
                }
            }
        }
    }
}

// ── Large widget (4×2 default, 3×2 minimum) ──────────────────────────────────

// Header row metrics. The font size is passed to WidgetLabel rather than left to its
// default so the measurement below and the rendered text can never drift apart.
private const val LargeHeaderFontSizeSp = 18f
private const val LargeHeaderRoomyGapDp = 48f
private const val LargeHeaderTightGapDp = 10f

// Does the header still fit once the roomy gap and the fixed-width E2 slot are taken out
// of the shell's inner width? Measured uppercased and bold to mirror WidgetLabel, with
// Paint's default sans-serif standing in for Glance's default text (as anchorDirectionLineFits does).
internal fun largeHeaderFitsRoomyGap(
    headerText: String,
    e2PlaceholderText: String,
    headerFontSizePx: Float,
    e2FontSizePx: Float,
    roomyGapPx: Float,
    availableWidthPx: Float,
): Boolean {
    val headerPaint = android.graphics.Paint().apply {
        textSize = headerFontSizePx
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD,
        )
    }
    val e2Paint = android.graphics.Paint().apply { textSize = e2FontSizePx }
    val needed = headerPaint.measureText(headerText.uppercase()) +
        roomyGapPx +
        e2Paint.measureText(e2PlaceholderText)
    return needed <= availableWidthPx
}

@Composable
private fun LargeWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetScale(WIDGET_BASELINE_KEY_LARGE)
    val size = LocalSize.current
    WidgetShell(
        scale = scale,
        contentAlignment = Alignment.Center,
    ) {
        if (isEmptySetup(snapshot)) {
            EmptyWidgetContent(
                iconSize = 22f,
                backgroundColor = colors.secondary,
                foregroundColor = colors.onSecondary
            )
            return@WidgetShell
        }
        val record = checkNotNull(snapshot)
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val e2Trend = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
        val e2DisplayUnit =
            BloodUnitKey.fromStorageValue(record.e2DisplayUnit) ?: BloodUnitKey.PG_ML
        val e2Text = LocalPreviewE2Text.current
            ?: e2Trend?.let {
                formatWidgetE2Text(
                    it.currentConcentration,
                    it.concentrationUnit,
                    e2DisplayUnit
                )
            }
        val doneCount = record.doneCount
        val totalCount = record.totalCount
        // Only treat the day as "nothing scheduled" when there are no rows at all to
        // surface — including last-night carry-overs and tonight's coming-up entries,
        // which aren't counted in totalCount but still represent real activity.
        val nothingScheduledToday = record.doseRows.isEmpty()
        // Match the medium widget's "addressed" definition so the badge appears whenever
        // every today slot is either DONE or LOGGED_OUT_OF_WINDOW (no actionable rows left).
//        fun WidgetDoseRow.isAddressed(): Boolean =
//            status == WidgetDoseStatus.DONE || status == WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
//        val allDone = totalCount > 0 &&
//            record.doseRows.none {
//                it.contextChip != WidgetDoseChip.LAST_NIGHT &&
//                    it.contextChip != WidgetDoseChip.COMING_UP &&
//                    !it.isAddressed()
//            }

        // When hideMedicationDetails, collapse same-(group, slot) rows into per-group rows so
        // the displayed name reflects group identity rather than a single medication.
        // Keyed on groupUuid (not groupName) since group names are not unique.
        val displayRows: List<WidgetDoseRow> = if (record.hideMedicationDetails) {
            val regularRows = record.doseRows.filter { it.contextChip != WidgetDoseChip.COMING_UP }
            val collapsed = regularRows
                .filter { !it.isManualRecord }
                .let(::groupRowsByScheduledGroupSlot)
                .map { rows ->
                    val count = rows.size
                    collapseToGroupRow(rows).copy(
                        medicationName = if (count > 1) {
                            "${rows.first().groupName} · $count"
                        } else {
                            rows.first().groupName
                        },
                    )
                }
            val manualRows = regularRows.filter { it.isManualRecord }
            val comingUpRows = record.doseRows
                .filter { it.contextChip == WidgetDoseChip.COMING_UP }
            (collapsed + manualRows).sortedBy { it.scheduledAt } + comingUpRows
        } else {
            record.doseRows
        }

        val lastNightRows = displayRows.filter { it.contextChip == WidgetDoseChip.LAST_NIGHT }
        val todayRows = displayRows.filter { it.contextChip == null }
        val comingUpRows = displayRows.filter { it.contextChip == WidgetDoseChip.COMING_UP }
        val listItems = buildList {
            if (lastNightRows.isNotEmpty()) {
                add(WidgetListItem.Header(context.getString(R.string.widget_last_night)))
                lastNightRows.forEach { add(WidgetListItem.Row(it)) }
            }
            if (todayRows.isNotEmpty()) {
                if (lastNightRows.isNotEmpty()) {
                    add(WidgetListItem.Header(context.getString(R.string.widget_today)))
                }
                todayRows.forEach { add(WidgetListItem.Row(it)) }
            }
            if (comingUpRows.isNotEmpty()) {
                add(WidgetListItem.Header(context.getString(R.string.widget_tonight)))
                comingUpRows.forEach { add(WidgetListItem.Row(it)) }
            }
        }

        val todayLabel = context.getString(R.string.widget_today)
        val countLabel = widgetLargeCountLabel(
            doneCount = doneCount,
            totalCount = totalCount,
            manualCount = record.manualCount,
            doneLabel = context.getString(R.string.main_today_summary_done_label),
            manualLabel = context.getString(R.string.main_today_summary_manual_label),
        )
        val headerText = if (countLabel != null) "$todayLabel · $countLabel" else todayLabel
        // A fit decision, not a width decision: how much room the header needs depends on
        // the counts and the locale, so a long label clips at widths where a short one is
        // still comfortable.
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.configuration.fontScale
        val e2GapDp = remember(headerText, e2DisplayUnit, size, scale, density, fontScale) {
            val fits = largeHeaderFitsRoomyGap(
                headerText = headerText,
                e2PlaceholderText = widgetE2PlaceholderText(e2DisplayUnit),
                headerFontSizePx = LargeHeaderFontSizeSp * scale * fontScale * density,
                e2FontSizePx = 16f * scale * fontScale * density,
                roomyGapPx = LargeHeaderRoomyGapDp * scale * density,
                availableWidthPx = (size.width - WidgetShellPadding * 2).value * density,
            )
            if (fits) LargeHeaderRoomyGapDp else LargeHeaderTightGapDp
        }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight().wrapContentHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetLabel(headerText, fontSize = LargeHeaderFontSizeSp.sp)
                    }
                    // The bar tracks plan adherence; hide it on plan-less days rather than
                    // drawing a misleading empty track.
                    if (totalCount > 0) {
                        Spacer(GlanceModifier.height((8 * scale).dp))
                        ProgressBar(doneCount = doneCount, totalCount = totalCount)
                    }
                }
                if (e2Text != null) {
                    Spacer(GlanceModifier.width((e2GapDp * scale).dp))
                    // Reserve the width of the widest (4-digit) E2 label via an invisible
                    // placeholder, then draw the live value over it, end-aligned. The bar shares
                    // this row through the weighted column, so pinning the E2 slot to a constant
                    // width keeps the bar from jumping as the live value shrinks/grows. The slot
                    // still scales with `scale` like everything else, so the existing scaling holds.
                    Box(contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = widgetE2PlaceholderText(e2DisplayUnit),
                            style = TextStyle(
                                color = fixedColorProvider(Color.Transparent),
                                fontSize = (16f * scale).sp,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = e2Text,
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontSize = (16f * scale).sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }

            val largeWidgetProgressBarBottomPadding =
                if (listItems.firstOrNull() is WidgetListItem.Header) 8 else 12
            Spacer(GlanceModifier.height((largeWidgetProgressBarBottomPadding * scale).dp))

            if (nothingScheduledToday) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundedBackgroundBox(
                            modifier = GlanceModifier.size((30f * scale).dp),
                            color = colors.primary,
                            shape = WidgetRoundedShape.Pill,
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = GlanceModifier.size((22f * scale).dp),
                                colorFilter = ColorFilter.tint(colors.onPrimary),
                            )
                        }
                        Spacer(GlanceModifier.height((6f * scale).dp))
                        Text(
                            text = context.getString(R.string.widget_no_doses_today),
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = (18f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            } else {
                // One row renderer reused by both list paths so they stay identical.
                val listItem: @Composable (index: Int, item: WidgetListItem) -> Unit =
                    { index, item ->
                        Column(
                            modifier = GlanceModifier.fillMaxWidth()
                                .padding(top = if (index > 0) 2.dp else 0.dp)
                        ) {
                            when (item) {
                                is WidgetListItem.Header -> SectionHeader(
                                    item.text,
                                    topPadding = if (index == 0) 0.dp else 4.dp
                                )

                                is WidgetListItem.Row -> DoseRow(
                                    row = item.row,
                                    showLogAction = item.row.status == WidgetDoseStatus.DUE_SOON ||
                                            item.row.status == WidgetDoseStatus.OVERDUE,
                                    hideMedicationDetails = record.hideMedicationDetails,
                                    highlightIntent = widgetRowHighlightIntent(context, item.row),
                                )
                            }
                        }
                    }
                if (LocalHostFreePreview.current) {
                    // Host-free apply() (config live preview) cannot bind a collection-backed
                    // LazyColumn, so render the same rows in a plain Column. The preview
                    // viewport is fixed-height, so any overflow simply clips — fine for an
                    // inert preview.
                    Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        listItems.forEachIndexed { index, item -> listItem(index, item) }
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        itemsIndexed(
                            items = listItems,
                            itemId = { index, _ -> (index + 1).toLong() },
                        ) { index, item -> listItem(index, item) }
                    }
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private fun previewSnapshot(context: Context): WidgetSnapshotRecord {
    val morningDoseTime = LocalDateTime.of(2026, 1, 1, 9, 0)
    val eveningDoseTime = LocalDateTime.of(2026, 1, 1, 21, 0)
    val timeFormatter = localizedShortTimeFormatter(
        Locale.getDefault(),
        uses24HourFormat = android.text.format.DateFormat.is24HourFormat(context),
    )
    val estradiolName = context.getString(R.string.medication_name_estradiol)
    val progesteroneName = context.getString(R.string.settings_calibration_analyte_prog)
    val cpaName = context.getString(R.string.medication_name_cyproterone_acetate)
    val oralLabel = context.getString(R.string.medication_application_oral)
    return WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = "UTC",
        anchorDateEpochDay = morningDoseTime.toLocalDate().toEpochDay(),
        doneCount = 1,
        totalCount = 3,
        manualCount = 0,
        hasActiveGroups = true,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        e2DisplayUnit = BloodUnitKey.PG_ML.storageValue,
        appLanguageTag = context.currentAppLocale().toLanguageTag(),
        doseRows = listOf(
            WidgetDoseRow(
                medicationName = cpaName,
                groupName = cpaName,
                colorKey = MedicationGroupColorKey.TEAL,
                routeLabel = oralLabel,
                doseText = context.getString(R.string.widget_preview_dose_text_cpa),
                status = WidgetDoseStatus.DONE,
                scheduledAt = morningDoseTime,
                trailingText = null,
                isManualRecord = false,
                contextChip = null,
                groupUuid = "00000000-0000-0000-0000-000000000101",
                scheduleTimeUuid = "00000000-0000-0000-0000-000000000201",
                medicationUuid = "00000000-0000-0000-0000-000000000301",
            ),
            WidgetDoseRow(
                medicationName = context.getString(R.string.medication_name_estradiol_valerate),
                groupName = estradiolName,
                colorKey = MedicationGroupColorKey.ROSE,
                routeLabel = oralLabel,
                doseText = "2 mg",
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = morningDoseTime,
                trailingText = morningDoseTime.format(timeFormatter),
                isManualRecord = false,
                contextChip = null,
                groupUuid = "00000000-0000-0000-0000-000000000102",
                scheduleTimeUuid = "00000000-0000-0000-0000-000000000202",
                medicationUuid = "00000000-0000-0000-0000-000000000302",
            ),
            WidgetDoseRow(
                medicationName = progesteroneName,
                groupName = progesteroneName,
                colorKey = MedicationGroupColorKey.INDIGO,
                routeLabel = oralLabel,
                doseText = "200 mg",
                status = WidgetDoseStatus.UPCOMING,
                scheduledAt = eveningDoseTime,
                trailingText = eveningDoseTime.format(timeFormatter),
                isManualRecord = false,
                contextChip = null,
                groupUuid = "00000000-0000-0000-0000-000000000103",
                scheduleTimeUuid = "00000000-0000-0000-0000-000000000203",
                medicationUuid = "00000000-0000-0000-0000-000000000303",
            ),
        ),
        pkProjection = null,
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@GlancePreview(widthDp = MEDIUM_WIDGET_PREVIEW_WIDTH_DP, heightDp = WIDGET_PREVIEW_HEIGHT_DP)
@Composable
private fun MediumWidgetPreview() {
    HrtPreviewContent { snapshot -> MediumWidgetContent(snapshot) }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@GlancePreview(widthDp = LARGE_WIDGET_PREVIEW_WIDTH_DP, heightDp = WIDGET_PREVIEW_HEIGHT_DP)
@Composable
private fun LargeWidgetPreview() {
    HrtPreviewContent { snapshot -> LargeWidgetContent(snapshot) }
}
