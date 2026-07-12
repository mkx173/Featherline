package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.journal.dayCount
import com.mkx.hrttracker.ui.journal.anchorIconRes
import com.mkx.hrttracker.util.currentAppLocale
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.glance.appwidget.updateAll as glanceUpdateAll

// 2×1 anchor widget. Per-instance state is just the anchorId (Task 4); name/icon/palette/
// date are read live from the journal at composition. Chrome (colour/scale/alpha/dark)
// comes from the shared widget appearance via HrtWidgetThemed.
internal val ANCHOR_WIDGET_PREVIEW_SIZE = DpSize(306.dp, 138.dp)

// Ceiling for the blocking tracked-dates load in provideGlance (and the anchor config
// window's seed): long enough for a cold SQLCipher open + first query in a widget-only
// process, short enough that a failed open falls back to the snapshot well within
// Glance's render budget instead of hanging.
// ponytail: fixed knob; revisit if slow devices fall back to the snapshot on cold render.
internal const val ANCHOR_WIDGET_AWAIT_TIMEOUT_MS = 5_000L

// The anchor is one cell tall, so scale == 1.0 resolves against its own preview viewport
// height rather than the dose widgets' 276dp reference.
private val ANCHOR_WIDGET_BASELINE_REFERENCE_DP = ANCHOR_WIDGET_PREVIEW_SIZE.height.value

// Fixed sample data for the launcher widget-picker previews: "HRT", started Jan 1 2026,
// 67 days in, default icon, no palette and no gradient. The API 31-34 static
// previewLayout (layout/hrt_widget_anchor_preview.xml) and the anchor_widget_preview_*
// strings mirror these values — keep them in sync.
internal val ANCHOR_PREVIEW_START_DATE: LocalDate = LocalDate.of(2026, 1, 1)
internal const val ANCHOR_PREVIEW_DAYS_PASSED = 67L

internal fun anchorPreviewAnchor(context: Context): TrackedDate = TrackedDate(
    id = "anchor-widget-preview",
    name = context.getString(R.string.anchor_widget_preview_name),
    icon = AnchorIcon.EVENT,
    date = ANCHOR_PREVIEW_START_DATE,
    palette = null,
    pinnedOrder = null,
)

private data class AnchorWidgetSettings(
    val adaptiveColorEnabled: Boolean,
    val appLanguageTag: String?,
)

internal sealed interface AnchorWidgetDisplayText {
    data class Empty(val message: String) : AnchorWidgetDisplayText
    data class Loaded(
        val anchor: TrackedDate,
        val directionLine: String,
        val daysText: String,
    ) : AnchorWidgetDisplayText
}

class HrtAnchorWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(ANCHOR_WIDGET_PREVIEW_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId =
            runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val entry = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
        val appearanceRepository = entry.widgetAppearanceRepository()
        val journalRepository = entry.journalRepository()
        val appTimeSource = entry.appTimeSource()
        val initialAppearance = runCatching {
            appearanceRepository.currentEffective(appWidgetId)
        }.getOrDefault(WidgetAppearance.Default)
        // Seed widget-facing settings the same way WidgetSnapshotBuilder bakes them for the
        // dose widgets. They are collected reactively inside provideContent below; display
        // strings are then baked from the localized LocalContext into AnchorWidgetDisplayText.
        val settingsRepository = entry.settingsRepository()
        val settings = runCatching { settingsRepository.getCurrentSettings() }.getOrNull()
        val initialWidgetSettings = AnchorWidgetSettings(
            adaptiveColorEnabled = settings?.adaptiveColorEnabled ?: true,
            appLanguageTag = settings?.appLanguageOption?.languageTag,
        )
        // Await real journal rows before composing: the cache/raw-flow cold-start window
        // reads as an empty journal, which rendered "Date removed — tap to choose" on a
        // widget whose anchor is fine. Bounded with a snapshot fallback: a widget-only
        // process is the only opener here (observeLoadedTrackedDates never emits on its own),
        // so on a failed/slow open we fall back to the last-known snapshot rather than
        // blanking a configured widget to the empty "choose a date" state, and never hang the
        // render forever. Empty only as a final resort (a genuine fresh install has no snapshot).
        val initialAnchors =
            journalRepository.awaitTrackedDatesOrSnapshot(ANCHOR_WIDGET_AWAIT_TIMEOUT_MS)

        provideContent {
            val anchorId = currentState(ANCHOR_ID_KEY)
            val appearanceFlow = remember(appWidgetId) {
                appearanceRepository.effectiveFor(appWidgetId)
            }
            val appearance by appearanceFlow.collectAsState(initial = initialAppearance)
            val widgetSettings by remember(settingsRepository) {
                settingsRepository.settingsState
                    .map { settings ->
                        AnchorWidgetSettings(
                            adaptiveColorEnabled = settings.adaptiveColorEnabled,
                            appLanguageTag = settings.appLanguageOption.languageTag,
                        )
                    }
                    .distinctUntilChanged()
            }.collectAsState(initial = initialWidgetSettings)
            val anchors by remember(journalRepository) { journalRepository.observeLoadedTrackedDates() }
                .collectAsState(initial = initialAnchors)
            // The render date as OBSERVABLE state, not an ambient LocalDate.now() read: a
            // session update() with no changed composition input skips recomposition
            // entirely, so a live session would keep yesterday's day count across a
            // date/time change until it died. Collecting the minute ticker (date-projected)
            // makes the flip a real state change; the ticker only runs while a session is
            // alive (WhileSubscribed), and refresh() is already fired on time/timezone
            // broadcasts, so this also rolls a foreground session over midnight for free.
            val today by remember(appTimeSource) {
                appTimeSource.currentMinute
                    .map { minute -> minute.toLocalDate() }
                    .distinctUntilChanged()
            }.collectAsState(initial = LocalDate.now())
            val anchor = anchorId?.let { id -> anchors.firstOrNull { it.id == id } }
            val backgroundFlag = currentState(BACKGROUND_FLAG_KEY)
                ?.let { name -> PrideFlag.entries.firstOrNull { it.name == name } }
            // Same derivation as the dose widgets' session path: the launcher's portrait
            // cell height drives the device-baseline component of the content scale.
            val deviceBaselineHeightDp = appWidgetId?.let { widgetId ->
                runCatching {
                    AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                }.getOrNull()?.let { options -> portraitBaselineHeightDp(options) }
            }
            HrtWidgetThemed(
                context,
                snapshot = null,
                appearance = appearance,
                deviceBaselineHeightDp = deviceBaselineHeightDp,
                adaptiveColorEnabled = widgetSettings.adaptiveColorEnabled,
                appLanguageTag = widgetSettings.appLanguageTag,
            ) {
                AnchorWidgetContent(
                    displayText = buildAnchorWidgetDisplayText(
                        LocalContext.current, anchor, today,
                    ),
                    appWidgetId = appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID,
                    backgroundFlag = backgroundFlag,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            // Sample-data preview for the launcher widget picker (API 35+ generated
            // previews, published by HomeWidgetManager): the fixed "HRT / 67 days"
            // anchor rendered through the real HrtWidgetThemed + AnchorWidgetContent
            // path with the default appearance, no gradient. Adaptive colour is off so
            // the preview pins the DefaultSeedColor scheme, matching the dose widget
            // previews (provideHrtPreviewContent) and the API 31–34 static XML previews,
            // and keeping the launcher-cached preview stable across wallpaper changes.
            // Renders in the system locale (appLanguageTag = null): the launcher draws
            // its picker chrome in the system locale, so a system-locale preview is the
            // consistent choice. The preview composes at the fixed reference size, so
            // pin the baseline to it rather than letting a captured device baseline
            // leak in.
            CompositionLocalProvider(
                LocalPreviewBaselineHeight provides ANCHOR_WIDGET_BASELINE_REFERENCE_DP,
            ) {
                HrtWidgetThemed(
                    context,
                    snapshot = null,
                    appearance = WidgetAppearance.Default,
                    adaptiveColorEnabled = false,
                    appLanguageTag = null,
                ) {
                    // Same preview content scale as the dose widgets
                    // (provideHrtPreviewContent), pinned inside HrtWidgetThemed so it
                    // overrides the appearance-derived LocalWidgetScale (1.0).
                    CompositionLocalProvider(
                        LocalWidgetScale provides WIDGET_PREVIEW_CONTENT_SCALE,
                    ) {
                        AnchorWidgetContent(
                            displayText = buildAnchorWidgetDisplayText(
                                context = LocalContext.current,
                                anchor = anchorPreviewAnchor(LocalContext.current),
                                today = ANCHOR_PREVIEW_START_DATE
                                    .plusDays(ANCHOR_PREVIEW_DAYS_PASSED),
                            ),
                            watermarkScale = WIDGET_PREVIEW_CONTENT_SCALE,
                        )
                    }
                }
            }
        }
    }
}

class HrtAnchorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtAnchorWidget()

    // A resize must repaint at the new size: the launcher rescales the LAST RemoteViews to
    // the new cells (stretching the baked watermark/bloom bitmaps, which draw FillBounds),
    // and the bare Glance session update super triggers stalls while the process is
    // backgrounded — exactly where a home-screen resize runs (observed on One UI: eight
    // optionsChanged events, zero recompositions). The synchronous push composes at the
    // live options size and paints immediately (same contract as the date receiver and the
    // config save). This also repaints a FRESH placement at its real size: One UI reports
    // the first non-zero options only after the config activity finishes, so the save-time
    // push composed at the reference fallback size. Launched on the app scope WITHOUT
    // goAsync (GlanceAppWidgetReceiver forbids overrides calling goAsync — see
    // cleanupAppearance in HrtWidget.kt).
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val applicationContext = context.applicationContext
        EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
            .appScope()
            .launch {
                // composeAnchorRemoteViews re-reads live options at compose time, so even
                // a push racing a newer resize event paints the current size.
                runCatching { updateAllAnchorWidgets(applicationContext) }
                    .onFailure { if (it is CancellationException) throw it }
            }
    }
}

