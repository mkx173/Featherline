package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
        // widget whose anchor is fine.
        val initialAnchors = runCatching {
            journalRepository.awaitTrackedDates()
        }.getOrDefault(emptyList())

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
            HrtWidgetThemed(context, snapshot = null, appearance = appearance) {
                AnchorWidgetContent(
                    anchor = anchor,
                    hasSelection = anchorId != null,
                    appWidgetId = appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID,
                    backgroundFlag = backgroundFlag,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                // A neutral preview anchor for the launcher widget picker.
                AnchorWidgetContent(anchor = null, hasSelection = false)
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
// a baked watermark backdrop. Empty / removed states route to the config Activity (re-pick)
// or the app. Rolls to full days past a year (resolved micro-decision); only the shortcut
// icon rolls to "Ny".
@Composable
internal fun AnchorWidgetContent(
    anchor: TrackedDate?,
    hasSelection: Boolean,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    backgroundFlag: PrideFlag? = null,
) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = LocalWidgetScale.current

    if (anchor == null) {
        // No selection yet, or the selected anchor was deleted: distinct copy, both tap to
        // the config Activity for this instance so the user can (re)choose.
        val message = if (hasSelection) {
            context.getString(R.string.anchor_widget_removed)
        } else {
            context.getString(R.string.anchor_widget_empty)
        }
        WidgetShell(
            scale = scale,
            contentAlignment = Alignment.Center,
            onClick = actionStartActivity(anchorReconfigureIntent(context, appWidgetId)),
        ) {
            Text(
                text = message,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = (15f * scale).sp),
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

    // A gradient flag (chosen in the widget config) replaces the appearance card entirely
    // (mutually exclusive): a LAYERED frost card — day/night base + baked bloom bitmap +
    // day/night scrim — so everything but the blooms flips with the system at apply time.
    // No flag → the appearance-themed WidgetShell card.
    val forcedDark = LocalWidgetForcedDark.current
    // forcedDark is null when the widget follows the system; resolve a concrete dark flag for
    // the bloom bake (light/dark bloom tuning) from the widget process' night-mode
    // configuration. Only the blooms depend on it — the provider layers carry both modes.
    val isDark = forcedDark ?: (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    val backgroundAlpha = LocalWidgetAlpha.current
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)
    val blooms = remember(backgroundFlag, isDark, widthPx, heightPx) {
        backgroundFlag?.let { flag ->
            renderAnchorBloomsBitmap(
                widthPx, heightPx, WidgetRoundedShape.Shell.radius.value * density,
                flagBloomColors(flag, isDark),
            )
        }
    }

    val onSurface =
        if (blooms != null) frostOnSurfaceProvider(forcedDark) else colors.onSurface
    val onSurfaceVariant =
        if (blooms != null) frostOnSurfaceVariantProvider(forcedDark) else colors.onSurfaceVariant

    // Hero stack (spec section 2): name + since-line top-left, day count bottom-right; the
    // glyph is the backdrop watermark, no longer an inline row item. The count is a
    // bottom-end-aligned overlay, NOT the column's last child: pinned to the bottom edge it
    // grows UPWARD when the appearance scale inflates it, instead of clipping below the card.
    val cardBody: @Composable () -> Unit = {
        Box(modifier = GlanceModifier.fillMaxSize()) {
            Column {
                Text(
                    text = anchor.name,
                    style = TextStyle(color = onSurface, fontSize = (15f * scale).sp,
                        fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = directionLine,
                    style = TextStyle(color = onSurfaceVariant, fontSize = (12f * scale).sp),
                    maxLines = 1,
                )
            }
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = daysText,
                    style = TextStyle(color = onSurface, fontSize = (26f * scale).sp,
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
    // Accent-tinted on the appearance card; neutral over a flag frost, matching the in-app
    // hero's "keep the glyph neutral on the wash" rule.
    val watermarkTint = if (blooms != null) {
        frostOnSurfaceVariantProvider(forcedDark)
    } else {
        groupAccentColor(anchor.palette, forcedDark)
    }
    val watermarkImage: @Composable () -> Unit = {
        Image(
            provider = ImageProvider(watermark),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(watermarkTint),
        )
    }

    if (blooms != null) {
        // Layered frost card: day/night base → baked blooms → day/night scrim → watermark →
        // content. The provider layers flip with the system; only the blooms are baked.
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(WidgetRoundedShape.Shell.radius)
                .clickable(actionStartActivity(anchorOpenMilestonesIntent(context))),
        ) {
            RoundedBackgroundBox(
                modifier = GlanceModifier.fillMaxSize(),
                color = frostBaseProvider(forcedDark, backgroundAlpha),
                shape = WidgetRoundedShape.Shell,
            )
            Image(
                provider = ImageProvider(blooms),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            RoundedBackgroundBox(
                modifier = GlanceModifier.fillMaxSize(),
                color = frostScrimProvider(forcedDark, backgroundAlpha),
                shape = WidgetRoundedShape.Shell,
            )
            watermarkImage()
            // 12.dp matches WidgetShell's inset so the frost and appearance cards align.
            Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) { cardBody() }
        }
    } else {
        WidgetShell(
            scale = scale,
            onClick = actionStartActivity(anchorOpenMilestonesIntent(context)),
            backdrop = watermarkImage,
        ) {
            cardBody()
        }
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
    val remoteViews = androidx.glance.appwidget.GlanceRemoteViews()
        .compose(context = context, size = size) {
            HrtWidgetThemed(context, snapshot = null, appearance = appearance) {
                AnchorWidgetContent(
                    anchor = anchor,
                    hasSelection = anchor != null,
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
