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
import androidx.glance.GlanceTheme
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.journal.dayCount
import com.mkx.hrttracker.ui.journal.anchorIconRes
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.glance.appwidget.updateAll as glanceUpdateAll

// 2×1 anchor widget. Per-instance state is just the anchorId (Task 4); name/icon/palette/
// date are read live from the journal at composition. Chrome (colour/scale/alpha/dark)
// comes from the shared widget appearance via HrtWidgetThemed.
internal val ANCHOR_WIDGET_PREVIEW_SIZE = DpSize(306.dp, 138.dp)

// Ceiling for the blocking tracked-dates load in provideGlance: long enough for a cold
// SQLCipher open + first query in a widget-only process, short enough that a failed open
// falls back to the snapshot well within Glance's render budget instead of hanging.
// ponytail: fixed knob; revisit if slow devices fall back to the snapshot on cold render.
private const val ANCHOR_WIDGET_AWAIT_TIMEOUT_MS = 5_000L

// The anchor is one cell tall, so scale == 1.0 resolves against its own preview viewport
// height rather than the dose widgets' 276dp reference.
private val ANCHOR_WIDGET_BASELINE_REFERENCE_DP = ANCHOR_WIDGET_PREVIEW_SIZE.height.value

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
        val initialAppearance = runCatching {
            appearanceRepository.currentEffective(appWidgetId)
        }.getOrDefault(WidgetAppearance.Default)
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
            val anchors by remember(journalRepository) { journalRepository.observeLoadedTrackedDates() }
                .collectAsState(initial = initialAnchors)
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
            ) {
                AnchorWidgetContent(
                    anchor = anchor,
                    appWidgetId = appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID,
                    backgroundFlag = backgroundFlag,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                // A neutral preview anchor for the launcher widget picker. The preview
                // composes at the fixed reference size, so pin the baseline to it rather
                // than letting a captured device baseline leak in.
                CompositionLocalProvider(
                    LocalPreviewBaselineHeight provides ANCHOR_WIDGET_BASELINE_REFERENCE_DP,
                ) {
                    AnchorWidgetContent(anchor = null)
                }
            }
        }
    }
}

class HrtAnchorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtAnchorWidget()
}

suspend fun updateAllAnchorWidgets(context: Context) {
    HrtAnchorWidget().glanceUpdateAll(context)
}

// Hero stack: name + since/planned-for top-left, day count bottom-right, with the glyph as
// a baked watermark backdrop. A null anchor (never chosen, or since deleted) shows one
// select-a-date state that taps to the config Activity. Always shows full days, even past
// a year.
@Composable
internal fun AnchorWidgetContent(
    anchor: TrackedDate?,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    backgroundFlag: PrideFlag? = null,
) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetScale(WIDGET_BASELINE_KEY_ANCHOR, ANCHOR_WIDGET_BASELINE_REFERENCE_DP)

    if (anchor == null) {
        // No selection yet, or the selected anchor was deleted: tap opens the config
        // Activity for this instance so the user can (re)choose.
        val message = context.getString(R.string.anchor_widget_empty)
        WidgetShell(
            scale = scale,
            contentAlignment = Alignment.Center,
            onClick = actionStartActivity(anchorReconfigureIntent(context, appWidgetId)),
        ) {
            Text(
                text = message,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = (16f * scale).sp),
            )
        }
        return
    }

    val today = LocalDate.now()
    val count = dayCount(date = anchor.date, today = today)
    val dateText = anchor.date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    )
    // In the hero stack the line has the card's full content width, so no fit ladder —
    // Glance ellipsizes on extreme launcher resizes.
    val directionLine = if (count.isFuture) {
        context.getString(R.string.anchor_widget_planned_for, dateText)
    } else {
        // Same wording as the in-app Milestones screen (spec section 3).
        context.getString(R.string.journal_since_date, dateText)
    }
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val daysText = context.resources.getQuantityString(
        R.plurals.anchor_widget_days, count.magnitude.toInt(), count.magnitude.toInt()
    )

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
                    text = directionLine,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = (16f * scale).sp),
                    maxLines = 1,
                )
            }
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = daysText,
                    style = TextStyle(color = colors.onSurface, fontSize = (32f * scale).sp,
                        fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }

    // Glyph watermark bled off the top-right corner, baked to a bitmap (Glance has no
    // alpha/offset modifiers) and layered under the padded content. The bitmap is white ink;
    // the colour comes from the ColorFilter below so the launcher resolves day/night at
    // RemoteViews apply time and the widget flips with the system without a recompose.
    val watermark = remember(anchor.icon, widthPx, heightPx) {
        renderAnchorWatermarkBitmap(
            context = context,
            iconRes = anchorIconRes(anchor.icon),
            widthPx = widthPx,
            heightPx = heightPx,
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
        Image(
            provider = ImageProvider(watermark),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colors.primary),
        )
    }

    WidgetShell(
        scale = scale,
        onClick = actionStartActivity(anchorOpenMilestonesIntent(context)),
        backdrop = backdrop,
    ) {
        cardBody()
    }
}

// Live anchor preview for the config screen — the dose-widget twin of
// composeWidgetPreviewRemoteViews, reusing the same HrtWidgetThemed + AnchorWidgetContent
// the real widget renders, at the instance's live launcher size (reference 2×1 size when
// unavailable). anchor == null renders the empty/"choose a date" state (Save stays
// disabled until a real anchor is picked).
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
internal suspend fun composeAnchorPreviewRemoteViews(
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
    val remoteViews = androidx.glance.appwidget.GlanceRemoteViews()
        .compose(context = context, size = size) {
            HrtWidgetThemed(
                context,
                snapshot = null,
                appearance = appearance,
                deviceBaselineHeightDp = deviceBaselineHeightDp,
            ) {
                AnchorWidgetContent(
                    anchor = anchor,
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