// Synchronous push + session reconcile, mirroring pushHrtWidgets (see the push note in
// HrtWidget.kt). A bare glanceUpdateAll only signals the Glance session, which (a) stalls
// while the process is backgrounded — exactly where the midnight/date-change receiver runs —
// and (b) skips recomposition entirely when no composition input changed, which is every
// date tick (LocalDate.now() is not observable state): the widget kept yesterday's day count
// until the app was reopened. Composing the RemoteViews here and pushing via updateAppWidget
// paints immediately from the background and re-evaluates the day count fresh. Plain views
// only (no LazyColumn), so the push is safe on every API level, like the medium dose widget.
// The trailing glanceUpdateAll reconciles the session — a fresh session re-composes today's
// content — so a launcher re-attach can't re-assert a stale composition.
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
suspend fun updateAllAnchorWidgets(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val ids = appWidgetManager.getAppWidgetIds(
        android.content.ComponentName(context, HrtAnchorWidgetReceiver::class.java)
    )
    // No instances: skip everything, including the tracked-dates await, so a widget-less
    // install never pays a DB open on the midnight/boot broadcasts (deferred-open policy).
    if (ids.isEmpty()) return
    val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
    val appearanceRepository = entry.widgetAppearanceRepository()
    val diagnosticsLogger = entry.diagnosticsLogger()
    // Same bounded await + snapshot fallback contract as provideGlance: never render a
    // configured widget as empty on a slow/failed open, never hang the broadcast window.
    val anchors = entry.journalRepository()
        .awaitTrackedDatesOrSnapshot(ANCHOR_WIDGET_AWAIT_TIMEOUT_MS)
    val glanceManager = GlanceAppWidgetManager(context)
    for (appWidgetId in ids) {
        // Per-instance guard: one broken instance must not stop the others repainting.
        runCatching {
            val prefs = androidx.glance.appwidget.state.getAppWidgetState(
                context,
                androidx.glance.state.PreferencesGlanceStateDefinition,
                glanceManager.getGlanceIdBy(appWidgetId),
            )
            val anchor = prefs.anchorId()?.let { id -> anchors.firstOrNull { it.id == id } }
            val appearance = runCatching {
                appearanceRepository.currentEffective(appWidgetId)
            }.getOrDefault(WidgetAppearance.Default)
            val render = composeAnchorRemoteViews(
                context = context,
                appearance = appearance,
                anchor = anchor,
                backgroundFlag = prefs.backgroundFlag(),
                appWidgetId = appWidgetId,
            )
            appWidgetManager.updateAppWidget(appWidgetId, render.remoteViews)
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            diagnosticsLogger.warning(
                "AnchorWidget",
                "anchor_widget_push_failed appWidgetId=$appWidgetId",
                throwable,
            )
        }
    }
    // Reconcile the sessions with what the push painted (see note above).
    HrtAnchorWidget().glanceUpdateAll(context)
}

// Hero stack: name + since/planned-for top-left, day count bottom-right, with the glyph as
// a baked watermark backdrop. A null anchor (never chosen, or since deleted) shows one
// select-a-date state that taps to the config Activity. Always shows full days, even past
// a year.
@Composable
internal fun AnchorWidgetContent(
    displayText: AnchorWidgetDisplayText,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    backgroundFlag: PrideFlag? = null,
    // Watermark shrink for the generated picker preview (WIDGET_PREVIEW_CONTENT_SCALE):
    // previews are a ~0.6× miniature of the reference card, and the glyph must shrink
    // with the text (the old XML preview's 72dp = 0.88 × 138dp × 0.6). Live renders and
    // the WYSIWYG config preview keep 1.0 — the backdrop is card-relative there and must
    // NOT follow the user's content-scale slider, so this is a parameter rather than a
    // LocalWidgetScale read.
    watermarkScale: Float = 1f,
) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetScale(WIDGET_BASELINE_KEY_ANCHOR, ANCHOR_WIDGET_BASELINE_REFERENCE_DP)

    if (displayText is AnchorWidgetDisplayText.Empty) {
        // No selection yet, or the selected anchor was deleted: tap opens the config
        // Activity for this instance so the user can (re)choose.
        WidgetShell(
            scale = scale,
            contentAlignment = Alignment.Center,
            onClick = actionStartActivity(anchorReconfigureIntent(context, appWidgetId)),
        ) {
            Text(
                text = displayText.message,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = (16f * scale).sp),
            )
        }
        return
    }

    val loaded = displayText as AnchorWidgetDisplayText.Loaded
    val anchor = loaded.anchor
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density

    // A gradient flag (chosen in the widget config) layers a baked bloom bitmap plus a
    // shell-tinted scrim over the appearance-seeded card, so the colour sliders tint the
    // background under the wash. Everything but the blooms is a day/night colour provider
    // that flips with the system at apply time.
    val forcedDark = LocalWidgetForcedDark.current
    // forcedDark is null when the widget follows the system; resolve a concrete dark flag for
    // the bloom bake (light/dark bloom tuning) from the widget process' night-mode
    // configuration. Only the blooms depend on it — the provider layers carry both modes.
    val isDark = forcedDark ?: (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    val backgroundAlpha = LocalWidgetAlpha.current
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)
    val blooms = remember(backgroundFlag, isDark, backgroundAlpha, widthPx, heightPx) {
        backgroundFlag?.let { flag ->
            renderAnchorBloomsBitmap(
                widthPx, heightPx, WidgetRoundedShape.Shell.radius.value * density,
                flagBloomColors(flag, isDark, backgroundAlpha),
            )
        }
    }

    // Hero stack (spec section 2): name + since-line top-left, day count bottom-right; the
    // glyph is the backdrop watermark, no longer an inline row item. The count is a
    // bottom-end-aligned overlay, NOT the column's last child: pinned to the bottom edge it
    // grows UPWARD when the appearance scale inflates it, instead of clipping below the card.
    val cardBody: @Composable () -> Unit = {
        Box(modifier = GlanceModifier.fillMaxSize()) {
            Column {
                Text(
                    text = anchor.name,
                    style = TextStyle(color = colors.onSurface, fontSize = (18f * scale).sp,
                        fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = loaded.directionLine,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = (16f * scale).sp),
                    maxLines = 1,
                )
            }
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = loaded.daysText,
                    style = TextStyle(color = colors.onSurface, fontSize = (32f * scale).sp,
                        fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }

    // Glyph watermark bled off the top-right corner, baked to a bitmap (Glance has no
    // alpha/offset modifiers) and layered under the padded content. The bleed is
    // pre-cropped into the bitmap and LAYOUT owns placement and size (top-end box,
    // card-height-relative dp), so a host re-applying the RemoteViews at a size other
    // than the composed one (picker preview box, resize window) can drift the glyph's
    // scale a little but can never deform it — the old full-card FillBounds bake
    // stretched non-uniformly there. The bitmap is white ink; the colour comes from the
    // ColorFilter below so the launcher resolves day/night at RemoteViews apply time and
    // the widget flips with the system without a recompose.
    val watermarkSizeDp = size.height.value * GLYPH_VISIBLE_HEIGHT_FRACTION * watermarkScale
    val watermarkSizePx = (watermarkSizeDp * density).toInt().coerceAtLeast(1)
    val watermark = remember(anchor.icon, watermarkSizePx) {
        renderAnchorWatermarkBitmap(
            context = context,
            iconRes = anchorIconRes(anchor.icon),
            visibleSizePx = watermarkSizePx,
            cornerRadiusPx = WidgetRoundedShape.Shell.radius.value * density,
        )
    }
    // The theme's primary, mirroring the in-app hero's tint rule: follows the appearance
    // seed instead of clashing with it, and stays legible under the translucent wash.
    // The full-bleed backdrop stacks under the shell's padded content: blooms → scrim →
    // watermark on a flag card, just the watermark on a plain one.
    val backdrop: @Composable () -> Unit = {
        if (blooms != null) {
            Image(
                provider = ImageProvider(blooms),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            RoundedBackgroundBox(
                modifier = GlanceModifier.fillMaxSize(),
                color = colors.widgetScrim,
                shape = WidgetRoundedShape.Shell,
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Image(
                provider = ImageProvider(watermark),
                contentDescription = null,
                modifier = GlanceModifier.size(watermarkSizeDp.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colors.primary),
            )
        }
    }

    WidgetShell(
        scale = scale,
        onClick = actionStartActivity(anchorOpenMilestonesIntent(context)),
        backdrop = backdrop,
    ) {
        cardBody()
    }
}

internal fun buildAnchorWidgetDisplayText(
    context: Context,
    anchor: TrackedDate?,
    today: LocalDate = LocalDate.now(),
): AnchorWidgetDisplayText {
    if (anchor == null) {
        return AnchorWidgetDisplayText.Empty(message = context.getString(R.string.anchor_widget_empty))
    }
    val count = dayCount(date = anchor.date, today = today)
    val dateText = anchor.date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(context.currentAppLocale())
    )
    val directionLine = if (count.isFuture) {
        context.getString(R.string.anchor_widget_planned_for, dateText)
    } else {
        // Same wording as the in-app Milestones screen (spec section 3).
        context.getString(R.string.journal_since_date, dateText)
    }
    val daysText = context.resources.getQuantityString(
        R.plurals.anchor_widget_days, count.magnitude.toInt(), count.magnitude.toInt()
    )
    return AnchorWidgetDisplayText.Loaded(
        anchor = anchor,
        directionLine = directionLine,
        daysText = daysText,
    )
}

// One-shot synchronous compose of an anchor widget instance — the dose-widget twin of
// composeWidgetPreviewRemoteViews. Reuses the same HrtWidgetThemed + AnchorWidgetContent
// the session path renders, at the instance's live launcher size (reference 2×1 size when
// unavailable). Serves both the config screen's live preview and updateAllAnchorWidgets'
// background push. anchor == null renders the empty/"choose a date" state.
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
internal suspend fun composeAnchorRemoteViews(
    context: Context,
    appearance: WidgetAppearance,
    anchor: TrackedDate?,
    backgroundFlag: PrideFlag?,
    appWidgetId: Int,
): WidgetConfigPreviewRender {
    // Live launcher options → the instance's actual cell size (WYSIWYG, matching the dose
    // widgets' preview); invalid id / no options → the fixed reference preview size.
    val size = anchorWidgetPreviewSizeDp(context, appWidgetId)
    val deviceBaselineHeightDp =
        widgetOptionsOrNull(context, appWidgetId)?.let(::portraitBaselineHeightDp)
    // Read the same settings the real widget does rather than assuming the applicationContext
    // resolves them: below API 33 the app context lags AppCompatDelegate.setApplicationLocales,
    // so relying on it would render the preview chrome in the system language even when the real
    // widget honours the app language. adaptiveColorEnabled likewise must reflect the setting so
    // the preview matches the placed widget's palette.
    val settings = runCatching {
        EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            .settingsRepository().getCurrentSettings()
    }.getOrNull()
    val remoteViews = androidx.glance.appwidget.GlanceRemoteViews()
        .compose(context = context, size = size) {
            HrtWidgetThemed(
                context,
                snapshot = null,
                appearance = appearance,
                deviceBaselineHeightDp = deviceBaselineHeightDp,
                adaptiveColorEnabled = settings?.adaptiveColorEnabled ?: true,
                appLanguageTag = settings?.appLanguageOption?.languageTag,
            ) {
                AnchorWidgetContent(
                    displayText = buildAnchorWidgetDisplayText(LocalContext.current, anchor),
                    // Forwarded so a background-pushed empty state taps to reconfigure THIS
                    // instance (same as the session path) instead of the Milestones fallback.
                    appWidgetId = appWidgetId,
                    backgroundFlag = backgroundFlag,
                )
            }
        }.remoteViews
    return WidgetConfigPreviewRender(remoteViews, size)
}

// Opens this widget instance's config so the user can pick/replace its anchor. Falls back
// to the app's Milestones screen when no valid id is available (e.g. the picker preview).
fun anchorReconfigureIntent(context: Context, appWidgetId: Int): android.content.Intent =
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
        anchorOpenMilestonesIntent(context)
    } else {
        android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            // WidgetConfigActivity is exported with the APPWIDGET_CONFIGURE intent-filter,
            // so launching it directly with the id reconfigures this instance.
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
